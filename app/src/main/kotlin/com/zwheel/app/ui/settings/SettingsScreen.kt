package com.zwheel.app.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.zwheel.app.data.settings.UserPreferences
import com.zwheel.app.service.HaPushResult
import com.zwheel.core.model.SpeedUnit
import com.zwheel.core.model.TemperatureUnit

@Composable
fun SettingsScreen(
    viewModel: SettingsViewModel = hiltViewModel(),
) {
    val preferences by viewModel.preferences.collectAsState()
    val haTestResult by viewModel.haTestResult.collectAsState()

    SettingsContent(
        preferences = preferences,
        haTestResult = haTestResult,
        onSpeedUnitSelected = viewModel::setSpeedUnit,
        onTemperatureUnitSelected = viewModel::setTemperatureUnit,
        onTireDiameterChanged = viewModel::setTireDiameter,
        onHaUrlChanged = viewModel::setHaUrl,
        onHaTokenChanged = viewModel::setHaToken,
        onTestHaConnection = viewModel::testHaConnection,
    )
}

@Composable
private fun SettingsContent(
    preferences: UserPreferences,
    haTestResult: HaPushResult?,
    onSpeedUnitSelected: (SpeedUnit) -> Unit,
    onTemperatureUnitSelected: (TemperatureUnit) -> Unit,
    onTireDiameterChanged: (Double) -> Unit,
    onHaUrlChanged: (String) -> Unit,
    onHaTokenChanged: (String) -> Unit,
    onTestHaConnection: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xffeeeeee))
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(24.dp),
    ) {
        SettingsHeader()
        UnitSelector(
            label = "SPEED UNIT",
            options = listOf(SpeedUnit.MPH to "MPH", SpeedUnit.KPH to "KPH"),
            selectedOption = preferences.speedUnit,
            onSelected = onSpeedUnitSelected,
        )
        UnitSelector(
            label = "TEMPERATURE UNIT",
            options = listOf(TemperatureUnit.FAHRENHEIT to "°F", TemperatureUnit.CELSIUS to "°C"),
            selectedOption = preferences.temperatureUnit,
            onSelected = onTemperatureUnitSelected,
        )
        TireDiameterControl(
            diameterInches = preferences.tireDiameterInches,
            onDiameterChanged = onTireDiameterChanged,
        )
        HomeAssistantSection(
            haUrl = preferences.haUrl,
            haToken = preferences.haToken,
            haTestResult = haTestResult,
            onUrlChanged = onHaUrlChanged,
            onTokenChanged = onHaTokenChanged,
            onTestConnection = onTestHaConnection,
        )
    }
}

@Composable
private fun SettingsHeader() {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(
            text = "Settings",
            color = Color(0xff111111),
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Black,
        )
        Text(
            text = "Units and tire calibration",
            color = Color(0xff555555),
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.Bold,
        )
    }
}

@Composable
private fun <T> UnitSelector(
    label: String,
    options: List<Pair<T, String>>,
    selectedOption: T,
    onSelected: (T) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        SectionLabel(label)
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            options.forEach { (option, text) ->
                FilterChip(
                    selected = option == selectedOption,
                    onClick = { onSelected(option) },
                    label = { Text(text = text) },
                )
            }
        }
    }
}

@Composable
private fun TireDiameterControl(
    diameterInches: Double,
    onDiameterChanged: (Double) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            SectionLabel("TIRE DIAMETER")
            Text(
                text = "%.1f in".format(diameterInches),
                color = Color(0xff111111),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Black,
            )
        }
        Slider(
            value = diameterInches.toFloat(),
            onValueChange = { onDiameterChanged(it.toDouble()) },
            valueRange = 8f..16f,
        )
    }
}

@Composable
private fun HomeAssistantSection(
    haUrl: String,
    haToken: String,
    haTestResult: HaPushResult?,
    onUrlChanged: (String) -> Unit,
    onTokenChanged: (String) -> Unit,
    onTestConnection: () -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        SectionLabel("HOME ASSISTANT")
        androidx.compose.material3.OutlinedTextField(
            value = haUrl,
            onValueChange = onUrlChanged,
            label = { Text("Server URL") },
            placeholder = { Text("http://homeassistant.local:8123") },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
        )
        if (haUrl.startsWith("http://")) {
            Text(
                text = "Token sent over unencrypted HTTP. Use https:// if available.",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.error,
            )
        }
        androidx.compose.material3.OutlinedTextField(
            value = haToken,
            onValueChange = onTokenChanged,
            label = { Text("Long-lived access token") },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Button(onClick = onTestConnection, enabled = haUrl.isNotBlank() && haToken.isNotBlank()) {
                Text("Test connection")
            }
            if (haTestResult != null) {
                val (label, color) = when (haTestResult) {
                    HaPushResult.Success -> "Connected" to Color(0xff007a3d)
                    HaPushResult.AuthFailed -> "Auth failed (check token)" to Color(0xff9b1c1c)
                    HaPushResult.Unreachable -> "Unreachable (check URL)" to Color(0xffb45309)
                    HaPushResult.BadUrl -> "Bad URL (needs http:// or https://)" to Color(0xff9b1c1c)
                }
                Text(text = label, color = color, style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Bold)
            }
        }
        Text(
            text = "When configured, battery % is pushed to HA as sensor.onewheel_battery while connected.",
            style = MaterialTheme.typography.bodySmall,
            color = Color(0xff777777),
        )
    }
}

@Composable
private fun SectionLabel(text: String) {
    Text(
        text = text,
        color = Color(0xff555555),
        style = MaterialTheme.typography.labelMedium,
        fontWeight = FontWeight.Black,
    )
}
