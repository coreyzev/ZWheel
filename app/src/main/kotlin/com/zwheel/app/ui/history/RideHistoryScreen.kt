package com.zwheel.app.ui.history

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle

@Composable
fun RideHistoryScreen(
    viewModel: RideHistoryViewModel = hiltViewModel(),
    onRideClick: (sessionId: String) -> Unit = {},
) {
    val sessions by viewModel.sessions.collectAsStateWithLifecycle()

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xffeeeeee))
            .padding(16.dp),
    ) {
        if (sessions.isEmpty()) {
            Text(
                text = "No rides yet. Go ride!",
                modifier = Modifier.align(Alignment.Center),
                color = Color(0xff555555),
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold,
            )
        } else {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                items(sessions, key = { it.id }) { item ->
                    RideHistoryCard(item, onClick = { onRideClick(item.id) })
                }
            }
        }
    }
}

@Composable
private fun RideHistoryCard(item: RideHistoryItem, onClick: () -> Unit = {}) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() },
        shape = RoundedCornerShape(12.dp),
        color = Color(0xff1a1a1a),
        contentColor = Color.White,
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    text = item.dateLabel,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Black,
                    letterSpacing = 0.sp,
                )
                Text(
                    text = item.durationLabel,
                    fontSize = 13.sp,
                    color = Color(0xffbbbbbb),
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 0.sp,
                )
            }
            Column(
                horizontalAlignment = Alignment.End,
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Text(
                    text = item.topSpeedLabel,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Black,
                    letterSpacing = 0.sp,
                )
                Text(
                    text = item.distanceLabel,
                    fontSize = 13.sp,
                    color = Color(0xffbbbbbb),
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 0.sp,
                )
            }
        }
    }
}
