package com.zwheel.app.ui.dashboard

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
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
    val statusLabel = if (fraction >= PushbackThresholds.CAUTION_FRACTION) {
        "approaching pushback"
    } else {
        "nominal"
    }

    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 18.dp, vertical = 8.dp),
    ) {
        Canvas(
            modifier = Modifier
                .weight(1f)
                .height(10.dp),
        ) {
            val trackHeight = 6.dp.toPx()
            val markerWidth = 4.dp.toPx()
            val markerHeight = 10.dp.toPx()
            val trackTop = (size.height - trackHeight) / 2f
            drawRect(c.border, topLeft = Offset(0f, trackTop), size = Size(size.width, trackHeight))
            drawRect(
                c.rampGood.copy(alpha = 0.30f),
                topLeft = Offset(0f, trackTop),
                size = Size(size.width / 3f, trackHeight),
            )
            drawRect(
                c.rampCaution.copy(alpha = 0.30f),
                topLeft = Offset(size.width / 3f, trackTop),
                size = Size(size.width / 3f, trackHeight),
            )
            drawRect(
                c.rampDanger.copy(alpha = 0.30f),
                topLeft = Offset(size.width * 2f / 3f, trackTop),
                size = Size(size.width / 3f, trackHeight),
            )
            val markerX = (fraction * size.width).coerceIn(0f, size.width) - markerWidth / 2f
            drawRect(
                barColor,
                topLeft = Offset(markerX.coerceIn(0f, size.width - markerWidth), (size.height - markerHeight) / 2f),
                size = Size(markerWidth, markerHeight),
            )
        }
        Spacer(Modifier.width(8.dp))
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
}
