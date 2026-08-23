package com.cloud42labo.serendipityspot.location

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.cloud42labo.serendipityspot.data.SpotLocalCache
import com.cloud42labo.serendipityspot.notification.NotificationHelper
import com.google.android.gms.location.Geofence
import com.google.android.gms.location.GeofencingEvent
import java.util.Calendar

/**
 * システムからジオフェンス到達イベントを受け取る。
 * アプリがバックグラウンド/終了状態でも呼ばれるため、ここでは
 * ローカルキャッシュ（スプレッドシートの最新コピー）だけを参照して通知する。
 * ネットワークやサインイン状態には依存しない。
 */
class GeofenceBroadcastReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        // 何を受け取ったかは、握りつぶす場合も含めて必ず残す。
        // 「イベント自体が来ていない」のか「来ているが弾いている」のかを
        // 端末だけで切り分けられるようにするため。
        if (intent.action != ACTION_GEOFENCE_EVENT) {
            SpotLocalCache.saveLastGeofenceEvent(context, "別のaction: ${intent.action}")
            return
        }

        val geofencingEvent = GeofencingEvent.fromIntent(intent)
        if (geofencingEvent == null) {
            SpotLocalCache.saveLastGeofenceEvent(context, "中身を取り出せず")
            return
        }
        if (geofencingEvent.hasError()) {
            SpotLocalCache.saveLastGeofenceEvent(context, "エラー code=${geofencingEvent.errorCode}")
            return
        }

        val transition = geofencingEvent.geofenceTransition
        val isEnter = transition == Geofence.GEOFENCE_TRANSITION_ENTER
        val isDwell = transition == Geofence.GEOFENCE_TRANSITION_DWELL
        if (!isEnter && !isDwell) {
            SpotLocalCache.saveLastGeofenceEvent(context, "transition=$transition（対象外）")
            return
        }

        NotificationHelper.ensureChannel(context)
        val cachedSpots = SpotLocalCache.load(context).associateBy { it.id }
        val now = System.currentTimeMillis()
        val preferences = SpotLocalCache.loadNotificationPreferences(context)
        val calendar = Calendar.getInstance().apply { timeInMillis = now }
        val dayOfWeek = calendar.get(Calendar.DAY_OF_WEEK)
        val minuteOfDay = calendar.get(Calendar.HOUR_OF_DAY) * 60 + calendar.get(Calendar.MINUTE)

        var notified = 0
        var suppressed = 0
        var unknown = 0

        geofencingEvent.triggeringGeofences?.forEach { geofence ->
            val spot = cachedSpots[geofence.requestId]
            if (spot == null) {
                unknown++
                return@forEach
            }
            val lastNotified = SpotLocalCache.lastNotifiedAt(context, spot.id)

            if (isDwell) {
                // 既に「寄った」を記録済みの滞在では、DWELLの再通知を出さない。
                // notifyVisitRecorded()とnotifyNearby()は同じ通知IDを使うため、
                // ここで通常の近接通知を出すと、表示中の「取り消す」ボタン付き確認通知が
                // 上書きされ、誤操作を取り消す手段が失われる
                // （Codexレビュー指摘、SPOT-04-S01のPR #25）。
                if (SpotLocalCache.hasRecentVisitRecord(context, spot.id, now, SAME_VISIT_MS)) {
                    suppressed++
                    return@forEach
                }

                // 二度目の合図。一度目を見逃した場合の救済なので、
                // 「同じ滞在の続き」であるときに限る。優先順位の定義は
                // NotificationSuppressionPolicy（SPOT-03-S02-T01/T02）。
                val shouldNotify = NotificationSuppressionPolicy.shouldNotifyOnDwell(
                    now = now,
                    lastNotifiedAt = lastNotified,
                    lastNudgedAt = SpotLocalCache.lastNudgedAt(context, spot.id),
                    sameVisitMs = SAME_VISIT_MS,
                    dayOfWeek = dayOfWeek,
                    minuteOfDay = minuteOfDay,
                    preferences = preferences,
                )
                if (!shouldNotify) {
                    suppressed++
                    return@forEach
                }
                SpotLocalCache.markNudged(context, spot.id, now)
                NotificationHelper.notifyNearby(context, spot, isNudge = true)
                notified++
                return@forEach
            }

            // 同じスポットで鳴り続けないようにする。出入りを繰り返す場所でも
            // 一定時間は1回にまとめる（クールダウン）。曜日・時間帯の制限も
            // ここで併せて評価する（SPOT-03-S02）。
            val shouldNotify = NotificationSuppressionPolicy.shouldNotifyOnEnter(
                now = now,
                lastNotifiedAt = lastNotified,
                dayOfWeek = dayOfWeek,
                minuteOfDay = minuteOfDay,
                preferences = preferences,
            )
            if (!shouldNotify) {
                suppressed++
                return@forEach
            }
            SpotLocalCache.markNotified(context, spot.id, now)
            NotificationHelper.notifyNearby(context, spot)
            notified++
        }

        SpotLocalCache.saveLastGeofenceEvent(
            context,
            "${if (isDwell) "DWELL" else "ENTER"} 通知=${notified}件 " +
                "抑制=${suppressed}件 不明=${unknown}件",
        )
    }

    companion object {
        const val ACTION_GEOFENCE_EVENT = "com.cloud42labo.serendipityspot.ACTION_GEOFENCE_EVENT"

        /**
         * 一度目の通知からこの時間内の DWELL は「同じ滞在の続き」とみなし、
         * 二度目の合図を送る。これを過ぎていたら、一度離れて戻ったと考えて送らない。
         */
        private const val SAME_VISIT_MS = 30 * 60 * 1000L
    }
}
