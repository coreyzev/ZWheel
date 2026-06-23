package com.zwheel.app.ui.dashboard

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.zwheel.app.ui.CellVoltageUiState
import com.zwheel.app.ui.JetBrainsMonoFamily
import com.zwheel.app.ui.Label
import com.zwheel.app.ui.LocalZWheelColors
import com.zwheel.app.ui.ZWheelColors

@Composable
fun CellStrip(cells: List<CellVoltageUiState>, modifier: Modifier = Modifier) {
    val c = LocalZWheelColors.current
    var expanded by remember { mutableStateOf(false) }
    val minVolts = cells.minOfOrNull { it.volts } ?: 0.0
    val maxVolts = cells.maxOfOrNull { it.volts } ?: 0.0
    val chevronRotation by animateFloatAsState(
        targetValue = if (expanded) 180f else 0f,
        animationSpec = tween(220),
        label = "cellStripChevron",
    )

    Column(modifier = modifier.animateContentSize()) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .clickable { expanded = !expanded },
        ) {
            Box(modifier = Modifier.weight(1f)) {
                Label("PER-CELL · ${cells.size}S")
            }
            Icon(Icons.Filled.ArrowUpward, contentDescription = null, tint = c.rampGood, modifier = Modifier.size(10.dp))
            Text("%.2fV".format(maxVolts), style = monoStyle(11, FontWeight.Bold), color = c.rampGood)
            androidx.compose.foundation.layout.Spacer(Modifier.size(4.dp))
            Icon(Icons.Filled.ArrowDownward, contentDescription = null, tint = c.rampDanger, modifier = Modifier.size(10.dp))
            Text("%.2fV".format(minVolts), style = monoStyle(11, FontWeight.Bold), color = c.rampDanger)
            Icon(
                imageVector = Icons.Filled.ExpandMore,
                contentDescription = null,
                tint = c.textMuted,
                modifier = Modifier.graphicsLayer { rotationZ = chevronRotation },
            )
        }
        Row(
            horizontalArrangement = Arrangement.spacedBy(3.dp),
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 8.dp),
        ) {
            cells.forEach { cell ->
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .height(32.dp)
                        .clip(RoundedCornerShape(3.dp))
                        .background(cellVoltageColor(cell.volts, c)),
                )
            }
        }
        AnimatedVisibility(visible = expanded) {
            LazyVerticalGrid(
                columns = GridCells.Fixed(5),
                userScrollEnabled = false,
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 320.dp)
                    .padding(top = 8.dp),
            ) {
                items(cells) { cell ->
                    CellVoltageTile(cell)
                }
            }
        }
    }
}

@Composable
private fun CellVoltageTile(cell: CellVoltageUiState) {
    val c = LocalZWheelColors.current
    val statusColor = cellVoltageColor(cell.volts, c)
    Surface(
        shape = RoundedCornerShape(14.dp),
        color = c.card,
        border = if (cell.isLow) BorderStroke(1.dp, statusColor) else BorderStroke(1.dp, c.border),
        modifier = Modifier.padding(3.dp),
    ) {
        Column(Modifier.padding(6.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Text(cell.label, style = monoStyle(9, FontWeight.Normal), color = c.textLabel)
            Text("%.2f".format(cell.volts), style = monoStyle(12, FontWeight.Bold), color = statusColor)
        }
    }
}

private fun cellVoltageColor(volts: Double, c: ZWheelColors): Color = when {
    volts >= CellThresholds.GOOD_VOLTS -> c.rampGood
    volts >= CellThresholds.LOW_VOLTS -> c.rampCaution
    else -> c.rampDanger
}

private fun monoStyle(size: Int, weight: FontWeight) = TextStyle(
    fontFamily = JetBrainsMonoFamily,
    fontSize = size.sp,
    fontWeight = weight,
    fontFeatureSettings = "tnum",
)
