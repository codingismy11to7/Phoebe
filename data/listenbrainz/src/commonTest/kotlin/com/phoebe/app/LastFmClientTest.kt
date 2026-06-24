package com.phoebe.app

import com.phoebe.app.data.LastFmClient
import com.phoebe.app.data.LastFmQueuedScrobble
import com.phoebe.app.data.lastFmSignature
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

class LastFmClientTest {
    @Test
    fun signatureSortsParametersAndUsesMd5() {
        val signature = lastFmSignature(
            mapOf(
                "track" to "Song",
                "method" to "track.scrobble",
                "artist" to "Artist",
                "format" to "json",
            ),
            sharedSecret = "secret",
        )

        assertEquals("d901b112acad4360b49a04d7fc56ed56", signature)
    }

    @Test
    fun validateSessionSignsUserGetInfoAndParsesUsername() = runTest {
        var fields = emptyMap<String, String>()
        val client = clientFor { request ->
            fields = request.formFields()
            respondJson("""{"user":{"name":"ada"}}""")
        }

        val validation = client.validateSession("api-key", "shared-secret", "session-key")

        assertTrue(validation.valid)
        assertEquals("ada", validation.username)
        assertEquals("user.getInfo", fields["method"])
        assertEquals("api-key", fields["api_key"])
        assertEquals("session-key", fields["sk"])
        assertEquals("json", fields["format"])
        assertTrue(fields["api_sig"].orEmpty().isNotBlank())
    }

    @Test
    fun getTokenSignsAuthRequestAndParsesToken() = runTest {
        var fields = emptyMap<String, String>()
        val client = clientFor { request ->
            fields = request.formFields()
            respondJson("""{"token":"auth-token"}""")
        }

        val token = client.getToken("api-key", "shared-secret")

        assertEquals("auth-token", token)
        assertEquals("auth.getToken", fields["method"])
        assertEquals("api-key", fields["api_key"])
        assertEquals("json", fields["format"])
        assertFalse(fields.containsKey("sk"))
        assertTrue(fields["api_sig"].orEmpty().isNotBlank())
    }

    @Test
    fun getSessionSignsTokenRequestAndParsesSessionKey() = runTest {
        var fields = emptyMap<String, String>()
        val client = clientFor { request ->
            fields = request.formFields()
            respondJson("""{"session":{"name":"ada","key":"session-key","subscriber":0}}""")
        }

        val session = client.getSession("api-key", "shared-secret", "auth-token")

        assertEquals("session-key", session.key)
        assertEquals("ada", session.username)
        assertEquals("auth.getSession", fields["method"])
        assertEquals("api-key", fields["api_key"])
        assertEquals("auth-token", fields["token"])
        assertEquals("json", fields["format"])
        assertFalse(fields.containsKey("sk"))
        assertTrue(fields["api_sig"].orEmpty().isNotBlank())
    }

    @Test
    fun authorizationUrlIncludesApiKeyAndToken() {
        val client = clientFor { respondJson("""{}""") }

        val url = client.authorizationUrl("api key", "token value")

        assertEquals("https://www.last.fm/api/auth/?api_key=api+key&token=token+value", url)
    }

    @Test
    fun scrobblePostsTimestampAndTrackMetadata() = runTest {
        var fields = emptyMap<String, String>()
        val client = clientFor { request ->
            fields = request.formFields()
            respondJson("""{"scrobbles":{"@attr":{"accepted":"1","ignored":"0"}}}""")
        }

        client.scrobble(
            apiKey = "api-key",
            sharedSecret = "shared-secret",
            sessionKey = "session-key",
            scrobble = LastFmQueuedScrobble(track(), listenedAtMs = 1_700_000_123_000L),
        )

        assertEquals("track.scrobble", fields["method"])
        assertEquals("Artist", fields["artist"])
        assertEquals("Song", fields["track"])
        assertEquals("Album", fields["album"])
        assertEquals("180", fields["duration"])
        assertEquals("1700000123", fields["timestamp"])
        assertFalse(fields.containsKey("callback"))
    }

    @Test
    fun scrobbleThrowsWhenLastFmIgnoresAcceptedHttpResponse() = runTest {
        val client = clientFor {
            respondJson(
                """
                {
                  "scrobbles": {
                    "@attr": { "accepted": "0", "ignored": "1" },
                    "scrobble": {
                      "ignoredMessage": { "code": "1", "#text": "Artist name missing" }
                    }
                  }
                }
                """.trimIndent(),
            )
        }

        val error = assertFailsWith<IllegalStateException> {
            client.scrobble(
                apiKey = "api-key",
                sharedSecret = "shared-secret",
                sessionKey = "session-key",
                scrobble = LastFmQueuedScrobble(track(), listenedAtMs = 1_700_000_123_000L),
            )
        }

        assertEquals("Artist name missing", error.message)
    }

    private fun clientFor(handler: suspend MockRequestHandleScope.(HttpRequestData) -> HttpResponseData): LastFmClient =
        LastFmClient(
            httpClient = testHttpClient(MockEngine(handler)),
            baseUrl = "https://lastfm.example/2.0/",
        )

    private fun MockRequestHandleScope.respondJson(content: String) = respond(
        content = content,
        status = HttpStatusCode.OK,
        headers = headersOf(HttpHeaders.ContentType, "application/json"),
    )

    private fun HttpRequestData.formFields(): Map<String, String> =
        bodyText()
            .split('&')
            .filter { it.isNotBlank() }
            .associate { pair ->
                val key = pair.substringBefore('=').decodeUrlFormComponent()
                val value = pair.substringAfter('=', "").decodeUrlFormComponent()
                key to value
            }

    private fun HttpRequestData.bodyText(): String =
        when (val content = body) {
            is OutgoingContent.ByteArrayContent -> content.bytes().decodeToString()
            is OutgoingContent.NoContent -> ""
            else -> content.toString()
        }

    private fun String.decodeUrlFormComponent(): String =
        replace("+", " ")
            .split('%')
            .let { parts ->
                buildString {
                    append(parts.first())
                    parts.drop(1).forEach { part ->
                        val hex = part.take(2)
                        append(hex.toInt(16).toChar())
                        append(part.drop(2))
                    }
                }
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
