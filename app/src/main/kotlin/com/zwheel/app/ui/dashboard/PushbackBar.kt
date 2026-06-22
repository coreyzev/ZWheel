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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
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
    val barColor = when {
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
            .padding(horizontal = 18.dp),
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
                color = barColor,
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
            drawRect(c.border, topLeft = Offset.Zero, size = size)
            drawRect(
                barColor,
                topLeft = Offset.Zero,
                size = Size((fraction * size.width).coerceIn(0f, size.width), size.height),
            )
        }
    }
}
