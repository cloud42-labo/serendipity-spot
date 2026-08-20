package com.cloud42labo.serendipityspot.location

import com.cloud42labo.serendipityspot.data.NotificationPreferences
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * SPOT-03-S02-T03: 再通知クールダウン・通知可能時間帯・DWELLの「同じ滞在」判定の
 * 優先順位を自動テストで固定する。
 */
class NotificationSuppressionPolicyTest {

    private val defaultPrefs = NotificationPreferences()
    private val sameVisitMs = 30 * 60 * 1000L

    // --- isWithinAllowedWindow ---

    @Test
    fun `default preferences allow any day and any time`() {
        assertTrue(
            NotificationSuppressionPolicy.isWithinAllowedWindow(
                dayOfWeek = 1,
                minuteOfDay = 0,
                preferences = defaultPrefs,
            ),
        )
        assertTrue(
            NotificationSuppressionPolicy.isWithinAllowedWindow(
                dayOfWeek = 7,
                minuteOfDay = 23 * 60 + 59,
                preferences = defaultPrefs,
            ),
        )
    }

    @Test
    fun `day outside allowedDays is blocked`() {
        val prefs = defaultPrefs.copy(allowedDays = setOf(2, 3, 4, 5, 6)) // 平日のみ
        assertFalse(
            NotificationSuppressionPolicy.isWithinAllowedWindow(
                dayOfWeek = 1, // 日曜
                minuteOfDay = 12 * 60,
                preferences = prefs,
            ),
        )
        assertTrue(
            NotificationSuppressionPolicy.isWithinAllowedWindow(
                dayOfWeek = 2, // 月曜
                minuteOfDay = 12 * 60,
                preferences = prefs,
            ),
        )
    }

    @Test
    fun `normal same-day window excludes times outside range`() {
        val prefs = defaultPrefs.copy(startMinute = 8 * 60, endMinute = 22 * 60)
        assertTrue(
            NotificationSuppressionPolicy.isWithinAllowedWindow(1, 8 * 60, prefs),
        )
        assertTrue(
            NotificationSuppressionPolicy.isWithinAllowedWindow(1, 21 * 60 + 59, prefs),
        )
        assertFalse(
            NotificationSuppressionPolicy.isWithinAllowedWindow(1, 22 * 60, prefs),
        )
        assertFalse(
            NotificationSuppressionPolicy.isWithinAllowedWindow(1, 7 * 60, prefs),
        )
    }

    @Test
    fun `overnight window wraps past midnight`() {
        val prefs = defaultPrefs.copy(startMinute = 22 * 60, endMinute = 6 * 60)
        // 23時は範囲内（開始側）
        assertTrue(
            NotificationSuppressionPolicy.isWithinAllowedWindow(1, 23 * 60, prefs),
        )
        // 深夜1時も範囲内（日をまたいだ終了側）
        assertTrue(
            NotificationSuppressionPolicy.isWithinAllowedWindow(1, 1 * 60, prefs),
        )
        // 昼10時は範囲外
        assertFalse(
            NotificationSuppressionPolicy.isWithinAllowedWindow(1, 10 * 60, prefs),
        )
    }

    // --- shouldNotifyOnEnter ---

    @Test
    fun `enter notifies when never notified before and within window`() {
        // lastNotifiedAt=0（未通知）は、既定クールダウン(180分)より十分先の時刻からなら
        // 経過時間がクールダウンを超えるため通知される。
        assertTrue(
            NotificationSuppressionPolicy.shouldNotifyOnEnter(
                now = 1_000_000_000L,
                lastNotifiedAt = 0L,
                dayOfWeek = 1,
                minuteOfDay = 12 * 60,
                preferences = defaultPrefs,
            ),
        )
    }

    @Test
    fun `enter is suppressed during cooldown`() {
        val prefs = defaultPrefs.copy(cooldownMinutes = 60)
        val now = 1_000_000_000L
        val lastNotified = now - 30 * 60_000L // 30分前
        assertFalse(
            NotificationSuppressionPolicy.shouldNotifyOnEnter(
                now = now,
                lastNotifiedAt = lastNotified,
                dayOfWeek = 1,
                minuteOfDay = 12 * 60,
                preferences = prefs,
            ),
        )
    }

    @Test
    fun `enter notifies again once cooldown has elapsed`() {
        val prefs = defaultPrefs.copy(cooldownMinutes = 60)
        val now = 1_000_000_000L
        val lastNotified = now - 61 * 60_000L // 61分前
        assertTrue(
            NotificationSuppressionPolicy.shouldNotifyOnEnter(
                now = now,
                lastNotifiedAt = lastNotified,
                dayOfWeek = 1,
                minuteOfDay = 12 * 60,
                preferences = prefs,
            ),
        )
    }

    @Test
    fun `enter is suppressed outside allowed window even if cooldown elapsed`() {
        // 時間帯制限はクールダウンより優先して評価される（最優先の抑止理由）。
        val prefs = defaultPrefs.copy(startMinute = 8 * 60, endMinute = 22 * 60, cooldownMinutes = 1)
        assertFalse(
            NotificationSuppressionPolicy.shouldNotifyOnEnter(
                now = 10_000_000L,
                lastNotifiedAt = 0L,
                dayOfWeek = 1,
                minuteOfDay = 23 * 60, // 範囲外
                preferences = prefs,
            ),
        )
    }

    // --- shouldNotifyOnDwell ---

    @Test
    fun `dwell notifies when same visit and not recently nudged`() {
        val now = 1_000_000_000L
        assertTrue(
            NotificationSuppressionPolicy.shouldNotifyOnDwell(
                now = now,
                lastNotifiedAt = now - 5 * 60_000L, // 5分前に初回通知（同じ滞在）
                lastNudgedAt = 0L,
                sameVisitMs = sameVisitMs,
                dayOfWeek = 1,
                minuteOfDay = 12 * 60,
                preferences = defaultPrefs,
            ),
        )
    }

    @Test
    fun `dwell is suppressed when not the same visit`() {
        val now = 1_000_000_000L
        assertFalse(
            NotificationSuppressionPolicy.shouldNotifyOnDwell(
                now = now,
                lastNotifiedAt = now - 40 * 60_000L, // sameVisitMs(30分)を超えている
                lastNudgedAt = 0L,
                sameVisitMs = sameVisitMs,
                dayOfWeek = 1,
                minuteOfDay = 12 * 60,
                preferences = defaultPrefs,
            ),
        )
    }

    @Test
    fun `dwell is suppressed when nudged recently even if same visit`() {
        val prefs = defaultPrefs.copy(cooldownMinutes = 60)
        val now = 1_000_000_000L
        assertFalse(
            NotificationSuppressionPolicy.shouldNotifyOnDwell(
                now = now,
                lastNotifiedAt = now - 5 * 60_000L,
                lastNudgedAt = now - 10 * 60_000L, // クールダウン(60分)内
                sameVisitMs = sameVisitMs,
                dayOfWeek = 1,
                minuteOfDay = 12 * 60,
                preferences = prefs,
            ),
        )
    }

    @Test
    fun `dwell is suppressed outside allowed window regardless of same visit`() {
        val prefs = defaultPrefs.copy(startMinute = 8 * 60, endMinute = 22 * 60)
        val now = 1_000_000_000L
        assertFalse(
            NotificationSuppressionPolicy.shouldNotifyOnDwell(
                now = now,
                lastNotifiedAt = now - 5 * 60_000L,
                lastNudgedAt = 0L,
                sameVisitMs = sameVisitMs,
                dayOfWeek = 1,
                minuteOfDay = 23 * 60, // 範囲外
                preferences = prefs,
            ),
        )
    }
}
