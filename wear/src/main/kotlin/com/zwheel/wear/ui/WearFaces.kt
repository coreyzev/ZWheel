package com.zwheel.wear.ui

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BluetoothSearching
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.wear.compose.material.Icon
import androidx.wear.compose.material.Text

@Composable
internal fun ActiveFace(state: WearDashboardUiState) {
    Box(
        Modifier.fillMaxSize().background(WearColors.screenBlack),
        contentAlignment = Alignment.Center,
    ) {
        ProgressArc(
            fraction = state.batteryPercent / 100f,
            arcColor = WearColors.lime,
            modifier = Modifier.align(Alignment.Center),
        )
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            SpeedHero(state, WearColors.lime, WearColors.textMuted)
            Text(state.speedUnitLabel, style = wearLabelStyle, color = WearColors.textSecondary)
            Spacer(Modifier.height(4.dp))
            Text(
                text = "${state.batteryDisplay}  ${state.rangeDisplay}",
                style = wearSmallStyle,
                color = WearColors.textSecondary,
            )
        }
        Text(
            text = "TOP  ${state.topSpeedDisplay}",
            style = wearSmallStyle,
            color = WearColors.textMuted,
            modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 18.dp),
        )
    }
}

@Composable
internal fun CautionFace(state: WearDashboardUiState) {
    val alpha by rememberInfiniteTransition(label = "caution-blink").animateFloat(
        initialValue = 1f,
        targetValue = 0.25f,
        animationSpec = infiniteRepeatable(
            animation = tween(550, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "alpha",
    )

    Box(
        Modifier.fillMaxSize().background(WearColors.screenBlack),
        contentAlignment = Alignment.Center,
    ) {
        ProgressArc(fraction = state.batteryPercent / 100f, arcColor = WearColors.amber)
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Text(
                text = "▲ PUSHBACK SOON",
                style = wearSmallStyle.copy(fontSize = 12.sp),
                color = WearColors.amber.copy(alpha = alpha),
                modifier = Modifier.padding(bottom = 4.dp),
            )
            SpeedHero(state, WearColors.amber, WearColors.amber.copy(alpha = 0.5f))
            Text(state.speedUnitLabel, style = wearLabelStyle, color = WearColors.amber)
            Spacer(Modifier.height(4.dp))
            Text(
                text = "${state.batteryDisplay}  ${state.rangeDisplay}",
                style = wearSmallStyle,
                color = WearColors.amber.copy(alpha = 0.7f),
            )
        }
        Text(
            text = "TOP  ${state.topSpeedDisplay}",
            style = wearSmallStyle,
            color = WearColors.amber.copy(alpha = 0.5f),
            modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 18.dp),
        )
    }
}

@Composable
internal fun AmbientFace(state: WearDashboardUiState) {
    Box(
        Modifier.fillMaxSize().background(WearColors.screenBlack),
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                state.speedDisplay,
                style = wearSpeedStyle.copy(fontSize = 52.sp),
                color = WearColors.textDim,
            )
            Text(
                "SPEED · ${state.speedUnitLabel}",
                style = wearLabelStyle.copy(letterSpacing = 2.sp),
                color = WearColors.textDim,
            )
        }
    }
}

@Composable
internal fun DisconnectedFace() {
    val transition = rememberInfiniteTransition(label = "scan")
    val ringAlpha by transition.animateFloat(
        initialValue = 0.6f,
        targetValue = 0f,
        animationSpec = infiniteRepeatable(
            animation = tween(1800, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Restart,
        ),
        label = "ring-alpha",
    )
    val ringRadius by transition.animateFloat(
        initialValue = 40f,
        targetValue = 100f,
        animationSpec = infiniteRepeatable(
            animation = tween(1800, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Restart,
        ),
        label = "ring-radius",
    )

    Box(
        Modifier.fillMaxSize().background(WearColors.screenBlack),
        contentAlignment = Alignment.Center,
    ) {
        Canvas(Modifier.fillMaxSize()) {
            drawCircle(
                color = WearColors.cyan.copy(alpha = ringAlpha),
                radius = ringRadius.dp.toPx(),
                style = Stroke(width = 2.dp.toPx()),
            )
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(
                imageVector = Icons.Filled.BluetoothSearching,
                contentDescription = "Searching",
                tint = WearColors.cyan,
                modifier = Modifier.size(36.dp),
            )
            Spacer(Modifier.height(8.dp))
            Text("SCANNING", style = wearLabelStyle, color = WearColors.cyan)
            Spacer(Modifier.height(4.dp))
            Text(
                "looking for board…",
                style = wearLabelStyle.copy(fontSize = 11.sp),
                color = WearColors.textMuted,
            )
        }
    }
}

@Composable
private fun SpeedHero(state: WearDashboardUiState, speedColor: Color, decimalColor: Color) {
    Row(verticalAlignment = Alignment.Bottom) {
        Text(state.speedDisplay, style = wearSpeedStyle, color = speedColor)
        Text(
            ".${state.speedDecimalDisplay}",
            style = wearSpeedStyle.copy(fontSize = 32.sp),
            color = decimalColor,
        )
    }
}

@Composable
private fun ProgressArc(
    fraction: Float,
    arcColor: Color,
    modifier: Modifier = Modifier,
) {
    Canvas(modifier = modifier.size(200.dp)) {
        val stroke = Stroke(width = 8.dp.toPx(), cap = StrokeCap.Round)
        val inset = 8.dp.toPx() / 2f
        val arcSize = Size(size.width - inset * 2, size.height - inset * 2)
        val topLeft = Offset(inset, inset)
        drawArc(WearColors.arcTrack, -90f, 360f, false, topLeft, arcSize, style = stroke)
        if (fraction > 0f) {
            drawArc(
                arcColor,
                -90f,
                360f * fraction.coerceIn(0f, 1f),
                false,
                topLeft,
                arcSize,
                style = stroke,
            )
        }
    }
}

@Composable
internal fun ErrorFace(errorCode: Int) {
    Box(
        Modifier
            .fillMaxSize()
            .background(Color(0xFFB00020)),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Text(
                text = "BOARD ERROR",
                style = wearLabelStyle,
                color = Color.White.copy(alpha = 0.7f),
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text = "ERR $errorCode",
                style = wearSpeedStyle,
                color = Color.White,
            )
        }
    }
}
