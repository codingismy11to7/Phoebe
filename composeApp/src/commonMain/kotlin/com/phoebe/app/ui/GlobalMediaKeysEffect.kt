package com.phoebe.app.ui

import androidx.compose.runtime.Composable
import com.phoebe.app.domain.PlayerState
import kotlinx.coroutines.flow.StateFlow

/**
 * Desktop: macOS uses Now Playing / remote commands; Windows & Linux use a global key hook.
 * Other targets: no-op.
 */
@Composable
expect fun GlobalMediaKeysEffect(
    playerFlow: StateFlow<PlayerState>,
    onTogglePlayPause: () -> Unit,
    onPlay: () -> Unit,
    onPause: () -> Unit,
    onNext: () -> Unit,
    onPrevious: () -> Unit,
    onSeek: (Long) -> Unit = {},
)
