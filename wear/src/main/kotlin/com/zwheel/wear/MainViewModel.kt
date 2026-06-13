package com.zwheel.wear

import androidx.lifecycle.ViewModel
import com.zwheel.core.model.WatchPayload
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.StateFlow
import javax.inject.Inject

@HiltViewModel
class MainViewModel @Inject constructor(
    private val repository: WearDataLayerRepository,
) : ViewModel() {

    val payload: StateFlow<WatchPayload?> = repository.payload
}
