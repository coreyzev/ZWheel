package com.zwheel.app.ui.settings

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.zwheel.app.BuildConfig
import com.zwheel.app.data.settings.UserPreferences
import com.zwheel.app.ui.JetBrainsMonoFamily
import com.zwheel.app.ui.LocalZWheelColors
import com.zwheel.app.ui.SairaFamily
import com.zwheel.core.model.SpeedUnit
import com.zwheel.core.model.TemperatureUnit

@Composable
internal fun UnitsSection(
    prefs: UserPreferences,
    onSpeedUnit: (SpeedUnit) -> Unit,
    onTempUnit: (TemperatureUnit) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(12.dp)) {
        SegmentedToggle(
            options = listOf(SpeedUnit.MPH to "MPH", SpeedUnit.KPH to "KPH"),
            selected = prefs.speedUnit,
            onSelected = onSpeedUnit,
        )
        SegmentedToggle(
            options = listOf(TemperatureUnit.FAHRENHEIT to "°F", TemperatureUnit.CELSIUS to "°C"),
            selected = prefs.temperatureUnit,
            onSelected = onTempUnit,
        )
    }
}

@Composable
internal fun DeveloperSection(modifier: Modifier = Modifier, onOpenBleDebug: () -> Unit) {
    val c = LocalZWheelColors.current
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        SectionEyebrow("DEVELOPER")
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(
                "BLE debug view",
                style = TextStyle(fontFamily = SairaFamily, fontSize = 14.sp, fontWeight = FontWeight.W600),
                color = c.textSecondary,
            )
            Switch(
                checked = false,
                onCheckedChange = { if (it) onOpenBleDebug() },
                colors = settingsSwitchColors(),
            )
        }
    }
}

@Composable
internal fun SupportSection(modifier: Modifier = Modifier) {
    val c = LocalZWheelColors.current
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        SectionEyebrow("SUPPORT")
        Text(
            "ZWheel is free and open source. If you enjoy it, consider buying the developer a coffee.",
            style = TextStyle(fontFamily = SairaFamily, fontSize = 13.sp, fontWeight = FontWeight.W400),
            color = c.textMuted,
            lineHeight = 19.sp,
        )
        OutlinedButton(
            onClick = { /* TODO(support): open donation URL. */ },
            border = BorderStroke(1.dp, c.buttonBorder),
            shape = RoundedCornerShape(999.dp),
            colors = ButtonDefaults.outlinedButtonColors(contentColor = c.textSecondary),
        ) {
            Text("Support development", fontFamily = SairaFamily, fontWeight = FontWeight.W600, fontSize = 13.sp)
        }
    }
}

@Composable
internal fun AboutSection(modifier: Modifier = Modifier) {
    val c = LocalZWheelColors.current
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        SectionEyebrow("ABOUT")
        Text(
            "ZWheel does not collect, store, or transmit any personal data. All ride data stays on your device. " +
                "BLE telemetry is read-only — the app never writes to your board.",
            style = TextStyle(fontFamily = SairaFamily, fontSize = 13.sp, fontWeight = FontWeight.W400),
            color = c.textMuted,
            lineHeight = 19.sp,
        )
    }
}

@Composable
internal fun SettingsFooter(modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 16.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = "ZWheel · v${BuildConfig.VERSION_NAME}",
            style = TextStyle(
                fontFamily = JetBrainsMonoFamily,
                fontSize = 10.sp,
                fontWeight = FontWeight.W400,
            ),
            color = Color(0xFF3A3E48), // spec §9 footer dim
        )
    }
}

@Composable
private fun <T> SegmentedToggle(options: List<Pair<T, String>>, selected: T, onSelected: (T) -> Unit) {
    val c = LocalZWheelColors.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(999.dp))
            .border(1.dp, c.buttonBorder, RoundedCornerShape(999.dp)),
    ) {
        options.forEach { (option, label) ->
            val isSelected = option == selected
            Box(
                modifier = Modifier
                    .weight(1f)
                    .background(if (isSelected) c.lime else Color.Transparent)
                    .clickable { onSelected(option) }
                    .padding(vertical = 10.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = label,
                    style = TextStyle(fontFamily = SairaFamily, fontSize = 14.sp, fontWeight = FontWeight.W700),
                    color = if (isSelected) c.screenBg else c.textSecondary,
                )
            }
        }
    }
}

@Composable
internal fun SectionEyebrow(text: String, modifier: Modifier = Modifier) {
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
        modifier = modifier,
    )
}

@Composable
internal fun settingsSwitchColors() = SwitchDefaults.colors(
    checkedThumbColor = LocalZWheelColors.current.lime,
    checkedTrackColor = LocalZWheelColors.current.lime.copy(alpha = 0.3f),
    uncheckedTrackColor = LocalZWheelColors.current.buttonBorder,
    uncheckedThumbColor = LocalZWheelColors.current.textMuted,
)
