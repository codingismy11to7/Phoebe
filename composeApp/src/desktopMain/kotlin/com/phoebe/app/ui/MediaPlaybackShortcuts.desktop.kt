package com.phoebe.app.ui

import androidx.compose.ui.Modifier
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type

/**
 * Global media keys are handled by [GlobalMediaKeysEffect] when jnativehook registers.
 * If that fails (e.g. blocked by OS policy), handle keys for the focused window here.
 * Space toggles play/pause via window-level [DesktopKeyboardShortcuts] on desktop.
 */
actual fun Modifier.mediaPlaybackShortcuts(
    onTogglePlayPause: () -> Unit,
    onPlay: () -> Unit,
    onPause: () -> Unit,
    onNext: () -> Unit,
    onPrevious: () -> Unit,
): Modifier {
    if (DesktopGlobalMediaKeyHook.isActive) return this
    return onPreviewKeyEvent { event ->
        if (event.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
        when (event.key) {
            Key.MediaPlay, Key.MediaPlayPause -> {
                onTogglePlayPause()
                true
            }
            Key.MediaPause -> {
                onPause()
                true
            }
            Key.MediaNext -> {
                onNext()
                true
            }
            Key.MediaPrevious -> {
                onPrevious()
                true
            }
            Key.MediaStop -> {
                onPause()
                true
            }
            else -> false
        }
    }
}
