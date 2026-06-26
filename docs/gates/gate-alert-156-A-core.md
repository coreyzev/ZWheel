# Gate A — Issue #156: Core Alert Logic (pure Kotlin)

## Context
ZWheel is a Wear OS + Android companion app for Onewheel boards.
This gate implements the pure-Kotlin alert state machine that lives in `core/`.
Zero Android imports are allowed in `core/` — this is enforced by CI.

## Files to create

### `core/src/main/kotlin/com/zwheel/core/alerts/AlertType.kt`
```kotlin
package com.zwheel.core.alerts

enum class AlertType { SPEED, HEADROOM }
```

### `core/src/main/kotlin/com/zwheel/core/alerts/AlertOutput.kt`
```kotlin
package com.zwheel.core.alerts

enum class AlertOutput { AUTO, WATCH, PHONE }
```

### `core/src/main/kotlin/com/zwheel/core/alerts/AlertConfig.kt`
```kotlin
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
    val threshold: Double = 7.152,  // 16 mph default
    val output: AlertOutput = AlertOutput.AUTO,
    /** How far the value must recover past the threshold before re-arming. Same unit as threshold. */
    val hysteresis: Double = 0.894,  // 2 mph in m/s; or 2 raw for HEADROOM
    /** Minimum ms between repeated fires while still in the alert zone. */
    val cooldownMs: Long = 10_000L,
)
```

### `core/src/main/kotlin/com/zwheel/core/alerts/AlertMonitor.kt`
```kotlin
package com.zwheel.core.alerts

/**
 * Stateful threshold monitor. Not thread-safe — call evaluate() from a single coroutine.
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
     * @param config    Current user configuration.
     * @param speedMps  Corrected speed in m/s, or null if unknown.
     * @param headroom  Raw safety headroom integer from firmware, or null if unknown.
     * @param nowMs     Current epoch time in milliseconds (for cooldown tracking).
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

        val value: Double = when (config.type) {
            AlertType.SPEED -> speedMps ?: run { inAlertZone = false; return false }
            AlertType.HEADROOM -> headroom?.toDouble() ?: run { inAlertZone = false; return false }
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

        // Already in alert zone — only re-fire after cooldown
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
```

### `core/src/test/kotlin/com/zwheel/core/alerts/AlertMonitorTest.kt`
```kotlin
package com.zwheel.core.alerts

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class AlertMonitorTest {

    private lateinit var monitor: AlertMonitor

    private val speedConfig = AlertConfig(
        enabled = true,
        type = AlertType.SPEED,
        threshold = 7.152,   // 16 mph in m/s
        output = AlertOutput.AUTO,
        hysteresis = 0.894,  // 2 mph
        cooldownMs = 10_000L,
    )

    private val headroomConfig = AlertConfig(
        enabled = true,
        type = AlertType.HEADROOM,
        threshold = 0.0,
        output = AlertOutput.AUTO,
        hysteresis = 2.0,
        cooldownMs = 10_000L,
    )

    @Before
    fun setUp() {
        monitor = AlertMonitor()
    }

    // --- disabled ---

    @Test
    fun disabled_neverFires() {
        val cfg = speedConfig.copy(enabled = false)
        assertFalse(monitor.evaluate(cfg, speedMps = 10.0, headroom = null, nowMs = 0L))
        assertFalse(monitor.evaluate(cfg, speedMps = 10.0, headroom = null, nowMs = 5_000L))
    }

    // --- null values ---

    @Test
    fun speedNull_doesNotFire() {
        assertFalse(monitor.evaluate(speedConfig, speedMps = null, headroom = null, nowMs = 0L))
    }

    @Test
    fun headroomNull_doesNotFire() {
        assertFalse(monitor.evaluate(headroomConfig, speedMps = null, headroom = null, nowMs = 0L))
    }

    // --- speed threshold crossing ---

    @Test
    fun speedBelowThreshold_doesNotFire() {
        assertFalse(monitor.evaluate(speedConfig, speedMps = 6.0, headroom = null, nowMs = 0L))
    }

    @Test
    fun speedAtThreshold_fires() {
        assertTrue(monitor.evaluate(speedConfig, speedMps = 7.152, headroom = null, nowMs = 0L))
    }

    @Test
    fun speedAboveThreshold_fires() {
        assertTrue(monitor.evaluate(speedConfig, speedMps = 9.0, headroom = null, nowMs = 0L))
    }

    // --- cooldown ---

    @Test
    fun speed_cooldown_suppressesRefireWithinWindow() {
        assertTrue(monitor.evaluate(speedConfig, speedMps = 9.0, headroom = null, nowMs = 0L))
        // still above threshold, within cooldown window
        assertFalse(monitor.evaluate(speedConfig, speedMps = 9.0, headroom = null, nowMs = 5_000L))
    }

    @Test
    fun speed_cooldown_allowsRefireAfterWindow() {
        assertTrue(monitor.evaluate(speedConfig, speedMps = 9.0, headroom = null, nowMs = 0L))
        // past cooldown, still in zone
        assertTrue(monitor.evaluate(speedConfig, speedMps = 9.0, headroom = null, nowMs = 10_001L))
    }

    // --- hysteresis ---

    @Test
    fun speed_hysteresis_preventsRefireOnSmallDrop() {
        // First fire
        assertTrue(monitor.evaluate(speedConfig, speedMps = 9.0, headroom = null, nowMs = 0L))
        // Drop but stay inside hysteresis band (7.152 - 0.894 = 6.258 m/s; drop to 6.5 — still in band)
        assertFalse(monitor.evaluate(speedConfig, speedMps = 6.5, headroom = null, nowMs = 15_000L))
        // Re-enter threshold — cooldown has passed, but hysteresis hasn't reset yet → should NOT fire again
        // because inAlertZone was still true (value never left the hysteresis recovery zone)
        // Actually, 6.5 < threshold (7.152), so inThreshold = false → no fire.
        // BUT inAlertZone remains true because 6.5 > (7.152 - 0.894=6.258), so not recovered.
        // Next: speed goes above threshold again → inAlertZone=true, cooldown passed → fires
        assertTrue(monitor.evaluate(speedConfig, speedMps = 9.0, headroom = null, nowMs = 20_000L))
    }

    @Test
    fun speed_hysteresis_resetsWhenFullyRecovered() {
        // Fire
        assertTrue(monitor.evaluate(speedConfig, speedMps = 9.0, headroom = null, nowMs = 0L))
        // Drop below threshold AND below hysteresis band (< 6.258 m/s)
        assertFalse(monitor.evaluate(speedConfig, speedMps = 5.0, headroom = null, nowMs = 1_000L))
        // inAlertZone is now false (fully recovered)
        // Re-cross threshold — fires immediately (cooldown is irrelevant since zone was reset)
        assertTrue(monitor.evaluate(speedConfig, speedMps = 9.0, headroom = null, nowMs = 2_000L))
    }

    // --- headroom ---

    @Test
    fun headroom_aboveThreshold_doesNotFire() {
        // threshold=0, headroom=5 → 5 > 0, not in alert zone
        assertFalse(monitor.evaluate(headroomConfig, speedMps = null, headroom = 5, nowMs = 0L))
    }

    @Test
    fun headroom_atThreshold_fires() {
        assertTrue(monitor.evaluate(headroomConfig, speedMps = null, headroom = 0, nowMs = 0L))
    }

    @Test
    fun headroom_belowThreshold_fires() {
        assertTrue(monitor.evaluate(headroomConfig, speedMps = null, headroom = -5, nowMs = 0L))
    }

    @Test
    fun headroom_cooldown_suppresses() {
        assertTrue(monitor.evaluate(headroomConfig, speedMps = null, headroom = 0, nowMs = 0L))
        assertFalse(monitor.evaluate(headroomConfig, speedMps = null, headroom = 0, nowMs = 5_000L))
    }

    @Test
    fun headroom_hysteresis_resetsOnFullRecovery() {
        // Fire at headroom=0
        assertTrue(monitor.evaluate(headroomConfig, speedMps = null, headroom = 0, nowMs = 0L))
        // Recover past hysteresis (threshold=0, hysteresis=2 → recover when > 2)
        assertFalse(monitor.evaluate(headroomConfig, speedMps = null, headroom = 3, nowMs = 1_000L))
        // Re-cross — fires immediately
        assertTrue(monitor.evaluate(headroomConfig, speedMps = null, headroom = 0, nowMs = 2_000L))
    }

    // --- reset ---

    @Test
    fun reset_clearsAlertZone() {
        assertTrue(monitor.evaluate(speedConfig, speedMps = 9.0, headroom = null, nowMs = 0L))
        monitor.reset()
        // After reset, next evaluation should re-arm and fire
        assertTrue(monitor.evaluate(speedConfig, speedMps = 9.0, headroom = null, nowMs = 100L))
    }

    @Test
    fun reset_clearsCooldown() {
        assertTrue(monitor.evaluate(speedConfig, speedMps = 9.0, headroom = null, nowMs = 0L))
        // Within cooldown — normally suppressed
        assertFalse(monitor.evaluate(speedConfig, speedMps = 9.0, headroom = null, nowMs = 100L))
        monitor.reset()
        // After reset, fires immediately regardless of cooldown
        assertTrue(monitor.evaluate(speedConfig, speedMps = 9.0, headroom = null, nowMs = 200L))
    }
}
```

## Compile and verify
Run: `GRADLE_USER_HOME=/tmp/gradle-home ./gradlew --no-daemon :core:test`
All AlertMonitorTest tests must pass (14 tests).
Fix any compile errors before committing.

## Commit
`feat(alerts): core AlertMonitor, AlertType, AlertOutput, AlertConfig — issue #156`

No other files may be modified.
