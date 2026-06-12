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
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
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
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

@Composable
fun ZWheelWearScreen() {
    val mockStateFlow = remember { mockWearDashboardStateFlow() }
    val state by mockStateFlow.collectAsState()
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
                    text = "%.1f".format(state.speedMph),
                    color = Color.White,
                    fontSize = 58.sp,
                    fontWeight = FontWeight.Black,
                    textAlign = TextAlign.Center,
                    letterSpacing = 0.sp,
                    lineHeight = 58.sp,
                )
                Text(
                    text = "MPH",
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
                WearStat("TOP", "%.1f".format(state.topSpeedMph))
                WearStat("BAT", "${state.batteryPercent}%")
                WearStat("RANGE", "%.1f".format(state.rangeMiles))
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
    val speedMph: Double,
    val topSpeedMph: Double,
    val batteryPercent: Int,
    val rangeMiles: Double,
    val connectionLabel: String,
)

private fun mockWearDashboardStateFlow(): StateFlow<WearDashboardUiState> = MutableStateFlow(
    WearDashboardUiState(
        speedMph = 14.8,
        topSpeedMph = 19.6,
        batteryPercent = 63,
        rangeMiles = 7.4,
        connectionLabel = "MOCK CONNECTED",
    ),
)
