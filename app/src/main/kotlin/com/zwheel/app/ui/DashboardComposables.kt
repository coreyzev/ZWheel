package com.zwheel.app.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
internal fun Header(state: DashboardUiState) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column {
            Text(
                text = state.boardName,
                color = Color(0xff111111),
                fontSize = 24.sp,
                fontWeight = FontWeight.Black,
                letterSpacing = 0.sp,
            )
            Text(
                text = "${state.connectionLabel}  RSSI ${state.rssi?.let { "$it dBm" } ?: "--"}",
                color = Color(0xff555555),
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 0.sp,
            )
        }
        Text(
            text = state.firmwareLabel,
            color = Color(0xff555555),
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = 0.sp,
        )
    }
}

@Composable
internal fun SpeedCard(state: DashboardUiState) {
    DashboardCard(color = Color(0xffffd400)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column {
                Label("SPEED")
                Metric(value = "%.1f".format(state.speedMph), unit = state.speedUnitLabel, size = 64)
                Text(
                    text = "TOP %.1f   RANGE %.1f %s".format(state.topSpeedMph, state.estimatedRangeMiles, state.rangeUnitLabel),
                    color = Color(0xff111111),
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Black,
                    letterSpacing = 0.sp,
                )
            }
            SpeedGauge(progress = state.speedMph / 25.0)
        }
    }
}

@Composable
internal fun BatteryPackCard(state: DashboardUiState) {
    DashboardCard(color = Color(0xffe4007f), contentColor = Color.White) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.Bottom,
        ) {
            Column {
                Label("BATTERY PACK", Color.White)
                Metric(value = state.batteryPercent.toString(), unit = "%", size = 56)
            }
            Column(horizontalAlignment = Alignment.End) {
                SmallStat("PACK", "%.1f V".format(state.packVoltage), Color.White)
                SmallStat("AMPS", "%.1f A".format(state.amps), Color.White)
                SmallStat("TEMP", "${state.controllerTempF} ${state.temperatureUnitLabel}", Color.White)
            }
        }
    }
}

@Composable
internal fun CellVoltageCard(cells: List<CellVoltageUiState>) {
    DashboardCard(color = Color(0xff7a3cff), contentColor = Color.White) {
        Label("CELL VOLTAGES", Color.White)
        LazyVerticalGrid(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 146.dp, max = 146.dp),
            columns = GridCells.Fixed(4),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            userScrollEnabled = false,
        ) {
            items(cells) { cell ->
                Surface(
                    shape = RoundedCornerShape(6.dp),
                    color = Color.White.copy(alpha = if (cell.isLow) 0.95f else 0.18f),
                    contentColor = if (cell.isLow) Color(0xff7a003d) else Color.White,
                ) {
                    Column(modifier = Modifier.padding(8.dp)) {
                        Text(
                            text = cell.label,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Black,
                            letterSpacing = 0.sp,
                        )
                        Text(
                            text = "%.2f".format(cell.volts),
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Black,
                            letterSpacing = 0.sp,
                        )
                    }
                }
            }
        }
    }
}

@Composable
internal fun TripStatsCard(
    state: DashboardUiState,
    locationGranted: Boolean = true,
    locationPermanentlyDenied: Boolean = false,
    onRequestLocation: () -> Unit = {},
) {
    DashboardCard(color = Color(0xff00a7c8), contentColor = Color(0xff061016)) {
        Label("TRIP STATS")
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            SmallStat("DISTANCE", "%.2f MI".format(state.tripMiles))
            SmallStat("USED", "%.2f AH".format(state.tripAmpHours))
            SmallStat("REGEN", "%.2f AH".format(state.regenAmpHours))
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.End,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (!locationGranted) {
                val gpsLabel = if (locationPermanentlyDenied) "GPS DENIED — OPEN SETTINGS" else "GPS OFF — TAP TO ENABLE"
                TextButton(onClick = onRequestLocation) {
                    Text(
                        text = gpsLabel,
                        color = Color(0xff9b1c1c),
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Black,
                        letterSpacing = 0.sp,
                    )
                }
            } else {
                val gpsColor = if (state.gpsLocked) Color(0xff007a3d) else Color(0xffb45309)
                Text(
                    text = if (state.gpsLocked) "GPS LOCKED" else "GPS SEARCHING",
                    color = gpsColor,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Black,
                    letterSpacing = 0.sp,
                )
            }
        }
    }
}

@Composable
internal fun RideModeCard(state: DashboardUiState) {
    DashboardCard(color = Color(0xff111111), contentColor = Color.White) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column {
                Label("RIDE MODE", Color.White)
                Text(
                    text = state.rideMode,
                    fontSize = 32.sp,
                    fontWeight = FontWeight.Black,
                    letterSpacing = 0.sp,
                )
            }
            Column(horizontalAlignment = Alignment.End) {
                SmallStat("LIGHTS", state.lightsLabel, Color.White)
                SmallStat("TIRE", "%.1f IN".format(state.tireDiameterInches), Color.White)
            }
        }
    }
}
