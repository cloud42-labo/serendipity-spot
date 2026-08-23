package com.cloud42labo.serendipityspot.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * SPOT-04-S01: 立ち寄り記録のJSON往復と、二重登録防止・取り消しのロジックを固定する。
 * VisitLogPolicyはContextに依存しない純粋関数なのでJUnitだけでテストできる
 * （NotificationSuppressionPolicyTestと同じ狙い）。
 */
class VisitRecordTest {

    // --- JSON往復 ---

    @Test
    fun `round-trips through JSON`() {
        val records = listOf(
            VisitRecord(id = "r1", spotId = "s1", spotTitle = "いい感じの喫茶店", recordedAt = 1_000L),
            VisitRecord(id = "r2", spotId = "s2", spotTitle = "駅前パン屋", recordedAt = 2_000L),
        )
        val restored = records.toJson().toVisitRecordList()
        assertEquals(records, restored)
    }

    @Test
    fun `blank JSON becomes an empty list`() {
        assertTrue("".toVisitRecordList().isEmpty())
    }

    // --- VisitLogPolicy.addVisitRecord（二重登録の防止） ---

    @Test
    fun `first tap adds a new record`() {
        val result = VisitLogPolicy.addVisitRecord(
            existing = emptyList(),
            spotId = "s1",
            spotTitle = "いい感じの喫茶店",
            at = 10_000L,
            newId = { "generated-id" },
        )
        assertFalse(result.wasDuplicate)
        assertEquals(1, result.records.size)
        assertEquals("generated-id", result.record.id)
        assertEquals("s1", result.record.spotId)
        assertEquals(10_000L, result.record.recordedAt)
    }

    @Test
    fun `tapping again within the guard window is treated as a duplicate`() {
        val existing = listOf(VisitRecord(id = "r1", spotId = "s1", spotTitle = "喫茶店", recordedAt = 10_000L))
        val result = VisitLogPolicy.addVisitRecord(
            existing = existing,
            spotId = "s1",
            spotTitle = "喫茶店",
            at = 10_000L + 60_000L, // 1分後、既定の重複ガード(5分)以内
        )
        assertTrue(result.wasDuplicate)
        assertEquals(existing, result.records) // 追加されていない
        assertEquals("r1", result.record.id) // 既存の記録を指す
    }

    @Test
    fun `tapping again after the guard window adds a new record`() {
        val existing = listOf(VisitRecord(id = "r1", spotId = "s1", spotTitle = "喫茶店", recordedAt = 10_000L))
        val at = 10_000L + 6 * 60_000L // 6分後、既定の重複ガード(5分)を超える
        val result = VisitLogPolicy.addVisitRecord(
            existing = existing,
            spotId = "s1",
            spotTitle = "喫茶店",
            at = at,
            newId = { "r2" },
        )
        assertFalse(result.wasDuplicate)
        assertEquals(2, result.records.size)
        assertEquals("r2", result.record.id)
    }

    @Test
    fun `duplicate guard is scoped to the same spot only`() {
        val existing = listOf(VisitRecord(id = "r1", spotId = "s1", spotTitle = "喫茶店", recordedAt = 10_000L))
        val result = VisitLogPolicy.addVisitRecord(
            existing = existing,
            spotId = "s2", // 別スポット
            spotTitle = "パン屋",
            at = 10_000L + 1_000L,
            newId = { "r2" },
        )
        assertFalse(result.wasDuplicate)
        assertEquals(2, result.records.size)
    }

    @Test
    fun `a record at exactly the guard boundary is still a duplicate`() {
        val existing = listOf(VisitRecord(id = "r1", spotId = "s1", spotTitle = "喫茶店", recordedAt = 10_000L))
        val result = VisitLogPolicy.addVisitRecord(
            existing = existing,
            spotId = "s1",
            spotTitle = "喫茶店",
            at = 10_000L + 5 * 60_000L, // ちょうど5分後（境界は含む）
            withinMs = 5 * 60_000L,
        )
        assertTrue(result.wasDuplicate)
    }

    // --- VisitLogPolicy.removeVisitRecord（誤操作の取り消し） ---

    @Test
    fun `removeVisitRecord removes only the matching record`() {
        val existing = listOf(
            VisitRecord(id = "r1", spotId = "s1", spotTitle = "喫茶店", recordedAt = 1_000L),
            VisitRecord(id = "r2", spotId = "s2", spotTitle = "パン屋", recordedAt = 2_000L),
        )
        val result = VisitLogPolicy.removeVisitRecord(existing, "r1")
        assertEquals(listOf(existing[1]), result)
    }

    @Test
    fun `removeVisitRecord with an unknown id is a no-op`() {
        val existing = listOf(VisitRecord(id = "r1", spotId = "s1", spotTitle = "喫茶店", recordedAt = 1_000L))
        assertEquals(existing, VisitLogPolicy.removeVisitRecord(existing, "unknown"))
    }

    // --- VisitLogPolicy.hasRecentVisitRecord（DWELL再通知の抑止判定、Codexレビュー指摘） ---

    @Test
    fun `hasRecentVisitRecord is true within the window for the same spot`() {
        val existing = listOf(VisitRecord(id = "r1", spotId = "s1", spotTitle = "喫茶店", recordedAt = 10_000L))
        assertTrue(VisitLogPolicy.hasRecentVisitRecord(existing, "s1", at = 10_000L + 60_000L, withinMs = 30 * 60_000L))
    }

    @Test
    fun `hasRecentVisitRecord is false after the window elapses`() {
        val existing = listOf(VisitRecord(id = "r1", spotId = "s1", spotTitle = "喫茶店", recordedAt = 10_000L))
        val withinMs = 30 * 60_000L
        assertFalse(VisitLogPolicy.hasRecentVisitRecord(existing, "s1", at = 10_000L + withinMs + 1, withinMs = withinMs))
    }

    @Test
    fun `hasRecentVisitRecord is scoped to the same spot only`() {
        val existing = listOf(VisitRecord(id = "r1", spotId = "s1", spotTitle = "喫茶店", recordedAt = 10_000L))
        assertFalse(VisitLogPolicy.hasRecentVisitRecord(existing, "s2", at = 10_000L + 1_000L, withinMs = 30 * 60_000L))
    }

    @Test
    fun `hasRecentVisitRecord is false with no records`() {
        assertFalse(VisitLogPolicy.hasRecentVisitRecord(emptyList(), "s1", at = 10_000L, withinMs = 30 * 60_000L))
    }
}
