package com.zwheel.app.ui.dashboard

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.PlatformTextStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.zwheel.app.ui.DashboardUiState
import com.zwheel.app.ui.JetBrainsMonoFamily
import com.zwheel.app.ui.LocalZWheelColors
import com.zwheel.app.ui.SairaFamily
import kotlin.math.absoluteValue

@Composable
fun SpeedSlab(state: DashboardUiState, modifier: Modifier = Modifier) {
    val c = LocalZWheelColors.current
    val fraction = speedFraction(state.speedMph, state.boardType.modelTopSpeedMph())
    val speedColor = when {
        fraction >= PushbackThresholds.DANGER_FRACTION -> c.rampDanger
        fraction >= PushbackThresholds.CAUTION_FRACTION -> c.rampCaution
        else -> c.rampGood
    }
    val intPart = state.speedMph.toInt().toString()
    val decPart = ".%01d".format(((state.speedMph.absoluteValue % 1) * 10).toInt())

    Box(
        modifier = modifier
            .fillMaxWidth()
            .background(Brush.verticalGradient(listOf(Color(0xFF13160D), c.screenBg)))
            .padding(top = 4.dp, bottom = 10.dp),
        contentAlignment = Alignment.Center,
    ) {
        androidx.compose.foundation.layout.Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Row(verticalAlignment = Alignment.Top) {
                Text(
                    text = intPart,
                    color = speedColor,
                    style = TextStyle(
                        fontFamily = SairaFamily,
                        fontSize = 96.sp,
                        fontWeight = FontWeight.Black,
                        lineHeight = 96.sp,
                        letterSpacing = (-6).sp,
                        fontFeatureSettings = "tnum",
                        platformStyle = PlatformTextStyle(includeFontPadding = false),
                    ),
                )
                Text(
                    text = decPart,
                    color = c.textSecondary,
                    style = TextStyle(
                        fontFamily = SairaFamily,
                        fontSize = 38.sp,
                        fontWeight = FontWeight.Black,
                        lineHeight = 38.sp,
                        letterSpacing = (-6).sp,
                        fontFeatureSettings = "tnum",
                        platformStyle = PlatformTextStyle(includeFontPadding = false),
                    ),
                )
            }
            Row(
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 18.dp),
            ) {
                Text(
                    text = state.speedUnitLabel,
                    color = c.textSecondary,
                    style = TextStyle(
                        fontFamily = SairaFamily,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                    ),
                )
                if (state.isSpeedCorrected) {
                    Surface(
                        shape = RoundedCornerShape(5.dp),
                        color = c.screenBg,
                        border = BorderStroke(1.dp, c.borderLime),
                    ) {
                        Text(
                            text = "TIRE-CORRECTED",
                            color = c.lime,
                            style = TextStyle(
                                fontFamily = JetBrainsMonoFamily,
                                fontSize = 9.sp,
                                fontWeight = FontWeight.Bold,
                            ),
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp),
                        )
                    }
                } else {
                    Surface(
                        shape = RoundedCornerShape(5.dp),
                        color = c.screenBg,
                        border = BorderStroke(1.dp, c.border),
                    ) {
                        Text(
                            text = "UNCORRECTED",
                            color = c.textDim,
                            style = TextStyle(
                                fontFamily = JetBrainsMonoFamily,
                                fontSize = 9.sp,
                                fontWeight = FontWeight.Bold,
                            ),
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp),
                        )
                    }
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Filled.ArrowUpward,
                        contentDescription = null,
                        tint = c.rampCaution,
                        modifier = Modifier.size(12.dp),
                    )
                    Text(
                        text = "%.1f".format(state.topSpeedMph),
                        color = c.rampCaution,
                        style = TextStyle(
                            fontFamily = JetBrainsMonoFamily,
                            fontSize = 11.sp,
                            fontFeatureSettings = "tnum",
                        ),
                    )
                }
            }
        }
    }
}
