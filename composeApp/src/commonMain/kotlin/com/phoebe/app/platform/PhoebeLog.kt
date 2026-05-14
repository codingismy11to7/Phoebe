package com.phoebe.app.platform

/** True for local/dev builds; false for store/release artifacts. */
expect fun isDebugBuild(): Boolean

/**
 * Debug-only logging. All methods no-op in release builds.
 * Prefer [v] for high-volume traces and [d] for lifecycle / error detail.
 */
object PhoebeLog {
    fun v(tag: String, message: String) {
        if (isDebugBuild()) platformLog(tag, message)
    }

    fun v(tag: String, lazyMessage: () -> String) {
        if (isDebugBuild()) platformLog(tag, lazyMessage())
    }

    fun d(tag: String, message: String) {
        if (isDebugBuild()) platformLog(tag, message)
    }

    fun d(tag: String, lazyMessage: () -> String) {
        if (isDebugBuild()) platformLog(tag, lazyMessage())
    }
}

internal expect fun platformLog(tag: String, message: String)
