package com.cloud42labo.serendipityspot

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.foundation.layout.Box
import androidx.compose.material3.Surface
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.cloud42labo.serendipityspot.data.SpotLocalCache
import com.cloud42labo.serendipityspot.ui.MapScreen
import com.cloud42labo.serendipityspot.ui.OnboardingIntro
import com.cloud42labo.serendipityspot.ui.SpotViewModel
import com.cloud42labo.serendipityspot.ui.components.PermissionRecoveryHint
import com.cloud42labo.serendipityspot.ui.theme.SerendipitySpotTheme

class MainActivity : ComponentActivity() {

    private val viewModel: SpotViewModel by viewModels()

    /** Sheets/Driveスコープの同意画面。ViewModelからの依頼で起動する。 */
    private val consentLauncher = registerForActivityResult(
        ActivityResultContracts.StartIntentSenderForResult()
    ) { result ->
        viewModel.onAuthorizationResult(result.data)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        handleFocusIntent(intent)

        setContent {
            SerendipitySpotTheme {
                Surface {
                    val uiState by viewModel.uiState.collectAsState()
                    var hasForegroundLocation by remember { mutableStateOf(hasForegroundLocationPermission()) }
                    var hasBackgroundLocation by remember { mutableStateOf(hasBackgroundLocationPermission()) }
                    var hasNotificationPermission by remember { mutableStateOf(hasNotificationPermissionGranted()) }
                    // 初回説明を終え、権限確認の段階まで来たか。まだ来ていない
                    // （＝これから聞く）間は「拒否された」復帰導線を出さない。
                    // 権限ダイアログを実際に出したかどうかではなく「確認済みか」で持つ。
                    // ダイアログを出さずスキップする分岐（既に許可済み等）でも、この後の
                    // 実際の権限状態と組み合わせて正しく復帰導線を出し分けられるようにする。
                    var permissionsChecked by remember { mutableStateOf(false) }
                    // 初回だけ、OSの権限ダイアログより先にアプリの目的と権限理由を説明する
                    // （STORY-05: 初回説明・権限説明）。既読なら即falseでこれまで通りの動作。
                    var showOnboarding by remember {
                        mutableStateOf(!SpotLocalCache.hasSeenOnboarding(this@MainActivity))
                    }

                    val backgroundLocationLauncher = rememberLauncherForActivityResult(
                        ActivityResultContracts.RequestPermission()
                    ) { granted -> hasBackgroundLocation = granted }

                    val foregroundLauncher = rememberLauncherForActivityResult(
                        ActivityResultContracts.RequestMultiplePermissions()
                    ) { grants ->
                        hasForegroundLocation = grants[Manifest.permission.ACCESS_FINE_LOCATION] == true ||
                            grants[Manifest.permission.ACCESS_COARSE_LOCATION] == true
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                            hasNotificationPermission = grants[Manifest.permission.POST_NOTIFICATIONS] == true
                        }
                        if (hasForegroundLocation && Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                            backgroundLocationLauncher.launch(Manifest.permission.ACCESS_BACKGROUND_LOCATION)
                        }
                    }

                    LaunchedEffect(Unit) {
                        viewModel.consentRequests.collect { intentSender ->
                            consentLauncher.launch(IntentSenderRequest.Builder(intentSender).build())
                        }
                    }

                    // 初回説明を閉じるまで権限ダイアログを出さない。
                    LaunchedEffect(showOnboarding) {
                        if (showOnboarding) return@LaunchedEffect
                        permissionsChecked = true
                        val permissions = mutableListOf(
                            Manifest.permission.ACCESS_FINE_LOCATION,
                            Manifest.permission.ACCESS_COARSE_LOCATION,
                        )
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                            permissions += Manifest.permission.POST_NOTIFICATIONS
                        }
                        if (!hasForegroundLocation) {
                            foregroundLauncher.launch(permissions.toTypedArray())
                        } else if (!hasBackgroundLocation && Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                            backgroundLocationLauncher.launch(Manifest.permission.ACCESS_BACKGROUND_LOCATION)
                        }
                    }

                    // 設定画面から戻ってきたときに実際の権限状態を読み直す。権限ランチャーの
                    // 結果コールバックは「アプリ内から求めた」場合しか通らないため、これが無いと
                    // 復帰カード経由でOSの設定から許可しても、アプリ内の表示が古いまま残る
                    // （Codexレビュー指摘）。
                    val lifecycleOwner = LocalLifecycleOwner.current
                    DisposableEffect(lifecycleOwner) {
                        val observer = LifecycleEventObserver { _, event ->
                            if (event == Lifecycle.Event.ON_RESUME) {
                                hasForegroundLocation = hasForegroundLocationPermission()
                                hasBackgroundLocation = hasBackgroundLocationPermission()
                                hasNotificationPermission = hasNotificationPermissionGranted()
                            }
                        }
                        lifecycleOwner.lifecycle.addObserver(observer)
                        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
                    }

                    if (showOnboarding) {
                        OnboardingIntro(onStart = {
                            SpotLocalCache.markOnboardingSeen(this@MainActivity)
                            showOnboarding = false
                        })
                    } else {
                        Box {
                            MapScreen(
                                uiState = uiState,
                                hasLocationPermission = hasForegroundLocation,
                                onSignInClick = { viewModel.signIn(this@MainActivity) },
                                onSignOutClick = viewModel::signOut,
                                onSaveSpot = viewModel::addSpot,
                                onEditSpot = viewModel::editSpot,
                                onDeleteSpot = viewModel::deleteSpot,
                                onTestNotification = viewModel::sendTestNotification,
                                onRefreshDiagnostics = viewModel::refreshDiagnostics,
                                onSearch = viewModel::searchPlaces,
                                onClearSearch = viewModel::clearSearchResults,
                                onFocusConsumed = viewModel::consumeFocusRequest,
                                onClearRoute = viewModel::clearRoute,
                                onRequestRoute = viewModel::requestRoute,
                                onRegistrationConfirmationShown = viewModel::consumeRegistrationConfirmation,
                            )

                            if (permissionsChecked && !hasForegroundLocation) {
                                PermissionRecoveryHint(
                                    "位置情報の権限がないため、現在地の表示や近づいたときの通知が" +
                                        "使えません。設定から位置情報を許可してください。",
                                )
                            } else if (uiState.user != null && hasForegroundLocation && !hasBackgroundLocation) {
                                PermissionRecoveryHint(
                                    "アプリを閉じていても通知するには、位置情報の権限を" +
                                        "「常に許可」に変更してください。",
                                )
                            } else if (
                                permissionsChecked &&
                                Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
                                !hasNotificationPermission
                            ) {
                                PermissionRecoveryHint(
                                    "通知の権限がないため、近づいても知らせが届きません。" +
                                        "設定から通知を許可してください。",
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleFocusIntent(intent)
    }

    private fun handleFocusIntent(intent: Intent?) {
        val spotId = intent?.getStringExtra(EXTRA_FOCUS_SPOT_ID) ?: return
        viewModel.focusSpot(spotId)
    }

    private fun hasForegroundLocationPermission(): Boolean =
        ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) ==
            PackageManager.PERMISSION_GRANTED

    private fun hasBackgroundLocationPermission(): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) return true
        return ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_BACKGROUND_LOCATION) ==
            PackageManager.PERMISSION_GRANTED
    }

    private fun hasNotificationPermissionGranted(): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return true
        return ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) ==
            PackageManager.PERMISSION_GRANTED
    }

    companion object {
        const val EXTRA_FOCUS_SPOT_ID = "extra_focus_spot_id"
    }
}
