package com.zwheel.core.alerts

import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class AlertMonitorTest {

    private lateinit var monitor: AlertMonitor

    private val speedConfig = AlertConfig(
        enabled = true,
        type = AlertType.SPEED,
        threshold = 7.152,
        output = AlertOutput.AUTO,
        hysteresis = 0.894,
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

    @BeforeEach
    fun setUp() {
        monitor = AlertMonitor()
    }

    @Test
    fun disabled_neverFires() {
        val cfg = speedConfig.copy(enabled = false)
        assertFalse(monitor.evaluate(cfg, speedMps = 10.0, headroom = null, nowMs = 0L))
        assertFalse(monitor.evaluate(cfg, speedMps = 10.0, headroom = null, nowMs = 5_000L))
    }

    @Test
    fun speedNull_doesNotFire() {
        assertFalse(monitor.evaluate(speedConfig, speedMps = null, headroom = null, nowMs = 0L))
    }

    @Test
    fun headroomNull_doesNotFire() {
        assertFalse(monitor.evaluate(headroomConfig, speedMps = null, headroom = null, nowMs = 0L))
    }

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

    @Test
    fun speed_cooldown_suppressesRefireWithinWindow() {
        assertTrue(monitor.evaluate(speedConfig, speedMps = 9.0, headroom = null, nowMs = 0L))
        assertFalse(monitor.evaluate(speedConfig, speedMps = 9.0, headroom = null, nowMs = 5_000L))
    }

    @Test
    fun speed_cooldown_allowsRefireAfterWindow() {
        assertTrue(monitor.evaluate(speedConfig, speedMps = 9.0, headroom = null, nowMs = 0L))
        assertTrue(monitor.evaluate(speedConfig, speedMps = 9.0, headroom = null, nowMs = 10_001L))
    }

    @Test
    fun speed_hysteresis_preventsRefireOnSmallDrop() {
        assertTrue(monitor.evaluate(speedConfig, speedMps = 9.0, headroom = null, nowMs = 0L))
        assertFalse(monitor.evaluate(speedConfig, speedMps = 6.5, headroom = null, nowMs = 15_000L))
        assertTrue(monitor.evaluate(speedConfig, speedMps = 9.0, headroom = null, nowMs = 20_000L))
    }

    @Test
    fun speed_hysteresis_resetsWhenFullyRecovered() {
        assertTrue(monitor.evaluate(speedConfig, speedMps = 9.0, headroom = null, nowMs = 0L))
        assertFalse(monitor.evaluate(speedConfig, speedMps = 5.0, headroom = null, nowMs = 1_000L))
        assertTrue(monitor.evaluate(speedConfig, speedMps = 9.0, headroom = null, nowMs = 2_000L))
    }

    @Test
    fun headroom_aboveThreshold_doesNotFire() {
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
        assertTrue(monitor.evaluate(headroomConfig, speedMps = null, headroom = 0, nowMs = 0L))
        assertFalse(monitor.evaluate(headroomConfig, speedMps = null, headroom = 3, nowMs = 1_000L))
        assertTrue(monitor.evaluate(headroomConfig, speedMps = null, headroom = 0, nowMs = 2_000L))
    }

    @Test
    fun reset_clearsAlertZone() {
        assertTrue(monitor.evaluate(speedConfig, speedMps = 9.0, headroom = null, nowMs = 0L))
        monitor.reset()
        assertTrue(monitor.evaluate(speedConfig, speedMps = 9.0, headroom = null, nowMs = 100L))
    }

    @Test
    fun reset_clearsCooldown() {
        assertTrue(monitor.evaluate(speedConfig, speedMps = 9.0, headroom = null, nowMs = 0L))
        assertFalse(monitor.evaluate(speedConfig, speedMps = 9.0, headroom = null, nowMs = 100L))
        monitor.reset()
        assertTrue(monitor.evaluate(speedConfig, speedMps = 9.0, headroom = null, nowMs = 200L))
    }
}
