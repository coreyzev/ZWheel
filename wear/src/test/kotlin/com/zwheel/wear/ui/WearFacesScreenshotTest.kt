package com.zwheel.wear.ui

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import com.github.takahirom.roborazzi.captureRoboImage
import com.zwheel.core.model.ConnectionState
import com.zwheel.core.model.SpeedUnit
import com.zwheel.core.model.WatchPayload
import com.zwheel.wear.ui.ZWheelWearScreen
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

// 240×240 dp — square render matching Corey's Galaxy Watch 7 Classic viewport.
// Round-screen clipping happens on device; this validates layout, color, and text.
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(
    sdk = [34],
    qualifiers = "w240dp-h240dp-xhdpi-keyshidden-nonav",
)
class WearFacesScreenshotTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun wear_active_face_record() {
        composeTestRule.setContent {
            ZWheelWearScreen(payload = ridingPayload(safetyHeadroom = 5), isAmbient = false)
        }
        composeTestRule.onRoot().captureRoboImage(
            "build/outputs/roborazzi/wear_active.png",
        )
    }

    @Test
    fun wear_caution_face_record() {
        composeTestRule.setContent {
            ZWheelWearScreen(
                payload = ridingPayload(
                    speedMps = 8.4,
                    topSpeedMps = 8.4,
                    batteryPercent = 48,
                    rangeMps = 5000.0,
                    safetyHeadroom = 0,
                ),
                isAmbient = false,
            )
        }
        composeTestRule.onRoot().captureRoboImage(
            "build/outputs/roborazzi/wear_caution.png",
        )
    }

    @Test
    fun wear_ambient_face_record() {
        composeTestRule.setContent {
            ZWheelWearScreen(payload = ridingPayload(safetyHeadroom = 5), isAmbient = true)
        }
        composeTestRule.onRoot().captureRoboImage(
            "build/outputs/roborazzi/wear_ambient.png",
        )
    }

    @Test
    fun wear_scanning_face_record() {
        composeTestRule.setContent {
            ZWheelWearScreen(
                payload = WatchPayload(
                    speedMetersPerSecondCorrected = null,
                    topSpeedMetersPerSecond = 0.0,
                    batteryPercent = null,
                    estimatedRangeMeters = null,
                    speedUnit = SpeedUnit.MPH,
                    isRiding = false,
                    connectionState = ConnectionState.SCANNING,
                    safetyHeadroom = null,
                ),
                isAmbient = false,
            )
        }
        composeTestRule.onRoot().captureRoboImage(
            "build/outputs/roborazzi/wear_scanning.png",
        )
    }

    private fun ridingPayload(
        speedMps: Double = 5.8,
        topSpeedMps: Double = 8.0,
        batteryPercent: Int = 72,
        rangeMps: Double = 9000.0,
        safetyHeadroom: Int = 5,
    ) = WatchPayload(
        speedMetersPerSecondCorrected = speedMps,
        topSpeedMetersPerSecond = topSpeedMps,
        batteryPercent = batteryPercent,
        estimatedRangeMeters = rangeMps,
        speedUnit = SpeedUnit.MPH,
        isRiding = true,
        connectionState = ConnectionState.SUBSCRIBED,
        safetyHeadroom = safetyHeadroom,
    )
}
