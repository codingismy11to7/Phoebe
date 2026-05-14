package com.phoebe.app.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

@Composable
actual fun DesktopKeyboardShortcutsEffect(onTogglePlayPause: () -> Unit) {
  RegisterDesktopKeyboardShortcuts(onTogglePlayPause)
}

actual fun Modifier.trackDesktopTextInputFocus(): Modifier = desktopTextInputFocusTracker()
