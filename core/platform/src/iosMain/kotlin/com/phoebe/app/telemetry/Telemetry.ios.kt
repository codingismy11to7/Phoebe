package com.phoebe.app.telemetry

import io.ktor.client.HttpClient
import io.ktor.client.engine.darwin.Darwin
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.contentType
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import platform.Foundation.NSDate
import platform.Foundation.NSLock
import platform.Foundation.timeIntervalSince1970

private const val TelemetryPlatform = "ios"
private const val MaxBreadcrumbs = 80

private var sentryClient: ManualSentryClient? = null
private var httpClient: HttpClient? = null
private var currentScreen: String? = null
private val breadcrumbs = ArrayDeque<ManualSentryBreadcrumb>()
private val breadcrumbsLock = NSLock()
private val telemetryScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

internal actual fun platformInitializeTelemetry(
    dsn: String,
    debug: Boolean,
    environment: String,
) {
    if (httpClient == null) {
        httpClient = HttpClient(Darwin)
    }
    sentryClient = createManualSentryClient(
        dsn = dsn,
        environment = environment,
        platform = TelemetryPlatform,
    )
    recordBreadcrumb(
        level = "debug",
        category = "telemetry",
        message = "Sentry initialized",
        data = mapOf("debug" to debug.toString()),
    )
}

internal actual fun platformTrackScreen(name: String, previous: String?) {
    currentScreen = name
    val attributes = screenAttributes(name, previous)
    recordBreadcrumb(
        level = "info",
        category = "navigation",
        message = "screen.view",
        data = attributes,
    )
    sendLog(
        level = "info",
        body = "screen.view",
        attributes = attributes,
    )
}

internal actual fun platformLogTelemetry(tag: String, message: String) {
    val attributes = mapOf(
        "log.tag" to tag,
    ) + currentScreenAttribute()
    recordBreadcrumb(
        level = "debug",
        category = "log.$tag",
        message = message,
        data = attributes,
    )
    sendLog(
        level = "debug",
        body = message,
        attributes = attributes,
    )
}

internal actual fun platformCaptureException(throwable: Throwable) {
    val message = throwable.message ?: throwable.toString()
    sendEvent(
        level = "error",
        logger = "exception",
        message = message,
        attributes = currentScreenAttribute(),
        exceptionType = throwable.telemetryType(),
        exceptionValue = message,
        stack = throwable.stackTraceToString(),
        handled = true,
    )
}

internal actual fun platformCloseTelemetry() {
    sentryClient = null
    currentScreen = null
    breadcrumbsLock.withLock {
        breadcrumbs.clear()
    }
    httpClient?.close()
    httpClient = null
}

private fun sendLog(
    level: String,
    body: String,
    attributes: Map<String, String>,
) {
    val client = sentryClient ?: return
    val envelope = manualSentryLogEnvelope(
        client = client,
        timestampSeconds = timestampSeconds(),
        level = level,
        body = body,
        attributes = attributes,
    )
    sendEnvelope(envelope)
}

private fun sendEvent(
    level: String,
    logger: String,
    message: String,
    attributes: Map<String, String>,
    exceptionType: String?,
    exceptionValue: String?,
    stack: String?,
    handled: Boolean,
) {
    val client = sentryClient ?: return
    val breadcrumbsSnapshot = breadcrumbsLock.withLock {
        breadcrumbs.toList()
    }
    val envelope = manualSentryEventEnvelope(
        client = client,
        timestampSeconds = timestampSeconds(),
        level = level,
        logger = logger,
        message = message,
        attributes = attributes,
        breadcrumbs = breadcrumbsSnapshot,
        exceptionType = exceptionType,
        exceptionValue = exceptionValue,
        stack = stack,
        handled = handled,
    )
    sendEnvelope(envelope)
}

private fun sendEnvelope(envelope: ManualSentryEnvelope) {
    val client = httpClient ?: return
    telemetryScope.launch {
        runCatching {
            client.post(envelope.endpoint) {
                contentType(ContentType.Text.Plain)
                setBody(envelope.body)
            }
        }
    }
}

private fun recordBreadcrumb(
    level: String,
    category: String,
    message: String,
    data: Map<String, String> = emptyMap(),
) {
    val breadcrumb = ManualSentryBreadcrumb(
        timestampSeconds = timestampSeconds(),
        level = level,
        category = category,
        message = message,
        data = data,
    )
    breadcrumbsLock.withLock {
        breadcrumbs.addLast(breadcrumb)
        while (breadcrumbs.size > MaxBreadcrumbs) {
            breadcrumbs.removeFirst()
        }
    }
}

private inline fun <T> NSLock.withLock(block: () -> T): T {
    lock()
    return try {
        block()
    } finally {
        unlock()
    }
}

private fun screenAttributes(name: String, previous: String?): Map<String, String> =
    mapOf("screen.name" to name) + previous
        ?.let { mapOf("screen.previous" to it) }
        .orEmpty()

private fun currentScreenAttribute(): Map<String, String> =
    currentScreen?.let { mapOf("screen.name" to it) }.orEmpty()

private fun timestampSeconds(): Double =
    NSDate().timeIntervalSince1970

private fun Throwable.telemetryType(): String =
    toString().substringBefore(':').ifBlank { "Throwable" }
