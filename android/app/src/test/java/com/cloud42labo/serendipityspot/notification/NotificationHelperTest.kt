package com.cloud42labo.serendipityspot.notification

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * SPOT-03-S03-T02: 通知本文の境界ケース（メモなし・短文・長文・改行や記号を含む文）で
 * 通知が破綻せず、施設名が常に識別できることを固定する。
 */
class NotificationHelperTest {

    // --- bodyFor（SPOT-03-S03-T01の本体） ---

    @Test
    fun `blank memo falls back to default body`() {
        assertEquals("近くに来ました！", NotificationHelper.bodyFor(""))
    }

    @Test
    fun `whitespace-only memo is treated as blank`() {
        assertEquals("近くに来ました！", NotificationHelper.bodyFor("   \n  "))
    }

    @Test
    fun `short memo passes through unchanged`() {
        assertEquals("お気に入りのパン屋", NotificationHelper.bodyFor("お気に入りのパン屋"))
    }

    @Test
    fun `memo at exactly the limit is not truncated`() {
        val memo = "あ".repeat(200)
        val body = NotificationHelper.bodyFor(memo)
        assertEquals(200, body.length)
        assertEquals(memo, body)
    }

    @Test
    fun `long memo is truncated with an ellipsis`() {
        val memo = "あ".repeat(300)
        val body = NotificationHelper.bodyFor(memo)
        assertEquals(201, body.length) // 200文字 + 省略記号1文字
        assertTrue(body.endsWith("…"))
        assertEquals("あ".repeat(200), body.removeSuffix("…"))
    }

    @Test
    fun `memo with newlines and symbols passes through without crashing`() {
        val memo = "駐車場は裏手→\n営業時間: 10:00〜19:00（水休み）★★★"
        val body = NotificationHelper.bodyFor(memo)
        assertEquals(memo, body)
    }

    // --- titleFor（施設名の識別性、SPOT-03-S03-T02のAC） ---

    @Test
    fun `title always contains the facility name on first notification`() {
        assertEquals("いい感じの喫茶店", NotificationHelper.titleFor("いい感じの喫茶店", isNudge = false))
    }

    @Test
    fun `title always contains the facility name on nudge`() {
        val title = NotificationHelper.titleFor("いい感じの喫茶店", isNudge = true)
        assertTrue(title.contains("いい感じの喫茶店"))
    }

    @Test
    fun `title is unaffected by how long or unusual the memo body is`() {
        // タイトルはspot.titleのみから組み立てられ、bodyFor(memo)の結果には一切依存しない。
        // メモがどれだけ長文・特殊文字でも施設名の識別性が保たれることの裏付け。
        val longMemoBody = NotificationHelper.bodyFor("あ".repeat(300))
        val title = NotificationHelper.titleFor("いい感じの喫茶店", isNudge = false)
        assertTrue(title.contains("いい感じの喫茶店"))
        assertTrue(longMemoBody.isNotEmpty())
    }
}
