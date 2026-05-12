package com.phoebe.app.ui

import androidx.compose.ui.Modifier

/**
 * Desktop media keys are handled globally by [GlobalMediaKeysEffect] (focused or not).
 * Keeping this as a no-op avoids duplicate toggles if Compose also delivered the same keys.
 */
actual fun Modifier.mediaPlaybackShortcuts(
    onTogglePlayPause: () -> Unit,
    onPlay: () -> Unit,
    onPause: () -> Unit,
    onNext: () -> Unit,
    onPrevious: () -> Unit,
): Modifier = this
