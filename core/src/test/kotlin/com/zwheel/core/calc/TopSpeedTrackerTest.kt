package com.zwheel.core.calc

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

class TopSpeedTrackerTest {
    @Test
    fun `tracks the highest non-null speed consumed`() {
        val tracker = DefaultTopSpeedTracker()

        tracker.consume(null)
        assertNull(tracker.currentTripMaxMetersPerSecond)

        tracker.consume(4.0)
        tracker.consume(3.5)
        tracker.consume(null)
        tracker.consume(7.25)
        tracker.consume(6.0)

        assertEquals(7.25, tracker.currentTripMaxMetersPerSecond)
    }

    @Test
    fun `reset clears the tracked max`() {
        val tracker = DefaultTopSpeedTracker()

        tracker.consume(5.0)
        tracker.reset()

        assertNull(tracker.currentTripMaxMetersPerSecond)
    }
}
