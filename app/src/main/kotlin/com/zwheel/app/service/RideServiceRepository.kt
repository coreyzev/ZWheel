package com.zwheel.app.service

import com.zwheel.app.ble.ConnectionState
import com.zwheel.core.model.BoardState
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

@Singleton
class RideServiceRepository @Inject constructor() {
    private val _boardState = MutableStateFlow(BoardState())
    val boardState: StateFlow<BoardState> = _boardState.asStateFlow()

    private val _connectionState = MutableStateFlow(ConnectionState.Idle)
    val connectionState: StateFlow<ConnectionState> = _connectionState.asStateFlow()

    internal fun updateBoardState(state: BoardState) {
        _boardState.value = state
    }

    internal fun updateConnectionState(state: ConnectionState) {
        _connectionState.value = state
    }
}
