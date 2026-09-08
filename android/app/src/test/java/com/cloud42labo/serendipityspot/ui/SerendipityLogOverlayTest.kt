package com.cloud42labo.serendipityspot.ui

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import com.cloud42labo.serendipityspot.data.Spot
import com.cloud42labo.serendipityspot.data.VisitRecord
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * SPOT-04-S02-T03 AC④「システムBackで地図へ復帰する」を検証する。
 *
 * [MapScreen]自体は[com.google.maps.android.compose.GoogleMap]を含みRobolectricでの
 * 実描画コストが高いため、Backの実処理を持つ[SerendipityLogOverlay]をここで直接
 * 描画してシステムBackを模擬する（`onBackPressedDispatcher.onBackPressed()`）。
 * [MapScreen]はこの関数をそのまま呼ぶだけなので、これは実装の複製ではなく本体の検証になる。
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class SerendipityLogOverlayTest {

    @get:Rule
    val composeTestRule = createAndroidComposeRule<ComponentActivity>()

    private val spot = Spot(id = "spot-1", lat = 35.0, lng = 139.0, title = "カフェ", memo = "")
    private val visit = VisitRecord(id = "visit-1", spotId = "spot-1", spotTitle = "カフェ", recordedAt = 1_700_000_000_000L)

    @Test
    fun `when not visible, nothing is shown and system back is not intercepted`() {
        var dismissed = false
        composeTestRule.setContent {
            SerendipityLogOverlay(
                visible = false,
                visitLog = listOf(visit),
                spots = listOf(spot),
                onDismiss = { dismissed = true },
                onDeleteRecord = {},
            )
        }

        composeTestRule.onNodeWithText("Serendipity Log").assertDoesNotExist()

        composeTestRule.activity.onBackPressedDispatcher.onBackPressed()
        composeTestRule.waitForIdle()

        assertFalse("enabled=falseのBackHandlerはonDismissを奪ってはいけない", dismissed)
    }

    @Test
    fun `when visible, the log screen is shown and system back calls onDismiss`() {
        var dismissed = false
        composeTestRule.setContent {
            SerendipityLogOverlay(
                visible = true,
                visitLog = listOf(visit),
                spots = listOf(spot),
                onDismiss = { dismissed = true },
                onDeleteRecord = {},
            )
        }

        composeTestRule.onNodeWithText("Serendipity Log").assertExists()

        composeTestRule.activity.onBackPressedDispatcher.onBackPressed()
        composeTestRule.waitForIdle()

        assertTrue("システムBackはonDismissを呼び、MapScreen側がshowVisitLog=falseへ戻す契約", dismissed)
    }

    @Test
    fun `when visible, onShown fires exactly once so the caller can refresh the log`() {
        var shownCount = 0
        composeTestRule.setContent {
            SerendipityLogOverlay(
                visible = true,
                visitLog = listOf(visit),
                spots = listOf(spot),
                onDismiss = {},
                onDeleteRecord = {},
                onShown = { shownCount++ },
            )
        }
        composeTestRule.waitForIdle()

        assertEquals(1, shownCount)
    }

    @Test
    fun `when not visible, onShown never fires`() {
        var shownCount = 0
        composeTestRule.setContent {
            SerendipityLogOverlay(
                visible = false,
                visitLog = emptyList(),
                spots = emptyList(),
                onDismiss = {},
                onDeleteRecord = {},
                onShown = { shownCount++ },
            )
        }
        composeTestRule.waitForIdle()

        assertEquals(0, shownCount)
    }

    @Test
    fun `return value mirrors the visible flag, matching MapScreen's early-return contract`() {
        var returned: Boolean? = null
        composeTestRule.setContent {
            returned = SerendipityLogOverlay(
                visible = true,
                visitLog = listOf(visit),
                spots = listOf(spot),
                onDismiss = {},
                onDeleteRecord = {},
            )
        }

        assertEquals(true, returned)
    }
}
