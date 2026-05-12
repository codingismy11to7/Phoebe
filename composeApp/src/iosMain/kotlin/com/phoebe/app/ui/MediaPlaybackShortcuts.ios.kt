package com.phoebe.app.ui

import androidx.compose.ui.Modifier

actual fun Modifier.mediaPlaybackShortcuts(
    onTogglePlayPause: () -> Unit,
    onPlay: () -> Unit,
    onPause: () -> Unit,
    onNext: () -> Unit,
    onPrevious: () -> Unit,
): Modifier = this
