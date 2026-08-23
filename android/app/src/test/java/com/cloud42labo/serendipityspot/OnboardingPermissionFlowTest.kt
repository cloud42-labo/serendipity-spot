package com.cloud42labo.serendipityspot

import android.os.Build
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * PR #26のHuman実機確認③「開示確認後、OSの位置情報権限設定へ正しく進める」の
 * うち、機械的に固定できる分岐条件を[nextOnboardingPermissionStep]として固定する
 * （SPOT-06-S01-T03）。実際の権限ダイアログの見た目・OS/メーカーごとの挙動は対象外。
 */
class OnboardingPermissionFlowTest {

    @Test
    fun `foreground location missing requests foreground first, regardless of background state or SDK`() {
        assertEquals(
            NextOnboardingPermissionStep.REQUEST_FOREGROUND,
            nextOnboardingPermissionStep(
                hasForegroundLocation = false,
                hasBackgroundLocation = false,
                sdkInt = Build.VERSION_CODES.Q,
            ),
        )
        assertEquals(
            NextOnboardingPermissionStep.REQUEST_FOREGROUND,
            nextOnboardingPermissionStep(
                hasForegroundLocation = false,
                hasBackgroundLocation = true,
                sdkInt = Build.VERSION_CODES.TIRAMISU,
            ),
        )
    }

    @Test
    fun `foreground granted but background missing on Android 10+ requests background`() {
        assertEquals(
            NextOnboardingPermissionStep.REQUEST_BACKGROUND,
            nextOnboardingPermissionStep(
                hasForegroundLocation = true,
                hasBackgroundLocation = false,
                sdkInt = Build.VERSION_CODES.Q,
            ),
        )
    }

    @Test
    fun `foreground granted but background missing below Android 10 needs no separate request`() {
        // Android 9以下はACCESS_BACKGROUND_LOCATION自体が存在せず、フォアグラウンド許可が
        // そのままバックグラウンドでも有効なため、別ダイアログは出さない
        // （MainActivity.hasBackgroundLocationPermission()と対応する前提）。
        assertEquals(
            NextOnboardingPermissionStep.NONE,
            nextOnboardingPermissionStep(
                hasForegroundLocation = true,
                hasBackgroundLocation = false,
                sdkInt = Build.VERSION_CODES.P,
            ),
        )
    }

    @Test
    fun `both permissions already granted needs no dialog`() {
        assertEquals(
            NextOnboardingPermissionStep.NONE,
            nextOnboardingPermissionStep(
                hasForegroundLocation = true,
                hasBackgroundLocation = true,
                sdkInt = Build.VERSION_CODES.Q,
            ),
        )
    }
}
