package com.zwheel.app.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider

private val ZWheelMaterialColors = darkColorScheme(
    primary = ZWheelDarkColors.lime,
    onPrimary = ZWheelDarkColors.screenBg,
    secondary = ZWheelDarkColors.cyan,
    tertiary = ZWheelDarkColors.cyan,
    background = ZWheelDarkColors.screenBg,
    surface = ZWheelDarkColors.card,
    onBackground = ZWheelDarkColors.textPrimary,
    onSurface = ZWheelDarkColors.textPrimary,
    error = ZWheelDarkColors.rampDanger,
)

@Composable
fun ZWheelTheme(content: @Composable () -> Unit) {
    CompositionLocalProvider(LocalZWheelColors provides ZWheelDarkColors) {
        MaterialTheme(
            colorScheme = ZWheelMaterialColors,
            typography = ZWheelTypography,
            content = content,
        )
    }
}
