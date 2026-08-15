package com.cloud42labo.serendipityspot.ui

import com.cloud42labo.serendipityspot.share.SharedPlace
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 共有を検索画面へ取り込むときの分岐（BUG-SPOT-02-03）。
 *
 * 特に「自動実行しない経路では前回の検索を捨てる」を固定する。捨てないと warm 起動時に
 * 古い結果とマーカーが新しい検索語の下に残り、共有直前の検索が後から完了して
 * 無関係な結果を出す（Codexレビュー指摘のP2）。
 */
class SharedSearchActionTest {

    @Test
    fun `施設名らしいタイトルは従来どおり自動検索される`() {
        val action = SharedSearchAction.forSharedPlace(
            SharedPlace.SearchTerm("イオンマリンピアショッピングセンター", sourceUrl = null),
        )

        assertEquals("イオンマリンピアショッピングセンター", action?.query)
        assertTrue(action!!.autoRun)
        assertNull(action.message)
    }

    @Test
    fun `施設名らしくないタイトルは検索欄へ入るが自動実行しない`() {
        val action = SharedSearchAction.forSharedPlace(
            SharedPlace.SearchTerm(
                query = "円安が家計に与える影響とは",
                sourceUrl = "https://example.com/news/1",
                autoRun = false,
            ),
        )

        // タイトルを捨てない。捨てると空欄で開いて利用者が打ち直すことになる。
        assertEquals("円安が家計に与える影響とは", action?.query)
        assertFalse(action!!.autoRun)
        assertEquals(SharedSearchAction.STAGED_MESSAGE, action.message)
    }

    @Test
    fun `自動実行しないタイトルは取り込み前に前回の検索を捨てる`() {
        val action = SharedSearchAction.forSharedPlace(
            SharedPlace.SearchTerm(
                query = "円安が家計に与える影響とは",
                sourceUrl = "https://example.com/news/1",
                autoRun = false,
            ),
        )

        assertTrue(action!!.clearPrevious)
    }

    @Test
    fun `解析不能な共有も前回の検索を捨てて空欄で開く`() {
        val action = SharedSearchAction.forSharedPlace(SharedPlace.Unparsable)

        assertEquals("", action?.query)
        assertTrue(action!!.clearPrevious)
        assertFalse(action.autoRun)
        assertEquals(SharedSearchAction.UNPARSABLE_MESSAGE, action.message)
    }

    @Test
    fun `自動検索する経路では検索側がジョブを捨てるので明示的なクリアは不要`() {
        val action = SharedSearchAction.forSharedPlace(
            SharedPlace.SearchTerm("千葉ポートタワー", sourceUrl = null),
        )

        assertFalse(action!!.clearPrevious)
    }

    @Test
    fun `座標が確定している共有は検索を使わない`() {
        val action = SharedSearchAction.forSharedPlace(
            SharedPlace.Located(lat = 35.6, lng = 140.1, name = "千葉ポートタワー", sourceUrl = null),
        )

        assertNull(action)
    }
}
