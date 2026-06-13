package com.phoebe.app.feature.playback

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.rememberUpdatedState
import com.phoebe.app.domain.PlayerState
import com.phoebe.app.player.IosNowPlayingCenter
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.StateFlow

@Composable
actual fun GlobalMediaKeysEffect(
    playerFlow: StateFlow<PlayerState>,
    onTogglePlayPause: () -> Unit,
    onPlay: () -> Unit,
    onPause: () -> Unit,
    onNext: () -> Unit,
    onPrevious: () -> Unit,
    onSeek: (Long) -> Unit,
) {
    val toggle = rememberUpdatedState(onTogglePlayPause)
    val play = rememberUpdatedState(onPlay)
    val pause = rememberUpdatedState(onPause)
    val next = rememberUpdatedState(onNext)
    val previous = rememberUpdatedState(onPrevious)
    val seek = rememberUpdatedState(onSeek)

    DisposableEffect(Unit) {
        IosNowPlayingCenter.onToggle = { toggle.value.invoke() }
        IosNowPlayingCenter.onPlay = { play.value.invoke() }
        IosNowPlayingCenter.onPause = { pause.value.invoke() }
        IosNowPlayingCenter.onNext = { next.value.invoke() }
        IosNowPlayingCenter.onPrevious = { previous.value.invoke() }
        IosNowPlayingCenter.onSeek = { positionMs -> seek.value.invoke(positionMs) }
        IosNowPlayingCenter.install()
        onDispose {
            IosNowPlayingCenter.shutdown()
        }
    }

    LaunchedEffect(playerFlow) {
        playerFlow.collectLatest { state ->
            val track = state.currentTrack
            val durationMs = when {
                state.durationMs > 0L -> state.durationMs
                track != null && track.durationMs > 0L -> track.durationMs
                else -> 0L
            }
            IosNowPlayingCenter.update(
                title = track?.title.orEmpty(),
                artist = track?.artist.orEmpty(),
                album = track?.album.orEmpty(),
                positionMs = state.positionMs,
                durationMs = durationMs,
                isPlaying = state.isPlaying && !state.isBuffering,
                artworkUrl = track?.thumbUrl,
            )
        }
    }
}
