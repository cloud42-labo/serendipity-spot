package com.cloud42labo.serendipityspot.ui

import com.cloud42labo.serendipityspot.share.SharedPlace

/**
 * 共有された内容を検索画面へ取り込むときの手順。
 *
 * [SearchFeedback] と同じく Android にも Compose にも依存しない純粋ロジックなので、
 * Robolectric 無しの JVM ユニットテストから直接検証できる。MapScreen の
 * `LaunchedEffect` に直接書くと、Compose のテスト基盤なしには検証できなくなる。
 */
data class SharedSearchAction(
    /** 検索欄へ入れる文字列。空文字なら空欄で開く。 */
    val query: String,
    /** 取り込み前に既存の検索結果と進行中の検索を捨てるか。 */
    val clearPrevious: Boolean,
    /** そのまま検索を自動実行するか。 */
    val autoRun: Boolean,
    /** 自動実行しない理由を伝える文言。出さない場合は null。 */
    val message: String?,
) {
    companion object {

        const val STAGED_MESSAGE = "共有されたページ名を入れました。検索してみてください"

        const val UNPARSABLE_MESSAGE = "共有された内容から場所を判別できませんでした。検索してみてください"

        /**
         * [shared] を検索画面へ取り込む手順を決める。座標が確定している
         * [SharedPlace.Located] は登録確認画面へ進むため検索を使わず、null を返す。
         *
         * 自動実行しない経路では必ず [clearPrevious] を立てる。立てないと、アプリが
         * 起動済み（warm）のときに前回の検索結果とマーカーが新しい検索語の下に残り、
         * さらに共有直前に走っていた検索が後から完了して無関係な結果を表示しうる
         * （Codexレビュー指摘）。自動実行する経路では [SpotViewModel.searchPlaces] が
         * 進行中の検索を捨てて結果を置き換えるので、ここで消す必要はない。
         */
        fun forSharedPlace(shared: SharedPlace): SharedSearchAction? = when (shared) {
            is SharedPlace.Located -> null

            is SharedPlace.SearchTerm ->
                if (shared.autoRun) {
                    SharedSearchAction(
                        query = shared.query,
                        clearPrevious = false,
                        autoRun = true,
                        message = null,
                    )
                } else {
                    // ブラウザ共有のページタイトル等、施設名とは限らないもの。検索語としては
                    // 引き継ぐが自動実行はしない。空欄で開くと利用者が打ち直す羽目になるため、
                    // 入れておいて「押せば探せる」状態にする。
                    SharedSearchAction(
                        query = shared.query,
                        clearPrevious = true,
                        autoRun = false,
                        message = STAGED_MESSAGE,
                    )
                }

            SharedPlace.Unparsable -> SharedSearchAction(
                query = "",
                clearPrevious = true,
                autoRun = false,
                message = UNPARSABLE_MESSAGE,
            )
        }
    }
}
