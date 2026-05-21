package com.phoebe.app.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import com.phoebe.app.domain.PlayerState
import kotlinx.coroutines.flow.StateFlow

/**
 * Desktop: macOS uses Now Playing / remote commands; Windows & Linux use a global key hook.
 * Other targets: no-op.
 */
@Composable
expect fun GlobalMediaKeysEffect(
    player: PlayerState,
    onTogglePlayPause: () -> Unit,
    onPlay: () -> Unit,
    onPause: () -> Unit,
    onNext: () -> Unit,
    onPrevious: () -> Unit,
    onSeek: (Long) -> Unit = {},
)

/** Collects high-frequency playback position updates in a leaf composable so the app shell stays skippable. */
@Composable
fun GlobalMediaKeysEffect(
    playerFlow: StateFlow<PlayerState>,
    onTogglePlayPause: () -> Unit,
    onPlay: () -> Unit,
    onPause: () -> Unit,
    onNext: () -> Unit,
    onPrevious: () -> Unit,
    onSeek: (Long) -> Unit = {},
) {
    val player by playerFlow.collectAsState()
    GlobalMediaKeysEffect(
        player = player,
        onTogglePlayPause = onTogglePlayPause,
        onPlay = onPlay,
        onPause = onPause,
        onNext = onNext,
        onPrevious = onPrevious,
        onSeek = onSeek,
    )
}
