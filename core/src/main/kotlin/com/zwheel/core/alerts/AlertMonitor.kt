package com.zwheel.core.alerts

/**
 * Stateful threshold monitor. Not thread-safe; call evaluate() from a single coroutine.
 *
 * Behaviour:
 *  - Arms when value crosses the threshold in the alert direction.
 *  - Once armed, suppresses re-fires for [AlertConfig.cooldownMs] ms.
 *  - Disarms (resets) only when value recovers past threshold + hysteresis in the safe direction.
 *  - Returns false if config.enabled == false or value is null.
 */
class AlertMonitor {

    private var inAlertZone = false
    private var lastFireMs = 0L

    /**
     * Evaluate current telemetry against config.
     *
     * @param config Current user configuration.
     * @param speedMps Corrected speed in m/s, or null if unknown.
     * @param headroom Raw safety headroom integer from firmware, or null if unknown.
     * @param nowMs Current epoch time in milliseconds (for cooldown tracking).
     * @return true if an alert should fire right now.
     */
    fun evaluate(
        config: AlertConfig,
        speedMps: Double?,
        headroom: Int?,
        nowMs: Long,
    ): Boolean {
        if (!config.enabled) {
            inAlertZone = false
            return false
        }

        val value = when (config.type) {
            AlertType.SPEED -> speedMps ?: run {
                inAlertZone = false
                return false
            }
            AlertType.HEADROOM -> headroom?.toDouble() ?: run {
                inAlertZone = false
                return false
            }
        }

        val inThreshold = when (config.type) {
            AlertType.SPEED -> value >= config.threshold
            AlertType.HEADROOM -> value <= config.threshold
        }

        val recovered = when (config.type) {
            AlertType.SPEED -> value < (config.threshold - config.hysteresis)
            AlertType.HEADROOM -> value > (config.threshold + config.hysteresis)
        }

        if (recovered) {
            inAlertZone = false
        }

        if (!inThreshold) return false

        if (inAlertZone && (nowMs - lastFireMs) < config.cooldownMs) return false

        inAlertZone = true
        lastFireMs = nowMs
        return true
    }

    /** Resets all state. Call when disconnecting or when alerts are toggled off. */
    fun reset() {
        inAlertZone = false
        lastFireMs = 0L
    }
}
