package com.cloud42labo.serendipityspot.share

import org.junit.Assert.assertEquals
import org.junit.Test

class ChromeShareIntentReaderTest {

    @Test
    fun `Chrome施設ページはEXTRA_TITLEを検索語として本文へ合成する`() {
        val result = ShareIntentReader.sharedTextOf(
            action = ShareIntentReader.ACTION_SEND,
            type = "text/plain",
            extraText = "https://www.aeon.jp/sc/marinpia/",
            extraTitle = "イオンマリンピアショッピングセンター",
        )

        assertEquals(
            "イオンマリンピアショッピングセンター\nhttps://www.aeon.jp/sc/marinpia/",
            result,
        )
    }

    @Test
    fun `一般記事タイトルはURLだけを維持して自動場所検索させない`() {
        val result = ShareIntentReader.sharedTextOf(
            action = ShareIntentReader.ACTION_SEND,
            type = "text/plain",
            extraText = "https://example.com/news/123",
            extraTitle = "今日の主要ニュースまとめ",
        )

        assertEquals("https://example.com/news/123", result)
    }

    @Test
    fun `施設タイトルがあってもURL以外の共有本文は改変しない`() {
        val result = ShareIntentReader.sharedTextOf(
            action = ShareIntentReader.ACTION_SEND,
            type = "text/plain",
            extraText = "東京タワー",
            extraTitle = "東京タワー",
        )

        assertEquals("東京タワー", result)
    }
}
