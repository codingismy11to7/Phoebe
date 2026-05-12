package com.phoebe.app

import com.phoebe.app.data.PlexClient
import com.phoebe.app.domain.PlexServer
import com.phoebe.app.testing.testHttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
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
        assertTrue(servers.single().uri.startsWith("https://"))
    }
}
