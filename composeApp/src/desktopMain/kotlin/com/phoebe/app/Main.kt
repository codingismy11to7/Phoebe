package com.phoebe.app

import androidx.compose.ui.graphics.painter.BitmapPainter
import androidx.compose.ui.res.loadImageBitmap
import androidx.compose.ui.res.useResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState

fun main() = application {
    // One-time loud banner so it's obvious which build the running JVM was started from.
    // Bump this on each interesting change you want to verify is live.
    println("[Phoebe] desktop launched — build tag: drag-drop-plex-sync-v2")
    val windowState = rememberWindowState(width = 1320.dp, height = 880.dp)
    // macOS bakes the squircle shape into app icons (unlike iOS/Android, which auto-mask),
    // so on Mac we use a pre-rounded variant. Other desktops keep the full-bleed square.
    val iconResource = if (System.getProperty("os.name").startsWith("Mac", ignoreCase = true)) {
        "icon-macos.png"
    } else {
        "icon.png"
    }
    val icon = useResource(iconResource) { BitmapPainter(loadImageBitmap(it)) }
    Window(
        onCloseRequest = ::exitApplication,
        title = "Phoebe",
        state = windowState,
        icon = icon,
    ) {
        App()
    }
}
