package com.zwheel.app.ui.screenshots

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import com.github.takahirom.roborazzi.RobolectricDeviceQualifiers
import com.github.takahirom.roborazzi.captureRoboImage
import com.zwheel.app.ui.ZWheelTheme
import com.zwheel.app.ui.history.HistoryListContent
import com.zwheel.app.ui.history.RideDetailContent
import com.zwheel.app.ui.history.RideDetailUiState
import com.zwheel.app.ui.history.RideHistoryItem
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

// NOTE: osmdroid MapView renders blank under Robolectric/Roborazzi - this is expected.
// The test captures the surrounding UI chrome (top bar, stat grid, board card).
// Visual coverage of the map itself requires a device/emulator instrumented test.
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(
    sdk = [34],
    qualifiers = RobolectricDeviceQualifiers.Pixel5,
)
class HistoryScreenshotTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun history_list_record() {
        composeTestRule.setContent {
            ZWheelTheme {
                HistoryListContent(
                    sessions = listOf(mockSessionWithGps(), mockSessionNoGps()),
                    isBoardConnected = false,
                    onRideClick = {},
                )
            }
        }
        composeTestRule.onRoot().captureRoboImage(
            "build/outputs/roborazzi/history.png",
        )
    }

    @Test
    fun history_empty_record() {
        composeTestRule.setContent {
            ZWheelTheme {
                HistoryListContent(
                    sessions = emptyList(),
                    isBoardConnected = false,
                    onRideClick = {},
                )
            }
        }
        composeTestRule.onRoot().captureRoboImage(
            "build/outputs/roborazzi/history_empty.png",
        )
    }

    @Test
    fun ride_detail_record() {
        composeTestRule.setContent {
            ZWheelTheme {
                RideDetailContent(state = mockDetailState())
            }
        }
        composeTestRule.onRoot().captureRoboImage(
            "build/outputs/roborazzi/ride_detail.png",
        )
    }
}

private fun mockSessionWithGps() = RideHistoryItem(
    id = "session-1",
    timeLabel = "8:14 AM",
    durationLabel = "24 min",
    distanceLabel = "3.24 mi",
    topSpeedLabel = "19.6 mph",
    boardName = "BoardyMcBoardface McGee",
    hasGps = true,
    thumbnailPoints = listOf(
        0.1f to 0.9f,
        0.2f to 0.7f,
        0.4f to 0.5f,
        0.6f to 0.4f,
        0.8f to 0.2f,
        0.9f to 0.1f,
    ),
    startEpochMillis = System.currentTimeMillis() - 3_600_000L,
)

private fun mockSessionNoGps() = RideHistoryItem(
    id = "session-2",
    timeLabel = "7:02 AM",
    durationLabel = "33 min · no GPS",
    distanceLabel = "5.10 mi",
    topSpeedLabel = "18.2 mph",
    boardName = "Gemini",
    hasGps = false,
    thumbnailPoints = emptyList(),
    startEpochMillis = System.currentTimeMillis() - 7_200_000L,
)

private fun mockDetailState() = RideDetailUiState(
    dateLabel = "Jun 18, 2024  8:14 AM",
    titleLabel = "Today · 8:14 AM",
    subtitleLabel = "Jun 18 · 24 min · BoardyMcBoardface McGee",
    boardName = "BoardyMcBoardface McGee",
    durationLabel = "24:08",
    distanceLabel = "3.24 mi",
    topSpeedLabel = "19.6 mph",
    avgSpeedLabel = "11.8 mph",
    gpsPoints = emptyList(),
    gpsDistanceLabel = "3.19 mi",
    ahUsedLabel = "4.1 Ah",
)
