package com.phoebe.app.player

import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import platform.CoreGraphics.CGRectMake
import platform.MediaPlayer.MPVolumeView
import platform.UIKit.UIApplication
import platform.UIKit.UISlider
import kotlin.math.abs
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.TimeSource

actual fun createSystemVolumeController(): SystemVolumeController = IosSystemVolumeController()

/**
 * iOS system volume bridge. Reads and writes through a hidden [MPVolumeView] slider
 * (Apple's supported pattern — there is no public set-volume API).
 */
@OptIn(ExperimentalForeignApi::class)
private class IosSystemVolumeController : SystemVolumeController {
    override val isSupported: Boolean = true
    private val _volume = MutableStateFlow(0.7f)
    override val volume: StateFlow<Float> = _volume
    private var pollJob: Job? = null
    private var volumeView: MPVolumeView? = null
    private var ignorePollsUntil = TimeSource.Monotonic.markNow()

    override fun start(scope: CoroutineScope) {
        if (pollJob != null) return
        ensureVolumeView()
        _volume.value = readSystemVolume()
        pollJob = scope.launch(Dispatchers.Main) {
            while (isActive) {
                delay(POLL_MS)
                if (TimeSource.Monotonic.markNow() < ignorePollsUntil) continue
                val current = readSystemVolume()
                if (abs(current - _volume.value) > 0.005f) {
                    _volume.value = current
                }
            }
        }
    }

    override fun setVolume(value: Float) {
        val clamped = value.coerceIn(0f, 1f)
        _volume.value = clamped
        ignorePollsUntil = TimeSource.Monotonic.markNow() + IGNORE_POLL_MS.milliseconds
        ensureVolumeView()
        volumeSlider()?.setValue(clamped, animated = false)
    }

    private fun ensureVolumeView() {
        if (volumeView != null) return
        val view = MPVolumeView(frame = CGRectMake(-1000.0, -1000.0, 100.0, 100.0))
        view.showsVolumeSlider = true
        UIApplication.sharedApplication.keyWindow?.addSubview(view)
        volumeView = view
    }

    private fun volumeSlider(): UISlider? =
        volumeView?.subviews?.firstOrNull { it is UISlider } as? UISlider

    private fun readSystemVolume(): Float =
        volumeSlider()?.value?.coerceIn(0f, 1f) ?: 0.7f

    companion object {
        const val POLL_MS = 400L
        const val IGNORE_POLL_MS = 600L
    }
}
