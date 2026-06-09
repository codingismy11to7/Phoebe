package com.phoebe.app

import com.phoebe.app.platform.MemoryPressureLevel
import com.phoebe.app.platform.PhoebeAppLifecycle

actual fun bindPlatformAppLifecycle(state: AppState) {
    PhoebeAppLifecycle.setMemoryPressureListener { level ->
        state.onMemoryPressure(level)
    }
}
