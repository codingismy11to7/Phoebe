package com.phoebe.app

import com.phoebe.app.data.PlexClient
import com.phoebe.app.data.PlexTimelineState
import com.phoebe.app.domain.PlexServer
import com.phoebe.app.testing.testHttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import java.io.IOException
import kotlinx.coroutines.runBlocking
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class PlexClientMockEngineDesktopTest {
    @Test
    fun serversParsesResourcesFromMockEngine() = runBlocking {
        val json = """
            [
              {
                "name": "plex",
                "product": "Plex Media Server",
                "clientIdentifier": "server-id",
                "owned": true,
                "provides": "server",
                "accessToken": "server-token-xyz",
                "connections": [
                  { "uri": "https://example.plex.direct:32400", "local": false }
                ]
              }
            ]
        """.trimIndent()
        val engine = MockEngine { request ->
            if (request.url.host == "plex.tv" && request.url.encodedPath.contains("resources")) {
                respond(
                    content = json,
                    status = HttpStatusCode.OK,
                    headers = headersOf(HttpHeaders.ContentType, "application/json"),
                )
            } else {
                respond("", HttpStatusCode.NotFound)
            }
        }
        val client = PlexClient(testHttpClient(engine))
        val servers: List<PlexServer> = client.servers("fake-token")
        assertEquals(1, servers.size)
        assertEquals("server-id", servers.single().id)
        assertEquals("server-token-xyz", servers.single().accessToken)
        assertTrue(servers.single().uri.startsWith("https://"))
    }

    @Test
    fun reportTimelineSendsTokenInQueryAndUsesGet() = runBlocking {
        var capturedMethod: String? = null
        var capturedToken: String? = null
        val engine = MockEngine { request ->
            capturedMethod = request.method.value
            capturedToken = request.url.parameters["X-Plex-Token"]
            respond(
                content = """{"MediaContainer":{"size":0}}""",
                status = HttpStatusCode.OK,
                headers = headersOf(HttpHeaders.ContentType, "application/json"),
            )
        }
        val client = PlexClient(testHttpClient(engine))
        client.reportTimeline(
            server = PlexServer("id", "plex", "https://plex.example:32400", owned = true),
            token = "secret-token",
            sessionIdentifier = "session-1",
            ratingKey = "123",
            timeMs = 5_000L,
            durationMs = 180_000L,
            state = PlexTimelineState.Playing,
        )
        assertEquals("GET", capturedMethod)
        assertEquals("secret-token", capturedToken)
    }

    @Test
    fun reportTimelineRetriesNextBaseAfterConnectionClosed() = runBlocking {
        val attemptedHosts = mutableListOf<String>()
        val engine = MockEngine { request ->
            attemptedHosts += request.url.host
            if (request.url.host == "first.example") {
                throw IOException("connection closed")
            }
            respond(
                content = """{"MediaContainer":{"size":0}}""",
                status = HttpStatusCode.OK,
                headers = headersOf(HttpHeaders.ContentType, "application/json"),
            )
        }
        val client = PlexClient(testHttpClient(engine))
        client.reportTimeline(
            server = PlexServer(
                id = "id",
                name = "plex",
                uri = "http://first.example:32400",
                owned = true,
                connectionUris = listOf("http://first.example:32400", "http://second.example:32400"),
                advertisedConnectionUris = listOf("http://first.example:32400", "http://second.example:32400"),
            ),
            token = "secret-token",
            sessionIdentifier = "session-1",
            ratingKey = "123",
            timeMs = 5_000L,
            durationMs = 180_000L,
            state = PlexTimelineState.Stopped,
        )

        assertEquals(listOf("first.example", "second.example"), attemptedHosts)
    }
}
