package com.zwheel.core.calc

import com.zwheel.core.model.BoardType
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

class RangeEstimatorTest {
    private val estimator = DefaultRangeEstimator()

    @Test
    fun `uses conservative board type default when calibration is absent`() {
        assertEquals(
            8.0,
            estimator.estimateKilometersRemaining(
                batteryPct = 50,
                boardType = BoardType.XR,
            ),
        )
    }

    @Test
    fun `uses calibration when present`() {
        assertEquals(
            10.0,
            estimator.estimateKilometersRemaining(
                batteryPct = 50,
                boardType = BoardType.XR,
                calibration = RangeCalibration(kilometersPerBatteryPercent = 0.20),
            ),
        )
    }

    @Test
    fun `returns null when battery percent is null`() {
        assertNull(
            estimator.estimateKilometersRemaining(
                batteryPct = null,
                boardType = BoardType.XR,
            ),
        )
    }
}
