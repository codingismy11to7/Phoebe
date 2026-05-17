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
import com.phoebe.app.platform.PhoebeLog
import com.phoebe.app.platform.appDisplayName
import com.phoebe.app.platform.isDebugBuild
import com.phoebe.app.ui.RegisterDesktopWindowKeyDispatcher
import com.sun.jna.Library
import com.sun.jna.Native
import com.sun.jna.Pointer
import java.awt.Image
import javax.imageio.ImageIO
import javax.swing.RootPaneContainer

fun main() {
    configureSandboxedNativeLibraries()
    applyMacDockIcon(isDebugBuild())
    application {
        PhoebeLog.d("Phoebe") { "desktop launched (debug=${isDebugBuild()})" }
        val windowState = rememberWindowState(width = 1480.dp, height = 880.dp)
        val isMacOs = isMacOs()
        // macOS bakes the squircle shape into app icons (unlike iOS/Android, which auto-mask),
        // so on Mac we use a pre-rounded variant. Other desktops keep the full-bleed square.
        val debugSuffix = if (isDebugBuild()) "-debug" else ""
        val iconResource = if (isMacOs) {
            "icon-macos$debugSuffix.png"
        } else {
            "icon$debugSuffix.png"
        }
        val icon = useResource(iconResource) { BitmapPainter(loadImageBitmap(it)) }
        Window(
            onCloseRequest = ::exitApplication,
            title = if (isMacOs) "" else appDisplayName(),
            state = windowState,
            icon = icon,
        ) {
            RegisterDesktopWindowKeyDispatcher(window)
            ApplyDesktopWindowChrome()
            App(
                onAppearanceChange = { useLightAppearance ->
                    WindowsWindowChrome.apply(window, useLightAppearance)
                },
            )
        }
    }
}

private fun configureSandboxedNativeLibraries() {
    if (System.getProperty("os.name").orEmpty().lowercase().contains("mac")) return
    if (System.getProperty("jnativehook.lib.path") != null) return

    val cacheRoot = System.getenv("XDG_CACHE_HOME")?.takeIf { it.isNotBlank() }
        ?: System.getProperty("user.home")?.plus("/.cache")
        ?: return
    val cacheFolder = if (isDebugBuild()) "phoebe-debug" else "phoebe"
    val nativeLibDir = java.io.File(cacheRoot, "$cacheFolder/native").apply { mkdirs() }
    System.setProperty("jnativehook.lib.path", nativeLibDir.absolutePath)
}

private fun isMacOs(): Boolean =
    System.getProperty("os.name").startsWith("Mac", ignoreCase = true)

/** macOS Dock icon comes from {@code -Xdock:icon} in packaged runs; override it for dev/debug. */
private fun applyMacDockIcon(debug: Boolean) {
    if (!isMacOs()) return
    val resourceName = if (debug) "icon-macos-debug.png" else "icon-macos.png"
    val dockImage = Thread.currentThread().contextClassLoader
        .getResourceAsStream(resourceName)
        ?.use(ImageIO::read)
        ?: return
    runCatching {
        val applicationClass = Class.forName("com.apple.eawt.Application")
        val application = applicationClass.getMethod("getApplication").invoke(null)
        applicationClass
            .getMethod("setDockIconImage", Image::class.java)
            .invoke(application, dockImage)
    }
}

private fun isWindows(): Boolean =
    System.getProperty("os.name").startsWith("Windows", ignoreCase = true)

@Composable
private fun WindowScope.ApplyDesktopWindowChrome() {
    if (!isMacOs() && !isWindows()) return

    DisposableEffect(window) {
        MacWindowChrome.apply(window)
        onDispose {}
    }
}

private object MacWindowChrome {
    fun apply(window: java.awt.Window) {
        if (!isMacOs()) return

        val rootPane = (window as? RootPaneContainer)?.rootPane
        rootPane?.putClientProperty("apple.awt.fullWindowContent", true)
        rootPane?.putClientProperty("apple.awt.transparentTitleBar", true)
        rootPane?.putClientProperty("apple.awt.windowTitleVisible", false)
    }
}

private object WindowsWindowChrome {
    private const val DWMWA_USE_IMMERSIVE_DARK_MODE = 20
    private const val DWMWA_USE_IMMERSIVE_DARK_MODE_BEFORE_20H1 = 19
    private const val DWMWA_BORDER_COLOR = 34
    private const val DWMWA_CAPTION_COLOR = 35
    private const val DWMWA_TEXT_COLOR = 36

    private const val DARK_SHELL_TOP = 0xFF151A27.toInt()
    private const val DARK_PRIMARY_TEXT = 0xFFF4F5F7.toInt()
    private const val LIGHT_SHELL_TOP = 0xFFFFFFFF.toInt()
    private const val LIGHT_PRIMARY_TEXT = 0xFF181B22.toInt()

    fun apply(window: java.awt.Window, useLightAppearance: Boolean) {
        if (!isWindows()) return

        runCatching {
            val shellTop = if (useLightAppearance) LIGHT_SHELL_TOP else DARK_SHELL_TOP
            val primaryText = if (useLightAppearance) LIGHT_PRIMARY_TEXT else DARK_PRIMARY_TEXT
            val hwnd = Native.getComponentPointer(window)
            if (setBooleanAttribute(hwnd, DWMWA_USE_IMMERSIVE_DARK_MODE, !useLightAppearance) != 0) {
                setBooleanAttribute(hwnd, DWMWA_USE_IMMERSIVE_DARK_MODE_BEFORE_20H1, !useLightAppearance)
            }
            setColorAttribute(hwnd, DWMWA_CAPTION_COLOR, shellTop)
            setColorAttribute(hwnd, DWMWA_BORDER_COLOR, shellTop)
            setColorAttribute(hwnd, DWMWA_TEXT_COLOR, primaryText)
        }.onFailure { error ->
            PhoebeLog.d("Phoebe") { "Windows title bar appearance unavailable: ${error.message}" }
        }
    }

    private fun setBooleanAttribute(hwnd: Pointer, attribute: Int, enabled: Boolean): Int {
        val value = intArrayOf(if (enabled) 1 else 0)
        return DwmApi.INSTANCE.DwmSetWindowAttribute(hwnd, attribute, value, Int.SIZE_BYTES)
    }

    private fun setColorAttribute(hwnd: Pointer, attribute: Int, argb: Int) {
        val value = intArrayOf(argb.toColorRef())
        DwmApi.INSTANCE.DwmSetWindowAttribute(hwnd, attribute, value, Int.SIZE_BYTES)
    }

    private fun Int.toColorRef(): Int {
        val red = this shr 16 and 0xFF
        val green = this shr 8 and 0xFF
        val blue = this and 0xFF
        return red or (green shl 8) or (blue shl 16)
    }

    private interface DwmApi : Library {
        fun DwmSetWindowAttribute(hwnd: Pointer, attribute: Int, value: IntArray, size: Int): Int

        companion object {
            val INSTANCE: DwmApi = Native.load("dwmapi", DwmApi::class.java)
        }
    }
}
