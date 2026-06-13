package com.phoebe.app.platform

import androidx.compose.ui.awt.ComposeWindow
import com.sun.jna.Native
import com.sun.jna.Pointer
import com.sun.jna.platform.win32.BaseTSD.LONG_PTR
import com.sun.jna.platform.win32.WinDef.HWND
import com.sun.jna.platform.win32.WinDef.LPARAM
import com.sun.jna.platform.win32.WinDef.LRESULT
import com.sun.jna.platform.win32.WinDef.RECT
import com.sun.jna.platform.win32.WinDef.WPARAM
import com.sun.jna.platform.win32.WinUser.WindowProc
import com.sun.jna.win32.StdCallLibrary
import com.sun.jna.win32.W32APIOptions
import java.awt.EventQueue
import java.awt.Window
import java.awt.event.WindowAdapter
import java.awt.event.WindowEvent

/**
 * Extra `user32.dll` entry points required to subclass an AWT/Compose window procedure.
 *
 * The standard Compose `undecorated = true` window relies on a pure-Kotlin resizer
 * (`UndecoratedWindowResizer`) for edge dragging, which feels laggy compared to native
 * resizing and Aero Snap. Re-routing `WM_NCHITTEST` through the OS restores the native
 * resize/snap behaviour while `WM_NCCALCSIZE` keeps the title bar hidden so our custom
 * Compose chrome can still own the whole window.
 */
internal interface User32Ex : StdCallLibrary {
    fun GetWindowLong(hWnd: HWND, nIndex: Int): Int
    fun SetWindowLong(hWnd: HWND, nIndex: Int, dwNewLong: Int): Int
    fun SetWindowLong(hWnd: HWND, nIndex: Int, proc: WindowProc): LONG_PTR
    fun SetWindowLongPtr(hWnd: HWND, nIndex: Int, proc: WindowProc): LONG_PTR
    fun CallWindowProc(proc: LONG_PTR, hWnd: HWND, uMsg: Int, wParam: WPARAM, lParam: LPARAM): LRESULT
    fun GetWindowRect(hWnd: HWND, rect: RECT): Boolean
    fun GetSystemMetrics(nIndex: Int): Int
    fun IsZoomed(hWnd: HWND): Boolean
    fun SetWindowPos(
        hWnd: HWND,
        hWndInsertAfter: HWND?,
        x: Int,
        y: Int,
        cx: Int,
        cy: Int,
        uFlags: Int,
    ): Boolean

    companion object {
        val INSTANCE: User32Ex? = runCatching {
            Native.load("user32", User32Ex::class.java, W32APIOptions.DEFAULT_OPTIONS)
        }.onFailure {
            PhoebeLog.d("Phoebe") { "Borderless window support unavailable: ${it.message}" }
        }.getOrNull()
    }
}

/** Resolves the native top-level window handle for an AWT window. */
internal object WindowsHwnd {
    fun resolve(window: Window): HWND? {
        val composeHandle = (window as? ComposeWindow)?.windowHandle ?: 0L
        if (composeHandle != 0L) return HWND(Pointer(composeHandle))
        val pointer = runCatching { Native.getWindowPointer(window) }
            .getOrNull()
            ?.takeIf { it != Pointer.NULL }
            ?: return null
        return HWND(pointer)
    }
}

/** System frame metrics used for both maximized client insets and resize hit-testing. */
internal object WindowsWindowMetrics {
    private const val SM_CXSIZEFRAME = 32
    private const val SM_CYSIZEFRAME = 33
    private const val SM_CXPADDEDBORDER = 92
    private const val MIN_RESIZE_BORDER = 6

    fun horizontalFrame(user32: User32Ex): Int =
        user32.GetSystemMetrics(SM_CXSIZEFRAME) + user32.GetSystemMetrics(SM_CXPADDEDBORDER)

    fun verticalFrame(user32: User32Ex): Int =
        user32.GetSystemMetrics(SM_CYSIZEFRAME) + user32.GetSystemMetrics(SM_CXPADDEDBORDER)

    /** Grab thickness (physical px) used for the invisible resize border. */
    fun resizeBorder(user32: User32Ex): Int =
        horizontalFrame(user32).coerceAtLeast(MIN_RESIZE_BORDER)
}

/** Reads the live frame state (currently just maximize) of a native window. */
internal object WindowsFrameStateSync {
    fun isMaximized(user32: User32Ex, hwnd: HWND): Boolean =
        runCatching { user32.IsZoomed(hwnd) }.getOrDefault(false)
}

/**
 * Subclassed window procedure that gives an `undecorated = true` Compose window native
 * edge-resize, Aero Snap and maximize/restore animations.
 *
 * - [WM_NCCALCSIZE] returns 0 so the entire window becomes client area (no native title bar),
 *   except when maximized where the client rect is inset by the frame to avoid spilling
 *   past the monitor work area.
 * - [WM_NCHITTEST] reports the standard resize-border hit codes near the window edges so the
 *   OS performs the resize itself; everything else stays `HTCLIENT` so Compose keeps handling
 *   clicks and `WindowDraggableArea` keeps handling title-bar dragging.
 */
internal class BorderlessWindowProcedure(
    private val hwnd: HWND,
    private val user32: User32Ex,
) : WindowProc {

    private val defaultProc: LONG_PTR = subclass()
    private var cachedWindowRect: RECT? = null

    private fun subclass(): LONG_PTR {
        val previous = runCatching { user32.SetWindowLongPtr(hwnd, GWLP_WNDPROC, this) }
            .getOrElse { user32.SetWindowLong(hwnd, GWLP_WNDPROC, this) }
        enableNativeResize()
        return previous
    }

    private fun enableNativeResize() {
        val style = user32.GetWindowLong(hwnd, GWL_STYLE)
        val resizable = style or WS_CAPTION or WS_THICKFRAME or WS_MAXIMIZEBOX or WS_MINIMIZEBOX
        if (resizable != style) {
            user32.SetWindowLong(hwnd, GWL_STYLE, resizable)
        }
        user32.SetWindowPos(
            hwnd,
            null,
            0,
            0,
            0,
            0,
            SWP_NOMOVE or SWP_NOSIZE or SWP_NOZORDER or SWP_NOACTIVATE or SWP_FRAMECHANGED,
        )
    }

    override fun callback(hWnd: HWND, uMsg: Int, wParam: WPARAM, lParam: LPARAM): LRESULT =
        when (uMsg) {
            WM_SIZE, WM_MOVE, WM_WINDOWPOSCHANGED -> {
                cachedWindowRect = null
                callDefault(hWnd, uMsg, wParam, lParam)
            }
            WM_NCCALCSIZE -> onNcCalcSize(hWnd, uMsg, wParam, lParam)
            WM_NCHITTEST -> onNcHitTest(lParam)
            else -> callDefault(hWnd, uMsg, wParam, lParam)
        }

    private fun callDefault(hWnd: HWND, uMsg: Int, wParam: WPARAM, lParam: LPARAM): LRESULT =
        user32.CallWindowProc(defaultProc, hWnd, uMsg, wParam, lParam)

    private fun onNcCalcSize(hWnd: HWND, uMsg: Int, wParam: WPARAM, lParam: LPARAM): LRESULT {
        if (wParam.toInt() == 0) return callDefault(hWnd, uMsg, wParam, lParam)
        if (WindowsFrameStateSync.isMaximized(user32, hwnd)) {
            // NCCALCSIZE_PARAMS.rgrc[0] is the proposed window rect; inset it by the frame so a
            // maximized borderless window does not overflow the monitor work area.
            val rect = Pointer(lParam.toLong())
            val insetX = WindowsWindowMetrics.horizontalFrame(user32)
            val insetY = WindowsWindowMetrics.verticalFrame(user32)
            rect.setInt(RECT_LEFT, rect.getInt(RECT_LEFT) + insetX)
            rect.setInt(RECT_TOP, rect.getInt(RECT_TOP) + insetY)
            rect.setInt(RECT_RIGHT, rect.getInt(RECT_RIGHT) - insetX)
            rect.setInt(RECT_BOTTOM, rect.getInt(RECT_BOTTOM) - insetY)
        }
        return LRESULT(0)
    }

    private fun windowRect(): RECT? {
        cachedWindowRect?.let { return it }
        val rect = RECT()
        if (!user32.GetWindowRect(hwnd, rect)) return null
        cachedWindowRect = rect
        return rect
    }

    private fun onNcHitTest(lParam: LPARAM): LRESULT {
        if (WindowsFrameStateSync.isMaximized(user32, hwnd)) return LRESULT(HTCLIENT.toLong())
        val packed = lParam.toInt()
        val x = (packed and 0xFFFF).toShort().toInt()
        val y = ((packed ushr 16) and 0xFFFF).toShort().toInt()
        val rect = windowRect() ?: return LRESULT(HTCLIENT.toLong())
        val border = WindowsWindowMetrics.resizeBorder(user32)
        val onLeft = x < rect.left + border
        val onRight = x >= rect.right - border
        val onTop = y < rect.top + border
        val onBottom = y >= rect.bottom - border
        val hit = when {
            onTop && onLeft -> HTTOPLEFT
            onTop && onRight -> HTTOPRIGHT
            onBottom && onLeft -> HTBOTTOMLEFT
            onBottom && onRight -> HTBOTTOMRIGHT
            onLeft -> HTLEFT
            onRight -> HTRIGHT
            onTop -> HTTOP
            onBottom -> HTBOTTOM
            else -> HTCLIENT
        }
        return LRESULT(hit.toLong())
    }

    private companion object {
        const val GWL_STYLE = -16
        const val GWLP_WNDPROC = -4

        const val WS_CAPTION = 0x00C00000
        const val WS_THICKFRAME = 0x00040000
        const val WS_MINIMIZEBOX = 0x00020000
        const val WS_MAXIMIZEBOX = 0x00010000

        const val SWP_NOSIZE = 0x0001
        const val SWP_NOMOVE = 0x0002
        const val SWP_NOZORDER = 0x0004
        const val SWP_NOACTIVATE = 0x0010
        const val SWP_FRAMECHANGED = 0x0020

        const val WM_SIZE = 0x0005
        const val WM_MOVE = 0x0003
        const val WM_WINDOWPOSCHANGED = 0x0047
        const val WM_NCCALCSIZE = 0x0083
        const val WM_NCHITTEST = 0x0084

        const val HTCLIENT = 1
        const val HTLEFT = 10
        const val HTRIGHT = 11
        const val HTTOP = 12
        const val HTTOPLEFT = 13
        const val HTTOPRIGHT = 14
        const val HTBOTTOM = 15
        const val HTBOTTOMLEFT = 16
        const val HTBOTTOMRIGHT = 17

        const val RECT_LEFT = 0L
        const val RECT_TOP = 4L
        const val RECT_RIGHT = 8L
        const val RECT_BOTTOM = 12L
    }
}

/**
 * Installs [BorderlessWindowProcedure] on the Windows main window so edge-drag resizing and
 * Aero Snap behave like a native window. No-op on other platforms.
 */
object WindowsUndecoratedWindowSupport {
    private const val MAX_ATTEMPTS = 12
    private val isWindows = System.getProperty("os.name").orEmpty().startsWith("Windows", ignoreCase = true)

    // Retains the WindowProc callbacks; JNA frees the native trampoline once the Kotlin object
    // is garbage collected, which would crash the window. Keyed by native HWND value.
    private val procedures = mutableMapOf<Long, BorderlessWindowProcedure>()

    fun install(window: Window, attempt: Int = 0) {
        if (!isWindows) return
        if (!EventQueue.isDispatchThread()) {
            EventQueue.invokeLater { install(window, attempt) }
            return
        }
        val user32 = User32Ex.INSTANCE ?: return
        val hwnd = WindowsHwnd.resolve(window)
        if (hwnd == null) {
            if (attempt < MAX_ATTEMPTS) {
                EventQueue.invokeLater { install(window, attempt + 1) }
            }
            return
        }
        val key = Pointer.nativeValue(hwnd.pointer)
        if (procedures.containsKey(key)) return
        val procedure = runCatching { BorderlessWindowProcedure(hwnd, user32) }
            .onFailure { PhoebeLog.d("Phoebe") { "Failed to install borderless window proc: ${it.message}" } }
            .getOrNull()
            ?: return
        procedures[key] = procedure
        window.addWindowListener(object : WindowAdapter() {
            override fun windowClosed(event: WindowEvent) {
                window.removeWindowListener(this)
                procedures.remove(key)
            }
        })
    }
}
