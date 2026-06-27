package com.zwheel.core.alerts

/**
 * All threshold/hysteresis values are in SI units:
 *   - SPEED: m/s
 *   - HEADROOM: raw firmware integer (Double for type uniformity)
 * The app layer converts user-facing units to SI before constructing AlertConfig.
 */
data class AlertConfig(
    val enabled: Boolean = false,
    val type: AlertType = AlertType.SPEED,
    /** Threshold in SI units. SPEED=m/s, HEADROOM=raw int as Double. */
    val threshold: Double = 7.152,
    val output: AlertOutput = AlertOutput.WATCH,
    /** How far the value must recover past the threshold before re-arming. Same unit as threshold. */
    val hysteresis: Double = 0.894,
    /** Minimum ms between repeated fires while still in the alert zone. */
    val cooldownMs: Long = 10_000L,
)
