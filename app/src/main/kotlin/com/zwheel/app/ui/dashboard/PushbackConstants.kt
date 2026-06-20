package com.zwheel.app.ui.dashboard

import com.zwheel.core.model.BoardType

/**
 * Firmware pushback thresholds as a fraction of model top speed.
 * // TODO(hardware-tune): exact thresholds vary per board / firmware version;
 * // tune on real hardware before shipping. These approximate values match
 * // observed behavior on XR (15.8 mph amber, 19.5 mph red) and Pint X.
 */
object PushbackThresholds {
    const val CAUTION_FRACTION = 0.80f
    const val DANGER_FRACTION = 0.95f
}

/**
 * Nominal firmware top speed in MPH, per model. Used to derive the pushback-headroom fraction.
 * // TODO(hardware-tune): Pint / Plus / V1 values estimated; verify on hardware.
 */
fun BoardType.modelTopSpeedMph(): Float = when (this) {
    BoardType.PINT_X -> 20.0f
    BoardType.XR -> 20.0f
    BoardType.PINT -> 16.0f
    BoardType.PLUS -> 19.0f
    BoardType.ONEWHEEL_V1 -> 15.0f
    BoardType.UNKNOWN -> 20.0f
}

/** Fraction of model top speed, clamped [0, 1]. */
fun speedFraction(speedMph: Double, modelTopSpeedMph: Float): Float =
    (speedMph.toFloat() / modelTopSpeedMph).coerceIn(0f, 1f)
