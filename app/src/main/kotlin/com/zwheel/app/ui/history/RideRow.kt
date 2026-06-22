package com.zwheel.app.ui.history

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.zwheel.app.ui.JetBrainsMonoFamily
import com.zwheel.app.ui.LocalZWheelColors
import com.zwheel.app.ui.SairaFamily

@Composable
fun RideRow(
    item: RideHistoryItem,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val c = LocalZWheelColors.current
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .clickable { onClick() },
        shape = RoundedCornerShape(12.dp),
        color = c.card,
        border = BorderStroke(1.dp, c.border),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Box(Modifier.alpha(if (item.hasGps) 1f else 0.7f)) {
                RouteThumbnail(points = item.thumbnailPoints)
            }

            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                Text(
                    text = item.timeLabel,
                    fontFamily = SairaFamily,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.W700,
                    color = c.textPrimary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = item.durationLabel,
                    fontFamily = JetBrainsMonoFamily,
                    fontSize = 10.sp,
                    color = c.textMuted,
                    maxLines = 1,
                )
                Text(
                    text = item.boardName,
                    fontFamily = JetBrainsMonoFamily,
                    fontSize = 10.sp,
                    color = c.textDimmest,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }

            Column(
                horizontalAlignment = Alignment.End,
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                Text(
                    text = item.distanceLabel,
                    color = c.textPrimary,
                    style = TextStyle(
                        fontFamily = SairaFamily,
                        fontSize = 19.sp,
                        fontWeight = FontWeight.W800,
                        fontFeatureSettings = "tnum",
                    ),
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Filled.ArrowUpward,
                        contentDescription = "top speed",
                        tint = c.rampCaution,
                        modifier = Modifier.size(10.dp),
                    )
                    Text(
                        text = item.topSpeedLabel,
                        color = c.rampCaution,
                        style = TextStyle(
                            fontFamily = JetBrainsMonoFamily,
                            fontSize = 10.sp,
                            fontFeatureSettings = "tnum",
                        ),
                    )
                }
            }
        }
    }
}
