package com.zwheel.app.ui.settings

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.NetworkWifi3Bar
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.zwheel.app.ui.JetBrainsMonoFamily
import com.zwheel.app.ui.LocalZWheelColors
import com.zwheel.app.ui.SairaFamily
import com.zwheel.core.model.BoardState
import com.zwheel.core.model.BoardType

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun ConnectedBoardCard(
    boardState: BoardState,
    rssi: Int?,
    customBoardName: String?,
    onSaveName: (String?) -> Unit,
    onDisconnect: () -> Unit,
    onForgetBoard: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val c = LocalZWheelColors.current
    val displayName = customBoardName?.takeIf { it.isNotBlank() }
        ?: boardState.identity?.name
        ?: "Not connected"
    val mono10 = TextStyle(
        fontFamily = JetBrainsMonoFamily,
        fontSize = 10.sp,
        fontFeatureSettings = "tnum",
    )
    var editing by remember { mutableStateOf(false) }
    var editText by remember(displayName) { mutableStateOf(displayName) }

    androidx.compose.foundation.layout.Column(modifier = modifier) {
        if (editing) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                BasicTextField(
                    value = editText,
                    onValueChange = { if (it.length <= 24) editText = it },
                    singleLine = true,
                    textStyle = TextStyle(
                        fontFamily = SairaFamily,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.W700,
                        color = c.textPrimary,
                    ),
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                    keyboardActions = KeyboardActions(onDone = {
                        onSaveName(editText.trim().ifBlank { null })
                        editing = false
                    }),
                    cursorBrush = SolidColor(c.lime),
                    modifier = Modifier
                        .weight(1f)
                        .border(1.dp, c.buttonBorder, RoundedCornerShape(10.dp))
                        .padding(horizontal = 10.dp, vertical = 6.dp),
                )
                Spacer(Modifier.width(8.dp))
                Icon(
                    imageVector = Icons.Filled.CheckCircle,
                    contentDescription = "Save board name",
                    tint = c.lime,
                    modifier = Modifier
                        .size(20.dp)
                        .clickable {
                            onSaveName(editText.trim().ifBlank { null })
                            editing = false
                        },
                )
            }
        } else {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(8.dp)
                        .drawBehind { drawCircle(c.rampGood.copy(alpha = 0.35f), radius = 14.dp.toPx()) }
                        .background(c.rampGood, CircleShape),
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    text = displayName,
                    style = TextStyle(
                        fontFamily = SairaFamily,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.W700,
                    ),
                    color = c.textPrimary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier
                        .weight(1f)
                        .clickable { editing = true },
                )
                Spacer(Modifier.width(8.dp))
                Icon(
                    imageVector = Icons.Filled.Edit,
                    contentDescription = "Edit board name",
                    tint = c.textMuted,
                    modifier = Modifier
                        .size(16.dp)
                        .clickable { editing = true },
                )
            }
        }

        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
            modifier = Modifier.padding(top = 6.dp),
        ) {
            val boardType = boardState.identity?.type ?: BoardType.UNKNOWN
            BoardTypeBadge(boardType)
            if (rssi != null) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        Icons.Filled.NetworkWifi3Bar,
                        contentDescription = "Signal",
                        tint = c.textSecondary,
                        modifier = Modifier.size(12.dp),
                    )
                    Spacer(Modifier.width(3.dp))
                    Text("$rssi dBm", style = mono10, color = c.textSecondary)
                }
            }
            val fw = boardState.identity?.firmwareRevision
            val cells = boardState.cellVoltages.size.takeIf { it > 0 }
            if (fw != null || cells != null) {
                val label = buildString {
                    if (fw != null) append("Fw $fw")
                    if (fw != null && cells != null) append(" · ")
                    if (cells != null) append("${cells}S")
                }
                Text(label, style = mono10, color = c.textSecondary)
            }
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            TextButton(
                onClick = onDisconnect,
                modifier = Modifier.weight(1f),
                colors = ButtonDefaults.textButtonColors(contentColor = c.rampDanger),
            ) {
                Text("Disconnect", fontFamily = SairaFamily, fontWeight = FontWeight.W600, fontSize = 14.sp)
            }
            TextButton(
                onClick = onForgetBoard,
                modifier = Modifier.weight(1f),
                colors = ButtonDefaults.textButtonColors(contentColor = c.textSecondary),
            ) {
                Text("Forget board", fontFamily = SairaFamily, fontWeight = FontWeight.W600, fontSize = 14.sp)
            }
        }
    }
}

@Composable
private fun BoardTypeBadge(type: BoardType) {
    val c = LocalZWheelColors.current
    val label = when (type) {
        BoardType.PINT_X -> "PINT X"
        BoardType.XR -> "XR"
        BoardType.PINT -> "PINT"
        BoardType.PLUS -> "PLUS"
        BoardType.ONEWHEEL_V1 -> "OW V1"
        BoardType.UNKNOWN -> return
    }
    Surface(
        shape = RoundedCornerShape(5.dp),
        border = BorderStroke(1.dp, c.borderLime),
        color = Color.Transparent,
    ) {
        Text(
            text = label,
            style = TextStyle(
                fontFamily = JetBrainsMonoFamily,
                fontSize = 9.sp,
                fontWeight = FontWeight.W700,
            ),
            color = c.lime,
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
        )
    }
}
