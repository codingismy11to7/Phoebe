package com.phoebe.app.telemetry

internal actual fun platformInitializeTelemetry(
    dsn: String,
    debug: Boolean,
    environment: String,
) = Unit

internal actual fun platformTrackScreen(name: String, previous: String?) = Unit

internal actual fun platformLogTelemetry(tag: String, message: String) = Unit

internal actual fun platformCaptureException(throwable: Throwable) = Unit
