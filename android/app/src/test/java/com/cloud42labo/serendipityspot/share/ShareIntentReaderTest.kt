package com.cloud42labo.serendipityspot.share

import org.junit.Test
import org.junit.Assert.*

/**
 * ShareIntentReader.sharedTextOf() のテスト。
 * 共有起動（ACTION_SEND）で渡された本文を正しく取り出す、および通常起動では
 * 共有処理を走らせない（既存導線の回帰防止）ことを検証する。
 *
 * テスト対象は action/type/extraText という純粋な文字列入力のみに依存し、
 * Robolectric を使わない素の JUnit4 テストで検証できる。
 */
class ShareIntentReaderTest {

    // ===== 共有起動（ACTION_SEND）で受け取れることのテスト =====

    @Test
    fun `共有起動で通常のテキストを受け取れる`() {
        // action=SEND, type=text/plain, extraText="東京タワー" → "東京タワー"
        val result = ShareIntentReader.sharedTextOf(
            action = "android.intent.action.SEND",
            type = "text/plain",
            extraText = "東京タワー"
        )
        assertEquals("東京タワー", result)
    }

    @Test
    fun `前後の空白は自動的に削除される`() {
        // 前後に空白のある本文は trim される
        val result = ShareIntentReader.sharedTextOf(
            action = "android.intent.action.SEND",
            type = "text/plain",
            extraText = "  東京タワー  "
        )
        assertEquals("東京タワー", result)
    }

    @Test
    fun `複数行の本文は改行を保ったまま返される`() {
        // 複数行の本文（改行を含む）→ 改行を保ったまま返る
        val input = "東京タワー\nhttps://maps.app.goo.gl/abc"
        val result = ShareIntentReader.sharedTextOf(
            action = "android.intent.action.SEND",
            type = "text/plain",
            extraText = input
        )
        assertEquals(input, result)
    }

    @Test
    fun `text の他のサブタイプでも受け取れる`() {
        // type が text/html など text/ で始まる他のサブタイプ → 受け取れる
        val result = ShareIntentReader.sharedTextOf(
            action = "android.intent.action.SEND",
            type = "text/html",
            extraText = "東京タワー"
        )
        assertEquals("東京タワー", result)
    }

    @Test
    fun `text_xml などもサポートしている`() {
        // text/ で始まるなら text/xml でも OK
        val result = ShareIntentReader.sharedTextOf(
            action = "android.intent.action.SEND",
            type = "text/xml",
            extraText = "データ"
        )
        assertEquals("データ", result)
    }

    // ===== 通常起動では共有処理が走らないこと（既存導線の回帰防止）=====

    @Test
    fun `通常起動（MAIN）では null を返す`() {
        // action=MAIN, type=null, extraText=null → null
        val result = ShareIntentReader.sharedTextOf(
            action = "android.intent.action.MAIN",
            type = null,
            extraText = null
        )
        assertNull(result)
    }

    @Test
    fun `通常起動では本文があっても共有として扱わない`() {
        // action=MAIN, type=text/plain, extraText="東京タワー" → null
        // （通常起動なら本文が入っていても絶対に共有として扱わない）
        val result = ShareIntentReader.sharedTextOf(
            action = "android.intent.action.MAIN",
            type = "text/plain",
            extraText = "東京タワー"
        )
        assertNull(result)
    }

    @Test
    fun `action が null のときは null を返す`() {
        // action=null → null
        val result = ShareIntentReader.sharedTextOf(
            action = null,
            type = "text/plain",
            extraText = "東京タワー"
        )
        assertNull(result)
    }

    @Test
    fun `VIEW アクションは対象外である`() {
        // action=VIEW → null
        val result = ShareIntentReader.sharedTextOf(
            action = "android.intent.action.VIEW",
            type = "text/plain",
            extraText = "東京タワー"
        )
        assertNull(result)
    }

    @Test
    fun `SEND_MULTIPLE アクションは対象外である`() {
        // action=SEND_MULTIPLE → null（単体SEND のみを対象とする）
        val result = ShareIntentReader.sharedTextOf(
            action = "android.intent.action.SEND_MULTIPLE",
            type = "text/plain",
            extraText = "東京タワー"
        )
        assertNull(result)
    }

    // ===== 受信値なし・不正値でクラッシュしないこと =====

    @Test
    fun `SEND で extraText が null のときは null を返す`() {
        // action=SEND, type=text/plain, extraText=null → null
        val result = ShareIntentReader.sharedTextOf(
            action = "android.intent.action.SEND",
            type = "text/plain",
            extraText = null
        )
        assertNull(result)
    }

    @Test
    fun `SEND で extraText が空文字のときは null を返す`() {
        // action=SEND, type=text/plain, extraText="" → null
        val result = ShareIntentReader.sharedTextOf(
            action = "android.intent.action.SEND",
            type = "text/plain",
            extraText = ""
        )
        assertNull(result)
    }

    @Test
    fun `SEND で extraText が空白のみのときは null を返す`() {
        // action=SEND, type=text/plain, extraText="   "（空白のみ）→ null
        val result = ShareIntentReader.sharedTextOf(
            action = "android.intent.action.SEND",
            type = "text/plain",
            extraText = "   "
        )
        assertNull(result)
    }

    @Test
    fun `SEND で extraText が空白文字のみのときは null を返す`() {
        // action=SEND, type=text/plain, extraText="\n\t "（空白文字だけ）→ null
        val result = ShareIntentReader.sharedTextOf(
            action = "android.intent.action.SEND",
            type = "text/plain",
            extraText = "\n\t "
        )
        assertNull(result)
    }

    @Test
    fun `SEND で type が null のときは null を返す`() {
        // action=SEND, type=null, extraText="東京タワー" → null
        // （mimeType が無い共有は対象外）
        val result = ShareIntentReader.sharedTextOf(
            action = "android.intent.action.SEND",
            type = null,
            extraText = "東京タワー"
        )
        assertNull(result)
    }

    @Test
    fun `SEND で type が image_jpeg のときは null を返す`() {
        // action=SEND, type=image/jpeg, extraText="東京タワー" → null
        // （画像共有は対象外）
        val result = ShareIntentReader.sharedTextOf(
            action = "android.intent.action.SEND",
            type = "image/jpeg",
            extraText = "東京タワー"
        )
        assertNull(result)
    }

    @Test
    fun `SEND で type が application_pdf のときは null を返す`() {
        // action=SEND, type=application/pdf → null
        // 本文は敢えて非nullにする。extraTextをnullにすると本文側の判定でも落ちてしまい、
        // mimeTypeの判定が効いているかを確かめられないため。
        val result = ShareIntentReader.sharedTextOf(
            action = "android.intent.action.SEND",
            type = "application/pdf",
            extraText = "資料.pdf"
        )
        assertNull(result)
    }

    @Test
    fun `極端に長い本文でも例外を投げずに処理できる`() {
        // 極端に長い本文（1万文字程度）でも例外を投げず、trim された文字列が返る
        val longText = "あ".repeat(10000)
        val result = ShareIntentReader.sharedTextOf(
            action = "android.intent.action.SEND",
            type = "text/plain",
            extraText = longText
        )
        assertEquals(longText, result)
    }

    @Test
    fun `極端に長い本文の前後に空白があっても trim される`() {
        // 超長い本文の前後に空白がある場合も trim される
        val longText = "あ".repeat(10000)
        val result = ShareIntentReader.sharedTextOf(
            action = "android.intent.action.SEND",
            type = "text/plain",
            extraText = "  $longText  "
        )
        assertEquals(longText, result)
    }
}
