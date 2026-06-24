package com.phoebe.app.data

import com.phoebe.app.domain.Track
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.forms.FormDataContent
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import io.ktor.http.Parameters
import io.ktor.http.URLBuilder
import io.ktor.http.isSuccess
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonPrimitive

@SingleIn(AppScope::class)
@Inject
class LastFmClient(
    private val httpClient: HttpClient,
    baseUrl: String = LastFmApiBaseUrl,
) {
    private val apiBase = baseUrl.trimEnd('/')

    suspend fun getToken(apiKey: String, sharedSecret: String): String {
        val response = signedPost(
            apiKey = apiKey,
            sharedSecret = sharedSecret,
            params = mapOf("method" to "auth.getToken"),
            operation = "auth token",
        )
        val body: LastFmTokenResponse = response.body()
        return body.token?.trim()?.takeIf { it.isNotBlank() }
            ?: throw LastFmRequestException(response.status.value, "Last.fm auth token response did not include a token.")
    }

    suspend fun getSession(apiKey: String, sharedSecret: String, token: String): LastFmSession {
        val response = signedPost(
            apiKey = apiKey,
            sharedSecret = sharedSecret,
            params = mapOf(
                "method" to "auth.getSession",
                "token" to token.trim(),
            ),
            operation = "session key",
        )
        val body: LastFmSessionResponse = response.body()
        val session = body.session
        val sessionKey = session?.key?.trim()?.takeIf { it.isNotBlank() }
        if (sessionKey == null) {
            throw LastFmRequestException(response.status.value, "Last.fm session response did not include a session key.")
        }
        return LastFmSession(
            key = sessionKey,
            username = session.name?.trim()?.takeIf { it.isNotBlank() },
        )
    }

    fun authorizationUrl(apiKey: String, token: String): String =
        URLBuilder(LastFmAuthorizationBaseUrl).apply {
            parameters.append("api_key", apiKey.trim())
            parameters.append("token", token.trim())
        }.buildString()

    suspend fun validateSession(apiKey: String, sharedSecret: String, sessionKey: String): LastFmSessionValidation {
        val response = signedPost(
            apiKey = apiKey,
            sharedSecret = sharedSecret,
            sessionKey = sessionKey,
            params = mapOf("method" to "user.getInfo"),
            operation = "session validation",
        )
        val body: LastFmUserGetInfoResponse = response.body()
        val username = body.user?.name?.trim()?.takeIf { it.isNotBlank() }
        return LastFmSessionValidation(valid = username != null, username = username)
    }

    suspend fun updateNowPlaying(apiKey: String, sharedSecret: String, sessionKey: String, track: Track) {
        signedPost(
            apiKey = apiKey,
            sharedSecret = sharedSecret,
            sessionKey = sessionKey,
            params = track.lastFmTrackParams("track.updateNowPlaying"),
            operation = "now playing",
        )
    }

    suspend fun scrobble(apiKey: String, sharedSecret: String, sessionKey: String, scrobble: LastFmQueuedScrobble) {
        val response = signedPost(
            apiKey = apiKey,
            sharedSecret = sharedSecret,
            sessionKey = sessionKey,
            params = scrobble.track.lastFmTrackParams("track.scrobble") +
                ("timestamp" to (scrobble.listenedAtMs / 1000L).toString()),
            operation = "scrobble",
        )
        val body: LastFmScrobbleResponse = response.body()
        val accepted = body.scrobbles?.attr?.acceptedCount() ?: return
        if (accepted > 0) return
        val ignored = body.scrobbles.scrobble?.ignoredMessage
        val reason = ignored?.text?.takeIf { it.isNotBlank() }
            ?: "Last.fm ignored the scrobble."
        throw LastFmRequestException(response.status.value, reason)
    }

    private suspend fun signedPost(
        apiKey: String,
        sharedSecret: String,
        sessionKey: String? = null,
        params: Map<String, String>,
        operation: String,
    ): HttpResponse {
        val signedParams = params
            .filterValues { it.isNotBlank() }
            .plus("api_key" to apiKey.trim())
            .let { signedParams ->
                sessionKey?.trim()?.takeIf { it.isNotBlank() }?.let { signedParams.plus("sk" to it) }
                    ?: signedParams
            }
        val requestParams = signedParams
            .plus("api_sig" to lastFmSignature(signedParams, sharedSecret.trim()))
            .plus("format" to "json")
        val response = httpClient.post(apiBase) {
            setBody(FormDataContent(Parameters.build {
                requestParams.forEach { (key, value) -> append(key, value) }
            }))
        }
        response.ensureLastFmSuccess(operation)
        return response
    }
}

data class LastFmSessionValidation(
    val valid: Boolean,
    val username: String?,
)

data class LastFmSession(
    val key: String,
    val username: String?,
)

data class LastFmQueuedScrobble(
    val track: Track,
    val listenedAtMs: Long,
)

class LastFmUnauthorizedException(message: String) : IllegalStateException(message)

class LastFmRequestException(
    val statusCode: Int,
    message: String,
) : IllegalStateException(message)

@Serializable
private data class LastFmUserGetInfoResponse(
    val user: LastFmUser? = null,
)

@Serializable
private data class LastFmUser(
    val name: String? = null,
)

@Serializable
private data class LastFmTokenResponse(
    val token: String? = null,
)

@Serializable
private data class LastFmSessionResponse(
    val session: LastFmSessionPayload? = null,
)

@Serializable
private data class LastFmSessionPayload(
    val name: String? = null,
    val key: String? = null,
)

@Serializable
private data class LastFmScrobbleResponse(
    val scrobbles: LastFmScrobblesPayload? = null,
)

@Serializable
private data class LastFmScrobblesPayload(
    @SerialName("@attr")
    val attr: LastFmScrobbleAttr? = null,
    val scrobble: LastFmScrobblePayload? = null,
)

@Serializable
private data class LastFmScrobbleAttr(
    val accepted: JsonElement? = null,
    val ignored: JsonElement? = null,
) {
    fun acceptedCount(): Int = accepted.lastFmInt()
}

@Serializable
private data class LastFmScrobblePayload(
    val ignoredMessage: LastFmIgnoredMessage? = null,
)

@Serializable
private data class LastFmIgnoredMessage(
    val code: String? = null,
    @SerialName("#text")
    val text: String? = null,
)

private fun JsonElement?.lastFmInt(): Int =
    this?.jsonPrimitive?.intOrNull ?: 0

@Serializable
private data class LastFmErrorResponse(
    val error: Int? = null,
    val message: String? = null,
    @SerialName("links")
    val links: List<String> = emptyList(),
)

private fun Track.lastFmTrackParams(method: String): Map<String, String> =
    buildMap {
        put("method", method)
        put("artist", artist.ifBlank { "Unknown artist" })
        put("track", title.ifBlank { "Untitled track" })
        album.takeIf { it.isNotBlank() }?.let { put("album", it) }
        albumArtist?.takeIf { it.isNotBlank() }?.let { put("albumArtist", it) }
        durationMs.takeIf { it > 0L }?.let { put("duration", (it / 1000L).toString()) }
        musicBrainzTrackId?.takeIf { it.isNotBlank() }?.let { put("mbid", it) }
    }

internal fun lastFmSignature(params: Map<String, String>, sharedSecret: String): String =
    md5HexForLastFm(
        params
            .filterKeys { it != "format" && it != "callback" && it != "api_sig" }
            .entries
            .sortedBy { entry -> entry.key }
            .joinToString(separator = "") { entry -> entry.key + entry.value } + sharedSecret,
    )

private suspend fun HttpResponse.ensureLastFmSuccess(operation: String) {
    val text = bodyAsText()
    if (status.isSuccess()) {
        val error = runCatching { ListenBrainzJson.decodeFromString<LastFmErrorResponse>(text) }.getOrNull()
        if (error?.error == null) return
        throw error.toLastFmException(operation, status.value)
    }
    val error = runCatching { ListenBrainzJson.decodeFromString<LastFmErrorResponse>(text) }.getOrNull()
    if (error != null) throw error.toLastFmException(operation, status.value)
    throw LastFmRequestException(status.value, "Last.fm $operation failed (${status.value}): ${text.take(240)}")
}

private fun LastFmErrorResponse.toLastFmException(operation: String, statusCode: Int): IllegalStateException {
    val message = message?.takeIf { it.isNotBlank() } ?: "Last.fm $operation failed."
    return if (error == 4 || error == 9 || error == 14 || error == 15) {
        LastFmUnauthorizedException(message)
    } else {
        LastFmRequestException(statusCode, "Last.fm $operation failed: $message")
    }
}

private const val LastFmApiBaseUrl = "https://ws.audioscrobbler.com/2.0/"
private const val LastFmAuthorizationBaseUrl = "https://www.last.fm/api/auth/"
