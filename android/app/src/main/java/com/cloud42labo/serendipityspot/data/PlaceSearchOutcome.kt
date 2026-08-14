package com.cloud42labo.serendipityspot.data

/**
 * 場所検索の結果。「0件」と「検索できなかった」を型で分ける。
 *
 * 以前は [PlaceSearcher] が失敗も0件もまとめて空リストへ潰していたため、画面側は
 * 「見つかりませんでした」としか言えず、通信断・バックエンド不在・本当に該当なしを
 * 区別できなかった（BUG-SPOT-03-01）。
 *
 * Android の Geocoder は、該当なしのときだけでなくバックエンドが無いときや
 * ネットワークが不通のときも空リストを返しうる（例外を投げるとは限らない）。
 * そのため「例外を捕まえる」だけでは足りず、[Unavailable] を明示的に判定している。
 */
sealed interface PlaceSearchOutcome {

    /** 検索そのものは成立した。[results] が空なら「該当なし」。 */
    data class Success(val results: List<PlaceResult>) : PlaceSearchOutcome

    /** 端末にジオコーダのバックエンドが無い。何度試しても結果は出ない。 */
    data object Unavailable : PlaceSearchOutcome

    /** 検索中に例外が出た（多くは通信エラー）。やり直す価値がある。 */
    data class Failed(val cause: Throwable) : PlaceSearchOutcome
}
