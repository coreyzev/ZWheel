package com.zwheel.app.ui.history

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.zwheel.app.ble.ConnectionManager
import com.zwheel.app.ble.ConnectionState
import com.zwheel.app.data.ride.RideRepository
import com.zwheel.app.data.settings.SettingsRepository
import com.zwheel.core.calc.UnitConversions
import com.zwheel.core.model.RideDataPoint
import com.zwheel.core.model.RideSession
import com.zwheel.core.model.SpeedUnit
import dagger.hilt.android.lifecycle.HiltViewModel
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import javax.inject.Inject
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

@HiltViewModel
class RideHistoryViewModel @Inject constructor(
    private val repository: RideRepository,
    private val prefs: SettingsRepository,
    connectionManager: ConnectionManager,
) : ViewModel() {
    val sessions: StateFlow<List<RideHistoryItem>> = combine(
        repository.getAllSessions(),
        prefs.preferences,
    ) { sessions, prefs ->
        val thumbnails = loadThumbnailPoints(sessions)
        // TODO(perf): lazy thumbnail loading if history list grows > 50 sessions.
        sessions.map { session ->
            val isMph = prefs.speedUnit == SpeedUnit.MPH
            val distanceLabel = if (isMph) {
                "%.2f mi".format(UnitConversions.metersToMiles(session.distanceMetersCorrected))
            } else {
                "%.2f km".format(UnitConversions.metersToKilometers(session.distanceMetersCorrected))
            }
            val topSpeedLabel = if (isMph) {
                "%.1f mph".format(
                    UnitConversions.metersPerSecondToMph(session.maxSpeedMetersPerSecondCorrected),
                )
            } else {
                "%.1f kph".format(
                    UnitConversions.metersPerSecondToKph(session.maxSpeedMetersPerSecondCorrected),
                )
            }
            val thumbnailPoints = thumbnails.getValue(session.id)
            val hasGps = thumbnailPoints.isNotEmpty()
            RideHistoryItem(
                id = session.id,
                timeLabel = formatTime(session.startEpochMillis),
                durationLabel = formatDuration(session.startEpochMillis, session.endEpochMillis, hasGps),
                distanceLabel = distanceLabel,
                topSpeedLabel = topSpeedLabel,
                boardName = prefs.customBoardName?.takeIf { it.isNotBlank() } ?: session.boardId,
                hasGps = hasGps,
                thumbnailPoints = thumbnailPoints,
                startEpochMillis = session.startEpochMillis,
            )
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val isBoardConnected: StateFlow<Boolean> = connectionManager.connectionState
        .map { it == ConnectionState.Connected }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), false)

    fun deleteSession(sessionId: String) {
        viewModelScope.launch {
            repository.deleteSession(sessionId)
        }
    }

    private suspend fun loadThumbnailPoints(
        sessions: List<RideSession>,
    ): Map<String, List<Pair<Float, Float>>> = coroutineScope {
        sessions.associate { session ->
            session.id to async {
                val points = repository.getPointsForSession(session.id).first()
                points.toThumbnailPoints()
            }
        }.mapValues { (_, deferred) -> deferred.await() }
    }
}

data class RideHistoryItem(
    val id: String,
    val timeLabel: String,
    val durationLabel: String,
    val distanceLabel: String,
    val topSpeedLabel: String,
    val boardName: String,
    val hasGps: Boolean,
    val thumbnailPoints: List<Pair<Float, Float>>,
    val startEpochMillis: Long,
)

private fun formatTime(epochMillis: Long): String =
    SimpleDateFormat("h:mm a", Locale.getDefault()).format(Date(epochMillis))

private fun formatDuration(startMs: Long, endMs: Long?, hasGps: Boolean): String {
    val minutes = (((endMs ?: startMs) - startMs) / 60_000).coerceAtLeast(0)
    return if (hasGps) "$minutes min" else "$minutes min · no GPS"
}

private fun List<RideDataPoint>.toThumbnailPoints(): List<Pair<Float, Float>> {
    val gpsPoints = mapNotNull { point ->
        val lat = point.latitude ?: return@mapNotNull null
        val lon = point.longitude ?: return@mapNotNull null
        lat to lon
    }
    return normalizeRoute(sampleEvenly(gpsPoints, 50))
}

/** Returns up to [max] evenly spaced elements from [list]. */
private fun <T> sampleEvenly(list: List<T>, max: Int): List<T> {
    if (list.size <= max) return list
    val step = list.size.toDouble() / max
    return List(max) { i -> list[(i * step).toInt()] }
}

/**
 * Normalize a list of (lat, lon) pairs so that the bounding box maps to [0,1]x[0,1].
 * Returns an empty list if fewer than 2 distinct points exist.
 */
private fun normalizeRoute(points: List<Pair<Double, Double>>): List<Pair<Float, Float>> {
    if (points.size < 2) return emptyList()
    val minLat = points.minOf { it.first }
    val maxLat = points.maxOf { it.first }
    val minLon = points.minOf { it.second }
    val maxLon = points.maxOf { it.second }
    if (minLat == maxLat && minLon == maxLon) return emptyList()
    val dLat = (maxLat - minLat).takeIf { it > 0.0 } ?: 1.0
    val dLon = (maxLon - minLon).takeIf { it > 0.0 } ?: 1.0
    return points.map { (lat, lon) ->
        Pair(((lon - minLon) / dLon).toFloat(), (1f - ((lat - minLat) / dLat).toFloat()))
    }
}
