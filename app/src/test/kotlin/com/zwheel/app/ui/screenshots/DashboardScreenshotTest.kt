package com.zwheel.app.ui.screenshots

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onRoot
import com.github.takahirom.roborazzi.RobolectricDeviceQualifiers
import com.github.takahirom.roborazzi.captureRoboImage
import com.zwheel.app.ui.ZWheelTheme
import com.zwheel.app.ui.dashboard.DashboardScreen
import com.zwheel.app.ui.mockDashboardState
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
class DashboardScreenshotTest {

    @get:Rule
    val composeTestRule = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun dashboard_record() {
        composeTestRule.setContent {
            ZWheelTheme {
                DashboardScreen(
                    state = mockDashboardState(),
                )
            }
        }
        composeTestRule.onRoot().captureRoboImage(
            "build/outputs/roborazzi/dashboard.png",
        )
    }
}
