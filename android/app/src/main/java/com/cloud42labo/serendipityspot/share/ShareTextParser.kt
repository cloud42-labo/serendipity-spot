package com.cloud42labo.serendipityspot.share

import java.net.URLDecoder

/**
 * 共有テキスト（他アプリからの ACTION_SEND 本文）を解析し、[SharedPlace] へ正規化する。
 *
 * ネットワークアクセスは一切行わない純粋ロジック。短縮URL（maps.app.goo.gl 等）の展開も
 * しない。短縮URLの場合はURLからは何も取れず、Google Mapsの共有慣習（本文が
 * 「施設名\n短縮URL」の形になる）を利用して、URL行を除いた本文側から施設名候補を拾う
 * ことで [SharedPlace.SearchTerm] に落ち着く。これは仕様上の想定どおりの挙動であり、
 * 短縮URLを展開できないことの回避策ではなく設計そのもの。
 *
 * android.* は一切importしない。android.net.Uri はJVMユニットテスト（Robolectric無し）
 * ではスタブがnullを返すため使えず、URL解析はすべて正規表現と java.net.URLDecoder で
 * 自前に行う。
 */
object ShareTextParser {

    private const val MAX_QUERY_LENGTH = 100

    private val URL_TOKEN_REGEX = Regex("""(https?://|geo:)\S+""")
    private val URL_ANYWHERE_REGEX = Regex("""https?://|geo:""")
    private val TRAILING_PUNCT_CHARS = setOf(
        '.', ',', ';', ':', '!', '?', '、', '。', '」', '』', '】', '》', '"', '\'',
    )
    private val BRACKET_PAIRS = listOf('(' to ')', '[' to ']', '{' to '}')

    private val GEO_PRIMARY_REGEX = Regex("""^geo:(-?[0-9]+(?:\.[0-9]+)?),(-?[0-9]+(?:\.[0-9]+)?)""")
    private val AT_COORD_REGEX = Regex("""@(-?[0-9]+(?:\.[0-9]+)?),(-?[0-9]+(?:\.[0-9]+)?)""")
    private val BANG_COORD_REGEX = Regex("""!3d(-?[0-9]+(?:\.[0-9]+)?)!4d(-?[0-9]+(?:\.[0-9]+)?)""")
    private val PLACE_SEGMENT_REGEX = Regex("""/maps/place/([^/?#]+)""")
    private val GEO_NAME_PAREN_REGEX = Regex("""q=[^&()]*\(([^)]+)\)""")
    private val PLAIN_COORD_REGEX =
        Regex("""(-?\d{1,3}(?:\.\d+)?)[\s　]*[,，][\s　]*(-?\d{1,3}(?:\.\d+)?)""")

    private val COORD_QUERY_KEYS = listOf("q", "query", "ll", "daddr", "center")

    private data class Coord(val lat: Double, val lng: Double)

    fun parse(rawText: String?): SharedPlace {
        if (rawText == null || rawText.isBlank()) return SharedPlace.Unparsable
        val text = rawText.trim()

        val urlToken = extractUrlToken(text)
        val coord = extractCoordinate(urlToken, text)
        val nameCandidate = normalizeSearchTerm(extractNameCandidate(urlToken, text))

        if (coord != null) {
            return SharedPlace.Located(
                lat = coord.lat,
                lng = coord.lng,
                name = nameCandidate,
                sourceUrl = urlToken,
            )
        }

        if (nameCandidate != null) {
            return SharedPlace.SearchTerm(query = nameCandidate, sourceUrl = urlToken)
        }

        val fallbackQuery = normalizeSearchTerm(removeAllUrls(text))
        if (fallbackQuery != null) {
            return SharedPlace.SearchTerm(query = fallbackQuery, sourceUrl = urlToken)
        }

        return SharedPlace.Unparsable
    }

    // ------------------------------------------------------------------
    // (b) URL抽出
    // ------------------------------------------------------------------

    private fun extractUrlToken(text: String): String? {
        val match = URL_TOKEN_REGEX.find(text) ?: return null
        val trimmed = trimUrlToken(match.value)
        return trimmed.ifEmpty { null }
    }

    /** 末尾の句読点・閉じ括弧（対応する開き括弧が無いもの）をURLから取り除く。 */
    private fun trimUrlToken(raw: String): String {
        var token = raw
        var changed = true
        while (changed) {
            changed = false
            if (token.isNotEmpty() && token.last() in TRAILING_PUNCT_CHARS) {
                token = token.dropLast(1)
                changed = true
                continue
            }
            for ((open, close) in BRACKET_PAIRS) {
                if (token.isNotEmpty() && token.last() == close) {
                    val opens = token.count { it == open }
                    val closes = token.count { it == close }
                    if (closes > opens) {
                        token = token.dropLast(1)
                        changed = true
                    }
                }
            }
        }
        return token
    }

    private fun removeAllUrls(text: String): String = URL_TOKEN_REGEX.replace(text) { " " }

    private fun isUrlLine(line: String): Boolean = URL_ANYWHERE_REGEX.containsMatchIn(line)

    // ------------------------------------------------------------------
    // (c) 座標の抽出
    // ------------------------------------------------------------------

    private fun extractCoordinate(urlToken: String?, rawText: String): Coord? {
        if (urlToken != null && urlToken.startsWith("geo:")) {
            val primary = GEO_PRIMARY_REGEX.find(urlToken)?.let { toCoord(it) }
            if (primary != null && isValidCoord(primary)) return primary
            // geo:0,0 のようなプレースホルダの場合は q= 側を優先する
            val fromQuery = coordFromQueryParams(urlToken, listOf("q"))
            if (fromQuery != null && isValidCoord(fromQuery)) return fromQuery
            return null
        }

        if (urlToken != null) {
            val fromAt = AT_COORD_REGEX.find(urlToken)?.let { toCoord(it) }
            if (fromAt != null && isValidCoord(fromAt)) return fromAt

            val fromQuery = coordFromQueryParams(urlToken, COORD_QUERY_KEYS)
            if (fromQuery != null && isValidCoord(fromQuery)) return fromQuery

            val fromBang = BANG_COORD_REGEX.find(urlToken)?.let { toCoord(it) }
            if (fromBang != null && isValidCoord(fromBang)) return fromBang

            // URLはあるが、その中に有効な座標が見つからなかった場合。
            // URL付き共有では本文の素の数値を座標として誤読しないよう、ここで打ち切る。
            return null
        }

        // URLが無い場合のみ、本文に素で書かれた座標を試す
        val plain = PLAIN_COORD_REGEX.find(rawText)?.let { toCoord(it) }
        if (plain != null && isValidCoord(plain)) return plain

        return null
    }

    private fun toCoord(match: MatchResult): Coord? {
        val lat = match.groupValues[1].toDoubleOrNull() ?: return null
        val lng = match.groupValues[2].toDoubleOrNull() ?: return null
        return Coord(lat, lng)
    }

    private fun isValidCoord(coord: Coord): Boolean {
        if (coord.lat.isNaN() || coord.lng.isNaN()) return false
        if (coord.lat < -90.0 || coord.lat > 90.0) return false
        if (coord.lng < -180.0 || coord.lng > 180.0) return false
        if (coord.lat == 0.0 && coord.lng == 0.0) return false // プレースホルダとして扱う
        return true
    }

    private fun coordFromQueryParams(url: String, keys: List<String>): Coord? {
        for (key in keys) {
            val match = Regex("""[?&]${Regex.escape(key)}=([^&]+)""").find(url) ?: continue
            val decoded = decodeUrlComponent(match.groupValues[1])
            val beforeParen = decoded.substringBefore("(").trim()
            val coord = parseCoordPair(beforeParen)
            if (coord != null) return coord
        }
        return null
    }

    private fun parseCoordPair(value: String): Coord? {
        val parts = value.split(",", "，")
        if (parts.size != 2) return null
        val lat = parts[0].trim().toDoubleOrNull() ?: return null
        val lng = parts[1].trim().toDoubleOrNull() ?: return null
        return Coord(lat, lng)
    }

    // ------------------------------------------------------------------
    // (d) 施設名候補の抽出
    // ------------------------------------------------------------------

    private fun extractNameCandidate(urlToken: String?, rawText: String): String? {
        if (urlToken != null) {
            PLACE_SEGMENT_REGEX.find(urlToken)?.let { match ->
                val decoded = decodeUrlComponent(match.groupValues[1]).trim()
                if (decoded.isNotBlank()) return decoded
            }

            if (urlToken.startsWith("geo:")) {
                GEO_NAME_PAREN_REGEX.find(urlToken)?.let { match ->
                    val decoded = decodeUrlComponent(match.groupValues[1]).trim()
                    if (decoded.isNotBlank()) return decoded
                }
            }

            for (key in listOf("q", "query")) {
                val match = Regex("""[?&]${Regex.escape(key)}=([^&]+)""").find(urlToken) ?: continue
                val decoded = decodeUrlComponent(match.groupValues[1]).trim()
                val beforeParen = decoded.substringBefore("(").trim()
                // 座標形式（lat,lng）の値は名前候補ではないのでスキップし、次のキーを試す
                if (beforeParen.isNotBlank() && parseCoordPair(beforeParen) == null) {
                    return decoded
                }
            }
        }

        // 共有本文のうち URL行を除いた最初の非空行
        for (line in rawText.lines()) {
            val trimmedLine = line.trim()
            if (trimmedLine.isEmpty()) continue
            if (isUrlLine(trimmedLine)) continue
            return trimmedLine
        }
        return null
    }

    // ------------------------------------------------------------------
    // (e) 検索語の正規化
    // ------------------------------------------------------------------

    private fun normalizeSearchTerm(raw: String?): String? {
        if (raw == null) return null
        val collapsed = raw.replace(Regex("""[\s　]+"""), " ").trim()
        if (collapsed.isEmpty()) return null
        // 記号だけの文字列（例: "!!! ???"）は検索語として採用しない
        if (collapsed.none { it.isLetterOrDigit() }) return null
        return if (collapsed.length > MAX_QUERY_LENGTH) collapsed.take(MAX_QUERY_LENGTH) else collapsed
    }

    /** URLDecoder.decode(..., "UTF-8") で %XX を復号し、"+" を空白へ変換する。 */
    private fun decodeUrlComponent(value: String): String = try {
        URLDecoder.decode(value, "UTF-8")
    } catch (e: Exception) {
        value.replace("+", " ")
    }
}
