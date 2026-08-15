package com.cloud42labo.serendipityspot.ui

import com.cloud42labo.serendipityspot.data.PlaceSearchOutcome

/**
 * 検索結果をユーザーへ伝える文言を決める。
 *
 * Android にも Compose にも依存しない純粋ロジックなので、Robolectric 無しの
 * JVMユニットテストから直接検証できる。
 *
 * 0件のときに検索語を文言へ含めているのは、単に親切だからではない。以前は結果が
 * 空のたびに同じ "見つかりませんでした" を代入しており、`LaunchedEffect(errorMessage)`
 * のキーが変化しないためスナックバーが二度目以降出なかった（BUG-SPOT-03-01 の
 * 「何も起きない」の正体）。検索語を含めれば、違う語で連続して失敗した場合は文言が
 * 変わる。ただし同じ語を続けて検索した場合は依然として同じ文言になるため、
 * 表示後に消費する側（[SpotViewModel.consumeErrorMessage]）と両方で担保している。
 */
object SearchFeedback {

    /** 表示すべきメッセージ。候補が取れた場合は何も出さないので null。 */
    fun messageFor(outcome: PlaceSearchOutcome, query: String): String? = when (outcome) {
        is PlaceSearchOutcome.Success ->
            if (outcome.results.isEmpty()) "「${query.trim()}」は見つかりませんでした" else null

        PlaceSearchOutcome.Unavailable ->
            "この端末では場所を検索できません（地図の検索機能が利用できない状態です）"

        is PlaceSearchOutcome.Failed ->
            "検索できませんでした。通信状態を確認してもう一度お試しください"
    }
}
