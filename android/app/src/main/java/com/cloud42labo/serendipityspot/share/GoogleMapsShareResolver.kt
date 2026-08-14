package com.cloud42labo.serendipityspot.share

import java.net.HttpURLConnection
import java.net.URL
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Google Maps が実機共有で施設名を付けず maps.app.goo.gl の短縮URLだけを渡すケースを補う。
 *
 * 外部APIは使わず、Google管理ドメイン内のHTTPリダイレクトだけを追跡し、最終URLを
 * 既存の [ShareTextParser] に渡せる文字列として返す。任意URLへのアクセスを避けるため、
 * 初期URLは maps.app.goo.gl に限定し、リダイレクト先もHTTPSのGoogleドメインだけ許可する。
 */
object GoogleMapsShareResolver {
    private const val MAX_REDIRECTS = 6
    private const val TIMEOUT_MS = 5_000

    private val SHORT_URL_REGEX = Regex(
        """https://maps\.app\.goo\.gl/[A-Za-z0-9_-]+""",
        RegexOption.IGNORE_CASE,
    )

    /**
     * 短縮URLを含まなければ元の本文をそのまま返す。
     * 短縮URLの解決に失敗した場合も元本文へ戻し、既存の安全なフォールバックに任せる。
     */
    suspend fun resolveIfNeeded(rawText: String): String = withContext(Dispatchers.IO) {
        val shortUrl = extractShortUrl(rawText) ?: return@withContext rawText
        resolveRedirects(shortUrl) ?: rawText
    }

    internal fun extractShortUrl(rawText: String?): String? {
        if (rawText.isNullOrBlank()) return null
        return SHORT_URL_REGEX.find(rawText)?.value
    }

    private fun resolveRedirects(shortUrl: String): String? {
        var current = runCatching { URL(shortUrl) }.getOrNull() ?: return null
        if (!isAllowedUrl(current, initial = true)) return null

        repeat(MAX_REDIRECTS) {
            val connection = runCatching {
                (current.openConnection() as HttpURLConnection).apply {
                    instanceFollowRedirects = false
                    connectTimeout = TIMEOUT_MS
                    readTimeout = TIMEOUT_MS
                    requestMethod = "GET"
                    setRequestProperty("User-Agent", "SerendipitySpot/1.2 Android")
                    setRequestProperty("Accept", "text/html,application/xhtml+xml")
                }
            }.getOrNull() ?: return null

            try {
                val code = runCatching { connection.responseCode }.getOrNull() ?: return null
                if (code !in 300..399) {
                    return current.toString()
                }

                val location = connection.getHeaderField("Location") ?: return null
                val next = runCatching { URL(current, location) }.getOrNull() ?: return null
                if (!isAllowedUrl(next, initial = false)) return null
                current = next
            } finally {
                connection.disconnect()
            }
        }

        return null
    }

    private fun isAllowedUrl(url: URL, initial: Boolean): Boolean {
        if (!url.protocol.equals("https", ignoreCase = true)) return false
        val host = url.host.lowercase()
        if (initial) return host == "maps.app.goo.gl"

        return host == "maps.app.goo.gl" ||
            host == "google.com" || host.endsWith(".google.com") ||
            host == "google.co.jp" || host.endsWith(".google.co.jp")
    }
}
