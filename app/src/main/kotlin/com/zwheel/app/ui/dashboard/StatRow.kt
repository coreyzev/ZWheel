package com.zwheel.app.ui.dashboard

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.Lightbulb
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.zwheel.app.ui.DashboardCard
import com.zwheel.app.ui.DashboardUiState
import com.zwheel.app.ui.Label
import com.zwheel.app.ui.LocalZWheelColors
import com.zwheel.app.ui.SairaFamily

@Composable
fun StatRow(state: DashboardUiState, modifier: Modifier = Modifier) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        modifier = modifier.height(IntrinsicSize.Min),
    ) {
        val tileShape = RoundedCornerShape(14.dp)
        Box(modifier = Modifier.weight(1f).fillMaxHeight()) {
            DashboardCard(modifier = Modifier.fillMaxHeight(), shape = tileShape) {
                Column {
                    DrawTile(state)
                }
            }
        }
        Box(modifier = Modifier.weight(1f).fillMaxHeight()) {
            DashboardCard(modifier = Modifier.fillMaxHeight(), shape = tileShape) {
                ModeTile(state)
            }
        }
        Box(modifier = Modifier.weight(1f).fillMaxHeight()) {
            DashboardCard(modifier = Modifier.fillMaxHeight(), shape = tileShape) {
                Column {
                    LightsTile(state)
                }
            }
        }
    }
}

@Composable
private fun DrawTile(state: DashboardUiState) {
    val c = LocalZWheelColors.current
    Icon(Icons.Filled.Bolt, contentDescription = null, tint = c.lime, modifier = Modifier.size(16.dp))
    Text(
        text = "%.1f A".format(state.amps),
        color = c.textPrimary,
        style = TextStyle(
            fontFamily = SairaFamily,
            fontSize = 20.sp,
            fontWeight = FontWeight.ExtraBold,
            fontFeatureSettings = "tnum",
        ),
    )
    Label("DRAW · %.2f Ah".format(state.tripAmpHours))
}

@Composable
private fun ModeTile(state: DashboardUiState) {
    val c = LocalZWheelColors.current
    Column {
        Label("MODE")
        Text(
            text = state.rideMode,
            color = c.lime,
            maxLines = 1,
            softWrap = false,
            overflow = TextOverflow.Ellipsis,
            style = TextStyle(
                fontFamily = SairaFamily,
                fontSize = 18.sp,
                fontWeight = FontWeight.ExtraBold,
            ),
        )
    }
}

@Composable
private fun LightsTile(state: DashboardUiState) {
    val c = LocalZWheelColors.current
    val color = if (state.lightsOn) c.lime else c.textDim
    Icon(Icons.Filled.Lightbulb, contentDescription = null, tint = color, modifier = Modifier.size(16.dp))
    Text(
        text = if (state.lightsOn) "ON" else "OFF",
        color = color,
        style = TextStyle(
            fontFamily = SairaFamily,
            fontSize = 20.sp,
            fontWeight = FontWeight.ExtraBold,
            fontFeatureSettings = "tnum",
        ),
    )
    Label("LIGHTS")
}
