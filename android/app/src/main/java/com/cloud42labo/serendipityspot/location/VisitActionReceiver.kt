package com.cloud42labo.serendipityspot.location

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.cloud42labo.serendipityspot.data.SpotLocalCache
import com.cloud42labo.serendipityspot.notification.NotificationHelper

/**
 * 通知のアクションボタン（「寄った」「取り消す」）を処理する（SPOT-04-S01-T02）。
 * [GeofenceBroadcastReceiver]と同じく、アプリがバックグラウンド/終了状態でも
 * 呼ばれるため、ローカルキャッシュ（[SpotLocalCache]）だけで完結させる。
 * 自動訪問判定はこのStoryの対象外で、ここは常にユーザーの明示的なタップに応じて動く。
 */
class VisitActionReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            ACTION_MARK_VISITED -> handleMarkVisited(context, intent)
            ACTION_UNDO_VISIT -> handleUndoVisit(context, intent)
        }
    }

    private fun handleMarkVisited(context: Context, intent: Intent) {
        val spotId = intent.getStringExtra(EXTRA_SPOT_ID) ?: return
        val spotTitle = intent.getStringExtra(EXTRA_SPOT_TITLE) ?: ""
        val notificationId = intent.getIntExtra(EXTRA_NOTIFICATION_ID, spotId.hashCode())

        val result = SpotLocalCache.addVisitRecord(context, spotId, spotTitle)
        // 重複タップの場合もresult.recordは直近の既存記録を指すため、
        // 「取り消す」は常にその1件だけを対象にできる。
        NotificationHelper.notifyVisitRecorded(
            context = context,
            notificationId = notificationId,
            spotTitle = spotTitle,
            recordId = result.record.id,
        )
    }

    private fun handleUndoVisit(context: Context, intent: Intent) {
        val recordId = intent.getStringExtra(EXTRA_RECORD_ID) ?: return
        val notificationId = intent.getIntExtra(EXTRA_NOTIFICATION_ID, -1)
        SpotLocalCache.removeVisitRecord(context, recordId)
        NotificationHelper.cancelVisitConfirmation(context, notificationId)
    }

    companion object {
        const val ACTION_MARK_VISITED = "com.cloud42labo.serendipityspot.ACTION_MARK_VISITED"
        const val ACTION_UNDO_VISIT = "com.cloud42labo.serendipityspot.ACTION_UNDO_VISIT"
        const val EXTRA_SPOT_ID = "extra_spot_id"
        const val EXTRA_SPOT_TITLE = "extra_spot_title"
        const val EXTRA_RECORD_ID = "extra_record_id"
        const val EXTRA_NOTIFICATION_ID = "extra_notification_id"
    }
}
