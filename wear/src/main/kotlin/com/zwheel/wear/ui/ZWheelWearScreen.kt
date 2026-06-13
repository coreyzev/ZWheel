package com.zwheel.wear.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.wear.compose.material.MaterialTheme
import androidx.wear.compose.material.Text
import com.zwheel.core.model.SpeedUnit
import com.zwheel.core.model.WatchPayload

private const val METERS_PER_SECOND_TO_MPH = 2.23694
private const val METERS_PER_SECOND_TO_KPH = 3.6
private const val METERS_TO_MILES = 0.000621371
private const val METERS_TO_KM = 0.001

@Composable
fun ZWheelWearScreen(payload: WatchPayload?) {
    val state = payload?.toUiState() ?: WearDashboardUiState.empty()
    WearDashboard(state = state)
}

@Composable
private fun WearDashboard(state: WearDashboardUiState) {
    MaterialTheme {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black)
                .padding(14.dp),
            contentAlignment = Alignment.Center,
        ) {
            BatteryRing(percent = state.batteryPercent)
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                Text(
                    text = state.speedDisplay,
                    color = Color.White,
                    fontSize = 58.sp,
                    fontWeight = FontWeight.Black,
                    textAlign = TextAlign.Center,
                    letterSpacing = 0.sp,
                    lineHeight = 58.sp,
                )
                Text(
                    text = state.speedUnitLabel,
                    color = Color(0xffffd400),
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Black,
                    letterSpacing = 0.sp,
                )
            }
            Row(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.Bottom,
            ) {
                WearStat("TOP", state.topSpeedDisplay)
                WearStat("BAT", state.batteryDisplay)
                WearStat("RANGE", state.rangeDisplay)
            }
            Text(
                modifier = Modifier.align(Alignment.TopCenter),
                text = state.connectionLabel,
                color = Color(0xff00d8ff),
                fontSize = 11.sp,
                fontWeight = FontWeight.Black,
                letterSpacing = 0.sp,
            )
        }
    }
}

@Composable
private fun WearStat(label: String, value: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            text = label,
            color = Color(0xff888888),
            fontSize = 9.sp,
            fontWeight = FontWeight.Black,
            letterSpacing = 0.sp,
        )
        Text(
            text = value,
            color = Color.White,
            fontSize = 14.sp,
            fontWeight = FontWeight.Black,
            letterSpacing = 0.sp,
        )
    }
}

@Composable
private fun BatteryRing(percent: Int) {
    Canvas(modifier = Modifier.size(174.dp)) {
        val stroke = Stroke(width = 7.dp.toPx(), cap = StrokeCap.Round)
        val inset = 7.dp.toPx()
        val arcSize = Size(size.width - inset * 2, size.height - inset * 2)
        val topLeft = Offset(inset, inset)
        drawArc(
            color = Color(0xff222222),
            startAngle = -90f,
            sweepAngle = 360f,
            useCenter = false,
            topLeft = topLeft,
            size = arcSize,
            style = stroke,
        )
        drawArc(
            color = Color(0xffe4007f),
            startAngle = -90f,
            sweepAngle = 360f * (percent.coerceIn(0, 100) / 100f),
            useCenter = false,
            topLeft = topLeft,
            size = arcSize,
            style = stroke,
        )
    }
}

private data class WearDashboardUiState(
    val speedDisplay: String,
    val speedUnitLabel: String,
    val topSpeedDisplay: String,
    val batteryPercent: Int,
    val batteryDisplay: String,
    val rangeDisplay: String,
    val connectionLabel: String,
) {
    companion object {
        fun empty() = WearDashboardUiState(
            speedDisplay = "--",
            speedUnitLabel = "MPH",
            topSpeedDisplay = "--",
            batteryPercent = 0,
            batteryDisplay = "--%",
            rangeDisplay = "--",
            connectionLabel = "DISCONNECTED",
        )
    }
}

private fun WatchPayload.toUiState(): WearDashboardUiState {
    val isMph = speedUnit == SpeedUnit.MPH
    val speedConversion = if (isMph) METERS_PER_SECOND_TO_MPH else METERS_PER_SECOND_TO_KPH
    val rangeConversion = if (isMph) METERS_TO_MILES else METERS_TO_KM
    val unitLabel = if (isMph) "MPH" else "KPH"

    val speedVal = speedMetersPerSecondCorrected
    val speedStr = if (speedVal != null) "%.1f".format(speedVal * speedConversion) else "--"

    val topSpeedStr = "%.1f".format(topSpeedMetersPerSecond * speedConversion)

    val batPct = batteryPercent ?: 0
    val batStr = if (batteryPercent != null) "$batteryPercent%" else "--%"

    val rangeVal = estimatedRangeMeters
    val rangeStr = if (rangeVal != null) "%.1f".format(rangeVal * rangeConversion) else "--"

    return WearDashboardUiState(
        speedDisplay = speedStr,
        speedUnitLabel = unitLabel,
        topSpeedDisplay = topSpeedStr,
        batteryPercent = batPct,
        batteryDisplay = batStr,
        rangeDisplay = rangeStr,
        connectionLabel = connectionState.name,
    )
}
