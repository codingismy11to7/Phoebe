package com.phoebe.app.player

import com.phoebe.app.domain.PlayerState
import com.phoebe.app.domain.RepeatMode
import com.phoebe.app.domain.Track
import kotlinx.coroutines.flow.StateFlow

interface AudioPlayer {
    val state: StateFlow<PlayerState>
    fun play(queue: List<Track>, startIndex: Int = 0)
    fun togglePlayPause()
    fun clearQueue()
    fun addToUpNext(track: Track)
    fun moveUpNext(fromIndex: Int, toIndex: Int)
    fun removeUpNext(index: Int)
    fun next()
    fun previous()
    fun seekTo(positionMs: Long)
    fun setShuffle(enabled: Boolean)
    fun setRepeat(mode: RepeatMode)
    fun setVolume(volume: Float)

    /**
     * Update the volume value the UI reads from [state] without touching the underlying
     * platform output volume. Used when system volume drives the slider so we don't
     * double-attenuate.
     */
    fun updateReportedVolume(volume: Float)
}

expect fun createAudioPlayer(): AudioPlayer
