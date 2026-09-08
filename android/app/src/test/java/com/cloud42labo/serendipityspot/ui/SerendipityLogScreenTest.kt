package com.cloud42labo.serendipityspot.ui

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.cloud42labo.serendipityspot.data.Spot
import com.cloud42labo.serendipityspot.data.VisitRecord
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * SPOT-04-S02-T03: Serendipity Log一覧画面の①表示内容、②削除確認と削除後反映、
 * ③空状態をRobolectric（JVM上、emulator不要）で固定する。
 * ④画面再表示時の再読込・⑤システムBackで地図へ復帰する検証は両方とも
 * [SerendipityLogOverlayTest]の担当（このComposable自体はBack処理も再読込トリガー
 * （`onShown`）も持たず、呼び出し側[SerendipityLogOverlay]が持つため。④はこの画面を
 * 継続的にマウントしたまま`visitLog`だけ差し替えても検証にならない — 実際に
 * `visible`をfalse→trueへ往復させ、2回目の`onShown`が新しいデータを届けることまで
 * 確認する必要があり、それは[SerendipityLogOverlay]レベルでしか意味を持たない）。
 *
 * Approach Decision（2026-09-06）どおり、実端末デモではなくソフトウェア挙動を
 * 決定的に確認する自動E2Eとして書く。物理端末での見え方はmerge後のAcceptanceで別途。
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class SerendipityLogScreenTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    private val dateFormat = SimpleDateFormat("yyyy/MM/dd HH:mm", Locale.JAPAN)

    private val cafeSpot = Spot(id = "spot-1", lat = 35.0, lng = 139.0, title = "カフェ", memo = "静かで作業しやすい")
    private val parkSpot = Spot(id = "spot-2", lat = 35.1, lng = 139.1, title = "公園", memo = "")

    private val cafeVisit = VisitRecord(id = "visit-1", spotId = "spot-1", spotTitle = "カフェ", recordedAt = 1_700_000_000_000L)
    private val parkVisit = VisitRecord(id = "visit-2", spotId = "spot-2", spotTitle = "公園", recordedAt = 1_700_000_100_000L)
    private val deletedSpotVisit = VisitRecord(id = "visit-3", spotId = "spot-missing", spotTitle = "無くなった場所", recordedAt = 1_700_000_200_000L)

    // --- ①表示内容 -----------------------------------------------------------------

    @Test
    fun `shows title, date and saved reason when the spot still has a memo`() {
        composeTestRule.setContent {
            SerendipityLogScreen(
                visitLog = listOf(cafeVisit),
                spots = listOf(cafeSpot),
                onBack = {},
                onDeleteRecord = {},
            )
        }

        composeTestRule.onNodeWithText("カフェ").assertExists()
        composeTestRule
            .onNodeWithText(dateFormat.format(Date(cafeVisit.recordedAt)) + "　静かで作業しやすい")
            .assertExists()
    }

    @Test
    fun `shows a placeholder when the spot exists but has no memo`() {
        composeTestRule.setContent {
            SerendipityLogScreen(
                visitLog = listOf(parkVisit),
                spots = listOf(parkSpot),
                onBack = {},
                onDeleteRecord = {},
            )
        }

        composeTestRule
            .onNodeWithText(dateFormat.format(Date(parkVisit.recordedAt)) + "　（保存理由の入力はありません）")
            .assertExists()
    }

    @Test
    fun `does not crash when the recorded spot was since deleted, and notes it instead of a reason`() {
        composeTestRule.setContent {
            SerendipityLogScreen(
                visitLog = listOf(deletedSpotVisit),
                spots = listOf(cafeSpot, parkSpot), // 元のspotIdはもう含まれない
                onBack = {},
                onDeleteRecord = {},
            )
        }

        // 記録時点のタイトルは残る
        composeTestRule.onNodeWithText("無くなった場所").assertExists()
        composeTestRule
            .onNodeWithText(dateFormat.format(Date(deletedSpotVisit.recordedAt)) + "　（このスポットは削除・変更されています）")
            .assertExists()
    }

    @Test
    fun `lists multiple records newest first`() {
        composeTestRule.setContent {
            // わざと古い→新しいの順で渡し、画面側が新しい順に並べ替えることを確認する。
            SerendipityLogScreen(
                visitLog = listOf(cafeVisit, parkVisit),
                spots = listOf(cafeSpot, parkSpot),
                onBack = {},
                onDeleteRecord = {},
            )
        }

        // 存在チェックだけでは`sortedByDescending`を外しても通ってしまう
        // （Codexレビュー指摘）。parkVisitの方がrecordedAtが新しいため、
        // 新しい順であれば「公園」が「カフェ」より上（boundsInRoot.topが小さい）に
        // 描画されるはずであることまで確認する。
        val parkTop = composeTestRule.onNodeWithText("公園").fetchSemanticsNode().boundsInRoot.top
        val cafeTop = composeTestRule.onNodeWithText("カフェ").fetchSemanticsNode().boundsInRoot.top
        assertTrue("newest (公園) must render above older (カフェ)", parkTop < cafeTop)
    }

    // --- ②削除確認と削除後反映 -------------------------------------------------------

    @Test
    fun `tapping delete shows a confirmation dialog naming the record, and does not delete until confirmed`() {
        var deletedId: String? = null
        composeTestRule.setContent {
            SerendipityLogScreen(
                visitLog = listOf(cafeVisit),
                spots = listOf(cafeSpot),
                onBack = {},
                onDeleteRecord = { deletedId = it },
            )
        }

        composeTestRule.onNodeWithContentDescription("「カフェ」の記録を削除").performClick()

        composeTestRule.onNodeWithText("記録を削除").assertExists()
        composeTestRule
            .onNodeWithText("「カフェ」への記録（${dateFormat.format(Date(cafeVisit.recordedAt))}）を削除します。")
            .assertExists()
        assertEquals(null, deletedId)
    }

    @Test
    fun `confirming the dialog calls onDeleteRecord with the record id and dismisses the dialog`() {
        var deletedId: String? = null
        composeTestRule.setContent {
            SerendipityLogScreen(
                visitLog = listOf(cafeVisit),
                spots = listOf(cafeSpot),
                onBack = {},
                onDeleteRecord = { deletedId = it },
            )
        }

        composeTestRule.onNodeWithContentDescription("「カフェ」の記録を削除").performClick()
        composeTestRule.onNodeWithText("削除").performClick()

        assertEquals("visit-1", deletedId)
        composeTestRule.onNodeWithText("記録を削除").assertDoesNotExist()
    }

    @Test
    fun `cancelling the dialog keeps the record and calls onDeleteRecord zero times`() {
        var deleteCalls = 0
        composeTestRule.setContent {
            SerendipityLogScreen(
                visitLog = listOf(cafeVisit),
                spots = listOf(cafeSpot),
                onBack = {},
                onDeleteRecord = { deleteCalls++ },
            )
        }

        composeTestRule.onNodeWithContentDescription("「カフェ」の記録を削除").performClick()
        composeTestRule.onNodeWithText("キャンセル").performClick()

        assertEquals(0, deleteCalls)
        composeTestRule.onNodeWithText("記録を削除").assertDoesNotExist()
        // 削除しなかった記録は一覧に残り続ける。
        composeTestRule.onNodeWithText("カフェ").assertExists()
    }

    @Test
    fun `after the caller removes the deleted record from visitLog, the row disappears from the list`() {
        // 実装では削除は呼び出し側（SpotViewModel）がvisitLogを更新することで反映される。
        // ここではその再コンポーズ後の一覧の見え方を確認する（削除後反映）。
        composeTestRule.setContent {
            var visitLog by remember { mutableStateOf(listOf(cafeVisit, parkVisit)) }
            SerendipityLogScreen(
                visitLog = visitLog,
                spots = listOf(cafeSpot, parkSpot),
                onBack = {},
                onDeleteRecord = { id -> visitLog = visitLog.filterNot { it.id == id } },
            )
        }

        composeTestRule.onNodeWithText("カフェ").assertExists()
        composeTestRule.onNodeWithText("公園").assertExists()

        composeTestRule.onNodeWithContentDescription("「カフェ」の記録を削除").performClick()
        composeTestRule.onNodeWithText("削除").performClick()

        composeTestRule.onNodeWithText("カフェ").assertDoesNotExist()
        composeTestRule.onNodeWithText("公園").assertExists()
    }

    // --- ③空状態 --------------------------------------------------------------------

    @Test
    fun `shows the empty state message when there are no records`() {
        composeTestRule.setContent {
            SerendipityLogScreen(
                visitLog = emptyList(),
                spots = listOf(cafeSpot),
                onBack = {},
                onDeleteRecord = {},
            )
        }

        composeTestRule.onNodeWithText("まだ記録がありません").assertExists()
        composeTestRule
            .onNodeWithText("スポットに近づいたときの通知で「寄った」を押すと、ここに記録されます")
            .assertExists()
    }

    @Test
    fun `deleting the last record transitions the screen into the empty state`() {
        composeTestRule.setContent {
            var visitLog by remember { mutableStateOf(listOf(cafeVisit)) }
            SerendipityLogScreen(
                visitLog = visitLog,
                spots = listOf(cafeSpot),
                onBack = {},
                onDeleteRecord = { id -> visitLog = visitLog.filterNot { it.id == id } },
            )
        }

        composeTestRule.onNodeWithText("まだ記録がありません").assertDoesNotExist()

        composeTestRule.onNodeWithContentDescription("「カフェ」の記録を削除").performClick()
        composeTestRule.onNodeWithText("削除").performClick()

        composeTestRule.onNodeWithText("まだ記録がありません").assertExists()
    }

    @Test
    fun `back icon in the top bar calls onBack`() {
        var backCalled = false
        composeTestRule.setContent {
            SerendipityLogScreen(
                visitLog = listOf(cafeVisit),
                spots = listOf(cafeSpot),
                onBack = { backCalled = true },
                onDeleteRecord = {},
            )
        }

        composeTestRule.onNodeWithContentDescription("履歴を閉じる").performClick()

        assertTrue(backCalled)
    }
}
