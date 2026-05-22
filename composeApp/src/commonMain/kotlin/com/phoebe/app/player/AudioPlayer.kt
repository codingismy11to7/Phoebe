package com.phoebe.app.player

import com.phoebe.app.domain.EqualizerProfile
import com.phoebe.app.domain.PlayerState
import com.phoebe.app.domain.RepeatMode
import com.phoebe.app.domain.Track
import kotlinx.coroutines.flow.StateFlow

interface AudioPlayer {
    val state: StateFlow<PlayerState>
    fun play(queue: List<Track>, startIndex: Int = 0)
    fun togglePlayPause()
    fun clearQueue()
    /** Stop playback and discard the entire queue, including the current track. */
    fun stopPlayback()
    fun addToUpNext(track: Track)
    fun appendToQueue(tracks: List<Track>)
    fun moveUpNext(fromIndex: Int, toIndex: Int)
    fun removeUpNext(index: Int)
    fun next()
    fun previous()
    fun seekTo(positionMs: Long)
    fun setShuffle(enabled: Boolean)
    fun setRepeat(mode: RepeatMode)
    fun setVolume(volume: Float)
    fun setCrossfadeDurationMs(durationMs: Long)
    fun setEqualizer(profile: EqualizerProfile)

    /**
     * Keep per-app output at unity while [updateReportedVolume] mirrors the OS level on the slider.
     */
    fun setUnityOutputVolume()

    /**
     * Update the volume value the UI reads from [state] without touching the underlying
     * platform output volume. Used when system volume drives the slider so we don't
     * double-attenuate.
     */
    fun updateReportedVolume(volume: Float)

    /**
     * Scales audible output by the OS mixer level (0..1) while [state.volume] stays the
     * in-app slider value. Used on desktop when hardware keys move PulseAudio/CoreAudio
     * but the slider only stores the app preference.
     */
    fun setSystemVolumeScale(scale: Float) = Unit
}

expect fun createAudioPlayer(): AudioPlayer
