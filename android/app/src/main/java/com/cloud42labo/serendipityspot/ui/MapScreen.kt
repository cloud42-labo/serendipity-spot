package com.cloud42labo.serendipityspot.ui

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.net.Uri
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
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
import androidx.compose.ui.text.style.TextOverflow
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

private val FALLBACK_CENTER = LatLng(35.6581, 139.7017)
private const val SPOT_ZOOM = 17f
private const val CURRENT_LOCATION_ZOOM = 16f
private const val FLAG_VIEWPORT = 24f
private const val FLAG_INK_LEFT = 5.2f
private const val FLAG_INK_TOP = 2.5f
private const val FLAG_INK_RIGHT = 18.4f
private const val FLAG_INK_BOTTOM = 21.5f
private val FLAG_ANCHOR = Offset(0.068f, 1.0f)

// FloatingActionButton（56dp）＋Spacing.lgの余白＋カードとの間隔分、右側スペースを
// 選択中スポットカードから避ける（BUG-06-1: fillMaxWidthのカードがBottomEndのFABと
// 重なっていた）。
private val SELECTED_SPOT_CARD_END_INSET = 88.dp

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
    onRegistrationConfirmationShown: () -> Unit,
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
    var pendingTitle by remember { mutableStateOf("") }
    var editingSpot by remember { mutableStateOf<Spot?>(null) }
    var searchMode by remember { mutableStateOf(false) }
    var resultListVisible by remember { mutableStateOf(false) }
    var query by remember { mutableStateOf("") }
    var deletingSpot by remember { mutableStateOf<Spot?>(null) }
    var selectedSpotId by rememberSaveable { mutableStateOf<String?>(null) }
    val selectedSpot = selectedSpotId?.let { id -> uiState.spots.firstOrNull { it.id == id } }
    var initialLocationApplied by rememberSaveable { mutableStateOf(false) }
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
        selectedSpotId = target.id
        onFocusConsumed()
    }

    LaunchedEffect(uiState.searchResults) {
        if (uiState.searchResults.isNotEmpty()) resultListVisible = true
    }

    LaunchedEffect(uiState.errorMessage) {
        uiState.errorMessage?.let { snackbarHostState.showSnackbar(it) }
    }

    // 登録直後の確認（STORY-05: 登録完了が分かる）。showSnackbarはSnackbarが消える
    // まで一時停止するため、消費（state更新）を先に済ませてから表示する。後にすると、
    // 表示中の回転で同じ値のLaunchedEffectが再実行されて再表示されたり、表示中に
    // 同名で連続登録した場合にキーが変わらず2回目が出ない（Codexレビュー指摘）。
    LaunchedEffect(uiState.registeredSpotTitle) {
        val title = uiState.registeredSpotTitle ?: return@LaunchedEffect
        onRegistrationConfirmationShown()
        snackbarHostState.showSnackbar("「$title」を登録しました")
    }

    LaunchedEffect(selectedSpotId, selectedSpot) {
        if (selectedSpotId != null && selectedSpot == null) {
            selectedSpotId = null
            onClearRoute()
        }
        scaffoldState.bottomSheetState.partialExpand()
    }

    val signedIn = uiState.user != null
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
        sheetPeekHeight = if (signedIn && selectedSpot == null) 112.dp else 0.dp,
        topBar = {
            TopAppBar(
                title = {
                    if (searchMode) {
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
                        // フォントサイズ最大でも、ログアウトボタン等の分だけ
                        // 幅が足りない場合に文字が崩れず「…」で収まるようにする
                        // （BUG-06-1）。
                        Text("ついでにスポット", maxLines = 1, overflow = TextOverflow.Ellipsis)
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
                            // 標準のTextButtonはcontentPaddingが広く、フォントサイズ
                            // 最大時に幅が伸びてタイトルを圧迫していた。paddingを
                            // 詰め、文字は1行固定にして幅の伸びを抑える（BUG-06-1）。
                            TextButton(
                                onClick = onSignOutClick,
                                contentPadding = PaddingValues(horizontal = Spacing.sm),
                            ) {
                                Text("ログアウト", maxLines = 1, overflow = TextOverflow.Ellipsis)
                            }
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
                uiSettings = MapUiSettings(zoomControlsEnabled = false, myLocationButtonEnabled = false),
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
                            lastMapEvent = "旗タップ: ${spot.title}"
                            if (selectedSpotId != spot.id) onClearRoute()
                            selectedSpotId = spot.id
                            true
                        },
                    )
                }

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
                    Polyline(points = route.points, color = plantedColor, width = 10f)
                }
            }

            if (!signedIn) SignInOverlay(onSignInClick = onSignInClick)

            selectedSpot?.let { spot ->
                SelectedSpotCard(
                    spot = spot,
                    route = uiState.routeToSpot,
                    isLoadingRoute = uiState.isLoadingRoute,
                    onRequestRoute = { onRequestRoute(spot.id) },
                    onStreetView = { openStreetView(context, spot.lat, spot.lng) },
                    onEdit = { editingSpot = spot; selectedSpotId = null },
                    onDelete = { deletingSpot = spot; selectedSpotId = null },
                    onClose = { selectedSpotId = null; onClearRoute() },
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(bottom = 88.dp, end = SELECTED_SPOT_CARD_END_INSET),
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
                        resultListVisible = false
                    },
                    modifier = Modifier.align(Alignment.TopCenter),
                )
            }

            if (signedIn) {
                Icon(
                    imageVector = Icons.Filled.Add,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.align(Alignment.Center).size(20.dp),
                )
                Column(
                    horizontalAlignment = Alignment.End,
                    modifier = Modifier.align(Alignment.BottomEnd).padding(Spacing.lg),
                ) {
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
                        Icon(Icons.Filled.Add, contentDescription = "地図の中心にスポットを登録")
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
                            Icon(Icons.Filled.LocationOn, contentDescription = "現在地へ移動")
                        }
                    }
                }
            }

            if (uiState.isLoading) CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
        }
    }

    editingSpot?.let { spot ->
        EditSpotDialog(
            spot = spot,
            onDismiss = { editingSpot = null },
            onSave = { title, memo -> onEditSpot(spot, title, memo); editingSpot = null },
        )
    }

    deletingSpot?.let { spot ->
        DeleteSpotDialog(
            spot = spot,
            onDismiss = { deletingSpot = null },
            onConfirm = { onDeleteSpot(spot); deletingSpot = null },
        )
    }

    pendingLatLng?.let { latLng ->
        RegisterSheet(
            initialTitle = pendingTitle,
            onDismiss = { pendingLatLng = null },
            onSave = { title, memo ->
                onSaveSpot(latLng.latitude, latLng.longitude, title, memo)
                pendingLatLng = null
                onClearSearch()
            },
        )
    }
}

private fun openStreetView(context: Context, lat: Double, lng: Double) {
    val inMaps = Intent(Intent.ACTION_VIEW, Uri.parse("google.streetview:cbll=$lat,$lng"))
        .setPackage("com.google.android.apps.maps")
    if (runCatching { context.startActivity(inMaps) }.isSuccess) return
    runCatching {
        context.startActivity(
            Intent(
                Intent.ACTION_VIEW,
                Uri.parse("https://www.google.com/maps/@?api=1&map_action=pano&viewpoint=$lat,$lng"),
            ),
        )
    }
}

private fun flagDescriptor(context: Context, tint: Int, heightDp: Int = 44): BitmapDescriptor? =
    runCatching {
        MapsInitializer.initialize(context)
        val drawable = ContextCompat.getDrawable(context, R.drawable.ic_flag_pin)!!.mutate()
        DrawableCompat.setTint(drawable, tint)
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
