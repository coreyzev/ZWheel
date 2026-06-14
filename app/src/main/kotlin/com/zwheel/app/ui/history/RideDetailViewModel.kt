package com.zwheel.app.ui.history

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.zwheel.app.data.ride.RideRepository
import com.zwheel.app.data.settings.SettingsRepository
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

data class RideDetailUiState(
    val dateLabel: String,
    val boardId: String,
    val durationLabel: String,
    val distanceLabel: String,
    val topSpeedLabel: String,
    val avgSpeedLabel: String,
    val gpsPoints: List<Triple<Double, Double, Double?>> = emptyList(),
    val gpsDistanceLabel: String = "--",
)

@HiltViewModel
class RideDetailViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val repository: RideRepository,
    private val prefs: SettingsRepository,
) : ViewModel() {

    private val sessionId: String = checkNotNull(savedStateHandle["sessionId"])

    private val _state = MutableStateFlow<RideDetailUiState?>(null)
    val state: StateFlow<RideDetailUiState?> = _state.asStateFlow()

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
            var gpsMeters = 0.0
            for (i in 0 until gpsPoints.size - 1) {
                val (lat1, lon1, _) = gpsPoints[i]
                val (lat2, lon2, _) = gpsPoints[i + 1]
                gpsMeters += haversineMeters(lat1, lon1, lat2, lon2)
            }
            val gpsDistanceLabel = if (gpsPoints.size < 2) {
                "--"
            } else if (speedUnit == SpeedUnit.MPH) {
                "%.2f mi (GPS)".format(gpsMeters / 1_609.344)
            } else {
                "%.2f km (GPS)".format(gpsMeters / 1_000.0)
            }
            _state.value = session.toUiState(speedUnit, gpsPoints, gpsDistanceLabel)
        }
    }

    private fun RideSession.toUiState(
        speedUnit: SpeedUnit,
        gpsPoints: List<Triple<Double, Double, Double?>> = emptyList(),
        gpsDistanceLabel: String = "--",
    ): RideDetailUiState {
        val isMph = speedUnit == SpeedUnit.MPH
        val distanceLabel = if (isMph) {
            "%.2f mi".format(UnitConversions.metersToMiles(distanceMetersCorrected))
        } else {
            "%.2f km".format(UnitConversions.metersToKilometers(distanceMetersCorrected))
        }
        val topSpeedLabel = if (isMph) {
            "%.1f mph".format(UnitConversions.metersPerSecondToMph(maxSpeedMetersPerSecondCorrected))
        } else {
            "%.1f kph".format(maxSpeedMetersPerSecondCorrected * 3.6)
        }
        val durationMillis = (endEpochMillis ?: startEpochMillis) - startEpochMillis
        val minutes = TimeUnit.MILLISECONDS.toMinutes(durationMillis)
        val seconds = TimeUnit.MILLISECONDS.toSeconds(durationMillis) % 60
        val durationLabel = "%d:%02d".format(minutes, seconds)

        // Average speed = total distance / total duration (only meaningful if > 0)
        val avgSpeedMps = if (durationMillis > 0) {
            distanceMetersCorrected / (durationMillis / 1000.0)
        } else {
            0.0
        }
        val avgSpeedLabel = if (isMph) {
            "%.1f mph".format(UnitConversions.metersPerSecondToMph(avgSpeedMps))
        } else {
            "%.1f kph".format(avgSpeedMps * 3.6)
        }
        return RideDetailUiState(
            dateLabel = SimpleDateFormat("MMM d, yyyy  h:mm a", Locale.getDefault())
                .format(Date(startEpochMillis)),
            boardId = boardId,
            durationLabel = durationLabel,
            distanceLabel = distanceLabel,
            topSpeedLabel = topSpeedLabel,
            avgSpeedLabel = avgSpeedLabel,
            gpsPoints = gpsPoints,
            gpsDistanceLabel = gpsDistanceLabel,
        )
    }
}

private fun haversineMeters(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
    val r = 6_371_000.0
    val p1 = Math.toRadians(lat1); val p2 = Math.toRadians(lat2)
    val dp = Math.toRadians(lat2 - lat1); val dl = Math.toRadians(lon2 - lon1)
    val a = Math.sin(dp / 2) * Math.sin(dp / 2) +
        Math.cos(p1) * Math.cos(p2) * Math.sin(dl / 2) * Math.sin(dl / 2)
    return r * 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a))
}
