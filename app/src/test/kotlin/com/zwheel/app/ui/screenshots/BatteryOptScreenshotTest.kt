package com.zwheel.app.ui.screenshots

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import com.github.takahirom.roborazzi.RobolectricDeviceQualifiers
import com.github.takahirom.roborazzi.captureRoboImage
import com.zwheel.app.ui.ZWheelTheme
import com.zwheel.app.ui.onboarding.OemBatteryAdviceScreen
import com.zwheel.app.ui.onboarding.samsungBatteryAdvice
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(
    sdk = [34],
    qualifiers = RobolectricDeviceQualifiers.Pixel5,
)
class BatteryOptScreenshotTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun batteryOpt_record() {
        composeTestRule.setContent {
            ZWheelTheme {
                OemBatteryAdviceScreen(
                    advice = samsungBatteryAdvice,
                    deviceLabel = "SAMSUNG DETECTED",
                )
            }
        }
        composeTestRule.onRoot().captureRoboImage(
            "build/outputs/roborazzi/battery_opt.png",
        )
    }
}
