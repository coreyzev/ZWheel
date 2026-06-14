package com.zwheel.wear

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.zwheel.wear.ui.ZWheelWearScreen

@Composable
fun MainScreen(
    viewModel: MainViewModel = hiltViewModel(),
    isAmbient: Boolean = false,
) {
    val payload by viewModel.payload.collectAsStateWithLifecycle()
    ZWheelWearScreen(payload = payload, isAmbient = isAmbient)
}
