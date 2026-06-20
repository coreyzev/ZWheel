package com.zwheel.wear.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import com.zwheel.core.model.ConnectionState
import com.zwheel.core.model.SpeedUnit
import com.zwheel.core.model.WatchPayload
import java.util.Locale
import kotlin.math.abs

private const val METERS_PER_SECOND_TO_MPH = 2.23694
private const val METERS_PER_SECOND_TO_KPH = 3.6
private const val METERS_TO_MILES = 0.000621371
private const val METERS_TO_KM = 0.001

@Composable
fun ZWheelWearScreen(payload: WatchPayload?, isAmbient: Boolean = false) {
    val state = payload?.toUiState() ?: WearDashboardUiState.empty()
    when (state.activeFace(isAmbient)) {
        WearDashboardUiState.Face.ACTIVE -> ActiveFace(state)
        WearDashboardUiState.Face.CAUTION -> CautionFace(state)
        WearDashboardUiState.Face.AMBIENT -> AmbientFace(state)
        WearDashboardUiState.Face.DISCONNECTED -> DisconnectedFace()
    }
}

internal data class WearDashboardUiState(
    val speedDisplay: String,
    val speedDecimalDisplay: String,
    val speedUnitLabel: String,
    val topSpeedDisplay: String,
    val batteryPercent: Int,
    val batteryDisplay: String,
    val rangeDisplay: String,
    val connectionLabel: String,
    val pushbackApproaching: Boolean,
    val isConnected: Boolean,
) {
    enum class Face { ACTIVE, CAUTION, AMBIENT, DISCONNECTED }

    fun activeFace(isAmbient: Boolean): Face = when {
        isAmbient -> Face.AMBIENT
        !isConnected -> Face.DISCONNECTED
        pushbackApproaching -> Face.CAUTION
        else -> Face.ACTIVE
    }

    companion object {
        fun empty() = WearDashboardUiState(
            speedDisplay = "--",
            speedDecimalDisplay = "-",
            speedUnitLabel = "MPH",
            topSpeedDisplay = "--",
            batteryPercent = 0,
            batteryDisplay = "--%",
            rangeDisplay = "--",
            connectionLabel = "SCANNING",
            pushbackApproaching = false,
            isConnected = false,
        )
    }
}

private fun WatchPayload.toUiState(): WearDashboardUiState {
    val isMph = speedUnit == SpeedUnit.MPH
    val speedConversion = if (isMph) METERS_PER_SECOND_TO_MPH else METERS_PER_SECOND_TO_KPH
    val rangeConversion = if (isMph) METERS_TO_MILES else METERS_TO_KM
    val unitLabel = if (isMph) "MPH" else "KPH"
    val speedFloat = speedMetersPerSecondCorrected?.times(speedConversion)
    val speedInt = speedFloat?.toInt()
    val speedDec = speedFloat?.let { (abs(it % 1) * 10).toInt() }

    // TODO(hardware-tune): safetyHeadroom <= 0 is treated as "approaching pushback".
    // Verify the exact zero-crossing on real hardware before shipping.
    // If safetyHeadroom is null (older phone app), fall back to false (safe / no warning).
    val pushbackApproaching = safetyHeadroom?.let { it <= 0 } ?: false
    val connected = connectionState == ConnectionState.SUBSCRIBED ||
        connectionState == ConnectionState.DEGRADED

    return WearDashboardUiState(
        speedDisplay = speedInt?.toString() ?: "--",
        speedDecimalDisplay = speedDec?.toString() ?: "-",
        speedUnitLabel = unitLabel,
        topSpeedDisplay = formatOneDecimal(topSpeedMetersPerSecond * speedConversion),
        batteryPercent = batteryPercent ?: 0,
        batteryDisplay = batteryPercent?.let { "$it%" } ?: "--%",
        rangeDisplay = estimatedRangeMeters?.let { formatOneDecimal(it * rangeConversion) } ?: "--",
        connectionLabel = connectionState.name,
        pushbackApproaching = pushbackApproaching,
        isConnected = connected,
    )
}

private fun formatOneDecimal(value: Double): String = String.format(Locale.US, "%.1f", value)

@Preview(widthDp = 240, heightDp = 240, showBackground = true, backgroundColor = 0xFF000000)
@Composable
private fun PreviewActiveFace() {
    ZWheelWearScreen(payload = previewPayload(safetyHeadroom = 5), isAmbient = false)
}

@Preview(widthDp = 240, heightDp = 240, showBackground = true, backgroundColor = 0xFF000000)
@Composable
private fun PreviewCautionFace() {
    ZWheelWearScreen(
        payload = previewPayload(
            speedMetersPerSecondCorrected = 8.4,
            topSpeedMetersPerSecond = 8.4,
            batteryPercent = 48,
            estimatedRangeMeters = 5000.0,
            safetyHeadroom = 0,
        ),
        isAmbient = false,
    )
}

@Preview(widthDp = 240, heightDp = 240, showBackground = true, backgroundColor = 0xFF000000)
@Composable
private fun PreviewAmbientFace() {
    ZWheelWearScreen(payload = previewPayload(safetyHeadroom = 4), isAmbient = true)
}

@Preview(widthDp = 240, heightDp = 240, showBackground = true, backgroundColor = 0xFF000000)
@Composable
private fun PreviewDisconnectedFace() {
    ZWheelWearScreen(
        payload = previewPayload(
            speedMetersPerSecondCorrected = null,
            topSpeedMetersPerSecond = 0.0,
            batteryPercent = null,
            estimatedRangeMeters = null,
            isRiding = false,
            connectionState = ConnectionState.SCANNING,
            safetyHeadroom = null,
        ),
        isAmbient = false,
    )
}

private fun previewPayload(
    speedMetersPerSecondCorrected: Double? = 5.8,
    topSpeedMetersPerSecond: Double = 8.0,
    batteryPercent: Int? = 72,
    estimatedRangeMeters: Double? = 9000.0,
    speedUnit: SpeedUnit = SpeedUnit.MPH,
    isRiding: Boolean = true,
    connectionState: ConnectionState = ConnectionState.SUBSCRIBED,
    safetyHeadroom: Int? = null,
) = WatchPayload(
    speedMetersPerSecondCorrected = speedMetersPerSecondCorrected,
    topSpeedMetersPerSecond = topSpeedMetersPerSecond,
    batteryPercent = batteryPercent,
    estimatedRangeMeters = estimatedRangeMeters,
    speedUnit = speedUnit,
    isRiding = isRiding,
    connectionState = connectionState,
    safetyHeadroom = safetyHeadroom,
)
