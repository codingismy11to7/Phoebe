package com.phoebe.app.player

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.StateFlow

/**
 * Bridges the app's volume slider to the operating-system volume:
 *
 *  - On platforms where this is supported (macOS desktop, Android, iOS hardware),
 *    hardware volume keys / rockers update the slider, and dragging the slider
 *    updates the OS music output volume.
 *  - When [isSupported] is false, the slider falls back to per-app player volume.
 */
interface SystemVolumeController {
    /** Whether this controller actually drives system volume. */
    val isSupported: Boolean

    /** Latest OS volume in 0..1, or 0.7f when [isSupported] is false. */
    val volume: StateFlow<Float>

    /** Begin observing OS volume on the given scope. Safe to call once. */
    fun start(scope: CoroutineScope)

    /** Set the OS volume in 0..1. No-op when [isSupported] is false. */
    fun setVolume(value: Float)
}

expect fun createSystemVolumeController(): SystemVolumeController
