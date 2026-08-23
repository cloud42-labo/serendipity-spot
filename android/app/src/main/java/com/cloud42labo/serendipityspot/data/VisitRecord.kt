package com.cloud42labo.serendipityspot.data

import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

/**
 * 通知の「寄った」アクションから記録した立ち寄り履歴（Serendipity Log、SPOT-04-S01）。
 * スプレッドシート（[Spot]の正）には一切書き戻さない、端末ローカルのみの記録。
 *
 * [spotTitle]は記録した瞬間のスポット名を複製して保持する。後でスプレッドシート側の
 * スポットが削除・改名されても、過去の記録が「どこへの記録だったか」を失わないため
 * （SPOT-PLAN-02の検証記録にある通り、Spot自体には立ち寄り履歴の概念が無いため、
 * 履歴側が自己完結して意味を保てる必要がある）。
 *
 * 自動訪問判定（ジオフェンス到達だけで記録する）はこのStoryの対象外。あくまで
 * ユーザーが通知上で明示的に操作した結果だけを記録する。
 */
data class VisitRecord(
    val id: String,
    val spotId: String,
    val spotTitle: String,
    val recordedAt: Long,
)

fun newVisitRecordId(): String = UUID.randomUUID().toString()

fun List<VisitRecord>.toJson(): String {
    val arr = JSONArray()
    forEach { record ->
        arr.put(
            JSONObject().apply {
                put("id", record.id)
                put("spotId", record.spotId)
                put("spotTitle", record.spotTitle)
                put("recordedAt", record.recordedAt)
            }
        )
    }
    return arr.toString()
}

fun String.toVisitRecordList(): List<VisitRecord> {
    if (isBlank()) return emptyList()
    val arr = JSONArray(this)
    return (0 until arr.length()).map { i ->
        val o = arr.getJSONObject(i)
        VisitRecord(
            id = o.getString("id"),
            spotId = o.getString("spotId"),
            spotTitle = o.optString("spotTitle", ""),
            recordedAt = o.getLong("recordedAt"),
        )
    }
}

/**
 * 記録の追加・重複判定を、Contextに依存しない純粋関数として切り出したもの
 * （[NotificationSuppressionPolicy][com.cloud42labo.serendipityspot.location.NotificationSuppressionPolicy]
 * と同じ狙い。JUnitだけでテストできるようにする）。
 * [SpotLocalCache]から現在時刻・既存の履歴を渡して使う。
 */
object VisitLogPolicy {

    /** 同じスポットへの記録が[withinMs]以内に既にあれば二重登録とみなす。 */
    private const val DEFAULT_DUPLICATE_GUARD_MS = 5 * 60 * 1000L // 5分

    /**
     * [existing]に[spotId]への記録を追加してよいか判定し、追加後の一覧を返す。
     * [withinMs]以内に同じスポットへの記録が既にあれば、通知の連打や配信重複と見なし
     * 追加しない（SPOT-04-S01-T02の「二重登録を防ぐ」AC）。その場合は直近の既存記録を、
     * 新規追加した場合は新しい記録を[AddVisitResult.record]として返す。
     */
    fun addVisitRecord(
        existing: List<VisitRecord>,
        spotId: String,
        spotTitle: String,
        at: Long,
        withinMs: Long = DEFAULT_DUPLICATE_GUARD_MS,
        newId: () -> String = ::newVisitRecordId,
    ): AddVisitResult {
        val recent = existing.lastOrNull { it.spotId == spotId && at - it.recordedAt in 0..withinMs }
        if (recent != null) {
            return AddVisitResult(records = existing, record = recent, wasDuplicate = true)
        }
        val record = VisitRecord(id = newId(), spotId = spotId, spotTitle = spotTitle, recordedAt = at)
        return AddVisitResult(records = existing + record, record = record, wasDuplicate = false)
    }

    /** [recordId]の記録を取り消す（誤操作の取り消し、履歴からの削除の最小単位）。 */
    fun removeVisitRecord(existing: List<VisitRecord>, recordId: String): List<VisitRecord> =
        existing.filterNot { it.id == recordId }

    data class AddVisitResult(
        val records: List<VisitRecord>,
        val record: VisitRecord,
        val wasDuplicate: Boolean,
    )
}
