package com.zwheel.app.ui.screenshots

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onRoot
import com.github.takahirom.roborazzi.RobolectricDeviceQualifiers
import com.github.takahirom.roborazzi.captureRoboImage
import com.zwheel.app.ble.ConnectionState
import com.zwheel.app.ui.ZWheelTheme
import com.zwheel.app.ui.connect.ConnectScreen
import com.zwheel.core.ports.ScanResult
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
class ConnectScreenshotTest {

    @get:Rule
    val composeTestRule = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun connect_record() {
        composeTestRule.setContent {
            ZWheelTheme {
                ConnectScreen(
                    connectionState = ConnectionState.Scanning,
                    devices = listOf(
                        ScanResult("board-1", "Corey's Pint X", -57),
                        ScanResult("board-2", "Garage XR", -68),
                    ),
                    onScan = {},
                    onConnect = {},
                    onDisconnect = {},
                )
            }
        }
        composeTestRule.onRoot().captureRoboImage(
            "build/outputs/roborazzi/connect.png",
        )
    }
}
