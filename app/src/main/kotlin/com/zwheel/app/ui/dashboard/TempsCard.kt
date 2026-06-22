package com.zwheel.app.ui.dashboard

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Divider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.zwheel.app.ui.DashboardCard
import com.zwheel.app.ui.DashboardUiState
import com.zwheel.app.ui.Label
import com.zwheel.app.ui.LocalZWheelColors
import com.zwheel.app.ui.SairaFamily

@Composable
fun TempsCard(state: DashboardUiState, modifier: Modifier = Modifier) {
    val c = LocalZWheelColors.current
    Box(modifier = modifier) {
        DashboardCard {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Label("TEMPERATURES")
                Row(
                    horizontalArrangement = Arrangement.SpaceEvenly,
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    TemperatureTile("CTRL", state.controllerTempF, state.temperatureUnitLabel)
                    Divider(color = c.divider, modifier = Modifier.height(40.dp).width(1.dp))
                    TemperatureTile("MOTOR", state.motorTempF, state.temperatureUnitLabel)
                    Divider(color = c.divider, modifier = Modifier.height(40.dp).width(1.dp))
                    TemperatureTile("BATT", state.batteryTempF, state.temperatureUnitLabel)
                }
            }
        }
    }
}

@Composable
private fun TemperatureTile(label: String, tempValue: Int, unit: String) {
    val c = LocalZWheelColors.current
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Label(label)
        Text(
            text = "$tempValue°$unit",
            color = c.textPrimary,
            style = TextStyle(
                fontFamily = SairaFamily,
                fontSize = 24.sp,
                fontWeight = FontWeight.ExtraBold,
                fontFeatureSettings = "tnum",
            ),
        )
    }
}
