package com.phoebe.app.telemetry

private const val TelemetryPlatform = "web"
private const val MaxBreadcrumbs = 80

private var sentryClient: ManualSentryClient? = null
private var currentScreen: String? = null
private val breadcrumbs = ArrayDeque<ManualSentryBreadcrumb>()

internal actual fun platformInitializeTelemetry(
    dsn: String,
    debug: Boolean,
    environment: String,
) {
    sentryClient = createManualSentryClient(
        dsn = dsn,
        environment = environment,
        platform = TelemetryPlatform,
    )
    installGlobalErrorHandlers { message, type, stack, url, userAgent ->
        val attributes = currentScreenAttribute()
        recordBreadcrumb(
            level = "error",
            category = "global",
            message = message,
            data = attributes,
        )
        sendEvent(
            level = "error",
            logger = "global",
            message = message,
            attributes = attributes,
            exceptionType = type.ifBlank { "Error" },
            exceptionValue = message,
            stack = stack,
            handled = false,
            requestUrl = url,
            userAgent = userAgent,
        )
    }
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
        requestUrl = currentBrowserUrl(),
        userAgent = currentUserAgent(),
    )
}

internal actual fun platformCloseTelemetry() {
    sentryClient = null
    currentScreen = null
    breadcrumbs.clear()
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
    requestUrl: String?,
    userAgent: String?,
) {
    val client = sentryClient ?: return
    val envelope = manualSentryEventEnvelope(
        client = client,
        timestampSeconds = timestampSeconds(),
        level = level,
        logger = logger,
        message = message,
        attributes = attributes,
        breadcrumbs = breadcrumbs.toList(),
        exceptionType = exceptionType,
        exceptionValue = exceptionValue,
        stack = stack,
        handled = handled,
        requestUrl = requestUrl,
        userAgent = userAgent,
    )
    sendEnvelope(envelope)
}

private fun sendEnvelope(envelope: ManualSentryEnvelope) {
    sendSentryEnvelope(envelope.endpoint, envelope.body)
}

private fun recordBreadcrumb(
    level: String,
    category: String,
    message: String,
    data: Map<String, String> = emptyMap(),
) {
    breadcrumbs.addLast(
        ManualSentryBreadcrumb(
            timestampSeconds = timestampSeconds(),
            level = level,
            category = category,
            message = message,
            data = data,
        ),
    )
    while (breadcrumbs.size > MaxBreadcrumbs) {
        breadcrumbs.removeFirst()
    }
}

private fun screenAttributes(name: String, previous: String?): Map<String, String> =
    mapOf("screen.name" to name) + previous
        ?.let { mapOf("screen.previous" to it) }
        .orEmpty()

private fun currentScreenAttribute(): Map<String, String> =
    currentScreen?.let { mapOf("screen.name" to it) }.orEmpty()

@OptIn(ExperimentalWasmJsInterop::class)
@JsFun("() => Date.now() / 1000")
private external fun timestampSeconds(): Double

@OptIn(ExperimentalWasmJsInterop::class)
@JsFun("() => typeof location !== 'undefined' ? String(location.href || '') : ''")
private external fun currentBrowserUrl(): String

@OptIn(ExperimentalWasmJsInterop::class)
@JsFun("() => typeof navigator !== 'undefined' ? String(navigator.userAgent || '') : ''")
private external fun currentUserAgent(): String

@OptIn(ExperimentalWasmJsInterop::class)
@JsFun(
    """
    (endpoint, body) => {
      try {
        const payload = String(body || "");
        if (typeof navigator !== "undefined" && navigator.sendBeacon && payload.length < 60000) {
          const blob = new Blob([payload], { type: "text/plain;charset=UTF-8" });
          if (navigator.sendBeacon(endpoint, blob)) return;
        }
        if (typeof fetch !== "undefined") {
          fetch(endpoint, {
            method: "POST",
            body: payload,
            keepalive: true,
            headers: { "Content-Type": "text/plain;charset=UTF-8" },
          }).catch(() => {});
        }
      } catch (_error) {}
    }
    """,
)
private external fun sendSentryEnvelope(endpoint: String, body: String)

@OptIn(ExperimentalWasmJsInterop::class)
@JsFun(
    """
    (callback) => {
      if (globalThis.__phoebeSentryGlobalHandlersInstalled) return;
      globalThis.__phoebeSentryGlobalHandlersInstalled = true;
      const url = () => typeof location !== "undefined" ? String(location.href || "") : "";
      const userAgent = () => typeof navigator !== "undefined" ? String(navigator.userAgent || "") : "";
      const errorMessage = (error, fallback) => {
        if (error && error.message) return String(error.message);
        return String(fallback || error || "Unknown browser error");
      };
      const errorType = (error, fallback) => {
        if (error && error.name) return String(error.name);
        return String(fallback || "Error");
      };
      const errorStack = (error) => error && error.stack ? String(error.stack) : "";
      window.addEventListener("error", (event) => {
        const error = event.error;
        const message = errorMessage(error, event.message);
        callback(message, errorType(error, "Error"), errorStack(error), url(), userAgent());
      });
      window.addEventListener("unhandledrejection", (event) => {
        const reason = event.reason;
        const message = errorMessage(reason, reason);
        callback(message, errorType(reason, "UnhandledRejection"), errorStack(reason), url(), userAgent());
      });
    }
    """,
)
private external fun installGlobalErrorHandlers(
    callback: (String, String, String, String, String) -> Unit,
)

private fun Throwable.telemetryType(): String =
    toString().substringBefore(':').ifBlank { "Throwable" }
