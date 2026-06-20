package com.zwheel.app.ui.screenshots

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onRoot
import com.github.takahirom.roborazzi.RobolectricDeviceQualifiers
import com.github.takahirom.roborazzi.captureRoboImage
import com.zwheel.app.ui.ZWheelTheme
import com.zwheel.app.ui.permissions.PermissionsScreen
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
class PermissionsScreenshotTest {

    @get:Rule
    val composeTestRule = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun permissions_record() {
        composeTestRule.setContent {
            ZWheelTheme {
                PermissionsScreen(
                    bleGranted = true,
                    blePermanentlyDenied = false,
                    locationGranted = false,
                    locationPermanentlyDenied = true,
                    onRequestBle = {},
                    onOpenBleSettings = {},
                    onRequestLocation = {},
                    onSkipLocation = {},
                )
            }
        }
        composeTestRule.onRoot().captureRoboImage(
            "build/outputs/roborazzi/permissions.png",
        )
    }
}
