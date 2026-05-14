package com.phoebe.app

import com.phoebe.app.data.PlexClient
import com.phoebe.app.data.SessionRepository
import com.phoebe.app.domain.PlexPin
import com.phoebe.app.domain.PlexServer
import com.phoebe.app.platform.PlatformStorage
import com.phoebe.app.testing.newInMemoryPhoebeDatabase
import com.phoebe.app.testing.testHttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.MockRequestHandleScope
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import kotlinx.coroutines.runBlocking
import org.junit.Test
import kotlin.test.assertEquals

class SessionRepositoryDesktopTest {
    @Test
    fun selectServerCanSkipResourceRefreshAfterFreshSignInServerList() = runBlocking {
        val (database, driver) = newInMemoryPhoebeDatabase()
        var resourcesCalls = 0
        val engine = MockEngine { request ->
            when (request.url.encodedPath) {
                "/api/v2/pins/1" -> respondJson("""{"id":1,"code":"ABCD","authToken":"user-token"}""")
                "/api/v2/user" -> respondJson("""{"username":"Plex listener"}""")
                "/api/v2/resources" -> {
                    resourcesCalls += 1
                    respondJson("[]")
                }
                else -> respond("", HttpStatusCode.NotFound)
            }
        }
        val repository = SessionRepository(
            plexClient = PlexClient(testHttpClient(engine)),
            database = database,
            storage = PlatformStorage(),
        )
        val server = PlexServer(
            id = "server-id",
            name = "Studio Plex",
            uri = "https://plex.example:32400",
            owned = true,
            accessToken = "server-token",
        )

        try {
            repository.completePin(PlexPin(id = 1, code = "ABCD", authUrl = "https://plex.example/auth"))
            val selected = repository.selectServer(server, refreshConnections = false)

            assertEquals(server, selected)
            assertEquals(server, repository.session.value?.selectedServer)
            assertEquals(0, resourcesCalls)
        } finally {
            driver.close()
        }
    }

    @Test
    fun restoreCanHydrateSavedSessionWithoutResourceRefresh() = runBlocking {
        val (database, driver) = newInMemoryPhoebeDatabase()
        val storage = PlatformStorage()
        var resourcesCalls = 0
        val engine = MockEngine { request ->
            when (request.url.encodedPath) {
                "/api/v2/pins/1" -> respondJson("""{"id":1,"code":"ABCD","authToken":"user-token"}""")
                "/api/v2/user" -> respondJson("""{"username":"Plex listener"}""")
                "/api/v2/resources" -> {
                    resourcesCalls += 1
                    respondJson("[]")
                }
                else -> respond("", HttpStatusCode.NotFound)
            }
        }
        val client = PlexClient(testHttpClient(engine))
        val server = PlexServer(
            id = "server-id",
            name = "Studio Plex",
            uri = "https://plex.example:32400",
            owned = true,
            connectionUris = listOf("https://plex.example:32400", "http://192.168.1.9:32400"),
            advertisedConnectionUris = listOf("https://plex.example:32400"),
            localConnectionUris = listOf("http://192.168.1.9:32400"),
            accessToken = "server-token",
            httpsRequired = true,
        )

        try {
            val repository = SessionRepository(client, database, storage)
            repository.completePin(PlexPin(id = 1, code = "ABCD", authUrl = "https://plex.example/auth"))
            repository.selectServer(server, refreshConnections = false)
            resourcesCalls = 0

            val restored = SessionRepository(client, database, storage)
            restored.restore(refreshConnections = false)

            assertEquals("server-id", restored.session.value?.selectedServer?.id)
            assertEquals("Studio Plex", restored.session.value?.selectedServer?.name)
            assertEquals(listOf("https://plex.example:32400", "http://192.168.1.9:32400"), restored.session.value?.selectedServer?.connectionUris)
            assertEquals(listOf("https://plex.example:32400"), restored.session.value?.selectedServer?.advertisedConnectionUris)
            assertEquals(listOf("http://192.168.1.9:32400"), restored.session.value?.selectedServer?.localConnectionUris)
            assertEquals("server-token", restored.session.value?.selectedServer?.accessToken)
            assertEquals(true, restored.session.value?.selectedServer?.httpsRequired)
            assertEquals(0, resourcesCalls)
        } finally {
            driver.close()
        }
    }
}

private fun MockRequestHandleScope.respondJson(content: String) = respond(
    content = content,
    status = HttpStatusCode.OK,
    headers = headersOf(HttpHeaders.ContentType, "application/json"),
)
