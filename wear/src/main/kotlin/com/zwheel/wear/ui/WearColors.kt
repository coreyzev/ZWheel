package com.zwheel.wear.ui

import androidx.compose.ui.graphics.Color

/**
 * Wear-local color tokens. Mirrors the relevant subset of ZWheelColors from the app module.
 * Do not import from app; wear and app are separate APKs.
 */
internal object WearColors {
    val screenBlack = Color(0xFF000000)
    val lime = Color(0xFFC6F24E)
    val amber = Color(0xFFFFB22E)
    val cyan = Color(0xFF38E0FF)
    val rampGood = Color(0xFF4FE086)
    val rampCaution = Color(0xFFFFB22E)
    val rampDanger = Color(0xFFFF5A5A)
    val textPrimary = Color(0xFFF2F4F7)
    val textSecondary = Color(0xFF9AA4B2)
    val textMuted = Color(0xFF7C8696)
    val textDim = Color(0xFF5A616E)
    val arcTrack = Color(0xFF222222)
}
