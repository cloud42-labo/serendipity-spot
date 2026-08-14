package com.cloud42labo.serendipityspot.ui

import android.app.Activity
import android.app.Application
import android.content.Intent
import android.content.IntentSender
import androidx.credentials.exceptions.GetCredentialCancellationException
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.cloud42labo.serendipityspot.auth.AuthorizationOutcome
import com.cloud42labo.serendipityspot.auth.GoogleAuthManager
import com.cloud42labo.serendipityspot.auth.SignedInUser
import com.cloud42labo.serendipityspot.data.DirectionsRepository
import com.cloud42labo.serendipityspot.data.PlaceResult
import com.cloud42labo.serendipityspot.data.PlaceSearchOutcome
import com.cloud42labo.serendipityspot.data.PlaceSearcher
import com.cloud42labo.serendipityspot.data.RouteInfo
import com.cloud42labo.serendipityspot.data.SheetsRepository
import com.cloud42labo.serendipityspot.data.SpotLocalCache
import com.cloud42labo.serendipityspot.notification.NotificationHelper
import com.cloud42labo.serendipityspot.data.Spot
import com.cloud42labo.serendipityspot.location.GeofenceHelper
import com.cloud42labo.serendipityspot.share.SharedPlace
import com.cloud42labo.serendipityspot.share.ShareTextParser
import com.google.android.gms.location.LocationServices
import com.google.android.gms.maps.model.LatLng
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await

data class SpotUiState(
    val user: SignedInUser? = null,
    val spots: List<Spot> = emptyList(),
    val isLoading: Boolean = false,
    val errorMessage: String? = null,
    val focusSpotId: String? = null,
    // 通知が来ないときの切り分け用。アプリの動作には影響しない。
    val lastRegistration: String? = null,
    val lastGeofenceEvent: String? = null,
    val searchResults: List<PlaceResult> = emptyList(),
    val isSearching: Boolean = false,
    val routeToSpot: RouteInfo? = null,
    val isLoadingRoute: Boolean = false,
    // 登録直後の確認表示用。連続で同じ名前を登録しても毎回出るよう、
    // 表示側が消費したら都度nullへ戻す（focusSpotIdと同じ消費パターン）。
    val registeredSpotTitle: String? = null,
    // 共有（ACTION_SEND）で受け取った内容の解析結果。表示側が消費したら null へ戻す
    // （focusSpotId と同じ消費パターン）。Unparsable も「解析できなかった」という
    // 結果として載せる。捨ててしまうと画面側がフォールバックを出せないため。
    val sharedPlace: SharedPlace? = null,
)

class SpotViewModel(application: Application) : AndroidViewModel(application) {

    private val authManager = GoogleAuthManager(application)
    private val repository = SheetsRepository(application)
    private val geofenceHelper = GeofenceHelper(application)
    private val placeSearcher = PlaceSearcher(application)
    private val directionsRepository = DirectionsRepository(application)

    private var spreadsheetId: String? = null

    private val _uiState = MutableStateFlow(SpotUiState())
    val uiState: StateFlow<SpotUiState> = _uiState.asStateFlow()

    /** Sheets/Driveの許可画面をActivityに出してもらうための依頼。 */
    private val _consentRequests = Channel<IntentSender>(Channel.BUFFERED)
    val consentRequests = _consentRequests.receiveAsFlow()

    init {
        authManager.cachedUser()?.let { user ->
            _uiState.update { it.copy(user = user) }
            refresh()
        }
    }

    fun signIn(activity: Activity) {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, errorMessage = null) }
            runCatching { authManager.signIn(activity) }
                .onSuccess { user ->
                    _uiState.update { it.copy(user = user, isLoading = false) }
                    loadSpots()
                }
                .onFailure { error ->
                    // ユーザーが自分でダイアログを閉じた場合はエラー表示しない
                    val message = if (error is GetCredentialCancellationException) null
                    else error.message ?: "サインインに失敗しました"
                    _uiState.update { it.copy(isLoading = false, errorMessage = message) }
                }
        }
    }

    fun signOut() {
        viewModelScope.launch {
            runCatching { authManager.signOut() }
            spreadsheetId = null
            _uiState.value = SpotUiState()
        }
    }

    fun onAuthorizationResult(data: Intent?) {
        viewModelScope.launch {
            val outcome = runCatching { authManager.authorizationResultFrom(data) }.getOrElse { error ->
                fail(error, "アクセス許可を確認できませんでした")
                return@launch
            }
            when (outcome) {
                is AuthorizationOutcome.Granted -> loadSpots()
                is AuthorizationOutcome.ConsentRequired -> _uiState.update {
                    it.copy(
                        isLoading = false,
                        errorMessage = "スプレッドシートへのアクセスを許可すると利用できます",
                    )
                }
            }
        }
    }

    /** 診断表示を最新にする。SharedPreferences から読み直すだけ。 */
    fun refreshDiagnostics() {
        val app = getApplication<Application>()
        _uiState.update {
            it.copy(
                lastRegistration = SpotLocalCache.loadLastRegistration(app),
                lastGeofenceEvent = SpotLocalCache.loadLastGeofenceEvent(app),
            )
        }
    }

    /**
     * 通知を出す経路が生きているかだけを確かめる。
     * ジオフェンスを経由せず、受信側と同じ NotificationHelper を直接呼ぶ。
     */
    fun sendTestNotification() {
        val app = getApplication<Application>()
        val spot = _uiState.value.spots.firstOrNull()
            ?: Spot(id = "test", lat = 0.0, lng = 0.0, title = "テスト", memo = "通知の確認用")
        NotificationHelper.ensureChannel(app)
        NotificationHelper.notifyNearby(app, spot)
    }

    /**
     * 住所・駅名・施設名から候補を引く。サインイン不要。
     * [nearLat]/[nearLng] は今見ている地図の中心。近くを優先して探すために渡す。
     *
     * 中心が信用できない場面（共有からのコールド起動直後で、まだ現在地が確定しておらず
     * 地図がフォールバック座標のまま）では null を渡す。中心±0.5°に絞ったまま探すと、
     * 遠方から共有された場所が「見つかりませんでした」になるため（Codexレビュー指摘）。
     */
    fun searchPlaces(query: String, nearLat: Double?, nearLng: Double?) {
        if (query.isBlank()) {
            clearSearchResults()
            return
        }
        // 検索を連打・打ち直しできる導線なので、前回の検索は必ず捨てる。
        // 残すと古い結果が後から届いて新しい検索結果を上書きしうる（routeJobと同じ理由）。
        searchJob?.cancel()
        searchJob = viewModelScope.launch {
            // 開始時に前回のメッセージを消す。同じ語で続けて失敗しても
            // null → メッセージ と値が動くので、表示側の LaunchedEffect が再実行される。
            _uiState.update { it.copy(isSearching = true, errorMessage = null) }
            val outcome = runCatching { placeSearcher.search(query, nearLat, nearLng) }
                .getOrElse { PlaceSearchOutcome.Failed(it) }
            val results = (outcome as? PlaceSearchOutcome.Success)?.results.orEmpty()
            _uiState.update {
                it.copy(
                    isSearching = false,
                    searchResults = results,
                    errorMessage = SearchFeedback.messageFor(outcome, query) ?: it.errorMessage,
                )
            }
        }
    }

    fun clearSearchResults() {
        searchJob?.cancel()
        _uiState.update { it.copy(searchResults = emptyList(), isSearching = false) }
    }

    /**
     * 表示済みのメッセージを消す。消さないと同じ文言が続けて出たときに
     * 状態が変化せず、2回目以降のスナックバーが出ない（BUG-SPOT-03-01）。
     */
    fun consumeErrorMessage() {
        _uiState.update { it.copy(errorMessage = null) }
    }

    fun consumeFocusRequest() {
        _uiState.update { it.copy(focusSpotId = null) }
    }

    fun consumeRegistrationConfirmation() {
        _uiState.update { it.copy(registeredSpotTitle = null) }
    }

    /** 共有起動で本文を受け取ったときに呼ばれる。空白のみは無視する。その場で解析する。 */
    fun onSharedText(text: String) {
        if (text.isBlank()) return
        _uiState.update { it.copy(sharedPlace = ShareTextParser.parse(text)) }
    }

    fun consumeSharedPlace() {
        _uiState.update { it.copy(sharedPlace = null) }
    }

    // スポットAの取得中にBへ選び直すと、Aの結果が後から届いてBのカードに
    // 誤表示されうる（Codexレビュー指摘）。新しい取得を始める前に必ず前回分を
    // キャンセルし、常に最新の1件だけが uiState に反映されるようにする。
    private var routeJob: Job? = null

    /** 進行中の検索。新しい検索を始めるときに捨てる。 */
    private var searchJob: Job? = null

    /** 通知タップ時にだけ呼ばれる。フォーカスと同時に現在地からの徒歩ルートも取りに行く。 */
    fun focusSpot(spotId: String) {
        _uiState.update { it.copy(focusSpotId = spotId) }
        fetchRouteToSpot(spotId)
    }

    fun clearRoute() {
        routeJob?.cancel()
        _uiState.update { it.copy(routeToSpot = null, isLoadingRoute = false) }
    }

    /** 地図上でスポットを選んだときに呼ばれる。focusSpotとは違い、カメラは動かさない。 */
    fun requestRoute(spotId: String) {
        fetchRouteToSpot(spotId)
    }

    private fun fetchRouteToSpot(spotId: String) {
        val spot = _uiState.value.spots.firstOrNull { it.id == spotId } ?: return
        routeJob?.cancel()
        routeJob = viewModelScope.launch {
            _uiState.update { it.copy(isLoadingRoute = true) }
            val client = LocationServices.getFusedLocationProviderClient(getApplication())
            val location = runCatching { client.lastLocation.await() }.getOrNull()
            val route = location?.let {
                directionsRepository.getWalkingRoute(
                    origin = LatLng(it.latitude, it.longitude),
                    destination = LatLng(spot.lat, spot.lng),
                )
            }
            _uiState.update {
                it.copy(
                    isLoadingRoute = false,
                    routeToSpot = route,
                    errorMessage = if (route == null) "経路を取得できませんでした" else it.errorMessage,
                )
            }
        }
    }

    fun refresh() {
        if (_uiState.value.user == null) return
        viewModelScope.launch { loadSpots() }
    }

    fun addSpot(lat: Double, lng: Double, title: String, memo: String) {
        if (_uiState.value.user == null || title.isBlank()) return
        val id = spreadsheetId ?: return
        viewModelScope.launch {
            val token = requireAccessToken() ?: return@launch

            runCatching {
                val sheets = authManager.sheetsService(token)
                repository.appendSpot(sheets, id, lat, lng, title, memo)
            }.onSuccess {
                // 追加直後の Spot は行番号を持たない（編集・削除ができない）。
                // シートから読み直して確定させる。ジオフェンスとキャッシュもここで揃う。
                loadSpots()
                _uiState.update { it.copy(registeredSpotTitle = title) }
            }.onFailure { error -> fail(error, "保存に失敗しました") }
        }
    }

    fun editSpot(spot: Spot, title: String, memo: String) {
        if (_uiState.value.user == null || title.isBlank()) return
        val id = spreadsheetId ?: return
        viewModelScope.launch {
            val token = requireAccessToken() ?: return@launch
            runCatching {
                val sheets = authManager.sheetsService(token)
                repository.updateSpotText(sheets, id, spot, title, memo)
            }.onSuccess { updated ->
                if (updated) loadSpots() else failStale()
            }.onFailure { error -> fail(error, "更新に失敗しました") }
        }
    }

    fun deleteSpot(spot: Spot) {
        if (_uiState.value.user == null) return
        val id = spreadsheetId ?: return
        viewModelScope.launch {
            val token = requireAccessToken() ?: return@launch
            runCatching {
                val sheets = authManager.sheetsService(token)
                repository.deleteSpot(sheets, id, spot)
            }.onSuccess { deleted ->
                if (deleted) loadSpots() else failStale()
            }.onFailure { error -> fail(error, "削除に失敗しました") }
        }
    }

    /** シート上の行が特定できないとき。読み直せば直る。 */
    private fun failStale() {
        _uiState.update {
            it.copy(isLoading = false, errorMessage = "対象の行を特定できませんでした。読み込み直してください")
        }
    }

    private suspend fun loadSpots() {
        val token = requireAccessToken() ?: return
        runCatching {
            val sheets = authManager.sheetsService(token)
            val drive = authManager.driveService(token)
            val id = spreadsheetId
                ?: repository.ensureSpreadsheet(sheets, drive).also { spreadsheetId = it }
            val spots = repository.loadSpots(sheets, id)
            geofenceHelper.resync(spots)
            spots
        }.onSuccess { spots ->
            _uiState.update { it.copy(spots = spots, isLoading = false) }
            refreshDiagnostics()
        }.onFailure { error -> fail(error, "読み込みに失敗しました") }
    }

    /**
     * アクセストークンを取る。まだ許可されていなければ許可画面をActivityに依頼し、
     * nullを返す（結果は[onAuthorizationResult]に戻ってくる）。
     */
    private suspend fun requireAccessToken(): String? {
        _uiState.update { it.copy(isLoading = true, errorMessage = null) }

        val outcome = runCatching { authManager.authorize() }.getOrElse { error ->
            fail(error, "アクセス許可の取得に失敗しました")
            return null
        }

        return when (outcome) {
            is AuthorizationOutcome.Granted -> outcome.accessToken
            is AuthorizationOutcome.ConsentRequired -> {
                _consentRequests.send(outcome.intentSender)
                _uiState.update { it.copy(isLoading = false) }
                null
            }
        }
    }

    private fun fail(error: Throwable, fallback: String) {
        _uiState.update { it.copy(isLoading = false, errorMessage = error.message ?: fallback) }
    }
}
