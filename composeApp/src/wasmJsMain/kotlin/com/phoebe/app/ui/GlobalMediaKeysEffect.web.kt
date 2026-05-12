package com.phoebe.app.ui

import androidx.compose.runtime.Composable
import com.phoebe.app.domain.PlayerState

@Composable
actual fun GlobalMediaKeysEffect(
    @Suppress("UNUSED_PARAMETER") player: PlayerState,
    onTogglePlayPause: () -> Unit,
    onPlay: () -> Unit,
    onPause: () -> Unit,
    onNext: () -> Unit,
    onPrevious: () -> Unit,
) {
}
