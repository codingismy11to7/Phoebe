package com.phoebe.app.data

import com.phoebe.app.domain.Track
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.header
import io.ktor.client.request.get
import io.ktor.client.request.parameter
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.contentType
import io.ktor.http.isSuccess
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonPrimitive

@SingleIn(AppScope::class)
@Inject
class ListenBrainzClient(
    private val httpClient: HttpClient,
    baseUrl: String = ListenBrainzApiBaseUrl,
) {
    private val apiBase = baseUrl.trimEnd('/')

    suspend fun validateToken(token: String): ListenBrainzTokenValidation {
        val response: ListenBrainzValidateTokenResponse = httpClient.get("$apiBase/1/validate-token") {
            listenBrainzAuth(token)
        }.body()
        return ListenBrainzTokenValidation(
            valid = response.valid,
            username = response.userName?.takeIf { it.isNotBlank() },
            message = response.message,
        )
    }

    suspend fun submitPlayingNow(token: String, track: Track): String? {
        val response = httpClient.post("$apiBase/1/submit-listens") {
            listenBrainzAuth(token)
            contentType(ContentType.Application.Json)
            parameter("return_msid", true)
            setBody(
                ListenBrainzSubmitListensRequest(
                    listenType = ListenBrainzListenType.PlayingNow.value,
                    payload = listOf(track.toListenBrainzPayload(listenedAtSeconds = null)),
                ),
            )
        }
        response.ensureListenBrainzSuccess("playing-now")
        return response.recordingMsidOrNull()
    }

    suspend fun submitListen(token: String, listen: ListenBrainzQueuedListen) {
        submitListens(token, listOf(listen))
    }

    suspend fun submitListens(token: String, listens: List<ListenBrainzQueuedListen>) {
        if (listens.isEmpty()) return
        val response = httpClient.post("$apiBase/1/submit-listens") {
            listenBrainzAuth(token)
            contentType(ContentType.Application.Json)
            setBody(
                ListenBrainzSubmitListensRequest(
                    listenType = ListenBrainzListenType.Single.value,
                    payload = listens.map { listen ->
                        listen.track.toListenBrainzPayload(listenedAtSeconds = listen.listenedAtMs / 1000L)
                    },
                ),
            )
        }
        response.ensureListenBrainzSuccess("listen submit")
    }

    suspend fun deletePlayingNow(token: String) {
        val response = httpClient.post("$apiBase/1/playing-now/delete") {
            listenBrainzAuth(token)
            contentType(ContentType.Application.Json)
            setBody(ListenBrainzDeletePlayingNowRequest(client = ListenBrainzSubmissionClient))
        }
        response.ensureListenBrainzSuccess("playing-now delete")
    }

    suspend fun submitRecordingFeedback(
        token: String,
        recordingMsid: String,
        score: ListenBrainzFeedbackScore,
    ) {
        val response = httpClient.post("$apiBase/1/feedback/recording-feedback") {
            listenBrainzAuth(token)
            contentType(ContentType.Application.Json)
            setBody(
                ListenBrainzRecordingFeedbackRequest(
                    recordingMsid = recordingMsid,
                    score = score.apiScore,
                ),
            )
        }
        response.ensureListenBrainzSuccess("recording feedback")
    }

    suspend fun getUserFeedbackForRecordingMsids(
        username: String,
        recordingMsids: List<String>,
    ): Map<String, ListenBrainzFeedbackScore?> {
        val normalizedMsids = recordingMsids.mapNotNull { it.trim().takeIf(String::isNotBlank) }.distinct()
        if (username.isBlank() || normalizedMsids.isEmpty()) return emptyMap()
        val response = httpClient.post("$apiBase/1/feedback/user/${username.trim()}/get-feedback-for-recordings") {
            contentType(ContentType.Application.Json)
            setBody(ListenBrainzFeedbackForRecordingsRequest(recordingMsids = normalizedMsids))
        }
        response.ensureListenBrainzSuccess("feedback lookup")
        val body: ListenBrainzFeedbackForRecordingsResponse = response.body()
        return body.feedback.mapNotNull { item ->
            val msid = item.recordingMsid?.takeIf { it.isNotBlank() } ?: return@mapNotNull null
            msid to item.score.toFeedbackScoreOrNull()
        }.toMap()
    }

    private fun io.ktor.client.request.HttpRequestBuilder.listenBrainzAuth(token: String) {
        header(HttpHeaders.Authorization, "Token ${token.trim()}")
    }
}

data class ListenBrainzTokenValidation(
    val valid: Boolean,
    val username: String?,
    val message: String? = null,
)

data class ListenBrainzQueuedListen(
    val track: Track,
    val listenedAtMs: Long,
)

enum class ListenBrainzFeedbackScore(val apiScore: Int) {
    Love(1),
    Hate(-1),
    Clear(0),
}

class ListenBrainzUnauthorizedException(message: String) : IllegalStateException(message)

class ListenBrainzRequestException(
    val statusCode: Int,
    message: String,
) : IllegalStateException(message)

private enum class ListenBrainzListenType(val value: String) {
    PlayingNow("playing_now"),
    Single("single"),
}

@Serializable
private data class ListenBrainzValidateTokenResponse(
    val valid: Boolean = false,
    @SerialName("user_name")
    val userName: String? = null,
    val message: String? = null,
)

@Serializable
private data class ListenBrainzSubmitListensRequest(
    @SerialName("listen_type")
    val listenType: String,
    val payload: List<ListenBrainzListenPayload>,
)

@Serializable
private data class ListenBrainzListenPayload(
    @SerialName("listened_at")
    val listenedAt: Long? = null,
    @SerialName("track_metadata")
    val trackMetadata: ListenBrainzTrackMetadata,
)

@Serializable
private data class ListenBrainzTrackMetadata(
    @SerialName("artist_name")
    val artistName: String,
    @SerialName("track_name")
    val trackName: String,
    @SerialName("release_name")
    val releaseName: String? = null,
    @SerialName("additional_info")
    val additionalInfo: JsonObject,
)

@Serializable
private data class ListenBrainzDeletePlayingNowRequest(
    val client: String,
)

@Serializable
private data class ListenBrainzRecordingFeedbackRequest(
    @SerialName("recording_msid")
    val recordingMsid: String,
    val score: Int,
)

@Serializable
private data class ListenBrainzFeedbackForRecordingsRequest(
    @SerialName("recording_msids")
    val recordingMsids: List<String>,
)

@Serializable
private data class ListenBrainzFeedbackForRecordingsResponse(
    val feedback: List<ListenBrainzFeedbackForRecordingsItem> = emptyList(),
)

@Serializable
private data class ListenBrainzFeedbackForRecordingsItem(
    @SerialName("recording_msid")
    val recordingMsid: String? = null,
    val score: Int = 0,
)

private fun Track.toListenBrainzPayload(listenedAtSeconds: Long?): ListenBrainzListenPayload =
    ListenBrainzListenPayload(
        listenedAt = listenedAtSeconds,
        trackMetadata = ListenBrainzTrackMetadata(
            artistName = artist.ifBlank { "Unknown artist" },
            trackName = title.ifBlank { "Untitled track" },
            releaseName = album.takeIf { it.isNotBlank() },
            additionalInfo = JsonObject(
                buildMap {
                    put("media_player", JsonPrimitive("Phoebe"))
                    put("submission_client", JsonPrimitive(ListenBrainzSubmissionClient))
                    put("duration_ms", JsonPrimitive(durationMs.coerceAtLeast(0L)))
                    put("phoebe_track_id", JsonPrimitive(id))
                    streamUrl.takeIf { it.startsWith("http://") || it.startsWith("https://") }?.let {
                        put("origin_url", JsonPrimitive(it))
                    }
                },
            ),
        ),
    )

private suspend fun HttpResponse.ensureListenBrainzSuccess(operation: String) {
    if (status.isSuccess()) return
    val body = bodyAsText().take(240)
    if (status.value == 401) {
        throw ListenBrainzUnauthorizedException("ListenBrainz token is invalid.")
    }
    throw ListenBrainzRequestException(
        statusCode = status.value,
        message = "ListenBrainz $operation failed (${status.value}): $body",
    )
}

private suspend fun HttpResponse.recordingMsidOrNull(): String? {
    val text = bodyAsText().takeIf { it.isNotBlank() } ?: return null
    val root = runCatching { ListenBrainzJson.parseToJsonElement(text) as? JsonObject }.getOrNull() ?: return null
    return root.stringValue("recording_msid")
        ?: root.objectValue("payload")?.stringValue("recording_msid")
        ?: root.objectValue("payload")?.objectValue("listen")?.stringValue("recording_msid")
}

private fun JsonObject.objectValue(name: String): JsonObject? =
    this[name] as? JsonObject

private fun JsonObject.stringValue(name: String): String? =
    this[name]?.jsonPrimitive?.contentOrNull?.takeIf { it.isNotBlank() }

private fun Int.toFeedbackScoreOrNull(): ListenBrainzFeedbackScore? =
    when (this) {
        ListenBrainzFeedbackScore.Love.apiScore -> ListenBrainzFeedbackScore.Love
        ListenBrainzFeedbackScore.Hate.apiScore -> ListenBrainzFeedbackScore.Hate
        else -> null
    }

private const val ListenBrainzApiBaseUrl = "https://api.listenbrainz.org"
private const val ListenBrainzSubmissionClient = "Phoebe"

internal val ListenBrainzJson = Json {
    ignoreUnknownKeys = true
    explicitNulls = false
}
