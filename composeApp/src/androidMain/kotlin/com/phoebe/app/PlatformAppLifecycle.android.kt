package com.phoebe.app

import com.phoebe.app.platform.MemoryPressureLevel
import com.phoebe.app.platform.PhoebeAppLifecycle
import com.phoebe.app.ui.RemoteArtworkCache

actual fun bindPlatformAppLifecycle(state: AppState) {
    PhoebeAppLifecycle.setUiVisibilityListener { visible ->
        if (visible) {
            RemoteArtworkCache.retryFailedLoadsNow()
        }
    }
    PhoebeAppLifecycle.setMemoryPressureListener { level ->
        when (level) {
            MemoryPressureLevel.UiHidden -> RemoteArtworkCache.trimForMemoryPressure(aggressive = false)
            MemoryPressureLevel.Moderate -> RemoteArtworkCache.trimForMemoryPressure(aggressive = true)
            MemoryPressureLevel.Critical -> RemoteArtworkCache.clearUnderMemoryPressure()
        }
        state.onMemoryPressure(level)
    }
}
