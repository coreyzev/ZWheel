package com.zwheel.app.ui.settings

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.zwheel.app.data.settings.UserPreferences
import com.zwheel.app.service.HaPushResult
import com.zwheel.app.ui.JetBrainsMonoFamily
import com.zwheel.app.ui.LocalZWheelColors
import com.zwheel.app.ui.SairaFamily
import com.zwheel.core.model.BoardIdentity
import com.zwheel.core.model.BoardState
import com.zwheel.core.model.BoardType
import com.zwheel.core.model.SpeedUnit
import com.zwheel.core.model.TemperatureUnit

@Composable
fun SettingsScreen(
    viewModel: SettingsViewModel = hiltViewModel(),
    onDisconnect: () -> Unit = {},
    onForgetBoard: () -> Unit = {},
    onOpenBleDebug: () -> Unit = {},
) {
    val preferences by viewModel.preferences.collectAsState()
    val haTestResult by viewModel.haTestResult.collectAsState()
    val boardState by viewModel.boardState.collectAsState()
    val rssi by viewModel.rssi.collectAsState()

    SettingsContent(
        preferences = preferences,
        haTestResult = haTestResult,
        boardState = boardState,
        rssi = rssi,
        onSaveBoardName = viewModel::setCustomBoardName,
        onSaveBoardTireDiameter = viewModel::saveBoardTireDiameter,
        onSpeedUnitSelected = viewModel::setSpeedUnit,
        onTemperatureUnitSelected = viewModel::setTemperatureUnit,
        onHaUrlChanged = viewModel::setHaUrl,
        onHaTokenChanged = viewModel::setHaToken,
        onTestHaConnection = viewModel::testHaConnection,
        onDisconnect = onDisconnect,
        onForgetBoard = onForgetBoard,
        onOpenBleDebug = onOpenBleDebug,
    )
}

@Composable
internal fun SettingsContent(
    preferences: UserPreferences,
    haTestResult: HaPushResult?,
    boardState: BoardState,
    rssi: Int?,
    onSaveBoardName: (String?) -> Unit,
    onSaveBoardTireDiameter: (Double) -> Unit,
    onSpeedUnitSelected: (SpeedUnit) -> Unit,
    onTemperatureUnitSelected: (TemperatureUnit) -> Unit,
    onHaUrlChanged: (String) -> Unit,
    onHaTokenChanged: (String) -> Unit,
    onTestHaConnection: () -> Unit,
    onDisconnect: () -> Unit,
    onForgetBoard: () -> Unit,
    onOpenBleDebug: () -> Unit,
) {
    val c = LocalZWheelColors.current
    val hasSavedBoard = boardState.identity != null || preferences.lastConnectedDeviceId != null
    val effectiveIdentity = boardState.identity ?: preferences.lastConnectedDeviceId?.let { id ->
        BoardIdentity(
            boardId = id,
            name = preferences.lastConnectedBoardType?.displayName ?: "Unknown board",
            type = preferences.lastConnectedBoardType ?: BoardType.UNKNOWN,
            serialNumber = preferences.lastConnectedSerial,
            batterySerialNumber = preferences.lastConnectedBatterySerial,
            hardwareRevision = preferences.lastConnectedHardwareRev,
            firmwareRevision = preferences.lastConnectedFirmwareRev,
        )
    }
    val effectiveTireDiameter = preferences.lastConnectedTireDiameterInches
        ?: effectiveIdentity?.type?.stockTireDiameterInches
        ?: 11.5

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .background(c.screenBg)
            .systemBarsPadding(),
        contentPadding = PaddingValues(bottom = 32.dp),
    ) {
        item {
            Text(
                text = "Settings",
                style = TextStyle(
                    fontFamily = SairaFamily,
                    fontSize = 26.sp,
                    fontWeight = FontWeight.W800,
                    letterSpacing = (-0.5).sp,
                ),
                color = c.textPrimary,
                modifier = Modifier.padding(start = 22.dp, end = 22.dp, top = 20.dp, bottom = 16.dp),
            )
        }
        if (hasSavedBoard) {
            item { SectionEyebrowRow("CONNECTED BOARD", modifier = Modifier.padding(horizontal = 18.dp)) }
            item {
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 18.dp, vertical = 8.dp),
                    shape = RoundedCornerShape(16.dp),
                    color = c.card,
                    border = BorderStroke(1.dp, c.border),
                ) {
                    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        ConnectedBoardCard(
                            boardState = boardState,
                            effectiveIdentity = effectiveIdentity,
                            rssi = rssi,
                            customBoardName = preferences.customBoardName,
                            tireDiameterInches = effectiveTireDiameter,
                            onSaveName = onSaveBoardName,
                            onSaveTireDiameter = onSaveBoardTireDiameter,
                            onDisconnect = onDisconnect,
                            onForgetBoard = onForgetBoard,
                        )
                        DeviceInfoDisclosure(identity = effectiveIdentity, rssi = rssi)
                    }
                }
            }
            item { Spacer(Modifier.height(22.dp)) }
        }
        item { SectionEyebrowRow("UNITS", modifier = Modifier.padding(horizontal = 18.dp)) }
        item {
            UnitsSection(
                prefs = preferences,
                onSpeedUnit = onSpeedUnitSelected,
                onTempUnit = onTemperatureUnitSelected,
                modifier = Modifier.padding(horizontal = 18.dp, vertical = 8.dp),
            )
        }
        item { Spacer(Modifier.height(22.dp)) }
        item {
            HomeAssistantSection(
                haUrl = preferences.haUrl,
                haToken = preferences.haToken,
                haTestResult = haTestResult,
                onUrlChanged = onHaUrlChanged,
                onTokenChanged = onHaTokenChanged,
                onTestConnection = onTestHaConnection,
                modifier = Modifier.padding(horizontal = 18.dp),
            )
        }
        item { Spacer(Modifier.height(22.dp)) }
        item {
            DeveloperSection(
                onOpenBleDebug = onOpenBleDebug,
                modifier = Modifier.padding(horizontal = 18.dp),
            )
        }
        item { Spacer(Modifier.height(22.dp)) }
        item { SupportSection(modifier = Modifier.padding(horizontal = 18.dp)) }
        item { Spacer(Modifier.height(22.dp)) }
        item { AboutSection(modifier = Modifier.padding(horizontal = 18.dp)) }
        item { SettingsFooter() }
    }
}

@Composable
private fun SectionEyebrowRow(text: String, modifier: Modifier = Modifier) {
    val c = LocalZWheelColors.current
    Text(
        text = text.uppercase(),
        style = TextStyle(
            fontFamily = JetBrainsMonoFamily,
            fontSize = 9.sp,
            fontWeight = FontWeight.W400,
            letterSpacing = 2.sp,
        ),
        color = c.textDimmest,
        modifier = modifier.padding(bottom = 6.dp),
    )
}
