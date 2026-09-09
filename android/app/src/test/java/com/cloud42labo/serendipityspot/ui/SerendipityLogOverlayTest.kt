package com.cloud42labo.serendipityspot.ui

import androidx.activity.ComponentActivity
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
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
    fun `closing and reopening the overlay fires onShown a second time, and the newly refreshed visitLog is what's rendered`() {
        // Codex-reported gap (round 2): the previous version of this test set
        // `visitLog` to the "fresh" data directly, independent of `onShown` —
        // so it would still pass even if `onShown` fired but the caller never
        // actually used it to reload data. The real contract (see
        // SerendipityLogScreen.kt's LaunchedEffect(Unit) { onShown() }) is that
        // MapScreen's own onShown handler is what re-fetches visitLog on each
        // reopen. This test now performs that reload FROM WITHIN onShown
        // itself — the second invocation, on reopen — and asserts a uniquely
        // titled new row that only the second onShown could have supplied.
        val parkSpot = Spot(id = "spot-2", lat = 35.1, lng = 139.1, title = "公園", memo = "")
        val newVisit = VisitRecord(id = "visit-2", spotId = "spot-2", spotTitle = "公園", recordedAt = 1_700_000_100_000L)

        var shownCount = 0
        var visible by mutableStateOf(true)
        var visitLog by mutableStateOf(listOf(visit))
        composeTestRule.setContent {
            SerendipityLogOverlay(
                visible = visible,
                visitLog = visitLog,
                spots = listOf(spot, parkSpot),
                onDismiss = { visible = false },
                onDeleteRecord = {},
                onShown = {
                    shownCount++
                    // Simulates MapScreen's real reload-on-show behavior: only
                    // the second call (the reopen) finds the newly recorded visit.
                    if (shownCount == 2) visitLog = listOf(visit, newVisit)
                },
            )
        }
        composeTestRule.waitForIdle()
        assertEquals(1, shownCount)
        composeTestRule.onNodeWithText("カフェ").assertExists()
        composeTestRule.onNodeWithText("公園").assertDoesNotExist()

        // Close, exactly as a system Back would (see the onDismiss tests above).
        visible = false
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithText("Serendipity Log").assertDoesNotExist()

        // Reopen — the second onShown fires and, per the contract under test,
        // is itself what delivers the freshly recorded visit.
        visible = true
        composeTestRule.waitForIdle()

        assertEquals("reopening must trigger a second onShown, not merely reuse the first", 2, shownCount)
        composeTestRule.onNodeWithText("Serendipity Log").assertExists()
        composeTestRule.onNodeWithText("公園").assertExists()
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
