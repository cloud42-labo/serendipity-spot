package com.cloud42labo.serendipityspot.ui

import com.cloud42labo.serendipityspot.data.PlaceResult
import com.cloud42labo.serendipityspot.data.PlaceSearchOutcome
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * BUG-SPOT-03-01 の回帰テスト。
 *
 * 「検索しても何も起きない」の正体は2つあった。
 * 1. 0件・検索失敗・検索機能なし がすべて空リストへ潰され、区別して伝えられなかった
 * 2. 0件のたびに同一文言を代入していたため、状態が変化せずスナックバーが再表示されなかった
 *
 * ここでは文言の決定ロジックを固定する。
 */
class SearchFeedbackTest {

    private val tokyoTower = PlaceResult(
        name = "東京タワー",
        subtitle = "東京都港区芝公園4-2-8",
        lat = 35.6586,
        lng = 139.7454,
    )

    @Test
    fun `候補が取れたときは何も表示しない`() {
        // 成功時に余計なメッセージを出さないこと
        val outcome = PlaceSearchOutcome.Success(listOf(tokyoTower))
        assertNull(SearchFeedback.messageFor(outcome, "東京タワー"))
    }

    @Test
    fun `0件のときは検索語を含めて見つからなかったと伝える`() {
        // 0件が「無反応」ではなくメッセージになること
        val message = SearchFeedback.messageFor(PlaceSearchOutcome.Success(emptyList()), "東京タワー")
        assertEquals("「東京タワー」は見つかりませんでした", message)
    }

    @Test
    fun `検索失敗は0件とは違う文言になる`() {
        // 通信エラー等を「見つかりませんでした」と誤って伝えないこと
        val failed = SearchFeedback.messageFor(
            PlaceSearchOutcome.Failed(java.io.IOException("timeout")),
            "東京タワー",
        )
        val empty = SearchFeedback.messageFor(PlaceSearchOutcome.Success(emptyList()), "東京タワー")
        assertNotEquals(empty, failed)
        assertTrue(failed!!.contains("通信"))
    }

    @Test
    fun `検索機能が使えない端末は0件とも失敗とも違う文言になる`() {
        // 何度やっても無駄なケースを、やり直せばよいケースと混同しないこと
        val unavailable = SearchFeedback.messageFor(PlaceSearchOutcome.Unavailable, "東京タワー")
        val empty = SearchFeedback.messageFor(PlaceSearchOutcome.Success(emptyList()), "東京タワー")
        val failed = SearchFeedback.messageFor(
            PlaceSearchOutcome.Failed(java.io.IOException("timeout")),
            "東京タワー",
        )
        assertNotEquals(empty, unavailable)
        assertNotEquals(failed, unavailable)
    }

    @Test
    fun `違う語で連続して0件になったときは文言が変わる`() {
        // 文言が変われば LaunchedEffect のキーが変わり、2回目も表示される。
        // 同じ語のときは変わらないため、表示側の消費（consumeErrorMessage）が必要になる。
        val first = SearchFeedback.messageFor(PlaceSearchOutcome.Success(emptyList()), "東京タワー")
        val second = SearchFeedback.messageFor(PlaceSearchOutcome.Success(emptyList()), "東京スカイツリー")
        assertNotEquals(first, second)
    }

    @Test
    fun `検索語の前後の空白は文言に持ち込まない`() {
        val message = SearchFeedback.messageFor(PlaceSearchOutcome.Success(emptyList()), "  東京タワー  ")
        assertEquals("「東京タワー」は見つかりませんでした", message)
    }
}
