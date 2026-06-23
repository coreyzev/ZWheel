package com.zwheel.app.ui.dashboard

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BluetoothConnected
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.zwheel.app.ui.DashboardUiState
import com.zwheel.app.ui.JetBrainsMonoFamily
import com.zwheel.app.ui.LocalZWheelColors
import com.zwheel.app.ui.SairaFamily
import com.zwheel.core.model.BoardType

@Composable
fun BoardHeader(state: DashboardUiState, modifier: Modifier = Modifier) {
    val c = LocalZWheelColors.current
    val mono11 = TextStyle(
        fontFamily = JetBrainsMonoFamily,
        fontSize = 11.sp,
        fontFeatureSettings = "tnum",
    )
    Column(modifier = modifier) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
            androidx.compose.foundation.layout.Box(
                modifier = Modifier
                    .size(8.dp)
                    .drawBehind {
                        drawCircle(
                            color = c.rampGood.copy(alpha = 0.35f),
                            radius = 12.dp.toPx(),
                        )
                    }
                    .background(c.rampGood, CircleShape),
            )
            Text(
                text = state.boardName,
                color = c.textPrimary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                style = TextStyle(
                    fontFamily = SairaFamily,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                ),
                modifier = Modifier
                    .weight(1f)
                    .padding(horizontal = 8.dp),
            )
            BoardTypeBadge(state.boardType)
        }
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(top = 4.dp),
        ) {
            Icon(
                imageVector = Icons.Filled.MyLocation,
                contentDescription = "GPS",
                tint = c.cyan,
                modifier = Modifier.size(12.dp),
            )
            Text("GPS · ", style = mono11, color = c.cyan)
            Icon(
                imageVector = Icons.Filled.BluetoothConnected,
                contentDescription = "BLE",
                tint = c.textMuted,
                modifier = Modifier.size(12.dp),
            )
            Text(state.rssi?.let { "$it dBm" } ?: "--", style = mono11, color = c.textMuted)
        }
    }
}

@Composable
private fun BoardTypeBadge(boardType: BoardType) {
    val c = LocalZWheelColors.current
    val label = when (boardType) {
        BoardType.PINT_X -> "PINT X"
        BoardType.XRC -> "XRC"
        BoardType.XR -> "XR"
        BoardType.PINT -> "PINT"
        BoardType.PLUS -> "PLUS"
        BoardType.ONEWHEEL_V1 -> "OW V1"
        BoardType.UNKNOWN -> ""
    }
    if (label.isEmpty()) return
    Surface(
        shape = RoundedCornerShape(5.dp),
        color = c.screenBg,
        border = BorderStroke(1.dp, c.borderLime),
    ) {
        Text(
            text = label,
            color = c.lime,
            style = TextStyle(
                fontFamily = JetBrainsMonoFamily,
                fontSize = 9.sp,
                fontWeight = FontWeight.Bold,
            ),
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp),
        )
    }
}
