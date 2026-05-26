package com.phoebe.app

import android.app.Application
import com.google.android.gms.cast.framework.CastContext
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
        runCatching { CastContext.getSharedInstance(this) }
        cancelPlatformDownloadRunner()
        appScope.launch {
            AndroidPlaybackRuntime.ensureInstalled()
        }
    }
}
