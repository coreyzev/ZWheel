package com.zwheel.app.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun DashboardCard(
    modifier: Modifier = Modifier,
    shape: androidx.compose.ui.graphics.Shape = RoundedCornerShape(16.dp),
    color: Color = LocalZWheelColors.current.card,
    contentColor: Color = LocalZWheelColors.current.textPrimary,
    content: @Composable () -> Unit,
) {
    val colors = LocalZWheelColors.current
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = shape,
        color = color,
        contentColor = contentColor,
        border = BorderStroke(1.dp, colors.border),
    ) {
        Box(modifier = Modifier.padding(14.dp)) {
            content()
        }
    }
}

@Composable
fun SpeedGauge(progress: Double) {
    val colors = LocalZWheelColors.current
    Canvas(
        modifier = Modifier
            .size(116.dp)
            .aspectRatio(1f),
    ) {
        val stroke = Stroke(width = 14.dp.toPx(), cap = StrokeCap.Round)
        val arcSize = Size(size.width - 18.dp.toPx(), size.height - 18.dp.toPx())
        val topLeft = Offset(9.dp.toPx(), 9.dp.toPx())
        drawArc(
            color = colors.border,
            startAngle = 160f,
            sweepAngle = 220f,
            useCenter = false,
            topLeft = topLeft,
            size = arcSize,
            style = stroke,
        )
        drawArc(
            color = colors.divider,
            startAngle = 160f,
            sweepAngle = 220f,
            useCenter = false,
            topLeft = topLeft,
            size = arcSize,
            style = Stroke(width = 8.dp.toPx(), cap = StrokeCap.Round),
        )
        drawArc(
            color = colors.lime,
            startAngle = 160f,
            sweepAngle = (220f * progress.coerceIn(0.0, 1.0)).toFloat(),
            useCenter = false,
            topLeft = topLeft,
            size = arcSize,
            style = stroke,
        )
    }
}

@Composable
fun Label(text: String, color: Color = LocalZWheelColors.current.textLabel) {
    Text(
        text = text.uppercase(),
        color = color,
        fontFamily = JetBrainsMonoFamily,
        fontSize = 9.sp,
        fontWeight = FontWeight.Normal,
        letterSpacing = 1.5.sp,
        lineHeight = 12.sp,
    )
}

@Composable
fun Metric(value: String, unit: String, size: Int) {
    val colors = LocalZWheelColors.current
    val unitParts = unit.lines()
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            text = "$value ${unitParts.first()}",
            color = colors.textPrimary,
            style = TextStyle(
                fontFamily = SairaFamily,
                fontSize = size.sp,
                fontWeight = FontWeight.ExtraBold,
                letterSpacing = 0.sp,
                lineHeight = size.sp,
                fontFeatureSettings = "tnum",
            ),
        )
        unitParts.drop(1).forEach { badge ->
            Text(
                text = badge,
                color = colors.textLabel,
                style = TextStyle(
                    fontFamily = JetBrainsMonoFamily,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 0.sp,
                    lineHeight = 12.sp,
                    fontFeatureSettings = "tnum",
                ),
            )
        }
    }
}

@Composable
fun SmallStat(label: String, value: String, color: Color = LocalZWheelColors.current.textPrimary) {
    val colors = LocalZWheelColors.current
    Column {
        Text(
            text = label,
            color = colors.textLabel,
            fontFamily = JetBrainsMonoFamily,
            fontSize = 10.sp,
            fontWeight = FontWeight.Normal,
            letterSpacing = 1.5.sp,
            lineHeight = 13.sp,
        )
        Text(
            text = value,
            color = color,
            style = TextStyle(
                fontFamily = SairaFamily,
                fontSize = 17.sp,
                fontWeight = FontWeight.ExtraBold,
                letterSpacing = 0.sp,
                fontFeatureSettings = "tnum",
            ),
        )
    }
}
