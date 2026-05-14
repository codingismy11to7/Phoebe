package com.phoebe.app.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.focus.onFocusChanged
import java.awt.Window
import java.util.concurrent.atomic.AtomicInteger

internal object DesktopKeyboardShortcuts {
  @Volatile
  var onTogglePlayPause: () -> Unit = {}

  private val textInputFocusCount = AtomicInteger(0)

  val isTextInputFocused: Boolean
    get() = textInputFocusCount.get() > 0

  fun textInputFocused() {
    textInputFocusCount.incrementAndGet()
  }

  fun textInputBlurred() {
    textInputFocusCount.decrementAndGet()
  }
}

internal fun Modifier.desktopTextInputFocusTracker(): Modifier = composed {
  var wasFocused by remember { mutableStateOf(false) }
  Modifier.onFocusChanged { state ->
    when {
      state.isFocused && !wasFocused -> {
        DesktopKeyboardShortcuts.textInputFocused()
        wasFocused = true
      }
      !state.isFocused && wasFocused -> {
        DesktopKeyboardShortcuts.textInputBlurred()
        wasFocused = false
      }
    }
  }
}

@Composable
internal fun RegisterDesktopKeyboardShortcuts(onTogglePlayPause: () -> Unit) {
  val toggle = rememberUpdatedState(onTogglePlayPause)
  DisposableEffect(Unit) {
    DesktopKeyboardShortcuts.onTogglePlayPause = { toggle.value.invoke() }
    onDispose {
      DesktopKeyboardShortcuts.onTogglePlayPause = {}
    }
  }
}

/**
 * Compose [androidx.compose.ui.window.Window] key callbacks only run when a composable has focus.
 * Register an AWT dispatcher so Space works when the window is focused but nothing in the tree is.
 */
@Composable
internal fun RegisterDesktopWindowKeyDispatcher(awtWindow: Window) {
  DisposableEffect(awtWindow) {
    val handler = DesktopSpaceKeyHandler.install(
      awtWindow,
      DesktopSpaceKeyHandler.SpaceToggle { DesktopKeyboardShortcuts.onTogglePlayPause() },
      DesktopSpaceKeyHandler.TextInputActive { DesktopKeyboardShortcuts.isTextInputFocused },
    )
    onDispose {
      handler.uninstall()
    }
  }
}
