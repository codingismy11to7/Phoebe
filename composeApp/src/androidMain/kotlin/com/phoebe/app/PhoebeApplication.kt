package com.phoebe.app

import android.app.Application
import android.content.ComponentCallbacks2
import com.google.android.gms.cast.framework.CastContext
import com.phoebe.app.platform.MemoryPressureLevel
import com.phoebe.app.platform.PhoebeAppLifecycle
import com.phoebe.app.platform.memoryPressureLevelForTrimLevel
import com.phoebe.app.platform.cancelPlatformDownloadRunner
import com.phoebe.app.player.AndroidPlaybackRuntime
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class PhoebeApplication : Application() {
    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    override fun onCreate() {
        super.onCreate()
        AndroidContextHolder.application = this
        registerComponentCallbacks(memoryPressureCallbacks)
        runCatching { CastContext.getSharedInstance(this) }
        cancelPlatformDownloadRunner()
        appScope.launch {
            AndroidPlaybackRuntime.ensureInstalled()
        }
    }

    override fun onTerminate() {
        unregisterComponentCallbacks(memoryPressureCallbacks)
        super.onTerminate()
    }

    private val memoryPressureCallbacks = object : ComponentCallbacks2 {
        override fun onConfigurationChanged(newConfig: android.content.res.Configuration) = Unit

        override fun onLowMemory() {
            PhoebeAppLifecycle.notifyMemoryPressure(MemoryPressureLevel.Critical)
        }

        @Suppress("DEPRECATION")
        override fun onTrimMemory(level: Int) {
            val pressure = memoryPressureLevelForTrimLevel(level) ?: return
            PhoebeAppLifecycle.notifyMemoryPressure(pressure)
        }
    }
}
