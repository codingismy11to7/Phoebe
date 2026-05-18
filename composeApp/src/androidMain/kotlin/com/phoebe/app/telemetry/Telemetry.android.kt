package com.phoebe.app.telemetry

import io.sentry.kotlin.multiplatform.Sentry
import io.sentry.kotlin.multiplatform.SentryLevel
import io.sentry.kotlin.multiplatform.protocol.Breadcrumb

private const val TelemetryPlatform = "android"

internal actual fun platformInitializeTelemetry(
    dsn: String,
    debug: Boolean,
    environment: String,
) {
    Sentry.init { options ->
        options.dsn = dsn
        options.debug = debug
        options.environment = environment
        options.attachScreenshot = true
        options.attachViewHierarchy = true
        options.enableAutoSessionTracking = true
        options.attachStackTrace = true
        options.attachThreads = true
        options.tracesSampleRate = 1.0
        options.logs.enabled = true
    }
    Sentry.configureScope { scope ->
        scope.setTag("app.platform", TelemetryPlatform)
        scope.setContext("app.platform", TelemetryPlatform)
    }
}

internal actual fun platformTrackScreen(name: String, previous: String?) {
    Sentry.configureScope { scope ->
        scope.setTag("app.platform", TelemetryPlatform)
        scope.setTag("screen", name)
        scope.setContext("app.platform", TelemetryPlatform)
        scope.setContext("screen", name)
    }
    Sentry.addBreadcrumb(
        Breadcrumb.navigation(previous ?: "app_start", name).apply {
            level = SentryLevel.INFO
            setData("app.platform", TelemetryPlatform)
        },
    )
    Sentry.logger.info("screen.view") {
        this["app.platform"] = TelemetryPlatform
        this["screen.name"] = name
        previous?.let { this["screen.previous"] = it }
    }
}

internal actual fun platformLogTelemetry(tag: String, message: String) {
    Sentry.addBreadcrumb(
        Breadcrumb.debug(message).apply {
            category = "log.$tag"
            setData("app.platform", TelemetryPlatform)
            setData("log.tag", tag)
        },
    )
    Sentry.logger.debug(message) {
        this["app.platform"] = TelemetryPlatform
        this["log.tag"] = tag
    }
}

internal actual fun platformCaptureException(throwable: Throwable) {
    Sentry.captureException(throwable)
}
