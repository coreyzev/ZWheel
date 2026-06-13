package com.zwheel.app.ui

import com.zwheel.app.data.settings.UserPreferences
import com.zwheel.core.model.BoardState
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class DashboardStateTest {
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
