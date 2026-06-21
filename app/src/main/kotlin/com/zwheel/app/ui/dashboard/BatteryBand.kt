package com.zwheel.app.ui.dashboard

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.zwheel.app.ui.DashboardCard
import com.zwheel.app.ui.DashboardUiState
import com.zwheel.app.ui.JetBrainsMonoFamily
import com.zwheel.app.ui.Label
import com.zwheel.app.ui.LocalZWheelColors
import com.zwheel.app.ui.SairaFamily
import com.zwheel.app.ui.ramp

@Composable
fun BatteryBand(state: DashboardUiState, modifier: Modifier = Modifier) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        modifier = modifier,
    ) {
        Box(modifier = Modifier.weight(1f)) {
            BatteryCard(state)
        }
        Box(modifier = Modifier.weight(1f)) {
            RangeCard(state)
        }
    }
}

@Composable
private fun BatteryCard(state: DashboardUiState) {
    val c = LocalZWheelColors.current
    val batteryFraction = (state.batteryPercent / 100f).coerceIn(0f, 1f)
    val batteryColor = c.ramp(batteryFraction)
    DashboardCard {
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Label("BATTERY")
            Text(
                text = "${state.batteryPercent}%",
                color = batteryColor,
                style = TextStyle(
                    fontFamily = SairaFamily,
                    fontSize = 48.sp,
                    fontWeight = FontWeight.ExtraBold,
                    fontFeatureSettings = "tnum",
                ),
            )
            LinearProgressIndicator(
                progress = batteryFraction,
                color = batteryColor,
                trackColor = c.border,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(4.dp)
                    .clip(RoundedCornerShape(999.dp)),
            )
            Text(
                text = "%.1fV · %dS PACK".format(state.packVoltage, state.cellVoltages.size),
                color = c.textLabel,
                style = TextStyle(fontFamily = JetBrainsMonoFamily, fontSize = 9.sp),
            )
        }
    }
}

@Composable
private fun RangeCard(state: DashboardUiState) {
    val c = LocalZWheelColors.current
    DashboardCard {
        Column {
            Label("EST. REMAINING")
            Text(
                text = "%.1f".format(state.estimatedRangeMiles),
                color = c.cyan,
                style = TextStyle(
                    fontFamily = SairaFamily,
                    fontSize = 48.sp,
                    fontWeight = FontWeight.ExtraBold,
                    fontFeatureSettings = "tnum",
                ),
            )
            Text(
                text = "${state.rangeUnitLabel} at current draw",
                color = c.textSecondary,
                style = TextStyle(fontFamily = JetBrainsMonoFamily, fontSize = 11.sp),
            )
        }
    }
}
