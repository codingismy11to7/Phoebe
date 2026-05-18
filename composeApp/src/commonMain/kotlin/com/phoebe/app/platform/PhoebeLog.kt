package com.phoebe.app.platform

import com.phoebe.app.telemetry.Telemetry

/** True for local/dev builds; false for store/release artifacts. */
expect fun isDebugBuild(): Boolean

/**
 * Local platform output is debug-only; telemetry still receives logs when configured.
 * Prefer [v] for high-volume traces and [d] for lifecycle / error detail.
 */
object PhoebeLog {
    fun v(tag: String, message: String) {
        if (isDebugBuild()) platformLog(tag, message)
        Telemetry.log(tag, message)
    }

    fun v(tag: String, lazyMessage: () -> String) {
        val message = lazyMessage()
        if (isDebugBuild()) platformLog(tag, message)
        Telemetry.log(tag, message)
    }

    fun d(tag: String, message: String) {
        if (isDebugBuild()) platformLog(tag, message)
        Telemetry.log(tag, message)
    }

    fun d(tag: String, lazyMessage: () -> String) {
        val message = lazyMessage()
        if (isDebugBuild()) platformLog(tag, message)
        Telemetry.log(tag, message)
    }
}

internal expect fun platformLog(tag: String, message: String)
