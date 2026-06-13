package com.phoebe.app.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

@Composable
actual fun DesktopKeyboardShortcutsEffect(onTogglePlayPause: () -> Unit) = Unit

actual fun Modifier.trackDesktopTextInputFocus(): Modifier = this
