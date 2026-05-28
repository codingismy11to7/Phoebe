package com.phoebe.app

import com.phoebe.app.data.ListenBrainzClient
import com.phoebe.app.data.ListenBrainzFeedbackScore
import com.phoebe.app.data.ListenBrainzQueuedListen
import com.phoebe.app.data.ListenBrainzRequestException
import com.phoebe.app.domain.Track
import com.phoebe.app.testing.testHttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.MockRequestHandleScope
import io.ktor.client.engine.mock.respond
import io.ktor.client.request.HttpRequestData
import io.ktor.client.request.HttpResponseData
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.content.OutgoingContent
import io.ktor.http.headersOf
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ListenBrainzClientTest {
    @Test
    fun validateTokenUsesAuthorizationHeaderAndParsesUsername() = runTest {
        var authorization: String? = null
        val client = clientFor { request ->
            authorization = request.headers[HttpHeaders.Authorization]
            respondJson("""{"valid":true,"user_name":"ada","message":"Token valid."}""")
        }

        val validation = client.validateToken("secret-token")

        assertTrue(validation.valid)
        assertEquals("ada", validation.username)
        assertEquals("Token secret-token", authorization)
    }

    @Test
    fun playingNowOmitsTimestampAndRequestsMsid() = runTest {
        var path = ""
        var returnMsid: String? = null
        var body = ""
        val client = clientFor { request ->
            path = request.url.encodedPath
            returnMsid = request.url.parameters["return_msid"]
            body = request.bodyText()
            respondJson("""{"payload":{"recording_msid":"msid-1"}}""")
        }

        val msid = client.submitPlayingNow("token", track())

        assertEquals("/1/submit-listens", path)
        assertEquals("true", returnMsid)
        assertEquals("msid-1", msid)
        assertTrue(body.contains(""""listen_type":"playing_now""""))
        assertTrue(body.contains(""""track_name":"Song""""))
        assertFalse(body.contains("listened_at"))
    }

    @Test
    fun singleListenIncludesTimestampAndDuration() = runTest {
        var body = ""
        val client = clientFor { request ->
            body = request.bodyText()
            respondJson("""{"status":"ok"}""")
        }

        client.submitListen("token", ListenBrainzQueuedListen(track(), listenedAtMs = 1_700_000_123_000L))

        assertTrue(body.contains(""""listen_type":"single""""))
        assertTrue(body.contains(""""listened_at":1700000123"""))
        assertTrue(body.contains(""""duration_ms":180000"""))
        assertTrue(body.contains(""""submission_client":"Phoebe""""))
    }

    @Test
    fun failedSingleListenExposesStatusCodeAndBody() = runTest {
        val client = clientFor {
            respond(
                content = """{"error":"bad listen"}""",
                status = HttpStatusCode.BadRequest,
                headers = headersOf(HttpHeaders.ContentType, "application/json"),
            )
        }

        val error = assertFailsWith<ListenBrainzRequestException> {
            client.submitListen("token", ListenBrainzQueuedListen(track(), listenedAtMs = 1_700_000_123_000L))
        }

        assertEquals(400, error.statusCode)
        assertTrue(error.message.orEmpty().contains("bad listen"))
    }

    @Test
    fun deletePlayingNowScopesToPhoebeClient() = runTest {
        var body = ""
        val client = clientFor { request ->
            body = request.bodyText()
            respondJson("""{"status":"ok"}""")
        }

        client.deletePlayingNow("token")

        assertTrue(body.contains(""""client":"Phoebe""""))
    }

    @Test
    fun feedbackPostsRequestedScore() = runTest {
        var body = ""
        val client = clientFor { request ->
            body = request.bodyText()
            respondJson("""{"status":"ok"}""")
        }

        client.submitRecordingFeedback("token", "msid-1", ListenBrainzFeedbackScore.Hate)

        assertTrue(body.contains(""""recording_msid":"msid-1""""))
        assertTrue(body.contains(""""score":-1"""))
    }

    @Test
    fun feedbackLookupPostsRecordingMsidsAndParsesScores() = runTest {
        var path = ""
        var body = ""
        val client = clientFor { request ->
            path = request.url.encodedPath
            body = request.bodyText()
            respondJson(
                """
                {
                  "count": 2,
                  "feedback": [
                    {"recording_msid":"msid-loved","score":1},
                    {"recording_msid":"msid-neutral","score":0}
                  ],
                  "offset": 0,
                  "total_count": 2
                }
                """.trimIndent(),
            )
        }

        val feedback = client.getUserFeedbackForRecordingMsids("ada", listOf("msid-loved", "msid-neutral"))

        assertEquals("/1/feedback/user/ada/get-feedback-for-recordings", path)
        assertTrue(body.contains(""""recording_msids":["msid-loved","msid-neutral"]"""))
        assertEquals(ListenBrainzFeedbackScore.Love, feedback["msid-loved"])
        assertEquals(null, feedback["msid-neutral"])
    }

    private fun clientFor(handler: suspend MockRequestHandleScope.(HttpRequestData) -> HttpResponseData): ListenBrainzClient =
        ListenBrainzClient(
            httpClient = testHttpClient(MockEngine(handler)),
            baseUrl = "https://listenbrainz.example",
        )

    private fun MockRequestHandleScope.respondJson(content: String) = respond(
        content = content,
        status = HttpStatusCode.OK,
        headers = headersOf(HttpHeaders.ContentType, "application/json"),
    )

    private fun HttpRequestData.bodyText(): String =
        when (val content = body) {
            is OutgoingContent.ByteArrayContent -> content.bytes().decodeToString()
            is OutgoingContent.NoContent -> ""
            else -> content.toString()
        }

    private fun track(): Track =
        Track(
            id = "local:track-1",
            title = "Song",
            artist = "Artist",
            album = "Album",
            durationMs = 180_000L,
            streamUrl = "https://music.example/song.mp3",
            downloadUrl = "https://music.example/song.mp3",
        )
}
