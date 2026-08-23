package com.cloud42labo.serendipityspot.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.assertHasClickAction
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * PR #26のHuman実機確認②「短い画面・横向き・フォント拡大でも開示文とボタンまで
 * スクロールして読め、操作不能にならないこと」を、Compose UIをRobolectric（JVM上、
 * emulator不要）で実描画してCIで固定する（SPOT-06-S01-T03）。
 *
 * [OnboardingIntro]は全体を`verticalScroll()`でラップしているため、画面が小さくても
 * スクロールでボタンへ到達できるはず、というのが実装意図。ここではその意図が
 * レイアウト上壊れていない（＝物理的にボタンへ到達不能になっていない）ことだけを
 * 確認する。メーカー独自スキンでの実際の見え方までは保証しない（実機確認に委ねる）。
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class OnboardingIntroLayoutTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    private val disclosureText =
        "登録したスポットへの接近を検知して通知するため、アプリを閉じているときや" +
            "使用していないときにも位置情報を利用します。近接判定は端末上で行われ、" +
            "この判定のための位置情報が開発者へ送信されることはありません。"

    @Test
    fun `on a short landscape screen, disclosure text and start button are reachable by scrolling`() {
        setContentWithBounds(width = 640.dp, height = 320.dp)
        assertDisclosureAndButtonReachable()
    }

    @Test
    fun `on a small portrait screen, disclosure text and start button are reachable by scrolling`() {
        setContentWithBounds(width = 320.dp, height = 480.dp)
        assertDisclosureAndButtonReachable()
    }

    @Test
    fun `with enlarged font scale, disclosure text and start button are reachable by scrolling`() {
        composeTestRule.setContent {
            CompositionLocalProvider(
                LocalDensity provides Density(
                    density = LocalDensity.current.density,
                    // OSの「フォントサイズ」設定の最大付近を想定（2.0 = 200%）。
                    fontScale = 2.0f,
                ),
            ) {
                Box(Modifier.size(360.dp, 640.dp)) {
                    OnboardingIntro(onStart = {})
                }
            }
        }
        assertDisclosureAndButtonReachable()
    }

    private fun setContentWithBounds(width: Dp, height: Dp) {
        composeTestRule.setContent {
            Box(Modifier.size(width, height)) {
                OnboardingIntro(onStart = {})
            }
        }
    }

    private fun assertDisclosureAndButtonReachable() {
        composeTestRule
            .onNodeWithText(disclosureText)
            .performScrollTo()
            .assertIsDisplayed()

        composeTestRule
            .onNodeWithText("はじめる")
            .performScrollTo()
            .assertIsDisplayed()
            .assertHasClickAction()
            .performClick()
    }
}
