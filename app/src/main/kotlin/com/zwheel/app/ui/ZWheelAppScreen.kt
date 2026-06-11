package com.zwheel.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun ZWheelAppScreen() {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xffeeeeee))
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        DashboardCard(
            label = "SPEED",
            value = "0",
            unit = "MPH",
            color = Color(0xffffd400),
        )
        DashboardCard(
            label = "BATTERY",
            value = "--",
            unit = "%",
            color = Color(0xffe4007f),
        )
        DashboardCard(
            label = "RIDE MODE",
            value = "DISCONNECTED",
            unit = "",
            color = Color(0xff111111),
            contentColor = Color.White,
        )
    }
}

@Composable
private fun DashboardCard(
    label: String,
    value: String,
    unit: String,
    color: Color,
    contentColor: Color = Color(0xff111111),
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(8.dp),
        color = color,
        contentColor = contentColor,
    ) {
        Column(modifier = Modifier.padding(18.dp)) {
            Text(
                text = label,
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 0.sp,
            )
            Text(
                text = "$value $unit".trim(),
                fontSize = 44.sp,
                fontWeight = FontWeight.Black,
                letterSpacing = 0.sp,
            )
        }
    }
}

@Preview
@Composable
private fun ZWheelAppScreenPreview() {
    ZWheelTheme {
        ZWheelAppScreen()
    }
}
