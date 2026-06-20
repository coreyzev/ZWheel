package com.zwheel.wear.ui

import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.zwheel.wear.R

internal val SairaWearFamily = FontFamily(
    Font(R.font.saira_bold, FontWeight.Bold),
    Font(R.font.saira_black, FontWeight.Black),
)

internal val MonoWearFamily = FontFamily(
    Font(R.font.jetbrains_mono_regular, FontWeight.Normal),
    Font(R.font.jetbrains_mono_medium, FontWeight.Medium),
    Font(R.font.jetbrains_mono_bold, FontWeight.Bold),
)

internal val wearSpeedStyle = TextStyle(
    fontFamily = SairaWearFamily,
    fontWeight = FontWeight.Bold,
    fontSize = 64.sp,
    fontFeatureSettings = "tnum",
    letterSpacing = 0.sp,
    lineHeight = 64.sp,
)

internal val wearLabelStyle = TextStyle(
    fontFamily = MonoWearFamily,
    fontWeight = FontWeight.Normal,
    fontSize = 13.sp,
    letterSpacing = 0.5.sp,
)

internal val wearSmallStyle = TextStyle(
    fontFamily = MonoWearFamily,
    fontWeight = FontWeight.Bold,
    fontSize = 11.sp,
    fontFeatureSettings = "tnum",
    letterSpacing = 0.sp,
)
