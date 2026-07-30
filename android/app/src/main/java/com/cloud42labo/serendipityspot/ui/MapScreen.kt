package com.cloud42labo.serendipityspot.ui

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.net.Uri
import androidx.compose.foundation.clickable
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.BottomSheetScaffold
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ElevatedButton
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberBottomSheetScaffoldState
import androidx.compose.material3.rememberModalBottomSheetState
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
import com.cloud42labo.serendipityspot.data.PlaceResult
import com.cloud42labo.serendipityspot.data.Spot
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
import com.cloud42labo.serendipityspot.data.RouteInfo
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await

/** 現在地が取れるまでの暫定表示（渋谷）。取れ次第そちらへ移す。 */
private val FALLBACK_CENTER = LatLng(35.6581, 139.7017)
private const val SPOT_ZOOM = 17f
private const val CURRENT_LOCATION_ZOOM = 16f

/** 竿の根元が地点を指すように、左下寄りに合わせる。 */
private val FLAG_ANCHOR = Offset(0.25f, 0.95f)

/**
 * 通知（ジオフェンス）を張れる件数の上限。GeofenceHelper.MAX_GEOFENCES と同じ値で、
 * Android/Google Play 側の制限。これを超えた分は登録できても通知は届かない。
 */
private const val NOTIFIABLE_LIMIT = 100

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
    // 初回だけ現在地へ寄せる。以後はユーザーの操作を邪魔しない。
    var initialLocationApplied by rememberSaveable { mutableStateOf(false) }

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
    val plantedColor = MaterialTheme.colorScheme.primary
    val unplantedColor = MaterialTheme.colorScheme.outline
    val plantedFlag = remember(plantedColor) { flagDescriptor(context, plantedColor.toArgb()) }
    val unplantedFlag = remember(unplantedColor) { flagDescriptor(context, unplantedColor.toArgb()) }

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
                        OutlinedTextField(
                            value = query,
                            onValueChange = { query = it },
                            placeholder = { Text("住所・駅名・施設名") },
                            singleLine = true,
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
                onTestNotification = onTestNotification,
                onRefreshDiagnostics = onRefreshDiagnostics,
                onEditClick = { editingSpot = it },
                onDeleteClick = { deletingSpot = it },
                onStreetViewClick = { openStreetView(context, it.lat, it.lng) },
                onSpotClick = { spot ->
                    cameraPositionState.position =
                        CameraPosition.fromLatLngZoom(LatLng(spot.lat, spot.lng), SPOT_ZOOM)
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
                    if (signedIn) {
                        pendingTitle = ""
                        pendingLatLng = latLng
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
                        icon = plantedFlag,
                        anchor = FLAG_ANCHOR,
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
                                pendingTitle = result.name
                                pendingLatLng = LatLng(result.lat, result.lng)
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

            if (uiState.isLoadingRoute || uiState.routeToSpot != null) {
                RouteInfoCard(
                    route = uiState.routeToSpot,
                    isLoading = uiState.isLoadingRoute,
                    onClose = onClearRoute,
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

            if (signedIn && hasLocationPermission) {
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
                    modifier = Modifier.align(Alignment.BottomEnd).padding(16.dp),
                ) {
                    Icon(
                        imageVector = Icons.Filled.LocationOn,
                        contentDescription = "現在地へ移動",
                    )
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
        AlertDialog(
            onDismissRequest = { deletingSpot = null },
            title = { Text("スポットを削除") },
            text = { Text("「${spot.title}」を削除します。スプレッドシートからも消えます。") },
            confirmButton = {
                TextButton(onClick = {
                    onDeleteSpot(spot)
                    deletingSpot = null
                }) { Text("削除") }
            },
            dismissButton = {
                TextButton(onClick = { deletingSpot = null }) { Text("キャンセル") }
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

@Composable
private fun SpotListSheet(
    spots: List<Spot>,
    lastRegistration: String?,
    lastGeofenceEvent: String?,
    onSpotClick: (Spot) -> Unit,
    onEditClick: (Spot) -> Unit,
    onDeleteClick: (Spot) -> Unit,
    onStreetViewClick: (Spot) -> Unit,
    onTestNotification: () -> Unit,
    onRefreshDiagnostics: () -> Unit,
) {
    // シート全体を1つの LazyColumn にする。見出しと診断を外側の Column に置いて
    // 高さで頭打ちにすると、はみ出した分（テスト通知ボタン）が切り捨てられて
    // 永久に押せなくなる。実際 v0.10.x はその状態だった。
    LazyColumn(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(max = 400.dp)
            .padding(bottom = 16.dp),
    ) {
        item {
            Text(
                text = "登録スポット（${spots.size} / $NOTIFIABLE_LIMIT）",
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(horizontal = 24.dp),
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "地図をタップすると新しいスポットを登録できます",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 24.dp),
            )
            Spacer(modifier = Modifier.height(4.dp))
            val over = spots.size - NOTIFIABLE_LIMIT
            Text(
                text = if (over > 0) {
                    "通知できるのは新しい $NOTIFIABLE_LIMIT 件までです（Androidの上限）。" +
                        "古い $over 件は一覧に残りますが、近づいても通知は届きません。"
                } else {
                    "通知できるのは $NOTIFIABLE_LIMIT 件までです（Androidの上限）。" +
                        "超えた分は古い方から通知の対象外になります。"
                },
                style = MaterialTheme.typography.bodySmall,
                color = if (over > 0) {
                    MaterialTheme.colorScheme.error
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                },
                modifier = Modifier.padding(horizontal = 24.dp),
            )
            Spacer(modifier = Modifier.height(12.dp))
            HorizontalDivider()
        }

        if (spots.isEmpty()) {
            item {
                Text(
                    text = "まだ登録がありません。",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(24.dp),
                )
            }
        } else {
            items(spots, key = { it.id }) { spot ->
                ListItem(
                    headlineContent = { Text(spot.title) },
                    supportingContent = {
                        if (spot.memo.isNotBlank()) Text(spot.memo)
                    },
                    leadingContent = {
                        Icon(
                            imageVector = Icons.Filled.LocationOn,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                        )
                    },
                    trailingContent = {
                        Row {
                            IconButton(onClick = { onStreetViewClick(spot) }) {
                                Icon(
                                    // ストリートビューの目印はペグマン（人型）なので、
                                    // 人のアイコンをそのまま使う。
                                    imageVector = Icons.Filled.Person,
                                    contentDescription = "${spot.title} をストリートビューで見る",
                                )
                            }
                            IconButton(onClick = { onEditClick(spot) }) {
                                Icon(
                                    imageVector = Icons.Filled.Edit,
                                    contentDescription = "${spot.title} を編集",
                                )
                            }
                            IconButton(onClick = { onDeleteClick(spot) }) {
                                Icon(
                                    imageVector = Icons.Filled.Delete,
                                    contentDescription = "${spot.title} を削除",
                                    tint = MaterialTheme.colorScheme.error,
                                )
                            }
                        }
                    },
                    modifier = Modifier.clickable { onSpotClick(spot) },
                )
            }
        }

        item {
            HorizontalDivider()
            DiagnosticsBlock(
                lastRegistration = lastRegistration,
                lastGeofenceEvent = lastGeofenceEvent,
                onTestNotification = onTestNotification,
                onRefresh = onRefreshDiagnostics,
            )
        }
    }
}

/**
 * 通知が来ないときの切り分け用。
 * 「ジオフェンスを登録できているか」と「イベントが端末に届いているか」を分けて見る。
 */
@Composable
private fun DiagnosticsBlock(
    lastRegistration: String?,
    lastGeofenceEvent: String?,
    onTestNotification: () -> Unit,
    onRefresh: () -> Unit,
) {
    Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 12.dp)) {
        Text(
            text = "診断",
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(modifier = Modifier.height(6.dp))
        Text(
            text = "登録: ${lastRegistration ?: "まだ記録なし"}",
            style = MaterialTheme.typography.bodySmall,
        )
        Text(
            text = "受信: ${lastGeofenceEvent ?: "まだ一度も受け取っていない"}",
            style = MaterialTheme.typography.bodySmall,
        )
        Spacer(modifier = Modifier.height(8.dp))
        Row {
            TextButton(onClick = onTestNotification) { Text("テスト通知") }
            Spacer(modifier = Modifier.width(8.dp))
            TextButton(onClick = onRefresh) { Text("更新") }
        }
    }
}

@Composable
private fun SignInOverlay(onSignInClick: () -> Unit) {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Card(
            elevation = CardDefaults.cardElevation(defaultElevation = 8.dp),
            modifier = Modifier.padding(32.dp),
        ) {
            Column(
                modifier = Modifier.padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Icon(
                    imageVector = Icons.Filled.LocationOn,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(40.dp),
                )
                Spacer(modifier = Modifier.height(12.dp))
                Text("ついでにスポット", style = MaterialTheme.typography.titleLarge)
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    "Googleアカウントでログインすると、スポットがあなたのスプレッドシートに保存されます",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(modifier = Modifier.height(20.dp))
                Button(onClick = onSignInClick, modifier = Modifier.fillMaxWidth()) {
                    Text("Googleでログイン")
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun RegisterSheet(
    initialTitle: String,
    onDismiss: () -> Unit,
    onSave: (title: String, memo: String) -> Unit,
) {
    val sheetState = rememberModalBottomSheetState()
    var title by remember(initialTitle) { mutableStateOf(initialTitle) }
    var memo by remember(initialTitle) { mutableStateOf("") }

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        Column(modifier = Modifier.fillMaxWidth().padding(24.dp)) {
            Text("スポット保存", style = MaterialTheme.typography.headlineSmall)
            Spacer(modifier = Modifier.height(16.dp))
            OutlinedTextField(
                value = title,
                onValueChange = { title = it },
                label = { Text("名前") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
            )
            Spacer(modifier = Modifier.height(12.dp))
            OutlinedTextField(
                value = memo,
                onValueChange = { memo = it },
                label = { Text("ひとことメモ") },
                modifier = Modifier.fillMaxWidth(),
                minLines = 2,
            )
            Spacer(modifier = Modifier.height(20.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
            ) {
                TextButton(onClick = onDismiss) { Text("キャンセル") }
                Spacer(modifier = Modifier.width(8.dp))
                ElevatedButton(
                    onClick = { onSave(title, memo) },
                    enabled = title.isNotBlank(),
                ) { Text("保存") }
            }
            Spacer(modifier = Modifier.height(8.dp))
        }
    }
}

@Composable
private fun EditSpotDialog(
    spot: Spot,
    onDismiss: () -> Unit,
    onSave: (title: String, memo: String) -> Unit,
) {
    var title by remember(spot.id) { mutableStateOf(spot.title) }
    var memo by remember(spot.id) { mutableStateOf(spot.memo) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("スポットを編集") },
        text = {
            Column {
                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it },
                    label = { Text("名前") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                )
                Spacer(modifier = Modifier.height(12.dp))
                OutlinedTextField(
                    value = memo,
                    onValueChange = { memo = it },
                    label = { Text("ひとことメモ") },
                    modifier = Modifier.fillMaxWidth(),
                    minLines = 2,
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onSave(title, memo) },
                enabled = title.isNotBlank(),
            ) { Text("保存") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("キャンセル") }
        },
    )
}

/**
 * 検索候補。地図の上に重ねるが、選ぶか閉じるかで即座に消える一時的なものなので
 * 常設の要素とは扱いが違う。
 */
@Composable
private fun SearchResults(
    results: List<PlaceResult>,
    onPick: (PlaceResult) -> Unit,
    modifier: Modifier = Modifier,
) {
    Card(
        modifier = modifier.fillMaxWidth().padding(12.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 6.dp),
    ) {
        LazyColumn(modifier = Modifier.heightIn(max = 280.dp)) {
            items(results) { result ->
                ListItem(
                    headlineContent = { Text(result.name) },
                    supportingContent = {
                        if (result.subtitle.isNotBlank() && result.subtitle != result.name) {
                            Text(result.subtitle)
                        }
                    },
                    leadingContent = {
                        Icon(
                            imageVector = Icons.Filled.Search,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    },
                    modifier = Modifier.clickable { onPick(result) },
                )
            }
        }
    }
}

@Composable
private fun RouteInfoCard(
    route: RouteInfo?,
    isLoading: Boolean,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Card(
        modifier = modifier.padding(12.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 6.dp),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(start = 16.dp, end = 4.dp, top = 8.dp, bottom = 8.dp),
        ) {
            if (isLoading) {
                CircularProgressIndicator(modifier = Modifier.size(20.dp))
                Spacer(modifier = Modifier.width(12.dp))
                Text("経路を確認中…")
            } else if (route != null) {
                Text("徒歩 ${route.durationText}・${route.distanceText}")
                IconButton(onClick = onClose) {
                    Icon(Icons.Filled.Close, contentDescription = "経路を閉じる")
                }
            }
        }
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
private fun flagDescriptor(context: Context, tint: Int, sizeDp: Int = 44): BitmapDescriptor? =
    runCatching {
        // BitmapDescriptorFactory は Maps が初期化されるまで使えない。
        // 地図の生成より先に呼ぶと落ちる（v0.10.0 の起動不能はこれが原因）。
        MapsInitializer.initialize(context)

        val drawable = ContextCompat.getDrawable(context, R.drawable.ic_flag_pin)!!.mutate()
        DrawableCompat.setTint(drawable, tint)

        val px = (sizeDp * context.resources.displayMetrics.density).toInt().coerceAtLeast(1)
        val bitmap = Bitmap.createBitmap(px, px, Bitmap.Config.ARGB_8888)
        drawable.setBounds(0, 0, px, px)
        drawable.draw(Canvas(bitmap))

        BitmapDescriptorFactory.fromBitmap(bitmap)
    }.getOrNull()
