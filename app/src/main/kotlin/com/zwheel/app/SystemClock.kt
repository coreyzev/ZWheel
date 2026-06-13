package com.zwheel.app

import com.zwheel.core.ports.Clock
import javax.inject.Inject

class SystemClock @Inject constructor() : Clock {
    override fun nowEpochMillis(): Long = System.currentTimeMillis()
}
