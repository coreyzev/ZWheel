package com.zwheel.app.ui.screenshots

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import com.github.takahirom.roborazzi.RobolectricDeviceQualifiers
import com.github.takahirom.roborazzi.captureRoboImage
import com.zwheel.app.data.settings.UserPreferences
import com.zwheel.app.ui.ZWheelTheme
import com.zwheel.app.ui.settings.SettingsContent
import com.zwheel.core.model.BoardIdentity
import com.zwheel.core.model.BoardState
import com.zwheel.core.model.BoardType
import com.zwheel.core.model.ConnectionState
import com.zwheel.core.model.SpeedUnit
import com.zwheel.core.model.TemperatureUnit
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
class SettingsScreenshotTest {

    @get:Rule
    val composeTestRule = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun settings_connected_board_record() {
        composeTestRule.setContent {
            ZWheelTheme {
                SettingsContent(
                    preferences = UserPreferences(
                        speedUnit = SpeedUnit.MPH,
                        temperatureUnit = TemperatureUnit.FAHRENHEIT,
                        tireDiameterInches = 10.5,
                        customBoardName = "BoardyMcBoardface McGee",
                    ),
                    haTestResult = null,
                    boardState = mockBoardState(),
                    rssi = -60,
                    onSaveBoardName = {},
                    onSpeedUnitSelected = {},
                    onTemperatureUnitSelected = {},
                    onTireDiameterChanged = {},
                    onHaUrlChanged = {},
                    onHaTokenChanged = {},
                    onTestHaConnection = {},
                    onDisconnect = {},
                    onForgetBoard = {},
                    onOpenBleDebug = {},
                )
            }
        }
        composeTestRule.onRoot().captureRoboImage(
            "build/outputs/roborazzi/settings.png",
        )
    }

    @Test
    fun settings_device_info_expanded_record() {
        composeTestRule.setContent {
            ZWheelTheme {
                SettingsContent(
                    preferences = UserPreferences(customBoardName = "BoardyMcBoardface McGee"),
                    haTestResult = null,
                    boardState = mockBoardState(),
                    rssi = -60,
                    onSaveBoardName = {},
                    onSpeedUnitSelected = {},
                    onTemperatureUnitSelected = {},
                    onTireDiameterChanged = {},
                    onHaUrlChanged = {},
                    onHaTokenChanged = {},
                    onTestHaConnection = {},
                    onDisconnect = {},
                    onForgetBoard = {},
                    onOpenBleDebug = {},
                )
            }
        }
        composeTestRule.onNodeWithText("DEVICE INFO").performClick()
        composeTestRule.onRoot().captureRoboImage(
            "build/outputs/roborazzi/settings_device_info_expanded.png",
        )
    }
}

private fun mockBoardState() = BoardState(
    identity = BoardIdentity(
        boardId = "TEST_001",
        name = "Pint X",
        type = BoardType.PINT_X,
        serialNumber = "18694",
        batterySerialNumber = "22136",
        firmwareRevision = "4134",
        hardwareRevision = "4209",
    ),
    connectionState = ConnectionState.SUBSCRIBED,
    cellVoltages = List(15) { 3.70 },
    batteryPercent = 80,
)
