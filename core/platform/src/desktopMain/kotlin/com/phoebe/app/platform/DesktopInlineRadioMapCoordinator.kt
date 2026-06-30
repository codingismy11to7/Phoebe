package com.phoebe.app.platform

import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.Executors
import java.util.concurrent.ScheduledFuture
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

/**
 * Tracks when the desktop inline radio map (JCEF) is mounted and when live radio playback
 * is starting. While both are active, browser script injection should be deferred so
 * Chromium does not starve JavaFX / Java Sound startup on Windows.
 */
object DesktopInlineRadioMapCoordinator {
    private const val LiveRadioStartupAutoEndMs = 32_000L

    private val inlineMapHosts = AtomicInteger(0)
    @Volatile
    private var liveRadioStartupActive = false
    private val autoEndExecutor = Executors.newSingleThreadScheduledExecutor { runnable ->
        Thread(runnable, "Phoebe-radio-map-playback-coordinator").apply { isDaemon = true }
    }
    private val resumeListeners = CopyOnWriteArrayList<() -> Unit>()
    @Volatile
    private var autoEndFuture: ScheduledFuture<*>? = null

    val isInlineMapActive: Boolean
        get() = inlineMapHosts.get() > 0

    val shouldDeferBrowserScripts: Boolean
        get() = isInlineMapActive && liveRadioStartupActive

    fun onInlineMapHostMounted() {
        inlineMapHosts.incrementAndGet()
    }

    fun onInlineMapHostUnmounted() {
        inlineMapHosts.updateAndGet { current -> (current - 1).coerceAtLeast(0) }
        if (!isInlineMapActive) {
            endLiveRadioStartup()
        }
    }

    fun onScriptsResume(listener: () -> Unit) {
        resumeListeners.add(listener)
    }

    fun beginLiveRadioStartup() {
        liveRadioStartupActive = true
        autoEndFuture?.cancel(false)
        autoEndFuture = autoEndExecutor.schedule(
            { endLiveRadioStartup() },
            LiveRadioStartupAutoEndMs,
            TimeUnit.MILLISECONDS,
        )
    }

    fun endLiveRadioStartup() {
        if (!liveRadioStartupActive) return
        val shouldResumeScripts = isInlineMapActive
        liveRadioStartupActive = false
        autoEndFuture?.cancel(false)
        autoEndFuture = null
        if (shouldResumeScripts) {
            resumeListeners.forEach { listener -> runCatching(listener) }
        }
    }
}
