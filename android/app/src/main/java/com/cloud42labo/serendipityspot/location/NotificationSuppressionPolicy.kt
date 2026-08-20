package com.cloud42labo.serendipityspot.location

import com.cloud42labo.serendipityspot.data.NotificationPreferences

/**
 * 通知を出す／抑止するかの判定を、Contextに依存しない純粋関数として切り出したもの
 * （SPOT-03-S02-T01／T02／T03）。[GeofenceBroadcastReceiver]から時刻・設定値を渡して使う。
 *
 * 優先順位（上から順に評価。先に該当した理由で抑止する）:
 * 1. **通知可能時間帯の範囲外** — 曜日・時間帯の制限に引っかかる場合は、
 *    ENTER/DWELLを問わず常に抑止する（SPOT-03-S02-T02のAC）。
 * 2. **ENTER: クールダウン中** — 同一スポットへの直近通知から
 *    `cooldownMinutes`未満なら抑止する（SPOT-03-S02-T01）。
 * 3. **DWELL: 同一滞在の続きでない、またはクールダウン中** —
 *    既存の「2分後も圏内なら再通知」仕様（SAME_VISIT_MS）との優先関係を
 *    ここで固定する。DWELLの合図はクールダウンより優先度が低い
 *    （クールダウン中はDWELLの再通知も出さない）。
 */
object NotificationSuppressionPolicy {

    /**
     * 曜日・時間帯の制限内かどうか。制限内＝通知してよい側なら true。
     * `allowedDays`が空集合（全曜日オフ）の場合は、どの曜日も一致しないため常に false
     * （＝常に抑止）になる。
     *
     * 日をまたぐ区間（`startMinute > endMinute`、例: 22:00〜翌6:00）では、
     * どの曜日で判定するかが開始側と終了側で異なる点に注意。「月曜22時〜翌6時」の
     * 窓は、開始側（22時台、`minuteOfDay >= start`）は当日（月曜）の曜日で判定するが、
     * 終了側（深夜0時〜6時、`minuteOfDay < end`）は前日の夜から続く同じ窓なので
     * **前日**（月曜の場合は日曜ではなく、この窓自体は月曜が起点なので「当日＝月曜」から
     * 見て1日前）の曜日で判定する必要がある。当日の曜日で終了側も判定すると、
     * 「月曜だけ許可」のつもりが火曜未明を誤って抑止し、逆に日曜の窓の続きである
     * 月曜未明を誤って許可してしまう（Codexレビュー指摘）。
     */
    fun isWithinAllowedWindow(
        dayOfWeek: Int,
        minuteOfDay: Int,
        preferences: NotificationPreferences,
    ): Boolean {
        val start = preferences.startMinute
        val end = preferences.endMinute
        if (start <= end) {
            // 通常の日中区間（例: 8:00〜22:00）。日をまたがないので当日の曜日で判定する。
            return dayOfWeek in preferences.allowedDays && minuteOfDay in start until end
        }
        // 日をまたぐ区間（例: 22:00〜翌6:00）。
        return when {
            minuteOfDay >= start -> dayOfWeek in preferences.allowedDays
            minuteOfDay < end -> previousDayOfWeek(dayOfWeek) in preferences.allowedDays
            else -> false
        }
    }

    /** [java.util.Calendar.DAY_OF_WEEK]（日=1〜土=7）における前日を返す。 */
    private fun previousDayOfWeek(dayOfWeek: Int): Int =
        if (dayOfWeek == 1) 7 else dayOfWeek - 1

    /** ENTER（最初の到達）で通知してよいか。 */
    fun shouldNotifyOnEnter(
        now: Long,
        lastNotifiedAt: Long,
        dayOfWeek: Int,
        minuteOfDay: Int,
        preferences: NotificationPreferences,
    ): Boolean {
        if (!isWithinAllowedWindow(dayOfWeek, minuteOfDay, preferences)) return false
        return now - lastNotifiedAt >= cooldownMs(preferences)
    }

    /** DWELL（滞在継続の二度目の合図）で通知してよいか。 */
    fun shouldNotifyOnDwell(
        now: Long,
        lastNotifiedAt: Long,
        lastNudgedAt: Long,
        sameVisitMs: Long,
        dayOfWeek: Int,
        minuteOfDay: Int,
        preferences: NotificationPreferences,
    ): Boolean {
        if (!isWithinAllowedWindow(dayOfWeek, minuteOfDay, preferences)) return false
        val sameVisit = now - lastNotifiedAt < sameVisitMs
        val nudgedRecently = now - lastNudgedAt < cooldownMs(preferences)
        return sameVisit && !nudgedRecently
    }

    private fun cooldownMs(preferences: NotificationPreferences): Long =
        preferences.cooldownMinutes * 60_000L
}
