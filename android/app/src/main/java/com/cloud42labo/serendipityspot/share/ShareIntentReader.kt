package com.cloud42labo.serendipityspot.share

/**
 * 共有（ACTION_SEND）で渡された本文を取り出す判定だけを、Intentから切り離して持つ。
 * android.content.Intent に依存すると Robolectric 無しのJVMユニットテストから
 * 判定ロジックだけを検証できないため、この判定は action/type/extraText/extraTitle という
 * 素の文字列だけを受け取る純粋関数として切り出している。
 * Intentの生成・実際の受信はMainActivity側の責務とする。
 */
object ShareIntentReader {
    const val ACTION_SEND = "android.intent.action.SEND"

    private val PLACE_TITLE_HINTS = listOf(
        "ショッピングセンター", "ショッピングモール", "モール", "センター", "店", "店舗",
        "タワー", "公園", "駅", "ホテル", "旅館", "レストラン", "カフェ", "美術館", "博物館",
        "水族館", "動物園", "神社", "寺", "病院", "クリニック", "学校", "大学", "役所",
        "ホール", "劇場", "スタジアム", "アリーナ", "空港", "港",
    )

    /**
     * 共有起動なら本文を返し、通常起動・対象外なら null を返す。
     *
     * Chrome等はURLをEXTRA_TEXT、ページタイトルをEXTRA_TITLEへ分離して渡すことがある。
     * URLだけでは場所判定できないため、タイトルが施設らしい場合だけ「タイトル\nURL」に
     * 合成して既存のShareTextParserへ渡す。一般ニュース等のタイトルを勝手に場所検索へ
     * 回さないため、施設らしさが無いタイトルは採用しない。
     */
    fun sharedTextOf(
        action: String?,
        type: String?,
        extraText: String?,
        extraTitle: String? = null,
    ): String? {
        if (action != ACTION_SEND) return null
        if (type == null || !type.startsWith("text/")) return null
        if (extraText == null || extraText.isBlank()) return null

        val text = extraText.trim()
        val title = extraTitle?.trim().orEmpty()
        if (isUrlOnly(text) && looksLikePlaceTitle(title)) {
            return "$title\n$text"
        }
        return text
    }

    private fun isUrlOnly(text: String): Boolean =
        text.startsWith("https://") || text.startsWith("http://")

    private fun looksLikePlaceTitle(title: String): Boolean {
        if (title.isBlank()) return false
        return PLACE_TITLE_HINTS.any { hint -> title.contains(hint, ignoreCase = true) }
    }
}
