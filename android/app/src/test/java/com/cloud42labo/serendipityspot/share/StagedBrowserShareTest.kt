package com.cloud42labo.serendipityspot.share

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * ブラウザ共有を手動ステージ（自動検索しない）で取り込むときの検索語（BUG-SPOT-02-03）。
 *
 * [ShareIntentReader] から [ShareTextParser.parseShared] までを通しで確かめる。ここが
 * 分かれていると、片方だけ直しても実際に検索欄へ入る文字列は直らない。
 *
 * 押さえたい退行: URLに `q=` / `query=` が付いていると
 * [ShareTextParser] がそれを本文行より先に採用するため、タイトルを合成しても
 * 検索欄にURLパラメータの値が入ってページ名が消える（Codexレビュー指摘）。
 */
class StagedBrowserShareTest {

    /** ブラウザ共有（URLはEXTRA_TEXT、ページ名はEXTRA_TITLE）を実際の経路どおりに解析する。 */
    private fun shareFromBrowser(url: String, title: String?): SharedPlace {
        val shared = ShareIntentReader.sharedShareTextOf(
            action = ShareIntentReader.ACTION_SEND,
            type = "text/plain",
            extraText = url,
            extraTitle = title,
        )!!
        return ShareTextParser.parseShared(shared.text, shared.autoSearch, shared.stagedTitle)
    }

    @Test
    fun `queryパラメータ付きURLでもページ名が検索語になる`() {
        val place = shareFromBrowser(
            url = "https://example.com/search?query=123",
            title = "今日の主要ニュースまとめ",
        )

        val term = place as SharedPlace.SearchTerm
        assertEquals("今日の主要ニュースまとめ", term.query)
        assertFalse(term.autoRun)
    }

    @Test
    fun `qパラメータ付きURLでもページ名が検索語になる`() {
        val place = shareFromBrowser(
            url = "https://example.com/find?q=%E3%83%AC%E3%82%B7%E3%83%94",
            title = "夏に作りたい冷たい麺",
        )

        val term = place as SharedPlace.SearchTerm
        assertEquals("夏に作りたい冷たい麺", term.query)
        assertFalse(term.autoRun)
    }

    @Test
    fun `パラメータの無いURLでは従来どおりページ名が検索語になる`() {
        val place = shareFromBrowser(
            url = "https://example.com/news/123",
            title = "今日の主要ニュースまとめ",
        )

        val term = place as SharedPlace.SearchTerm
        assertEquals("今日の主要ニュースまとめ", term.query)
        assertFalse(term.autoRun)
    }

    @Test
    fun `施設名らしいタイトルは従来どおり自動検索される`() {
        val place = shareFromBrowser(
            url = "https://www.aeon.jp/sc/marinpia/",
            title = "イオンマリンピアショッピングセンター",
        )

        val term = place as SharedPlace.SearchTerm
        assertEquals("イオンマリンピアショッピングセンター", term.query)
        assertTrue(term.autoRun)
    }

    @Test
    fun `Google Maps共有の座標は上書きされない`() {
        // 本文がURLだけではない（施設名＋URL）ので合成もステージも起きない従来経路。
        val place = ShareTextParser.parseShared(
            text = "東京タワー\nhttps://www.google.com/maps/place/%E6%9D%B1%E4%BA%AC%E3%82%BF%E3%83%AF%E3%83%BC/@35.6585805,139.7454329,17z",
            autoSearch = true,
        )

        val located = place as SharedPlace.Located
        assertEquals(35.6585805, located.lat, 0.0000001)
        assertEquals(139.7454329, located.lng, 0.0000001)
    }

    @Test
    fun `座標が取れる共有は手動ステージでも登録経路のまま`() {
        // Located は検索を経由しないので、stagedTitle があっても影響を受けてはいけない。
        val place = ShareTextParser.parseShared(
            text = "https://www.google.com/maps/place/x/@35.6585805,139.7454329,17z",
            autoSearch = false,
            stagedTitle = "無関係なページ名",
        )

        assertTrue(place is SharedPlace.Located)
    }

    @Test
    fun `検索語にならないタイトルのときは解析結果を残す`() {
        // 記号だけのタイトルは normalizeSearchTerm が弾く。URL由来の候補へ落とす。
        val place = ShareTextParser.parseShared(
            text = "!!!\nhttps://example.com/search?query=%E5%8D%83%E8%91%89%E3%83%9D%E3%83%BC%E3%83%88%E3%82%BF%E3%83%AF%E3%83%BC",
            autoSearch = false,
            stagedTitle = "!!!",
        )

        val term = place as SharedPlace.SearchTerm
        assertEquals("千葉ポートタワー", term.query)
        assertFalse(term.autoRun)
    }

    @Test
    fun `素のparseはURLパラメータを本文行より優先する`() {
        // このテストは直すべき挙動ではなく、上の退行テストが効いている理由の記録。
        // parseShared 側の上書きが外れると、検索欄へ入るのはこちらの "123" に戻る。
        val place = ShareTextParser.parse("今日の主要ニュースまとめ\nhttps://example.com/search?query=123")

        assertEquals("123", (place as SharedPlace.SearchTerm).query)
    }

    @Test
    fun `解析不能な共有は従来どおりUnparsableのまま`() {
        assertTrue(ShareTextParser.parseShared("   ", autoSearch = false) is SharedPlace.Unparsable)
    }
}
