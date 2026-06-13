package com.zwheel.app.ui

import androidx.compose.foundation.Canvas
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun DashboardCard(
    color: Color,
    contentColor: Color = Color(0xff111111),
    content: @Composable () -> Unit,
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(8.dp),
        color = color,
        contentColor = contentColor,
    ) {
        Box(modifier = Modifier.padding(18.dp)) {
            content()
        }
    }
}

@Composable
fun SpeedGauge(progress: Double) {
    Canvas(
        modifier = Modifier
            .size(116.dp)
            .aspectRatio(1f),
    ) {
        val stroke = Stroke(width = 14.dp.toPx(), cap = StrokeCap.Round)
        val arcSize = Size(size.width - 18.dp.toPx(), size.height - 18.dp.toPx())
        val topLeft = Offset(9.dp.toPx(), 9.dp.toPx())
        drawArc(
            color = Color(0xff111111).copy(alpha = 0.18f),
            startAngle = 160f,
            sweepAngle = 220f,
            useCenter = false,
            topLeft = topLeft,
            size = arcSize,
            style = stroke,
        )
        drawArc(
            color = Color(0xff111111),
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
fun Label(text: String, color: Color = Color(0xff111111)) {
    Text(
        text = text,
        color = color,
        fontSize = 12.sp,
        fontWeight = FontWeight.Black,
        letterSpacing = 0.sp,
    )
}

@Composable
fun Metric(value: String, unit: String, size: Int) {
    val unitParts = unit.lines()
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            text = "$value ${unitParts.first()}",
            fontSize = size.sp,
            fontWeight = FontWeight.Black,
            letterSpacing = 0.sp,
            lineHeight = size.sp,
        )
        unitParts.drop(1).forEach { badge ->
            Text(
                text = badge,
                color = Color(0xff8a1f11),
                fontSize = 11.sp,
                fontWeight = FontWeight.Black,
                letterSpacing = 0.sp,
                lineHeight = 12.sp,
            )
        }
    }
}

@Composable
fun SmallStat(label: String, value: String, color: Color = Color(0xff111111)) {
    Column {
        Text(
            text = label,
            color = color.copy(alpha = 0.72f),
            fontSize = 10.sp,
            fontWeight = FontWeight.Black,
            letterSpacing = 0.sp,
        )
        Text(
            text = value,
            color = color,
            fontSize = 17.sp,
            fontWeight = FontWeight.Black,
            letterSpacing = 0.sp,
        )
    }
}
