package com.phoebe.app.feature.playback

import androidx.compose.ui.Modifier

/**
 * Optional in-window media key handling. Desktop uses [GlobalMediaKeysEffect] instead so keys
 * work when the window is not focused; other targets leave [Modifier] unchanged.
 */
expect fun Modifier.mediaPlaybackShortcuts(
    onTogglePlayPause: () -> Unit,
    onPlay: () -> Unit,
    onPause: () -> Unit,
    onNext: () -> Unit,
    onPrevious: () -> Unit,
): Modifier
