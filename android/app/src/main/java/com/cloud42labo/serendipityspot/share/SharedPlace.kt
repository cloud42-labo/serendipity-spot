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

    /**
     * 座標は取れなかったが、場所名らしき語が取れた。既存検索へ引き継ぐ。
     *
     * [autoRun] が false のときは検索欄へ流し込むだけで、検索は実行しない。
     * ブラウザ共有のページタイトルは施設名とは限らず（ニュース見出し等）、
     * それを自動で場所検索へ回すと意図しない検索が走るため。ただしタイトル自体は
     * 捨てない。捨てると検索欄が空で開くだけになり、利用者が手で打ち直すことになる
     * （SPOT-02-S02-T03 の「元テキストを可能な範囲で検索語として引き継ぐ」に反する）。
     */
    data class SearchTerm(
        val query: String,
        val sourceUrl: String?,
        val autoRun: Boolean = true,
    ) : SharedPlace

    /** 場所として解釈できない。自動登録も自動検索もしてはいけない。 */
    data object Unparsable : SharedPlace
}
