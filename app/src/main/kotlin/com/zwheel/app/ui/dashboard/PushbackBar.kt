package com.zwheel.app.ui.dashboard

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.zwheel.app.ui.DashboardUiState
import com.zwheel.app.ui.JetBrainsMonoFamily
import com.zwheel.app.ui.LocalZWheelColors

@Composable
fun PushbackBar(state: DashboardUiState, modifier: Modifier = Modifier) {
    val c = LocalZWheelColors.current
    val fraction = speedFraction(state.speedMph, state.boardType.modelTopSpeedMph())
    val labelColor = when {
        fraction >= PushbackThresholds.DANGER_FRACTION -> c.rampDanger
        fraction >= PushbackThresholds.CAUTION_FRACTION -> c.rampCaution
        else -> c.rampGood
    }
    val statusLabel = when {
        fraction >= PushbackThresholds.DANGER_FRACTION -> "pushback · ease off"
        fraction >= PushbackThresholds.CAUTION_FRACTION -> "approaching pushback"
        else -> "nominal"
    }

    Column(
        verticalArrangement = Arrangement.spacedBy(8.dp),
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 18.dp, vertical = 8.dp),
    ) {
        Row(
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(
                text = "PUSHBACK HEADROOM",
                color = c.textDimmest,
                style = TextStyle(
                    fontFamily = JetBrainsMonoFamily,
                    fontSize = 9.sp,
                    letterSpacing = 1.5.sp,
                ),
            )
            Text(
                text = statusLabel,
                color = labelColor,
                style = TextStyle(
                    fontFamily = JetBrainsMonoFamily,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Normal,
                ),
            )
        }
        Canvas(
            modifier = Modifier
                .fillMaxWidth()
                .height(8.dp),
        ) {
            val radius = CornerRadius(size.height / 2)
            drawRoundRect(c.border, topLeft = Offset.Zero, size = size, cornerRadius = radius)
            val fillWidth = (fraction * size.width).coerceIn(0f, size.width)
            if (fillWidth > 0f) {
                val fillBrush = Brush.horizontalGradient(
                    PushbackThresholds.CAUTION_FRACTION - 0.05f to c.rampGood,
                    PushbackThresholds.CAUTION_FRACTION + 0.03f to c.rampCaution,
                    PushbackThresholds.DANGER_FRACTION - 0.03f to c.rampCaution,
                    PushbackThresholds.DANGER_FRACTION + 0.02f to c.rampDanger,
                    startX = 0f,
                    endX = size.width,
                )
                drawRoundRect(
                    brush = fillBrush,
                    topLeft = Offset.Zero,
                    size = Size(fillWidth, size.height),
                    cornerRadius = radius,
                )
            }
        }
    }
}
