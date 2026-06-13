package com.phoebe.app.player

import com.phoebe.app.domain.PlayerState
import com.phoebe.app.domain.Track

data class MusicAssistantRemotePlayback(
    val tracks: List<Track>,
    val index: Int,
    val target: String,
    val shuffle: Boolean = false,
)

fun MusicAssistantRemotePlayback.asPlayerState(fallback: PlayerState): PlayerState {
    val currentTrack = tracks.getOrNull(index)
    return PlayerState(
        queue = tracks,
        currentIndex = index,
        isPlaying = true,
        bufferedPositionMs = currentTrack?.durationMs ?: 0L,
        durationMs = currentTrack?.durationMs ?: 0L,
        shuffle = shuffle,
        volume = fallback.volume,
    )
}
