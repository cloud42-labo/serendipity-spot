package com.cloud42labo.serendipityspot.share

/**
 * 共有テキストを解析した結果。後段（登録確認画面・検索フォールバック）は
 * この3状態だけを見れば分岐できる。
 */
sealed interface SharedPlace {

    /** 座標まで確定できた。登録確認画面へそのまま渡せる。 */
    data class Located(
        val lat: Double,
        val lng: Double,
        val name: String?,
        val sourceUrl: String?,
    ) : SharedPlace

    /** 座標は取れなかったが、場所名らしき語が取れた。既存検索へ引き継ぐ。 */
    data class SearchTerm(
        val query: String,
        val sourceUrl: String?,
    ) : SharedPlace

    /** 場所として解釈できない。自動登録も自動検索もしてはいけない。 */
    data object Unparsable : SharedPlace
}
