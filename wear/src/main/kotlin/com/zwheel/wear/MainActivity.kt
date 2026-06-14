package com.zwheel.wear

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.wear.ambient.AmbientLifecycleObserver
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    @Inject
    lateinit var repository: WearDataLayerRepository

    private var isAmbient by mutableStateOf(false)

    private val ambientObserver = AmbientLifecycleObserver(
        this,
        object : AmbientLifecycleObserver.AmbientLifecycleCallback {
            override fun onEnterAmbient(
                ambientDetails: AmbientLifecycleObserver.AmbientDetails,
            ) {
                isAmbient = true
            }
            override fun onExitAmbient() {
                isAmbient = false
            }
            override fun onUpdateAmbient() {
                // Data Layer keeps state current; no periodic redraw needed.
            }
        },
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        lifecycle.addObserver(ambientObserver)
        setContent {
            MainScreen(isAmbient = isAmbient)
        }
    }

    override fun onResume() {
        super.onResume()
        repository.register()
    }

    override fun onPause() {
        super.onPause()
        repository.unregister()
    }
}
