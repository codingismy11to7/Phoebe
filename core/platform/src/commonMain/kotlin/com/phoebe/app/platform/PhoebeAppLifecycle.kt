package com.phoebe.app.platform

import kotlin.concurrent.Volatile

/**
 * Process-wide UI visibility and memory-pressure hooks shared across platforms.
 * Android drives this from [com.phoebe.app.PhoebeApplication] and [com.phoebe.app.MainActivity].
 */
object PhoebeAppLifecycle {
    @Volatile
    var isUiVisible: Boolean = true
        private set

    private var memoryPressureListener: ((MemoryPressureLevel) -> Unit)? = null

    fun setMemoryPressureListener(listener: ((MemoryPressureLevel) -> Unit)?) {
        memoryPressureListener = listener
    }

    fun setUiVisible(visible: Boolean) {
        isUiVisible = visible
        if (!visible) {
            notifyMemoryPressure(MemoryPressureLevel.UiHidden)
        }
    }

    fun notifyMemoryPressure(level: MemoryPressureLevel) {
        memoryPressureListener?.invoke(level)
    }
}
