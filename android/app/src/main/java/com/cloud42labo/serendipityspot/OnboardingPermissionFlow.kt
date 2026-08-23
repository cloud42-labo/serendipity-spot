package com.cloud42labo.serendipityspot

import android.os.Build

/**
 * 初回説明（[com.cloud42labo.serendipityspot.ui.OnboardingIntro]）を閉じた直後、
 * 次にどの権限ダイアログを出すべきかを決める純粋関数。[MainActivity]の
 * `LaunchedEffect(showOnboarding)`から呼ぶ。
 *
 * PR #26のHuman実機確認③「開示確認後、OSの位置情報権限設定へ正しく進める」の
 * うち、機械的に固定できる分岐条件だけを切り出したもの。実際の権限ダイアログの
 * 見た目・OS/メーカーごとの挙動そのものは対象外（実機確認に委ねる。SPOT-06-S01-T03）。
 */
internal enum class NextOnboardingPermissionStep {
    REQUEST_FOREGROUND,
    REQUEST_BACKGROUND,
    NONE,
}

internal fun nextOnboardingPermissionStep(
    hasForegroundLocation: Boolean,
    hasBackgroundLocation: Boolean,
    sdkInt: Int = Build.VERSION.SDK_INT,
): NextOnboardingPermissionStep = when {
    !hasForegroundLocation -> NextOnboardingPermissionStep.REQUEST_FOREGROUND
    !hasBackgroundLocation && sdkInt >= Build.VERSION_CODES.Q -> NextOnboardingPermissionStep.REQUEST_BACKGROUND
    else -> NextOnboardingPermissionStep.NONE
}
