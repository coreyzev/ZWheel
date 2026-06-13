package com.zwheel.app.ui.history

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.zwheel.app.data.ride.RideRepository
import com.zwheel.app.data.settings.SettingsRepository
import com.zwheel.core.calc.UnitConversions
import com.zwheel.core.model.SpeedUnit
import dagger.hilt.android.lifecycle.HiltViewModel
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn

@HiltViewModel
class RideHistoryViewModel @Inject constructor(
    private val repository: RideRepository,
    private val prefs: SettingsRepository,
) : ViewModel() {
    val sessions: StateFlow<List<RideHistoryItem>> = combine(
        repository.getAllSessions(),
        prefs.preferences,
    ) { sessions, prefs ->
        sessions.map { session ->
            val isMph = prefs.speedUnit == SpeedUnit.MPH
            val distanceLabel = if (isMph) {
                "%.1f mi".format(UnitConversions.metersToMiles(session.distanceMetersCorrected))
            } else {
                "%.1f km".format(UnitConversions.metersToKilometers(session.distanceMetersCorrected))
            }
            val topSpeedLabel = if (isMph) {
                "%.0f mph".format(
                    UnitConversions.metersPerSecondToMph(session.maxSpeedMetersPerSecondCorrected),
                )
            } else {
                "%.0f kph".format(
                    UnitConversions.metersPerSecondToKph(session.maxSpeedMetersPerSecondCorrected),
                )
            }
            RideHistoryItem(
                id = session.id,
                dateLabel = formatDate(session.startEpochMillis),
                durationLabel = formatDuration(session.startEpochMillis, session.endEpochMillis),
                distanceLabel = distanceLabel,
                topSpeedLabel = topSpeedLabel,
            )
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
}

data class RideHistoryItem(
    val id: String,
    val dateLabel: String,
    val durationLabel: String,
    val distanceLabel: String,
    val topSpeedLabel: String,
)

private fun formatDate(epochMillis: Long): String =
    SimpleDateFormat("MMM d, yyyy", Locale.getDefault()).format(Date(epochMillis))

private fun formatDuration(startMs: Long, endMs: Long?): String {
    if (endMs == null) return "--"
    val minutes = ((endMs - startMs) / 1000 / 60).toInt()
    return "${minutes}m"
}
