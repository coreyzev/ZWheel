package com.zwheel.app.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val ZWheelColors = lightColorScheme(
    primary = Color(0xff111111),
    secondary = Color(0xffe4007f),
    tertiary = Color(0xff00a7c8),
    background = Color(0xffeeeeee),
    surface = Color.White,
)

@Composable
fun ZWheelTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = ZWheelColors,
        content = content,
    )
}
