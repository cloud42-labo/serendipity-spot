package com.cloud42labo.serendipityspot.share

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * ブラウザ（Chrome等）からの共有。URLはEXTRA_TEXT、ページタイトルはEXTRA_TITLEへ
 * 分離して渡されるため、タイトルを本文へ合成してから解析する。
 *
 * 方針の変更（2026-08-15）: 以前は施設名らしいタイトルのときだけ合成し、それ以外は
 * URLだけを残していた。しかしそれだと検索欄が空で開くだけになり、利用者が結局
 * 手で打ち直すことになる（実機で「ブラウザ共有が動かない」として報告された）。
 * 現在はタイトルを常に引き継いだうえで、自動検索するかどうかだけを分ける。
 * 「一般記事のタイトルを勝手に場所検索へ回さない」という元の狙いは、
 * 自動実行しないことで達成されるため、タイトルを捨てる必要はない。
 */
class ChromeShareIntentReaderTest {

    @Test
    fun `Chrome施設ページはEXTRA_TITLEを検索語として本文へ合成する`() {
        val result = ShareIntentReader.sharedShareTextOf(
            action = ShareIntentReader.ACTION_SEND,
            type = "text/plain",
            extraText = "https://www.aeon.jp/sc/marinpia/",
            extraTitle = "イオンマリンピアショッピングセンター",
        )!!

        assertEquals(
            "イオンマリンピアショッピングセンター\nhttps://www.aeon.jp/sc/marinpia/",
            result.text,
        )
        // 施設名らしいので、そのまま検索してよい
        assertTrue(result.autoSearch)
    }

    @Test
    fun `一般記事タイトルも引き継ぐが自動場所検索はさせない`() {
        val result = ShareIntentReader.sharedShareTextOf(
            action = ShareIntentReader.ACTION_SEND,
            type = "text/plain",
            extraText = "https://example.com/news/123",
            extraTitle = "今日の主要ニュースまとめ",
        )!!

        // タイトルは捨てない（捨てると検索欄が空で開くだけになる）
        assertEquals("今日の主要ニュースまとめ\nhttps://example.com/news/123", result.text)
        // ただし勝手に場所検索は走らせない
        assertFalse(result.autoSearch)
    }

    @Test
    fun `閉店ニュースは店の文字を含んでも自動検索しない`() {
        val result = ShareIntentReader.sharedShareTextOf(
            action = ShareIntentReader.ACTION_SEND,
            type = "text/plain",
            extraText = "https://example.com/news/closed",
            extraTitle = "駅前の老舗が閉店へ",
        )!!

        assertEquals("駅前の老舗が閉店へ\nhttps://example.com/news/closed", result.text)
        assertFalse(result.autoSearch)
    }

    @Test
    fun `施設タイトルがあってもURL以外の共有本文は改変しない`() {
        // 本文がURLだけでない場合は、本文側に情報があるのでタイトル合成はしない
        val result = ShareIntentReader.sharedShareTextOf(
            action = ShareIntentReader.ACTION_SEND,
            type = "text/plain",
            extraText = "東京タワー",
            extraTitle = "東京タワー",
        )!!

        assertEquals("東京タワー", result.text)
        assertTrue(result.autoSearch)
    }

    @Test
    fun `タイトルが無いURLだけの共有は本文を変えず自動検索の対象にする`() {
        // Google Maps の短縮URL共有など。合成する材料が無いのでそのまま渡す。
        val result = ShareIntentReader.sharedShareTextOf(
            action = ShareIntentReader.ACTION_SEND,
            type = "text/plain",
            extraText = "https://maps.app.goo.gl/abc123",
            extraTitle = null,
        )!!

        assertEquals("https://maps.app.goo.gl/abc123", result.text)
        assertTrue(result.autoSearch)
    }

    @Test
    fun `手動ステージのときはタイトルを別値でも渡す`() {
        val result = ShareIntentReader.sharedShareTextOf(
            action = ShareIntentReader.ACTION_SEND,
            type = "text/plain",
            extraText = "https://example.com/search?query=123",
            extraTitle = "今日の主要ニュースまとめ",
        )!!

        assertFalse(result.autoSearch)
        // 合成本文だけでは、URLの query=123 が本文行より優先されてタイトルが消える。
        // 別値で持たせて後段が優先できるようにする。
        assertEquals("今日の主要ニュースまとめ", result.stagedTitle)
    }

    @Test
    fun `自動検索する経路ではタイトルを別値で渡さない`() {
        val result = ShareIntentReader.sharedShareTextOf(
            action = ShareIntentReader.ACTION_SEND,
            type = "text/plain",
            extraText = "https://www.aeon.jp/sc/marinpia/",
            extraTitle = "イオンマリンピアショッピングセンター",
        )!!

        assertTrue(result.autoSearch)
        assertNull(result.stagedTitle)
    }

    @Test
    fun `タイトルの無い共有には別値が付かない`() {
        val result = ShareIntentReader.sharedShareTextOf(
            action = ShareIntentReader.ACTION_SEND,
            type = "text/plain",
            extraText = "https://maps.app.goo.gl/abc123",
            extraTitle = null,
        )!!

        assertNull(result.stagedTitle)
    }

    @Test
    fun `従来の sharedTextOf は合成後の本文を返す`() {
        // 既存の呼び出し互換。本文だけが要る場面ではこちらを使える。
        val text = ShareIntentReader.sharedTextOf(
            action = ShareIntentReader.ACTION_SEND,
            type = "text/plain",
            extraText = "https://www.aeon.jp/sc/marinpia/",
            extraTitle = "イオンマリンピアショッピングセンター",
        )

        assertEquals(
            "イオンマリンピアショッピングセンター\nhttps://www.aeon.jp/sc/marinpia/",
            text,
        )
    }
}
