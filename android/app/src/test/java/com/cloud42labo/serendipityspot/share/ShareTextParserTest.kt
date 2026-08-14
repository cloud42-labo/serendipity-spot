package com.cloud42labo.serendipityspot.share

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

class ShareTextParserTest {

    // ------------------------------------------------------------------
    // (a) 前処理
    // ------------------------------------------------------------------

    @Test
    fun `null本文はUnparsable`() {
        assertEquals(SharedPlace.Unparsable, ShareTextParser.parse(null))
    }

    @Test
    fun `空文字はUnparsable`() {
        assertEquals(SharedPlace.Unparsable, ShareTextParser.parse(""))
    }

    @Test
    fun `空白のみはUnparsable`() {
        assertEquals(SharedPlace.Unparsable, ShareTextParser.parse("   \n\t  "))
    }

    // ------------------------------------------------------------------
    // (c)(d) Google Maps URL 系
    // ------------------------------------------------------------------

    @Test
    fun `place付きURLは座標と名前の両方が取れる`() {
        val result = ShareTextParser.parse(
            "https://www.google.com/maps/place/東京タワー/@35.6586,139.7454,17z",
        )
        val located = result as? SharedPlace.Located ?: fail("Located exp待だったが $result") as Nothing
        assertEquals(35.6586, located.lat, 0.0001)
        assertEquals(139.7454, located.lng, 0.0001)
        assertEquals("東京タワー", located.name)
    }

    @Test
    fun `atだけの座標URLはLocatedで名前はnullでよい`() {
        val result = ShareTextParser.parse("https://www.google.com/maps/@35.6586,139.7454,17z")
        val located = result as? SharedPlace.Located ?: fail("Located期待だったが $result") as Nothing
        assertEquals(35.6586, located.lat, 0.0001)
        assertEquals(139.7454, located.lng, 0.0001)
    }

    @Test
    fun `api1クエリ形式はLocated`() {
        val result = ShareTextParser.parse(
            "https://www.google.com/maps/search/?api=1&query=35.6586,139.7454",
        )
        val located = result as? SharedPlace.Located ?: fail("Located期待だったが $result") as Nothing
        assertEquals(35.6586, located.lat, 0.0001)
        assertEquals(139.7454, located.lng, 0.0001)
    }

    @Test
    fun `geoスキームのみはLocated`() {
        val result = ShareTextParser.parse("geo:35.6586,139.7454")
        val located = result as? SharedPlace.Located ?: fail("Located期待だったが $result") as Nothing
        assertEquals(35.6586, located.lat, 0.0001)
        assertEquals(139.7454, located.lng, 0.0001)
    }

    @Test
    fun `geoの0,0プレースホルダはq側の座標と名前を採用する`() {
        val result = ShareTextParser.parse("geo:0,0?q=35.6586,139.7454(東京タワー)")
        val located = result as? SharedPlace.Located ?: fail("Located期待だったが $result") as Nothing
        assertEquals(35.6586, located.lat, 0.0001)
        assertEquals(139.7454, located.lng, 0.0001)
        assertEquals("東京タワー", located.name)
    }

    // ------------------------------------------------------------------
    // 短縮URL・一般URL
    // ------------------------------------------------------------------

    @Test
    fun `短縮URLと施設名の組はSearchTermで施設名が採用される`() {
        val result = ShareTextParser.parse("東京タワー\nhttps://maps.app.goo.gl/abc123")
        val searchTerm = result as? SharedPlace.SearchTerm ?: fail("SearchTerm期待だったが $result") as Nothing
        assertEquals("東京タワー", searchTerm.query)
    }

    @Test
    fun `一般URLのみはUnparsable`() {
        val result = ShareTextParser.parse("https://example.com/article/1")
        assertEquals(SharedPlace.Unparsable, result)
    }

    @Test
    fun `一般URLと記事タイトルの組はSearchTerm`() {
        val result = ShareTextParser.parse("渋谷のおいしいカフェ10選\nhttps://example.com/a")
        assertTrue(result is SharedPlace.SearchTerm)
    }

    // ------------------------------------------------------------------
    // プレーンテキスト
    // ------------------------------------------------------------------

    @Test
    fun `プレーンテキストのみはSearchTerm`() {
        val result = ShareTextParser.parse("渋谷スクランブル交差点")
        assertTrue(result is SharedPlace.SearchTerm)
    }

    @Test
    fun `素の座標テキストはLocated`() {
        val result = ShareTextParser.parse("35.6586, 139.7454")
        val located = result as? SharedPlace.Located ?: fail("Located期待だったが $result") as Nothing
        assertEquals(35.6586, located.lat, 0.0001)
        assertEquals(139.7454, located.lng, 0.0001)
    }

    // ------------------------------------------------------------------
    // 妥当性検証・異常系
    // ------------------------------------------------------------------

    @Test
    fun `範囲外座標は採用されずLocatedにならない`() {
        val result = ShareTextParser.parse("https://www.google.com/maps/@999.0,999.0,17z")
        assertTrue("Locatedになってはいけない: $result", result !is SharedPlace.Located)
    }

    @Test
    fun `0,0のみのgeoはプレースホルダとして扱いLocatedにならない`() {
        val result = ShareTextParser.parse("geo:0,0")
        assertTrue("Locatedになってはいけない: $result", result !is SharedPlace.Located)
    }

    @Test
    fun `記号だけの本文はUnparsable`() {
        val result = ShareTextParser.parse("!!! ???")
        assertEquals(SharedPlace.Unparsable, result)
    }

    @Test
    fun `極端に長い本文でも例外を投げず検索語は100文字以内`() {
        val longText = "あ".repeat(5000)
        val result = ShareTextParser.parse(longText)
        val searchTerm = result as? SharedPlace.SearchTerm ?: fail("SearchTerm期待だったが $result") as Nothing
        assertTrue(searchTerm.query.length <= 100)
    }

    @Test
    fun `URLを含む極端に長い本文でも例外を投げない`() {
        val longText = "https://example.com/" + "a".repeat(10000) + "\n" + "見どころ満載のスポット".repeat(50)
        val result = ShareTextParser.parse(longText)
        assertTrue(result is SharedPlace.SearchTerm || result is SharedPlace.Unparsable)
        if (result is SharedPlace.SearchTerm) {
            assertTrue(result.query.length <= 100)
        }
    }

    // ------------------------------------------------------------------
    // sourceUrl の伝播
    // ------------------------------------------------------------------

    @Test
    fun `LocatedのsourceUrlに抽出したURLが入る`() {
        val result = ShareTextParser.parse("https://www.google.com/maps/@35.6586,139.7454,17z")
        val located = result as? SharedPlace.Located ?: fail("Located期待だったが $result") as Nothing
        assertEquals("https://www.google.com/maps/@35.6586,139.7454,17z", located.sourceUrl)
    }

    @Test
    fun `SearchTermのsourceUrlに抽出したURLが入る`() {
        val result = ShareTextParser.parse("東京タワー\nhttps://maps.app.goo.gl/abc123")
        val searchTerm = result as? SharedPlace.SearchTerm ?: fail("SearchTerm期待だったが $result") as Nothing
        assertEquals("https://maps.app.goo.gl/abc123", searchTerm.sourceUrl)
    }

    @Test
    fun `URLが無い場合のsourceUrlはnull`() {
        val result = ShareTextParser.parse("渋谷スクランブル交差点")
        val searchTerm = result as? SharedPlace.SearchTerm ?: fail("SearchTerm期待だったが $result") as Nothing
        assertNull(searchTerm.sourceUrl)
    }

    // ------------------------------------------------------------------
    // URL末尾の句読点・閉じ括弧の除去
    // ------------------------------------------------------------------

    @Test
    fun `URL末尾の句読点は取り除かれる`() {
        val result = ShareTextParser.parse(
            "見て！ https://www.google.com/maps/@35.6586,139.7454,17z。",
        )
        val located = result as? SharedPlace.Located ?: fail("Located期待だったが $result") as Nothing
        assertEquals(false, located.sourceUrl?.endsWith("。"))
    }
}
