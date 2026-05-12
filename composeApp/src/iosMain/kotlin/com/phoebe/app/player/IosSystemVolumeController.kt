package com.phoebe.app.player

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

actual fun createSystemVolumeController(): SystemVolumeController = IosSystemVolumeController()

/**
 * iOS only exposes a read-only `AVAudioSession.outputVolume`, and the iOS audio player
 * in this codebase is still a stub. Until that lands, expose a no-op controller so the
 * slider falls back to per-app player volume.
 */
private class IosSystemVolumeController : SystemVolumeController {
    override val isSupported: Boolean = false
    override val volume: StateFlow<Float> = MutableStateFlow(0.7f)
    override fun start(scope: CoroutineScope) = Unit
    override fun setVolume(value: Float) = Unit
}
