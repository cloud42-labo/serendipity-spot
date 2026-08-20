package com.cloud42labo.serendipityspot.data

/**
 * 再通知クールダウンと通知可能時間帯のユーザー設定（SPOT-03-S02）。
 *
 * 既定値は「制限なし」（現行動作と同じ）。`allowedDays`が7曜日すべて、
 * `startMinute=0`／`endMinute=DAY_MINUTES`のときは時間帯制限なしとして扱う
 * （[com.cloud42labo.serendipityspot.location.NotificationSuppressionPolicy]参照）。
 */
data class NotificationPreferences(
    val cooldownMinutes: Int = DEFAULT_COOLDOWN_MINUTES,
    val allowedDays: Set<Int> = ALL_DAYS,
    val startMinute: Int = 0,
    val endMinute: Int = DAY_MINUTES,
) {
    companion object {
        /** 旧`NOTIFY_COOLDOWN_MS`（3時間）と同じ既定値。 */
        const val DEFAULT_COOLDOWN_MINUTES = 180
        const val DAY_MINUTES = 24 * 60

        /** [java.util.Calendar.DAY_OF_WEEK] の値（日=1〜土=7）の全曜日。 */
        val ALL_DAYS = setOf(1, 2, 3, 4, 5, 6, 7)
    }
}
