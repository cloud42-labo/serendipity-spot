package com.cloud42labo.serendipityspot.share

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class GoogleMapsShareResolverTest {

    @Test
    fun `実機で得たmaps app goo gl短縮URLを抽出できる`() {
        val text = "https://maps.app.goo.gl/ZVgra8ycxy7HGVws5"
        assertEquals(text, GoogleMapsShareResolver.extractShortUrl(text))
    }

    @Test
    fun `施設名付き共有からも短縮URLを抽出できる`() {
        assertEquals(
            "https://maps.app.goo.gl/abc123",
            GoogleMapsShareResolver.extractShortUrl("東京タワー\nhttps://maps.app.goo.gl/abc123"),
        )
    }

    @Test
    fun `一般URLは短縮URLとして扱わない`() {
        assertNull(GoogleMapsShareResolver.extractShortUrl("https://example.com/article/1"))
    }

    @Test
    fun `google以外のgoo gl風ホストは扱わない`() {
        assertNull(GoogleMapsShareResolver.extractShortUrl("https://evil.example/maps.app.goo.gl/abc123"))
    }
}
