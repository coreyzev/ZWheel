package com.zwheel.app.ui.dashboard

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
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
import com.zwheel.app.ui.JetBrainsMonoFamily
import com.zwheel.app.ui.Label
import com.zwheel.app.ui.LocalZWheelColors
import com.zwheel.app.ui.SairaFamily
import com.zwheel.app.ui.SmallStat

@Composable
fun TripCard(state: DashboardUiState, modifier: Modifier = Modifier) {
    val c = LocalZWheelColors.current
    Box(modifier = modifier) {
        DashboardCard {
            Row(
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Column {
                    Label("TRIP DISTANCE")
                    Text(
                        text = "%.2f".format(state.tripMiles),
                        color = c.textPrimary,
                        style = TextStyle(
                            fontFamily = SairaFamily,
                            fontSize = 48.sp,
                            fontWeight = FontWeight.ExtraBold,
                            fontFeatureSettings = "tnum",
                        ),
                    )
                    Text(
                        text = state.rangeUnitLabel,
                        color = c.textLabel,
                        style = TextStyle(fontFamily = JetBrainsMonoFamily, fontSize = 11.sp),
                    )
                }
                Column(
                    horizontalAlignment = Alignment.End,
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    SmallStat("Ah", "%.2f".format(state.tripAmpHours))
                    SmallStat("REGEN", "+%.2f".format(state.regenAmpHours))
                    // TODO(avg-speed)
                    SmallStat(
                        "AVG",
                        if (state.avgSpeedMph > 0.0) {
                            "%.1f %s".format(state.avgSpeedMph, state.speedUnitLabel)
                        } else {
                            "--"
                        },
                    )
                }
            }
        }
    }
}
