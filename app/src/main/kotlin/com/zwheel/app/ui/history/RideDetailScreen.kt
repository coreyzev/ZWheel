package com.zwheel.app.ui.history

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Fullscreen
import androidx.compose.material.icons.filled.IosShare
import androidx.compose.material.icons.filled.LocationOff
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.zwheel.app.ui.JetBrainsMonoFamily
import com.zwheel.app.ui.LocalZWheelColors
import com.zwheel.app.ui.SairaFamily
import org.osmdroid.config.Configuration
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView

@Composable
fun RideDetailScreen(
    viewModel: RideDetailViewModel = hiltViewModel(),
    onBack: () -> Unit = {},
    onOpenMap: () -> Unit = {},
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val c = LocalZWheelColors.current
    if (state == null) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center,
        ) {
            CircularProgressIndicator(color = c.lime)
        }
        return
    }
    RideDetailContent(state = state!!, onBack = onBack, onOpenMap = onOpenMap)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun RideDetailContent(
    state: RideDetailUiState,
    onBack: () -> Unit = {},
    onOpenMap: () -> Unit = {},
) {
    val c = LocalZWheelColors.current
    Scaffold(
        containerColor = c.screenBg,
        topBar = { RideDetailTopBar(state = state, onBack = onBack) },
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(
                horizontal = 18.dp,
                vertical = 12.dp,
            ),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item { MiniMapCard(state = state, onOpenMap = onOpenMap) }
            item { StatGrid(state = state) }
            item { BoardCard(state = state) }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun RideDetailTopBar(state: RideDetailUiState, onBack: () -> Unit) {
    val c = LocalZWheelColors.current
    TopAppBar(
        navigationIcon = {
            IconButton(onClick = onBack) {
                Icon(Icons.Filled.ArrowBack, contentDescription = "Back", tint = c.textPrimary)
            }
        },
        title = {
            Column {
                Text(
                    text = state.titleLabel,
                    fontFamily = SairaFamily,
                    fontSize = 20.sp,
                    fontWeight = FontWeight.W800,
                    color = c.textPrimary,
                    letterSpacing = (-0.3).sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = state.subtitleLabel,
                    fontFamily = JetBrainsMonoFamily,
                    fontSize = 10.sp,
                    color = c.textMuted,
                    softWrap = true,
                )
            }
        },
        actions = {
            IconButton(
                onClick = {
                    // TODO(share): implement ride share export.
                },
            ) {
                Icon(Icons.Filled.IosShare, contentDescription = "Share", tint = c.textSecondary)
            }
        },
        colors = TopAppBarDefaults.topAppBarColors(containerColor = c.screenBg),
    )
}

@Composable
private fun MiniMapCard(state: RideDetailUiState, onOpenMap: () -> Unit) {
    val c = LocalZWheelColors.current
    Surface(
        shape = RoundedCornerShape(18.dp),
        color = c.mapBg,
        border = BorderStroke(1.dp, c.border),
        modifier = Modifier
            .fillMaxWidth()
            .height(230.dp),
    ) {
        if (state.gpsPoints.isEmpty()) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    Icons.Filled.LocationOff,
                    contentDescription = null,
                    tint = c.textDim,
                    modifier = Modifier.height(48.dp),
                )
            }
        } else {
            Box(Modifier.fillMaxSize()) {
                val context = LocalContext.current
                val mapView = remember {
                    MapView(context).apply {
                        Configuration.getInstance().userAgentValue = context.packageName
                        setTileSource(TileSourceFactory.MAPNIK)
                        setMultiTouchControls(false)
                        isClickable = false
                    }
                }
                DisposableEffect(Unit) {
                    mapView.onResume()
                    onDispose { mapView.onPause() }
                }
                AndroidView(
                    factory = { mapView },
                    update = { mv ->
                        mv.applySpeedColoredRoute(state.gpsPoints, strokeWidthPx = 10f)
                        val mid = state.gpsPoints[state.gpsPoints.size / 2]
                        mv.controller.setZoom(15.5)
                        mv.controller.setCenter(GeoPoint(mid.first, mid.second))
                    },
                    modifier = Modifier.fillMaxSize(),
                )
                SpeedLegendChip(modifier = Modifier.align(Alignment.BottomStart).padding(10.dp))
                Surface(
                    shape = RoundedCornerShape(999.dp),
                    color = c.legendCard.copy(alpha = 0.8f),
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(10.dp),
                    onClick = onOpenMap,
                ) {
                    Row(
                        Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        Icon(
                            Icons.Filled.Fullscreen,
                            contentDescription = "Full map",
                            tint = c.textPrimary,
                            modifier = Modifier.height(16.dp),
                        )
                        Text(
                            "Full map",
                            fontFamily = JetBrainsMonoFamily,
                            fontSize = 10.sp,
                            color = c.textPrimary,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun StatGrid(state: RideDetailUiState) {
    val c = LocalZWheelColors.current
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            StatTile("DURATION", state.durationLabel, c.textPrimary, Modifier.weight(1f))
            StatTile("DISTANCE", state.distanceLabel, c.textPrimary, Modifier.weight(1f))
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            StatTile("TOP SPEED", state.topSpeedLabel, c.rampCaution, Modifier.weight(1f))
            StatTile("AVG SPEED", state.avgSpeedLabel, c.textPrimary, Modifier.weight(1f))
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            StatTile("GPS DISTANCE", state.gpsDistanceLabel, c.cyan, Modifier.weight(1f))
            StatTile("Ah USED", state.ahUsedLabel, c.textPrimary, Modifier.weight(1f))
        }
    }
}

@Composable
private fun StatTile(label: String, value: String, valueColor: Color, modifier: Modifier = Modifier) {
    val c = LocalZWheelColors.current
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = c.card,
        border = BorderStroke(1.dp, c.border),
        modifier = modifier,
    ) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(label, fontFamily = JetBrainsMonoFamily, fontSize = 9.sp, color = c.textLabel, letterSpacing = 2.sp)
            Text(
                value,
                color = valueColor,
                style = TextStyle(
                    fontFamily = SairaFamily,
                    fontSize = 26.sp,
                    fontWeight = FontWeight.W800,
                    fontFeatureSettings = "tnum",
                ),
            )
        }
    }
}

@Composable
private fun BoardCard(state: RideDetailUiState) {
    val c = LocalZWheelColors.current
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = c.card,
        border = BorderStroke(1.dp, c.border),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text("BOARD", fontFamily = JetBrainsMonoFamily, fontSize = 9.sp, color = c.textLabel, letterSpacing = 2.sp)
            Text(
                state.boardId,
                fontFamily = SairaFamily,
                fontSize = 16.sp,
                fontWeight = FontWeight.W700,
                color = c.textPrimary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}
