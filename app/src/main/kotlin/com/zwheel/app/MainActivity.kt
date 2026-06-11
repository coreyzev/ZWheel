package com.zwheel.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import com.zwheel.app.ui.ZWheelAppScreen
import com.zwheel.app.ui.ZWheelTheme
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            ZWheelTheme {
                ZWheelAppScreen()
            }
        }
    }
}
