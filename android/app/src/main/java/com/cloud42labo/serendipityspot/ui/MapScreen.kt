package com.cloud42labo.serendipityspot.ui

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.net.Uri
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.BottomSheetScaffold
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberBottomSheetScaffoldState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.core.graphics.drawable.DrawableCompat
import com.cloud42labo.serendipityspot.R
import com.cloud42labo.serendipityspot.data.Spot
import com.cloud42labo.serendipityspot.ui.components.AppTextField
import com.cloud42labo.serendipityspot.ui.theme.Spacing
import com.google.android.gms.location.LocationServices
import com.google.android.gms.maps.MapsInitializer
import com.google.android.gms.maps.model.BitmapDescriptor
import com.google.android.gms.maps.model.BitmapDescriptorFactory
import com.google.android.gms.maps.model.CameraPosition
import com.google.android.gms.maps.model.LatLng
import com.google.maps.android.compose.GoogleMap
import com.google.maps.android.compose.MapProperties
import com.google.maps.android.compose.MapUiSettings
import com.google.maps.android.compose.Marker
import com.google.maps.android.compose.MarkerState
import com.google.maps.android.compose.Polyline
import com.google.maps.android.compose.rememberCameraPositionState
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await

/** 現在地が取れるまでの暫定表示（渋谷）。取れ次第そちらへ移す。 */
private val FALLBACK_CENTER = LatLng(35.6581, 139.7017)
private const val SPOT_ZOOM = 17f
private const val CURRENT_LOCATION_ZOOM = 16f

/**
 * `ic_flag_pin.xml` の viewport(24x24) のうち、実際に絵がある範囲。
 * 竿 x5.2..7.0 / y2.5..21.5、旗 x7.0..18.4 / y3.2..12.0 の外接矩形。
 * ここを変えたら [FLAG_ANCHOR] も計算し直すこと。
 */
private const val FLAG_VIEWPORT = 24f
private const val FLAG_INK_LEFT = 5.2f
private const val FLAG_INK_TOP = 2.5f
private const val FLAG_INK_RIGHT = 18.4f
private const val FLAG_INK_BOTTOM = 21.5f

/**
 * 竿の根元が地点を指すように合わせる。切り出したビットマップ内での、
 * 竿の中心x = (6.1-5.2)/13.2 ≒ 0.068、竿の下端y = 1.0。
 */
private val FLAG_ANCHOR = Offset(0.068f, 1.0f)

@OptIn(ExperimentalMaterial3Api::class)
@SuppressLint("MissingPermission")
@Composable
fun MapScreen(
    uiState: SpotUiState,
    hasLocationPermission: Boolean,
    onSignInClick: () -> Unit,
    onSignOutClick: () -> Unit,
    onSaveSpot: (lat: Double, lng: Double, title: String, memo: String) -> Unit,
    onEditSpot: (spot: Spot, title: String, memo: String) -> Unit,
    onDeleteSpot: (spot: Spot) -> Unit,
    onTestNotification: () -> Unit,
    onRefreshDiagnostics: () -> Unit,
    onSearch: (query: String, nearLat: Double, nearLng: Double) -> Unit,
    onClearSearch: () -> Unit,
    onFocusConsumed: () -> Unit,
    onClearRoute: () -> Unit,
    onRequestRoute: (spotId: String) -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val scaffoldState = rememberBottomSheetScaffoldState()
    val snackbarHostState = remember { SnackbarHostState() }
    val keyboardController = LocalSoftwareKeyboardController.current

    val cameraPositionState = rememberCameraPositionState {
        position = CameraPosition.fromLatLngZoom(FALLBACK_CENTER, CURRENT_LOCATION_ZOOM)
    }

    var pendingLatLng by remember { mutableStateOf<LatLng?>(null) }
    // 検索から来た場合は候補の名前を初期値にする。地図タップなら空。
    var pendingTitle by remember { mutableStateOf("") }
    var editingSpot by remember { mutableStateOf<Spot?>(null) }
    var searchMode by remember { mutableStateOf(false) }
    // 候補の一覧を出しているか。1つ選んだら閉じるが、旗は地図に残す。
    var resultListVisible by remember { mutableStateOf(false) }
    var query by remember { mutableStateOf("") }
    var deletingSpot by remember { mutableStateOf<Spot?>(null) }
    // タップまたは通知で選ばれている登録済みスポットのID。地図下部のカードに情報と
    // 主要アクション（経路・ストリートビュー・編集・削除）をまとめて出す。
    // Spotそのものではなくidで持ち、編集直後もuiState.spotsから最新の内容を引く。
    // 画面回転などでの再生成後も選択・カードが残るようrememberSaveableで持つ
    // （経路はViewModel側のuiStateに残り続けるため、ここが消えると閉じ手段のない
    // 経路表示だけが残ってしまう）。
    var selectedSpotId by rememberSaveable { mutableStateOf<String?>(null) }
    val selectedSpot = selectedSpotId?.let { id -> uiState.spots.firstOrNull { it.id == id } }
    // 初回だけ現在地へ寄せる。以後はユーザーの操作を邪魔しない。
    var initialLocationApplied by rememberSaveable { mutableStateOf(false) }
    // 「タップしたのに何も起きない」を端末上で切り分けるための記録。
    // 地図に届いたのか、旗に吸われたのかが診断欄に出る。通知の診断と同じ考え方。
    var lastMapEvent by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(hasLocationPermission) {
        if (!hasLocationPermission || initialLocationApplied) return@LaunchedEffect
        val client = LocationServices.getFusedLocationProviderClient(context)
        val location = runCatching { client.lastLocation.await() }.getOrNull()
        if (location != null) {
            cameraPositionState.position = CameraPosition.fromLatLngZoom(
                LatLng(location.latitude, location.longitude),
                CURRENT_LOCATION_ZOOM,
            )
        }
        initialLocationApplied = true
    }

    LaunchedEffect(uiState.focusSpotId) {
        val target = uiState.spots.firstOrNull { it.id == uiState.focusSpotId } ?: return@LaunchedEffect
        cameraPositionState.position =
            CameraPosition.fromLatLngZoom(LatLng(target.lat, target.lng), SPOT_ZOOM)
        // 通知タップも「地図上でスポットを選んだ」のと同じ扱いにし、
        // 同じ情報・アクションカードを出す。
        selectedSpotId = target.id
        onFocusConsumed()
    }

    LaunchedEffect(uiState.searchResults) {
        if (uiState.searchResults.isNotEmpty()) resultListVisible = true
    }

    LaunchedEffect(uiState.errorMessage) {
        uiState.errorMessage?.let { snackbarHostState.showSnackbar(it) }
    }

    val signedIn = uiState.user != null

    // 登録済みは濃く、検索で出ただけの候補は薄く。
    // 「まだ立っていない旗」をタップすると登録に進む、という見え方にする。
    // 選択中は同じ色のまま一回り大きくして、他の登録済みと見分けられるようにする。
    val plantedColor = MaterialTheme.colorScheme.primary
    val unplantedColor = MaterialTheme.colorScheme.outline
    val plantedFlag = remember(plantedColor) { flagDescriptor(context, plantedColor.toArgb()) }
    val unplantedFlag = remember(unplantedColor) { flagDescriptor(context, unplantedColor.toArgb()) }
    val selectedFlag = remember(plantedColor) {
        flagDescriptor(context, plantedColor.toArgb(), heightDp = 56)
    }

    BottomSheetScaffold(
        scaffoldState = scaffoldState,
        snackbarHost = { SnackbarHost(snackbarHostState) },
        sheetPeekHeight = if (signedIn) 112.dp else 0.dp,
        topBar = {
            TopAppBar(
                title = {
                    if (searchMode) {
                        // 虫めがねを押しても、キーボードのEnter（検索キー）を押しても同じ。
                        val runSearch: () -> Unit = {
                            val center = cameraPositionState.position.target
                            onSearch(query, center.latitude, center.longitude)
                            keyboardController?.hide()
                        }
                        AppTextField(
                            value = query,
                            onValueChange = { query = it },
                            placeholder = { Text("住所・駅名・施設名") },
                            modifier = Modifier.fillMaxWidth(),
                            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                            keyboardActions = KeyboardActions(onSearch = { runSearch() }),
                            trailingIcon = {
                                IconButton(onClick = runSearch) {
                                    Icon(Icons.Filled.Search, contentDescription = "検索する")
                                }
                            },
                        )
                    } else {
                        Text("ついでにスポット")
                    }
                },
                actions = {
                    if (searchMode) {
                        IconButton(onClick = {
                            searchMode = false
                            query = ""
                            resultListVisible = false
                            onClearSearch()
                        }) {
                            Icon(Icons.Filled.Close, contentDescription = "検索を閉じる")
                        }
                    } else {
                        IconButton(onClick = { searchMode = true }) {
                            Icon(Icons.Filled.Search, contentDescription = "場所を検索")
                        }
                        if (signedIn) {
                            TextButton(onClick = onSignOutClick) { Text("ログアウト") }
                        }
                    }
                },
            )
        },
        sheetContent = {
            SpotListSheet(
                spots = uiState.spots,
                lastRegistration = uiState.lastRegistration,
                lastGeofenceEvent = uiState.lastGeofenceEvent,
                lastMapEvent = lastMapEvent,
                onTestNotification = onTestNotification,
                onRefreshDiagnostics = onRefreshDiagnostics,
                onEditClick = { editingSpot = it },
                onDeleteClick = { deletingSpot = it },
                onStreetViewClick = { openStreetView(context, it.lat, it.lng) },
                onSpotClick = { spot ->
                    cameraPositionState.position =
                        CameraPosition.fromLatLngZoom(LatLng(spot.lat, spot.lng), SPOT_ZOOM)
                    if (selectedSpotId != spot.id) onClearRoute()
                    selectedSpotId = spot.id
                    scope.launch { scaffoldState.bottomSheetState.partialExpand() }
                },
            )
        },
    ) { padding ->
        Box(modifier = Modifier.fillMaxSize().padding(padding)) {
            GoogleMap(
                modifier = Modifier.fillMaxSize(),
                cameraPositionState = cameraPositionState,
                properties = MapProperties(isMyLocationEnabled = hasLocationPermission && signedIn),
                // 標準の現在地ボタンは地図の右上に固定され動かせないため使わない。
                // 代わりに同じ働きの FAB を右下（親指の届く位置）に置く。
                uiSettings = MapUiSettings(
                    zoomControlsEnabled = false,
                    myLocationButtonEnabled = false,
                ),
                onMapClick = { latLng ->
                    lastMapEvent = "地図タップ: %.5f, %.5f".format(latLng.latitude, latLng.longitude)
                    if (signedIn) {
                        pendingTitle = ""
                        pendingLatLng = latLng
                        selectedSpotId = null
                        onClearRoute()
                    }
                },
            ) {
                uiState.spots.forEach { spot ->
                    // MarkerState をその場で作ると再コンポーズのたびに作り直され、
                    // 目印の再生成でタップを取りこぼすことがある。位置が変わるまで使い回す。
                    val markerState = remember(spot.id, spot.lat, spot.lng) {
                        MarkerState(LatLng(spot.lat, spot.lng))
                    }
                    Marker(
                        state = markerState,
                        title = spot.title,
                        snippet = spot.memo,
                        icon = if (spot.id == selectedSpotId) selectedFlag else plantedFlag,
                        anchor = FLAG_ANCHOR,
                        onClick = {
                            // true を返して既定動作を止める。既定では吹き出しを出すのに
                            // 加えて「そのマーカーが中心に来るようカメラを動かす」ため、
                            // 地図が動いて次のタップ位置がずれる。代わりに地図下部のカードで
                            // 情報とアクションをまとめて出す。
                            lastMapEvent = "旗タップ: ${spot.title}"
                            if (selectedSpotId != spot.id) onClearRoute()
                            selectedSpotId = spot.id
                            true
                        },
                    )
                }

                // 検索で当たった場所。まだ立っていない旗として置く。
                // タップすると登録に進み、保存されたら立った旗に変わる。
                if (signedIn) {
                    uiState.searchResults.forEach { result ->
                        val markerState = remember(result.lat, result.lng, result.name) {
                            MarkerState(LatLng(result.lat, result.lng))
                        }
                        Marker(
                            state = markerState,
                            title = result.name,
                            snippet = "タップして登録",
                            icon = unplantedFlag,
                            anchor = FLAG_ANCHOR,
                            onClick = {
                                // 薄い旗をタップしたら、そのまま登録画面へ進む。
                                // true を返して既定の吹き出し表示を抑える。
                                lastMapEvent = "候補タップ: ${result.name}"
                                pendingTitle = result.name
                                pendingLatLng = LatLng(result.lat, result.lng)
                                selectedSpotId = null
                                onClearRoute()
                                true
                            },
                        )
                    }
                }

                uiState.routeToSpot?.let { route ->
                    Polyline(
                        points = route.points,
                        color = plantedColor,
                        width = 10f,
                    )
                }
            }

            if (!signedIn) {
                SignInOverlay(onSignInClick = onSignInClick)
            }

            selectedSpot?.let { spot ->
                SelectedSpotCard(
                    spot = spot,
                    route = uiState.routeToSpot,
                    isLoadingRoute = uiState.isLoadingRoute,
                    onRequestRoute = { onRequestRoute(spot.id) },
                    onStreetView = { openStreetView(context, spot.lat, spot.lng) },
                    onEdit = {
                        editingSpot = spot
                        selectedSpotId = null
                    },
                    onDelete = {
                        deletingSpot = spot
                        selectedSpotId = null
                    },
                    onClose = {
                        selectedSpotId = null
                        onClearRoute()
                    },
                    // 右下の現在地FAB（56dp + 余白16dp）より上に置いて重ならないようにする。
                    modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 88.dp),
                )
            }

            if (resultListVisible && uiState.searchResults.isNotEmpty()) {
                SearchResults(
                    results = uiState.searchResults,
                    onPick = { result ->
                        cameraPositionState.position =
                            CameraPosition.fromLatLngZoom(LatLng(result.lat, result.lng), SPOT_ZOOM)
                        searchMode = false
                        query = ""
                        // 一覧は閉じるが、候補は地図上に「まだ立っていない旗」として残す。
                        // 登録するかは旗をタップして決める。
                        resultListVisible = false
                    },
                    modifier = Modifier.align(Alignment.TopCenter),
                )
            }

            // 地図の中心の照準。「＋」で登録したときにどこへ立つのかを示す。
            if (signedIn) {
                Icon(
                    imageVector = Icons.Filled.Add,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.align(Alignment.Center).size(20.dp),
                )
            }

            if (signedIn) {
                Column(
                    horizontalAlignment = Alignment.End,
                    modifier = Modifier.align(Alignment.BottomEnd).padding(Spacing.lg),
                ) {
                    // 地図タップに頼らない確実な入口。Maps SDK のタップ経路に
                    // 依存しないので、「地図タップが効かない」ときの切り分けにも使える。
                    FloatingActionButton(
                        onClick = {
                            val center = cameraPositionState.position.target
                            lastMapEvent = "＋ボタン: %.5f, %.5f".format(center.latitude, center.longitude)
                            pendingTitle = ""
                            pendingLatLng = center
                            selectedSpotId = null
                            onClearRoute()
                        },
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Add,
                            contentDescription = "地図の中心にスポットを登録",
                        )
                    }

                    if (hasLocationPermission) {
                        Spacer(modifier = Modifier.height(Spacing.md))
                        FloatingActionButton(
                            onClick = {
                                scope.launch {
                                    val client = LocationServices.getFusedLocationProviderClient(context)
                                    val location = runCatching { client.lastLocation.await() }.getOrNull()
                                    if (location != null) {
                                        cameraPositionState.position = CameraPosition.fromLatLngZoom(
                                            LatLng(location.latitude, location.longitude),
                                            CURRENT_LOCATION_ZOOM,
                                        )
                                    } else {
                                        snackbarHostState.showSnackbar("現在地を取得できませんでした")
                                    }
                                }
                            },
                        ) {
                            Icon(
                                imageVector = Icons.Filled.LocationOn,
                                contentDescription = "現在地へ移動",
                            )
                        }
                    }
                }
            }

            if (uiState.isLoading) {
                CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
            }
        }
    }

    editingSpot?.let { spot ->
        EditSpotDialog(
            spot = spot,
            onDismiss = { editingSpot = null },
            onSave = { title, memo ->
                onEditSpot(spot, title, memo)
                editingSpot = null
            },
        )
    }

    deletingSpot?.let { spot ->
        DeleteSpotDialog(
            spot = spot,
            onDismiss = { deletingSpot = null },
            onConfirm = {
                onDeleteSpot(spot)
                deletingSpot = null
            },
        )
    }

    pendingLatLng?.let { latLng ->
        RegisterSheet(
            initialTitle = pendingTitle,
            onDismiss = { pendingLatLng = null },
            onSave = { title, memo ->
                onSaveSpot(latLng.latitude, latLng.longitude, title, memo)
                pendingLatLng = null
                // 立った旗に置き換わるので、候補の旗は片付ける
                onClearSearch()
            },
        )
    }
}

/**
 * 指定地点のストリートビューを開く。
 *
 * アプリ内に埋め込む手もあるが、Googleマップ本体の方が表示が速く操作にも慣れがある。
 * Googleマップが無い端末のためにブラウザへ落とす。
 */
private fun openStreetView(context: Context, lat: Double, lng: Double) {
    val inMaps = Intent(Intent.ACTION_VIEW, Uri.parse("google.streetview:cbll=$lat,$lng"))
        .setPackage("com.google.android.apps.maps")
    if (runCatching { context.startActivity(inMaps) }.isSuccess) return

    val onWeb = Intent(
        Intent.ACTION_VIEW,
        Uri.parse("https://www.google.com/maps/@?api=1&map_action=pano&viewpoint=$lat,$lng"),
    )
    runCatching { context.startActivity(onWeb) }
}

/**
 * 旗の目印を作る。ベクタ画像を色だけ変えて2種類に使い回す。
 *
 * 登録済み（立った旗）と検索候補（まだ立っていない旗）を、形ではなく濃さで区別する。
 * 形を変えると別のものに見えてしまい、「同じ旗がこれから立つ」という関係が伝わらない。
 *
 * **失敗しても null を返す。** 目印の見た目のために起動不能になるのは筋が悪いので、
 * 呼び出し側は null なら既定のマーカーにフォールバックする。
 */
private fun flagDescriptor(context: Context, tint: Int, heightDp: Int = 44): BitmapDescriptor? =
    runCatching {
        // BitmapDescriptorFactory は Maps が初期化されるまで使えない。
        // 地図の生成より先に呼ぶと落ちる（v0.10.0 の起動不能はこれが原因）。
        MapsInitializer.initialize(context)

        val drawable = ContextCompat.getDrawable(context, R.drawable.ic_flag_pin)!!.mutate()
        DrawableCompat.setTint(drawable, tint)

        // viewport 全体ではなく「絵のある範囲」だけを切り出してビットマップにする。
        // 以前は 24x24 の viewport をそのまま 44dp 四方に描いており、面積の
        // 半分以上が透明なのにマーカーの当たり判定を持っていた。旗の右下あたりの
        // 「何も無いように見える場所」をタップすると地図ではなく旗に吸われる。
        val inkWidth = FLAG_INK_RIGHT - FLAG_INK_LEFT
        val inkHeight = FLAG_INK_BOTTOM - FLAG_INK_TOP
        val scale = heightDp * context.resources.displayMetrics.density / inkHeight

        val width = (inkWidth * scale).toInt().coerceAtLeast(1)
        val height = (inkHeight * scale).toInt().coerceAtLeast(1)
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)

        val canvas = Canvas(bitmap)
        canvas.translate(-FLAG_INK_LEFT * scale, -FLAG_INK_TOP * scale)
        val viewport = (FLAG_VIEWPORT * scale).toInt()
        drawable.setBounds(0, 0, viewport, viewport)
        drawable.draw(canvas)

        BitmapDescriptorFactory.fromBitmap(bitmap)
    }.getOrNull()
