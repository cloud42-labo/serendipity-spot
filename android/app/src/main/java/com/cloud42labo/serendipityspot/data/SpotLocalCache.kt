package com.cloud42labo.serendipityspot.data

import android.content.Context
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * スプレッドシートの内容をローカルに1部だけキャッシュする。
 * 端末再起動直後、まだGoogleサインイン処理やネットワーク疎通が済んでいない
 * タイミングでもジオフェンスを再登録できるようにするための最小限のもの。
 * 正はあくまでスプレッドシート側で、これは復元用のキャッシュにすぎない。
 */
object SpotLocalCache {
    private const val PREFS = "serendipity_spot_cache"
    private const val KEY_SPOTS = "spots_json"
    private const val KEY_SPREADSHEET_ID = "spreadsheet_id"
    private const val KEY_LAST_REGISTRATION = "diag_last_registration"
    private const val KEY_LAST_EVENT = "diag_last_event"
    private const val KEY_NOTIFIED_AT_PREFIX = "notified_at_"
    private const val KEY_NUDGED_AT_PREFIX = "nudged_at_"
    private const val KEY_ONBOARDING_SEEN = "onboarding_seen"
    private const val KEY_COOLDOWN_MINUTES = "notif_cooldown_minutes"
    private const val KEY_ALLOWED_DAYS = "notif_allowed_days"
    private const val KEY_START_MINUTE = "notif_start_minute"
    private const val KEY_END_MINUTE = "notif_end_minute"

    fun save(context: Context, spots: List<Spot>) {
        prefs(context).edit().putString(KEY_SPOTS, spots.toJson()).apply()
    }

    fun load(context: Context): List<Spot> {
        val json = prefs(context).getString(KEY_SPOTS, null) ?: return emptyList()
        return runCatching { json.toSpotList() }.getOrDefault(emptyList())
    }

    fun saveSpreadsheetId(context: Context, id: String) {
        prefs(context).edit().putString(KEY_SPREADSHEET_ID, id).apply()
    }

    fun loadSpreadsheetId(context: Context): String? =
        prefs(context).getString(KEY_SPREADSHEET_ID, null)

    /** そのスポットに最後に通知した時刻。まだ通知していなければ0。 */
    fun lastNotifiedAt(context: Context, spotId: String): Long =
        prefs(context).getLong(KEY_NOTIFIED_AT_PREFIX + spotId, 0L)

    fun markNotified(context: Context, spotId: String, at: Long = System.currentTimeMillis()) {
        prefs(context).edit().putLong(KEY_NOTIFIED_AT_PREFIX + spotId, at).apply()
    }

    /** そのスポットで「まだ近くにいます」の二度目を送った時刻。 */
    fun lastNudgedAt(context: Context, spotId: String): Long =
        prefs(context).getLong(KEY_NUDGED_AT_PREFIX + spotId, 0L)

    fun markNudged(context: Context, spotId: String, at: Long = System.currentTimeMillis()) {
        prefs(context).edit().putLong(KEY_NUDGED_AT_PREFIX + spotId, at).apply()
    }

    /** 初回説明（アプリの目的・権限の理由）を見せたら true。端末単位で一度だけ表示する。 */
    fun hasSeenOnboarding(context: Context): Boolean =
        prefs(context).getBoolean(KEY_ONBOARDING_SEEN, false)

    fun markOnboardingSeen(context: Context) {
        prefs(context).edit().putBoolean(KEY_ONBOARDING_SEEN, true).apply()
    }

    // --- 再通知クールダウン・通知可能時間帯の設定（SPOT-03-S02）。既定値は
    //     NotificationPreferencesのデフォルト（=制限なし・旧来のクールダウン3時間）と一致させる。

    fun loadNotificationPreferences(context: Context): NotificationPreferences {
        val p = prefs(context)
        val daysCsv = p.getString(KEY_ALLOWED_DAYS, null)
        val allowedDays = daysCsv
            ?.split(",")
            ?.mapNotNull { it.trim().toIntOrNull() }
            ?.toSet()
            ?.takeIf { it.isNotEmpty() }
            ?: NotificationPreferences.ALL_DAYS
        return NotificationPreferences(
            cooldownMinutes = p.getInt(
                KEY_COOLDOWN_MINUTES,
                NotificationPreferences.DEFAULT_COOLDOWN_MINUTES,
            ),
            allowedDays = allowedDays,
            startMinute = p.getInt(KEY_START_MINUTE, 0),
            endMinute = p.getInt(KEY_END_MINUTE, NotificationPreferences.DAY_MINUTES),
        )
    }

    fun saveNotificationPreferences(context: Context, preferences: NotificationPreferences) {
        prefs(context).edit()
            .putInt(KEY_COOLDOWN_MINUTES, preferences.cooldownMinutes)
            .putString(KEY_ALLOWED_DAYS, preferences.allowedDays.sorted().joinToString(","))
            .putInt(KEY_START_MINUTE, preferences.startMinute)
            .putInt(KEY_END_MINUTE, preferences.endMinute)
            .apply()
    }

    // --- 以下は診断用。通知が来ないときに「登録できているか」「イベントが届いているか」を
    //     切り分けるためだけのもの。アプリの動作そのものには影響しない。

    fun saveLastRegistration(context: Context, text: String) {
        prefs(context).edit().putString(KEY_LAST_REGISTRATION, stamped(text)).apply()
    }

    fun loadLastRegistration(context: Context): String? =
        prefs(context).getString(KEY_LAST_REGISTRATION, null)

    fun saveLastGeofenceEvent(context: Context, text: String) {
        prefs(context).edit().putString(KEY_LAST_EVENT, stamped(text)).apply()
    }

    fun loadLastGeofenceEvent(context: Context): String? =
        prefs(context).getString(KEY_LAST_EVENT, null)

    private fun stamped(text: String): String {
        val now = SimpleDateFormat("MM/dd HH:mm:ss", Locale.JAPAN).format(Date())
        return "$now  $text"
    }

    private fun prefs(context: Context) =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
}
