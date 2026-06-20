package com.zwheel.app.ui.history

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.zwheel.app.ui.JetBrainsMonoFamily
import com.zwheel.app.ui.LocalZWheelColors

@Composable
fun SpeedLegendChip(modifier: Modifier = Modifier) {
    val c = LocalZWheelColors.current
    Surface(
        shape = RoundedCornerShape(999.dp),
        color = c.legendCard.copy(alpha = 0.8f),
        modifier = modifier,
    ) {
        Row(
            Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Box(
                Modifier
                    .width(40.dp)
                    .height(6.dp)
                    .clip(RoundedCornerShape(3.dp))
                    .background(Brush.horizontalGradient(listOf(c.cyan, c.rampCaution, c.rampDanger))),
            )
            Text("slow", fontFamily = JetBrainsMonoFamily, fontSize = 8.sp, color = c.cyan)
            Text("fast", fontFamily = JetBrainsMonoFamily, fontSize = 8.sp, color = c.rampDanger)
        }
    }
}
