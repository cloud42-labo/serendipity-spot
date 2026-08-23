package com.cloud42labo.serendipityspot.data

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * SPOT-06-S01-T01（PR #26）で、初回オンボーディングにバックグラウンド位置情報の開示を
 * 追加した際、バージョンキーを持たない既存ユーザーにも一度だけ再表示するようにした
 * （[SpotLocalCache.hasSeenOnboarding]）。その版数比較部分は[SpotLocalCache.isOnboardingVersionCurrent]
 * として純粋関数に切り出してあるため、Robolectric等のContext依存なしにテストできる
 * （SPOT-06-S01-T03）。
 */
class SpotLocalCacheOnboardingVersionTest {

    @Test
    fun `never-saved key (pre-v2 existing user) is not current, so disclosure is shown again`() {
        // KEY_ONBOARDING_VERSIONを一度も書いていない既存ユーザーは
        // prefs.getInt(KEY_ONBOARDING_VERSION, 0) によりstoredVersion=0で呼ばれる。
        assertFalse(SpotLocalCache.isOnboardingVersionCurrent(0))
    }

    @Test
    fun `stored version below current is not current`() {
        assertFalse(
            SpotLocalCache.isOnboardingVersionCurrent(SpotLocalCache.CURRENT_ONBOARDING_VERSION - 1),
        )
    }

    @Test
    fun `stored version equal to current is current`() {
        assertTrue(
            SpotLocalCache.isOnboardingVersionCurrent(SpotLocalCache.CURRENT_ONBOARDING_VERSION),
        )
    }

    @Test
    fun `stored version above current (future downgrade scenario) is still current`() {
        assertTrue(
            SpotLocalCache.isOnboardingVersionCurrent(SpotLocalCache.CURRENT_ONBOARDING_VERSION + 1),
        )
    }
}
