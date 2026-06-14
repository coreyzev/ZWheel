package com.zwheel.app.ui.history

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import org.osmdroid.config.Configuration
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.Polyline

@Composable
fun RideDetailScreen(
    viewModel: RideDetailViewModel = hiltViewModel(),
    onBack: () -> Unit = {},
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        // Back button
        TextButton(onClick = onBack) {
            Text("← Back", color = Color(0xff00d8ff), fontSize = 14.sp)
        }

        if (state == null) {
            Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
            return@Column
        }

        val s = state!!

        Text(
            text = s.dateLabel,
            fontSize = 20.sp,
            fontWeight = FontWeight.Black,
            letterSpacing = 0.sp,
        )
        Text(
            text = "Board: ${s.boardId}",
            fontSize = 13.sp,
            color = Color(0xffbbbbbb),
        )

        HorizontalDivider(color = Color(0xff333333))

        DetailRow(label = "Distance", value = s.distanceLabel)
        DetailRow(label = "Duration", value = s.durationLabel)
        DetailRow(label = "Top Speed", value = s.topSpeedLabel)
        DetailRow(label = "Avg Speed", value = s.avgSpeedLabel)

        if (s.gpsPoints.isNotEmpty()) {
            val context = LocalContext.current
            val geoPoints = remember(s.gpsPoints) {
                s.gpsPoints.map { (lat, lon) -> GeoPoint(lat, lon) }
            }
            val mapView = remember {
                MapView(context).apply {
                    Configuration.getInstance().userAgentValue = context.packageName
                    setTileSource(TileSourceFactory.MAPNIK)
                    setMultiTouchControls(true)
                    isClickable = true
                }
            }
            DisposableEffect(Unit) {
                mapView.onResume()
                onDispose { mapView.onPause() }
            }
            AndroidView(
                factory = { mapView },
                update = { mv ->
                    mv.overlays.clear()
                    val polyline = Polyline().apply { setPoints(geoPoints) }
                    mv.overlays.add(polyline)
                    val midpoint = geoPoints[geoPoints.size / 2]
                    mv.controller.setZoom(15.5)
                    mv.controller.setCenter(midpoint)
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(300.dp),
            )
        }
    }
}

@Composable
private fun DetailRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(text = label, fontSize = 15.sp, color = Color(0xffbbbbbb))
        Text(text = value, fontSize = 16.sp, fontWeight = FontWeight.Bold, letterSpacing = 0.sp)
    }
}
