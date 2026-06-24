package com.zwheel.app.ui.history

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BluetoothSearching
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DirectionsRun
import androidx.compose.material.icons.filled.Timeline
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.Text
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.zwheel.app.ui.JetBrainsMonoFamily
import com.zwheel.app.ui.LocalZWheelColors
import com.zwheel.app.ui.SairaFamily
import java.util.Calendar

@Composable
fun RideHistoryScreen(
    viewModel: RideHistoryViewModel = hiltViewModel(),
    onRideClick: (sessionId: String) -> Unit = {},
) {
    val sessions by viewModel.sessions.collectAsStateWithLifecycle()
    val isBoardConnected by viewModel.isBoardConnected.collectAsStateWithLifecycle()

    HistoryListContent(
        sessions = sessions,
        isBoardConnected = isBoardConnected,
        onRideClick = onRideClick,
        onDeleteSession = viewModel::deleteSession,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun HistoryListContent(
    sessions: List<RideHistoryItem>,
    isBoardConnected: Boolean,
    onRideClick: (String) -> Unit,
    onDeleteSession: (String) -> Unit = {},
) {
    val c = LocalZWheelColors.current
    if (sessions.isEmpty()) {
        EmptyHistoryContent(isBoardConnected = isBoardConnected)
        return
    }

    val now = remember { System.currentTimeMillis() }
    val grouped = remember(sessions) {
        sessions
            .groupBy { dateGroupOf(it.startEpochMillis, now) }
            .entries
            .sortedBy { it.key.ordinal }
            .map { (group, items) -> group to items }
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .background(c.screenBg),
        contentPadding = PaddingValues(horizontal = 18.dp, vertical = 14.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        item {
            Text(
                text = "Ride history",
                fontFamily = SairaFamily,
                fontSize = 26.sp,
                fontWeight = FontWeight.W800,
                color = c.textPrimary,
                letterSpacing = (-0.5).sp,
                modifier = Modifier.padding(bottom = 6.dp),
            )
        }

        grouped.forEach { (group, items) ->
            item(key = "header_${group.name}") {
                Text(
                    text = group.label(),
                    fontFamily = JetBrainsMonoFamily,
                    fontSize = 9.sp,
                    color = c.textDimmest,
                    letterSpacing = 2.sp,
                    modifier = Modifier.padding(top = 10.dp, bottom = 4.dp),
                )
            }
            items(items, key = { it.id }) { item ->
                val dismissState = rememberSwipeToDismissBoxState(
                    confirmValueChange = { value ->
                        if (value == SwipeToDismissBoxValue.EndToStart ||
                            value == SwipeToDismissBoxValue.StartToEnd
                        ) {
                            onDeleteSession(item.id)
                            true
                        } else {
                            false
                        }
                    },
                    positionalThreshold = { totalDistance -> totalDistance * 0.4f },
                )

                SwipeToDismissBox(
                    state = dismissState,
                    backgroundContent = {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(vertical = 4.dp)
                                .background(
                                    color = Color(0xFFB00020),
                                    shape = RoundedCornerShape(12.dp),
                                ),
                            contentAlignment = if (dismissState.dismissDirection ==
                                SwipeToDismissBoxValue.EndToStart
                            ) {
                                Alignment.CenterEnd
                            } else {
                                Alignment.CenterStart
                            },
                        ) {
                            Icon(
                                imageVector = Icons.Default.Delete,
                                contentDescription = "Delete ride",
                                tint = Color.White,
                                modifier = Modifier.padding(horizontal = 20.dp),
                            )
                        }
                    },
                ) {
                    RideRow(item = item, onClick = { onRideClick(item.id) })
                }
            }
        }
    }
}

@Composable
private fun EmptyHistoryContent(isBoardConnected: Boolean) {
    val c = LocalZWheelColors.current
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(c.screenBg),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier.padding(32.dp),
        ) {
            Surface(
                shape = RoundedCornerShape(16.dp),
                color = c.card,
                border = BorderStroke(1.dp, c.border),
                modifier = Modifier.size(72.dp),
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = Icons.Filled.Timeline,
                        contentDescription = null,
                        tint = c.textDim,
                        modifier = Modifier.size(36.dp),
                    )
                }
            }
            Text(
                text = "No rides yet",
                fontFamily = SairaFamily,
                fontSize = 20.sp,
                fontWeight = FontWeight.W800,
                color = c.textPrimary,
            )
            Text(
                text = "Complete a ride to see it here.",
                fontFamily = SairaFamily,
                fontSize = 14.sp,
                color = c.textMuted,
                textAlign = TextAlign.Center,
            )
            if (isBoardConnected) {
                Surface(
                    shape = RoundedCornerShape(999.dp),
                    color = c.divider,
                    border = BorderStroke(1.dp, c.border),
                    modifier = Modifier.clickable { },
                ) {
                    Row(
                        Modifier.padding(horizontal = 20.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Icon(
                            imageVector = Icons.Filled.DirectionsRun,
                            contentDescription = null,
                            tint = c.textSecondary,
                            modifier = Modifier.size(18.dp),
                        )
                        Text(
                            text = "Go for a ride",
                            fontFamily = SairaFamily,
                            fontSize = 15.sp,
                            fontWeight = FontWeight.W600,
                            color = c.textSecondary,
                        )
                    }
                }
            } else {
                Button(
                    onClick = { },
                    shape = RoundedCornerShape(999.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = c.lime),
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Icon(
                            imageVector = Icons.Filled.BluetoothSearching,
                            contentDescription = null,
                            tint = c.screenBg,
                            modifier = Modifier.size(18.dp),
                        )
                        Text(
                            text = "Connect a board",
                            fontFamily = SairaFamily,
                            fontSize = 15.sp,
                            fontWeight = FontWeight.W700,
                            color = c.screenBg,
                        )
                    }
                }
            }
        }
    }
}

private enum class DateGroup { TODAY, EARLIER_THIS_WEEK, OLDER }

private fun dateGroupOf(epochMillis: Long, nowMillis: Long): DateGroup {
    val cal = Calendar.getInstance()
    cal.timeInMillis = nowMillis
    val todayStart = cal.apply {
        set(Calendar.HOUR_OF_DAY, 0)
        set(Calendar.MINUTE, 0)
        set(Calendar.SECOND, 0)
        set(Calendar.MILLISECOND, 0)
    }.timeInMillis
    val weekStart = todayStart - (cal.get(Calendar.DAY_OF_WEEK) - Calendar.SUNDAY) * 86_400_000L
    return when {
        epochMillis >= todayStart -> DateGroup.TODAY
        epochMillis >= weekStart -> DateGroup.EARLIER_THIS_WEEK
        else -> DateGroup.OLDER
    }
}

private fun DateGroup.label(): String = when (this) {
    DateGroup.TODAY -> "TODAY"
    DateGroup.EARLIER_THIS_WEEK -> "EARLIER THIS WEEK"
    DateGroup.OLDER -> "OLDER"
}
