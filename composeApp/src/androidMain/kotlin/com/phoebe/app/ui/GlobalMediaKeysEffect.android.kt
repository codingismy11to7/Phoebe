package com.phoebe.app.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import com.phoebe.app.domain.PlayerState
import com.phoebe.app.player.AndroidAudioPlayerHolder

@Composable
actual fun GlobalMediaKeysEffect(
    @Suppress("UNUSED_PARAMETER") player: PlayerState,
    onTogglePlayPause: () -> Unit,
    onPlay: () -> Unit,
    onPause: () -> Unit,
    onNext: () -> Unit,
    onPrevious: () -> Unit,
    onSeek: (Long) -> Unit,
) {
    LaunchedEffect(Unit) {
        AndroidAudioPlayerHolder.ensureConnected()
    }
}
