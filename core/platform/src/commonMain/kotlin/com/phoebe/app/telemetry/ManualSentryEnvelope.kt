package com.phoebe.app.telemetry

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlin.random.Random

internal data class ManualSentryClient(
    val endpoint: String,
    val environment: String,
    val appPlatform: String,
    val eventPlatform: String,
    val sdkName: String,
)

internal data class ManualSentryEnvelope(
    val endpoint: String,
    val body: String,
)

internal data class ManualSentryBreadcrumb(
    val timestampSeconds: Double,
    val level: String,
    val category: String,
    val message: String,
    val data: Map<String, String> = emptyMap(),
)

private val manualSentryJson = Json {
    explicitNulls = false
}

private val dsnPattern = Regex("""^(https?)://([^@]+)@([^/]+)/(\d+).*$""")

internal fun createManualSentryClient(
    dsn: String,
    environment: String,
    platform: String,
): ManualSentryClient? {
    val match = dsnPattern.matchEntire(dsn.trim()) ?: return null
    val scheme = match.groupValues[1]
    val publicKey = match.groupValues[2].substringBefore(':')
    val host = match.groupValues[3]
    val projectId = match.groupValues[4]
    val sdkName = "phoebe-kmp-manual"
    val endpoint = "$scheme://$host/api/$projectId/envelope/" +
        "?sentry_key=$publicKey&sentry_version=7&sentry_client=$sdkName/1.0"
    return ManualSentryClient(
        endpoint = endpoint,
        environment = environment,
        appPlatform = platform,
        eventPlatform = sentryEventPlatform(platform),
        sdkName = sdkName,
    )
}

internal fun manualSentryLogEnvelope(
    client: ManualSentryClient,
    timestampSeconds: Double,
    level: String,
    body: String,
    attributes: Map<String, String>,
): ManualSentryEnvelope {
    val item = buildJsonObject {
        put("timestamp", timestampSeconds)
        put("trace_id", randomSentryHex(byteCount = 16))
        put("level", level)
        put("body", body)
        put("severity_number", severityNumber(level))
        put("attributes", sentryLogAttributes(attributes + baseAttributes(client)))
    }
    val payload = buildJsonObject {
        put("items", buildJsonArray { add(item) })
    }
    return client.envelope(
        envelopeHeader = client.envelopeHeader(),
        itemHeader = buildJsonObject {
            put("type", "log")
            put("item_count", 1)
            put("content_type", "application/vnd.sentry.items.log+json")
        },
        payload = payload,
    )
}

internal fun manualSentryEventEnvelope(
    client: ManualSentryClient,
    timestampSeconds: Double,
    level: String,
    logger: String,
    message: String,
    attributes: Map<String, String>,
    breadcrumbs: List<ManualSentryBreadcrumb>,
    exceptionType: String? = null,
    exceptionValue: String? = null,
    stack: String? = null,
    handled: Boolean = true,
    requestUrl: String? = null,
    userAgent: String? = null,
): ManualSentryEnvelope {
    val eventId = randomSentryHex(byteCount = 16)
    val event = buildJsonObject {
        put("event_id", eventId)
        put("timestamp", timestampSeconds)
        put("platform", client.eventPlatform)
        put("level", level)
        put("logger", logger)
        put("environment", client.environment)
        put(
            "message",
            buildJsonObject {
                put("message", message)
            },
        )
        put("tags", sentryTags(attributes + baseAttributes(client)))
        if (breadcrumbs.isNotEmpty()) {
            put("breadcrumbs", sentryBreadcrumbs(breadcrumbs))
        }
        if (exceptionType != null || exceptionValue != null) {
            put(
                "exception",
                buildJsonObject {
                    put(
                        "values",
                        buildJsonArray {
                            add(
                                buildJsonObject {
                                    put("type", exceptionType ?: "Error")
                                    put("value", exceptionValue ?: message)
                                    put(
                                        "mechanism",
                                        buildJsonObject {
                                            put("type", logger)
                                            put("handled", handled)
                                        },
                                    )
                                },
                            )
                        },
                    )
                },
            )
        }
        if (!stack.isNullOrBlank()) {
            put(
                "extra",
                buildJsonObject {
                    put("stack", stack)
                },
            )
        }
        if (!requestUrl.isNullOrBlank() || !userAgent.isNullOrBlank()) {
            put(
                "request",
                buildJsonObject {
                    requestUrl?.takeIf(String::isNotBlank)?.let { put("url", it) }
                    userAgent?.takeIf(String::isNotBlank)?.let {
                        put(
                            "headers",
                            buildJsonObject {
                                put("User-Agent", it)
                            },
                        )
                    }
                },
            )
        }
    }
    return client.envelope(
        envelopeHeader = buildJsonObject {
            put("event_id", eventId)
            put(
                "sdk",
                buildJsonObject {
                    put("name", client.sdkName)
                    put("version", "1.0")
                },
            )
        },
        itemHeader = buildJsonObject {
            put("type", "event")
            put("content_type", "application/json")
        },
        payload = event,
    )
}

private fun ManualSentryClient.envelope(
    itemHeader: JsonObject,
    payload: JsonObject,
    envelopeHeader: JsonObject,
): ManualSentryEnvelope =
    ManualSentryEnvelope(
        endpoint = endpoint,
        body = listOf(envelopeHeader, itemHeader, payload)
            .joinToString(separator = "\n", postfix = "\n") { jsonObject ->
                manualSentryJson.encodeToString(JsonObject.serializer(), jsonObject)
            },
    )

private fun ManualSentryClient.envelopeHeader(): JsonObject =
    buildJsonObject {
        put(
            "sdk",
            buildJsonObject {
                put("name", sdkName)
                put("version", "1.0")
            },
        )
    }

private fun sentryTags(attributes: Map<String, String>): JsonObject =
    buildJsonObject {
        attributes.forEach { (key, value) ->
            put(key, value)
        }
    }

private fun sentryLogAttributes(attributes: Map<String, String>): JsonObject =
    buildJsonObject {
        attributes.forEach { (key, value) ->
            put(
                key,
                buildJsonObject {
                    put("value", value)
                    put("type", "string")
                },
            )
        }
    }

private fun sentryBreadcrumbs(breadcrumbs: List<ManualSentryBreadcrumb>): JsonObject =
    buildJsonObject {
        put(
            "values",
            buildJsonArray {
                breadcrumbs.forEach { breadcrumb ->
                    add(
                        buildJsonObject {
                            put("timestamp", breadcrumb.timestampSeconds)
                            put("type", "default")
                            put("category", breadcrumb.category)
                            put("level", breadcrumb.level)
                            put("message", breadcrumb.message)
                            if (breadcrumb.data.isNotEmpty()) {
                                put("data", sentryTags(breadcrumb.data))
                            }
                        },
                    )
                }
            },
        )
    }

private fun baseAttributes(client: ManualSentryClient): Map<String, String> =
    mapOf("app.platform" to client.appPlatform)

private fun sentryEventPlatform(appPlatform: String): String =
    when (appPlatform) {
        "web" -> "javascript"
        "ios" -> "cocoa"
        else -> appPlatform
    }

private fun severityNumber(level: String): Int =
    when (level) {
        "trace" -> 1
        "debug" -> 5
        "info" -> 9
        "warn" -> 13
        "error" -> 17
        "fatal" -> 21
        else -> 9
    }

private fun randomSentryHex(byteCount: Int): String {
    val hex = "0123456789abcdef"
    return buildString(byteCount * 2) {
        repeat(byteCount) {
            val value = Random.nextInt(0, 256)
            append(hex[value ushr 4])
            append(hex[value and 0x0f])
        }
    }
}
