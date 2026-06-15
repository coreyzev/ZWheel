package com.zwheel.app.ui

import com.zwheel.app.data.settings.UserPreferences
import com.zwheel.core.model.BoardState
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class DashboardStateTest {
    @Test
    fun `tripAmpHours and regenAmpHours are mapped from BoardState`() {
        val state = BoardState(
            tripAmpHours = 2.5,
            tripRegenAmpHours = 0.3,
        ).toDashboardUiState(
            prefs = UserPreferences(),
            topSpeedMetersPerSecond = null,
            estimatedRangeKilometers = null,
        )
        assertEquals(2.5, state.tripAmpHours, 0.001)
        assertEquals(0.3, state.regenAmpHours, 0.001)
    }

    @Test
    fun `live speed does not display raw odometer fallback`() {
        val state = BoardState(
            speedMetersPerSecondCorrected = null,
            speedMetersPerSecondRaw = 12.96,
        ).toDashboardUiState(
            prefs = UserPreferences(),
            topSpeedMetersPerSecond = null,
            estimatedRangeKilometers = null,
        )

        assertEquals(0.0, state.speedMph)
    }
}
