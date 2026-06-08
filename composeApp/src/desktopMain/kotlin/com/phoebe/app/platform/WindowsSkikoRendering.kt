package com.phoebe.app.platform

/**
 * Configures Skiko before the first frame on Windows.
 *
 * Compose Desktop on Windows is sensitive to renderer choice and when system properties are
 * applied. Hover flicker is a known Skiko/Swing issue (especially with adaptive-sync displays);
 * ANGLE is usually the most stable GPU path. Override with `PHOEBE_SKIKO_RENDER_API` or
 * `-Dphoebe.skiko.renderApi` (`ANGLE`, `OPENGL`, `DIRECT3D`, `SOFTWARE`, `SOFTWARE_COMPAT`).
 */
internal fun configureWindowsDesktopRendering() {
    if (!isWindowsDesktop()) return
    if (System.getProperty("skiko.renderApi") != null ||
        System.getProperty("skiko.rendering.angle.enabled") != null
    ) {
        return
    }

    val renderApi = System.getenv("PHOEBE_SKIKO_RENDER_API")
        ?.trim()
        ?.takeIf { it.isNotEmpty() }
        ?: System.getProperty("phoebe.skiko.renderApi")
            ?.trim()
            ?.takeIf { it.isNotEmpty() }

    when (renderApi?.uppercase()) {
        "ANGLE", null -> System.setProperty("skiko.rendering.angle.enabled", "true")
        else -> System.setProperty("skiko.renderApi", renderApi.uppercase())
    }
}

internal fun isWindowsDesktop(): Boolean =
    System.getProperty("os.name").orEmpty().startsWith("Windows", ignoreCase = true)
