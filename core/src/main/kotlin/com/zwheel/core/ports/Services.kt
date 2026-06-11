package com.zwheel.core.ports

import com.zwheel.core.model.BoardState
import kotlinx.coroutines.flow.StateFlow

interface BoardStateService {
    val state: StateFlow<BoardState>
}
