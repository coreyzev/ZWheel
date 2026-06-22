package com.zwheel.app.ui

import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

data class ZWheelColors(
    val screenBg: Color, val navBg: Color, val mapBg: Color, val insetRow: Color,
    val legendCard: Color, val card: Color, val cardElevated: Color,
    val border: Color, val divider: Color, val frameBorder: Color, val buttonBorder: Color,
    val borderGreen: Color, val borderRed: Color, val borderLime: Color, val borderBattery: Color,
    val textPrimary: Color, val textStatus: Color, val textSecondary: Color, val textMuted: Color,
    val textLabel: Color, val textDim: Color, val textDimmest: Color, val separator: Color,
    val lime: Color, val cyan: Color,
    val rampGood: Color, val rampCaution: Color, val rampDanger: Color,
)

val ZWheelDarkColors = ZWheelColors(
    screenBg = Color(0xFF0A0B0E), navBg = Color(0xFF0C0D11), mapBg = Color(0xFF0C0E12),
    insetRow = Color(0xFF0E1014), legendCard = Color(0xFF101319), card = Color(0xFF121419),
    cardElevated = Color(0xFF181B22),
    border = Color(0xFF20242D), divider = Color(0xFF1A1D24), frameBorder = Color(0xFF23262F),
    buttonBorder = Color(0xFF262B35), borderGreen = Color(0xFF1C4030), borderRed = Color(0xFF3A1C1C),
    borderLime = Color(0xFF2A3520), borderBattery = Color(0xFF223018),
    textPrimary = Color(0xFFF2F4F7), textStatus = Color(0xFFCFD5DE), textSecondary = Color(0xFF9AA4B2),
    textMuted = Color(0xFF7C8696), textLabel = Color(0xFF6B7484), textDim = Color(0xFF5A616E),
    textDimmest = Color(0xFF4A5260), separator = Color(0xFF2A2E36),
    lime = Color(0xFFC6F24E), cyan = Color(0xFF38E0FF),
    rampGood = Color(0xFF4FE086), rampCaution = Color(0xFFFFB22E), rampDanger = Color(0xFFFF5A5A),
)

val LocalZWheelColors = staticCompositionLocalOf { ZWheelDarkColors }

fun ZWheelColors.ramp(fraction: Float): Color = when {
    fraction < 0.33f -> rampDanger
    fraction < 0.66f -> rampCaution
    else -> rampGood
}
