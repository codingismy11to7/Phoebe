package com.phoebe.app.ui

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

/**
 * Tracks whether [GlobalMediaKeysEffect] registered jnativehook. When false, in-window
 * [mediaPlaybackShortcuts] handle media keys for the focused window.
 */
internal object DesktopGlobalMediaKeyHook {
    var isActive by mutableStateOf(false)
}
