package com.cloud42labo.serendipityspot.share

import org.junit.Assert.assertNull
import org.junit.Test

class GoogleMapsShareResolverSecurityTest {
    @Test
    fun `httpの短縮URLは対象外`() {
        assertNull(GoogleMapsShareResolver.extractShortUrl("http://maps.app.goo.gl/abc123"))
    }

    @Test
    fun `短縮URLに見える文字列だけでは対象外`() {
        assertNull(GoogleMapsShareResolver.extractShortUrl("maps.app.goo.gl/abc123"))
    }
}
