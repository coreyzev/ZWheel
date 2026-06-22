package com.zwheel.app.ui.history

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.LocationOff
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import com.zwheel.app.ui.LocalZWheelColors

@Composable
fun RouteThumbnail(
    points: List<Pair<Float, Float>>,
    modifier: Modifier = Modifier,
) {
    val c = LocalZWheelColors.current
    Box(
        modifier
            .size(62.dp, 48.dp)
            .clip(RoundedCornerShape(10.dp))
            .background(c.mapBg),
        contentAlignment = Alignment.Center,
    ) {
        if (points.size < 2) {
            Icon(
                imageVector = Icons.Filled.LocationOff,
                contentDescription = null,
                tint = c.textDim,
                modifier = Modifier.size(20.dp),
            )
            return@Box
        }

        Canvas(Modifier.matchParentSize()) {
            val path = Path()
            val padPx = 6.dp.toPx()
            val w = size.width - 2 * padPx
            val h = size.height - 2 * padPx
            points.forEachIndexed { i, (nx, ny) ->
                val x = padPx + nx * w
                val y = padPx + ny * h
                if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
            }
            drawPath(
                path = path,
                color = c.cyan,
                style = Stroke(width = 2.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round),
            )
            points.firstOrNull()?.let { (nx, ny) ->
                drawCircle(
                    color = c.lime,
                    radius = 3.dp.toPx(),
                    center = Offset(padPx + nx * w, padPx + ny * h),
                )
            }
            points.lastOrNull()?.let { (nx, ny) ->
                drawCircle(
                    color = c.rampDanger,
                    radius = 3.dp.toPx(),
                    center = Offset(padPx + nx * w, padPx + ny * h),
                )
            }
        }
    }
}
