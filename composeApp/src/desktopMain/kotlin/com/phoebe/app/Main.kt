package com.phoebe.app

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.graphics.painter.BitmapPainter
import androidx.compose.ui.res.loadImageBitmap
import androidx.compose.ui.res.useResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.WindowScope
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState
import javax.swing.RootPaneContainer

fun main() = application {
    // One-time loud banner so it's obvious which build the running JVM was started from.
    // Bump this on each interesting change you want to verify is live.
    println("[Phoebe] desktop launched — build tag: drag-drop-plex-sync-v2")
    val windowState = rememberWindowState(width = 1320.dp, height = 880.dp)
    val isMacOs = isMacOs()
    // macOS bakes the squircle shape into app icons (unlike iOS/Android, which auto-mask),
    // so on Mac we use a pre-rounded variant. Other desktops keep the full-bleed square.
    val iconResource = if (isMacOs) {
        "icon-macos.png"
    } else {
        "icon.png"
    }
    val icon = useResource(iconResource) { BitmapPainter(loadImageBitmap(it)) }
    Window(
        onCloseRequest = ::exitApplication,
        title = if (isMacOs) "" else "Phoebe",
        state = windowState,
        icon = icon,
    ) {
        ApplyMacWindowChrome()
        App()
    }
}

private fun isMacOs(): Boolean =
    System.getProperty("os.name").startsWith("Mac", ignoreCase = true)

@Composable
private fun WindowScope.ApplyMacWindowChrome() {
    if (!isMacOs()) return

    DisposableEffect(window) {
        val rootPane = (window as? RootPaneContainer)?.rootPane
        rootPane?.putClientProperty("apple.awt.fullWindowContent", true)
        rootPane?.putClientProperty("apple.awt.transparentTitleBar", true)
        rootPane?.putClientProperty("apple.awt.windowTitleVisible", false)
        onDispose {}
    }
}
