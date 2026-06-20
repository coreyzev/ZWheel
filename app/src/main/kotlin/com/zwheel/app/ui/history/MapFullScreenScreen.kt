package com.zwheel.app.ui.history

import android.app.Activity
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.view.WindowCompat
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.zwheel.app.data.ride.RideRepository
import com.zwheel.app.ui.JetBrainsMonoFamily
import com.zwheel.app.ui.LocalZWheelColors
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import org.osmdroid.config.Configuration
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView

@HiltViewModel
class MapFullScreenViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val repository: RideRepository,
) : ViewModel() {

    private val sessionId: String = checkNotNull(savedStateHandle["sessionId"])

    private val _gpsPoints = MutableStateFlow<List<Triple<Double, Double, Double?>>>(emptyList())
    val gpsPoints: StateFlow<List<Triple<Double, Double, Double?>>> = _gpsPoints.asStateFlow()

    init {
        viewModelScope.launch {
            val points = repository.getPointsForSession(sessionId).first()
            _gpsPoints.value = points.mapNotNull { p ->
                val lat = p.latitude ?: return@mapNotNull null
                val lon = p.longitude ?: return@mapNotNull null
                Triple(lat, lon, p.speedMetersPerSecondCorrected)
            }
        }
    }
}

@Composable
fun MapFullScreenScreen(
    viewModel: MapFullScreenViewModel = hiltViewModel(),
    onBack: () -> Unit = {},
) {
    val gpsPoints by viewModel.gpsPoints.collectAsStateWithLifecycle()
    val c = LocalZWheelColors.current
    val view = LocalView.current
    DisposableEffect(view) {
        val window = (view.context as? Activity)?.window
        if (window != null) WindowCompat.setDecorFitsSystemWindows(window, false)
        onDispose {
            if (window != null) WindowCompat.setDecorFitsSystemWindows(window, true)
        }
    }

    Box(modifier = Modifier.fillMaxSize().background(c.mapBg)) {
        if (gpsPoints.isNotEmpty()) {
            val context = LocalContext.current
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
                    mv.applySpeedColoredRoute(gpsPoints, strokeWidthPx = 12f)
                    val mid = gpsPoints[gpsPoints.size / 2]
                    mv.controller.setZoom(15.5)
                    mv.controller.setCenter(GeoPoint(mid.first, mid.second))
                },
                modifier = Modifier.fillMaxSize(),
            )
        }

        Box(
            Modifier
                .fillMaxWidth()
                .background(
                    Brush.verticalGradient(
                        listOf(c.screenBg.copy(alpha = 0.8f), c.screenBg.copy(alpha = 0f)),
                        endY = 120f,
                    ),
                )
                .statusBarsPadding()
                .padding(horizontal = 16.dp, vertical = 10.dp),
        ) {
            Surface(
                shape = CircleShape,
                color = c.legendCard.copy(alpha = 0.6f),
                modifier = Modifier
                    .size(36.dp)
                    .clickable { onBack() },
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        Icons.Filled.Close,
                        contentDescription = "Close",
                        tint = c.textPrimary,
                        modifier = Modifier.size(20.dp),
                    )
                }
            }
        }

        Box(
            Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .background(
                    Brush.verticalGradient(
                        listOf(c.screenBg.copy(alpha = 0f), c.screenBg.copy(alpha = 0.87f)),
                        startY = 0f,
                        endY = 200f,
                    ),
                )
                .navigationBarsPadding()
                .padding(16.dp),
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    SpeedLegendChip()
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        Box(Modifier.size(10.dp).border(2.dp, c.lime, CircleShape))
                        Text(
                            "Start",
                            fontFamily = JetBrainsMonoFamily,
                            fontSize = 9.sp,
                            color = c.textMuted,
                        )
                    }
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        Box(Modifier.size(10.dp).background(c.rampDanger, CircleShape))
                        Text(
                            "End",
                            fontFamily = JetBrainsMonoFamily,
                            fontSize = 9.sp,
                            color = c.textMuted,
                        )
                    }
                }
            }
        }
    }
}
