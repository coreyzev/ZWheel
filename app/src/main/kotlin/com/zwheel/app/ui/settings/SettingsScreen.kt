package com.zwheel.app.ui.settings

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Info
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
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
) {
    val preferences by viewModel.preferences.collectAsState()
    val haTestResult by viewModel.haTestResult.collectAsState()
    val boardState by viewModel.boardState.collectAsState()
    val rssi by viewModel.rssi.collectAsState()
    val isDebugLogging by viewModel.isDebugLogging.collectAsState()
    val debugStatus by viewModel.debugStatus.collectAsState()

    SettingsContent(
        preferences = preferences,
        haTestResult = haTestResult,
        boardState = boardState,
        rssi = rssi,
        isDebugLogging = isDebugLogging,
        debugStatus = debugStatus,
        onSaveBoardName = viewModel::setCustomBoardName,
        onSaveBoardTireDiameter = viewModel::saveBoardTireDiameter,
        onSpeedUnitSelected = viewModel::setSpeedUnit,
        onTemperatureUnitSelected = viewModel::setTemperatureUnit,
        onHaUrlChanged = viewModel::setHaUrl,
        onHaTokenChanged = viewModel::setHaToken,
        onTestHaConnection = viewModel::testHaConnection,
        onClearHaSensors = viewModel::clearHaSensors,
        onDisconnect = onDisconnect,
        onForgetBoard = onForgetBoard,
        onToggleDebugLogging = viewModel::setDebugLogging,
        onSaveDebugPassword = viewModel::saveDebugPassword,
        onRestartDebugLogging = viewModel::restartDebugLogging,
        onPairDebug = viewModel::pairDebug,
        onUploadDebug = viewModel::uploadDebug,
        onShareDebug = viewModel::shareDebug,
    )
}

@Composable
internal fun SettingsContent(
    preferences: UserPreferences,
    haTestResult: HaPushResult?,
    boardState: BoardState,
    rssi: Int?,
    isDebugLogging: Boolean,
    debugStatus: String?,
    onSaveBoardName: (String?) -> Unit,
    onSaveBoardTireDiameter: (Double) -> Unit,
    onSpeedUnitSelected: (SpeedUnit) -> Unit,
    onTemperatureUnitSelected: (TemperatureUnit) -> Unit,
    onHaUrlChanged: (String) -> Unit,
    onHaTokenChanged: (String) -> Unit,
    onTestHaConnection: () -> Unit,
    onClearHaSensors: () -> Unit,
    onDisconnect: () -> Unit,
    onForgetBoard: () -> Unit,
    onToggleDebugLogging: (Boolean) -> Unit,
    onSaveDebugPassword: (String) -> Unit,
    onRestartDebugLogging: () -> Unit,
    onPairDebug: () -> Unit,
    onUploadDebug: () -> Unit,
    onShareDebug: () -> Unit,
) {
    val c = LocalZWheelColors.current
    val hasSavedBoard = boardState.identity != null || preferences.lastConnectedDeviceId != null
    val effectiveIdentity = boardState.identity ?: preferences.lastConnectedDeviceId?.let { id ->
        BoardIdentity(
            boardId = id,
            name = preferences.lastConnectedBoardName
                ?: preferences.lastConnectedBoardType?.displayName
                ?: "Unknown board",
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
    var showBoardTooltip by remember { mutableStateOf(false) }
    if (showBoardTooltip) {
        AlertDialog(
            onDismissRequest = { showBoardTooltip = false },
            confirmButton = {
                TextButton(onClick = { showBoardTooltip = false }) {
                    Text("OK", fontFamily = SairaFamily)
                }
            },
            title = { Text("About board data", fontFamily = SairaFamily, fontWeight = FontWeight.W700) },
            text = {
                Text(
                    "Lifetime ODO and Ah are reported by the board controller, not calculated by ZWheel. " +
                        "Swapping tires doesn't reset the ODO. Replacing the battery doesn't reset Ah. " +
                        "These values reflect what the controller has recorded since the factory.",
                    fontFamily = SairaFamily,
                    fontSize = 14.sp,
                )
            },
        )
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .background(c.screenBg),
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
            item {
                Row(
                    modifier = Modifier
                        .padding(horizontal = 18.dp)
                        .padding(bottom = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Text(
                        text = "CONNECTED BOARD",
                        style = TextStyle(
                            fontFamily = JetBrainsMonoFamily,
                            fontSize = 9.sp,
                            fontWeight = FontWeight.W400,
                            letterSpacing = 2.sp,
                        ),
                        color = c.textDimmest,
                        modifier = Modifier.weight(1f),
                    )
                    Icon(
                        imageVector = Icons.Default.Info,
                        contentDescription = "About board data",
                        tint = c.textDimmest,
                        modifier = Modifier
                            .size(14.dp)
                            .clickable { showBoardTooltip = true },
                    )
                }
            }
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
                onClearSensors = onClearHaSensors,
                modifier = Modifier.padding(horizontal = 18.dp),
            )
        }
        item { Spacer(Modifier.height(22.dp)) }
        item {
            DeveloperSection(
                isDebugLogging = isDebugLogging,
                debugPassword = preferences.bleDebugPassword,
                debugStatus = debugStatus,
                onToggleLogging = onToggleDebugLogging,
                onSavePassword = onSaveDebugPassword,
                onRestartLogging = onRestartDebugLogging,
                onPair = onPairDebug,
                onUpload = onUploadDebug,
                onShare = onShareDebug,
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
