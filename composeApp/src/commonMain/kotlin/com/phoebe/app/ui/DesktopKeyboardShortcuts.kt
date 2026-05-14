package com.phoebe.app.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

@Composable
expect fun DesktopKeyboardShortcutsEffect(onTogglePlayPause: () -> Unit)

expect fun Modifier.trackDesktopTextInputFocus(): Modifier
