package com.zwheel.app.ui.dashboard

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.zwheel.app.ui.DashboardCard
import com.zwheel.app.ui.DashboardUiState
import com.zwheel.app.ui.LocalZWheelColors
import com.zwheel.app.ui.ZWheelTheme
import com.zwheel.app.ui.mockDashboardState

@Composable
fun DashboardScreen(
    state: DashboardUiState,
    modifier: Modifier = Modifier,
    onRequestLocation: () -> Unit = {},
    locationGranted: Boolean = true,
    locationPermanentlyDenied: Boolean = false,
) {
    val c = LocalZWheelColors.current

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(c.screenBg),
    ) {
        LazyColumn(contentPadding = PaddingValues(bottom = 16.dp)) {
            item {
                BoardHeader(
                    state = state,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 18.dp, vertical = 12.dp),
                )
            }
            item { SpeedSlab(state) }
            item { PushbackBar(state) }
            item {
                BatteryBand(
                    state = state,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 18.dp, end = 18.dp, top = 10.dp),
                )
            }
            item {
                TripCard(
                    state = state,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 18.dp, end = 18.dp, top = 10.dp),
                )
            }
            item {
                StatRow(
                    state = state,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 18.dp, end = 18.dp, top = 10.dp),
                )
            }
            item {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 18.dp, end = 18.dp, top = 10.dp),
                ) {
                    DashboardCard {
                        CellStrip(state.cellVoltages)
                    }
                }
            }
            item {
                TempsCard(
                    state = state,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 18.dp, end = 18.dp, top = 10.dp),
                )
            }
        }
    }
}

@Preview(showBackground = true, widthDp = 390, heightDp = 844)
@Composable
private fun DashboardScreenPreview() {
    ZWheelTheme {
        DashboardScreen(state = mockDashboardState())
    }
}
