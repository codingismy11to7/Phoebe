package com.phoebe.app.player

import com.phoebe.app.domain.PlayerState
import com.phoebe.app.domain.Track
import com.phoebe.app.domain.isLocalMediaPlayback
import com.phoebe.app.domain.isPlexLibraryTrack
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

data class CastState(
    val isAvailable: Boolean = false,
    val isConnected: Boolean = false,
    val deviceName: String? = null,
    val queue: List<Track> = emptyList(),
    val currentIndex: Int = -1,
    val isPlaying: Boolean = false,
    val isBuffering: Boolean = false,
    val positionMs: Long = 0L,
    val durationMs: Long = 0L,
    val message: String? = null,
) {
    val currentTrack: Track? get() = queue.getOrNull(currentIndex)
}

interface CastController {
    val state: StateFlow<CastState>
    fun showDevicePicker()
    fun disconnect()
    fun loadQueue(queue: List<Track>, startIndex: Int = 0)
    fun togglePlayPause()
    fun next()
    fun previous()
    fun seekTo(positionMs: Long)
}

open class UnavailableCastController(
    private val unavailableMessage: String = "Chromecast is not available on this platform.",
) : CastController {
    private val mutableState = MutableStateFlow(CastState(message = unavailableMessage))
    override val state: StateFlow<CastState> = mutableState

    override fun showDevicePicker() {
        mutableState.value = mutableState.value.copy(message = unavailableMessage)
    }

    override fun disconnect() = Unit
    override fun loadQueue(queue: List<Track>, startIndex: Int) = showDevicePicker()
    override fun togglePlayPause() = Unit
    override fun next() = Unit
    override fun previous() = Unit
    override fun seekTo(positionMs: Long) = Unit
}

fun Track.isChromecastPlayable(): Boolean =
    isPlexLibraryTrack() && !isLocalMediaPlayback() && streamUrl.isNotBlank()

fun List<Track>.isChromecastPlayableQueue(): Boolean = isNotEmpty() && all { it.isChromecastPlayable() }

fun CastState.asPlayerState(fallback: PlayerState): PlayerState =
    fallback.copy(
        queue = queue,
        currentIndex = currentIndex,
        isPlaying = isPlaying,
        isBuffering = isBuffering,
        positionMs = positionMs,
        durationMs = durationMs.takeIf { it > 0L } ?: currentTrack?.durationMs ?: fallback.durationMs,
    )

expect fun createCastController(audioPlayer: AudioPlayer): CastController
