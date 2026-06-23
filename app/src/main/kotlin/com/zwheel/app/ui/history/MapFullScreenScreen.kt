package com.zwheel.app.ui.history

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.zwheel.app.data.ride.RideRepository
import com.zwheel.app.data.settings.SettingsRepository
import com.zwheel.app.ui.JetBrainsMonoFamily
import com.zwheel.app.ui.LocalZWheelColors
import com.zwheel.app.ui.SairaFamily
import com.zwheel.core.calc.UnitConversions
import com.zwheel.core.model.RideSession
import com.zwheel.core.model.SpeedUnit
import dagger.hilt.android.lifecycle.HiltViewModel
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.TimeUnit
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

data class MapFullScreenUiState(
    val gpsPoints: List<Triple<Double, Double, Double?>>,
    val rideTimeLabel: String, val distanceLabel: String, val durationLabel: String, val topSpeedLabel: String,
)

@HiltViewModel
class MapFullScreenViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val repository: RideRepository,
    private val prefs: SettingsRepository,
) : ViewModel() {

    private val sessionId: String = checkNotNull(savedStateHandle["sessionId"])

    private val _state = MutableStateFlow<MapFullScreenUiState?>(null)
    val state: StateFlow<MapFullScreenUiState?> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            val session = repository.getSession(sessionId) ?: return@launch
            val speedUnit = prefs.preferences.first().speedUnit
            val points = repository.getPointsForSession(sessionId).first()
            val gpsPoints = points.mapNotNull { p ->
                val lat = p.latitude ?: return@mapNotNull null
                val lon = p.longitude ?: return@mapNotNull null
                Triple(lat, lon, p.speedMetersPerSecondCorrected)
            }
            _state.value = session.toMapUiState(speedUnit, gpsPoints)
        }
    }

    private fun RideSession.toMapUiState(
        speedUnit: SpeedUnit,
        gpsPoints: List<Triple<Double, Double, Double?>>,
    ): MapFullScreenUiState {
        val isMph = speedUnit == SpeedUnit.MPH
        val durationMillis = (endEpochMillis ?: startEpochMillis) - startEpochMillis
        val minutes = TimeUnit.MILLISECONDS.toMinutes(durationMillis)
        val seconds = TimeUnit.MILLISECONDS.toSeconds(durationMillis) % 60
        return MapFullScreenUiState(
            gpsPoints = gpsPoints,
            rideTimeLabel = SimpleDateFormat("h:mm a", Locale.getDefault()).format(Date(startEpochMillis)),
            distanceLabel = if (isMph) {
                "%.2f mi".format(UnitConversions.metersToMiles(distanceMetersCorrected))
            } else {
                "%.2f km".format(UnitConversions.metersToKilometers(distanceMetersCorrected))
            },
            durationLabel = "%d:%02d".format(minutes, seconds),
            topSpeedLabel = if (isMph) {
                "↑ %.1f mph".format(UnitConversions.metersPerSecondToMph(maxSpeedMetersPerSecondCorrected))
            } else {
                "↑ %.1f kph".format(maxSpeedMetersPerSecondCorrected * 3.6)
            },
        )
    }
}
@Composable
fun MapFullScreenScreen(
    viewModel: MapFullScreenViewModel = hiltViewModel(),
    onBack: () -> Unit = {},
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val gpsPoints = state?.gpsPoints.orEmpty()
    val c = LocalZWheelColors.current

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
            state?.rideTimeLabel?.let { rideTimeLabel ->
                Surface(
                    color = c.legendCard.copy(alpha = 0.7f),
                    shape = RoundedCornerShape(999.dp),
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(10.dp),
                ) {
                    Text(
                        rideTimeLabel,
                        fontFamily = JetBrainsMonoFamily,
                        fontSize = 10.sp,
                        color = c.textMuted,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
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
                state?.let { mapState ->
                    Spacer(Modifier.height(8.dp))
                    Row(
                        horizontalArrangement = Arrangement.SpaceEvenly,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        MapStatChip(
                            value = mapState.distanceLabel,
                            label = "DIST",
                        )
                        MapStatChip(
                            value = mapState.durationLabel,
                            label = "TIME",
                        )
                        MapStatChip(
                            value = mapState.topSpeedLabel,
                            label = "TOP",
                            isTopSpeed = true,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun MapStatChip(value: String, label: String, isTopSpeed: Boolean = false) {
    val c = LocalZWheelColors.current
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            value, fontFamily = SairaFamily, fontWeight = FontWeight.ExtraBold, fontSize = 16.sp,
            color = if (isTopSpeed) c.rampCaution else c.textPrimary,
        )
        Text(
            label, fontFamily = JetBrainsMonoFamily, fontSize = 9.sp,
            letterSpacing = 1.5.sp, color = c.textLabel,
        )
    }
}
