package com.phoebe.app.player

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

actual fun createSystemVolumeController(): SystemVolumeController = WebSystemVolumeController()

private class WebSystemVolumeController : SystemVolumeController {
    private val mutableVolume = MutableStateFlow(0.7f)

    override val isSupported: Boolean = false
    override val volume: StateFlow<Float> = mutableVolume

    override fun start(scope: CoroutineScope) = Unit

    override fun setVolume(value: Float) {
        mutableVolume.value = value.coerceIn(0f, 1f)
    }
}
