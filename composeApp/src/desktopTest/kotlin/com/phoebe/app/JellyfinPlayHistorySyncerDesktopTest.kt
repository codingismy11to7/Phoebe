package com.phoebe.app

import app.cash.sqldelight.db.SqlDriver
import com.phoebe.app.data.EmbyClient
import com.phoebe.app.data.JellyfinClient
import com.phoebe.app.data.JellyfinPlayHistorySyncResult
import com.phoebe.app.data.JellyfinPlayHistorySyncer
import com.phoebe.app.data.PlayHistoryRepository
import com.phoebe.app.domain.CatalogSnapshot
import com.phoebe.app.domain.MediaProviderType
import com.phoebe.app.domain.MusicLibrary
import com.phoebe.app.domain.PlexServer
import com.phoebe.app.domain.PlexSession
import com.phoebe.app.domain.Track
import com.phoebe.app.testing.newInMemoryPhoebeDatabase
import com.phoebe.app.testing.testHttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

class JellyfinPlayHistorySyncerDesktopTest {
    private var driver: SqlDriver? = null
    private var repository: PlayHistoryRepository? = null

    @After
    fun tearDown() = runBlocking {
        repository?.closeAndJoin()
        repository = null
        driver?.close()
        driver = null
    }

    @Test
    fun syncImportsEmbyUserDataPlayCounts() = runBlocking {
        val starts = mutableListOf<String?>()
        val limits = mutableListOf<String?>()
        val engine = MockEngine { request ->
            starts += request.url.parameters["startIndex"]
            limits += request.url.parameters["limit"]
            respond(
                content = """
                    {
                      "Items": [
                        {
                          "Id": "track-1",
                          "Type": "Audio",
                          "Name": "Night Signals",
                          "UserData": {
                            "PlayCount": 2,
                            "LastPlayedDate": "2026-05-17T20:30:00.0000000Z"
                          }
                        },
                        {
                          "Id": "missing",
                          "Type": "Audio",
                          "Name": "Missing",
                          "UserData": {
                            "PlayCount": 4,
                            "LastPlayedDate": "2026-05-17T20:31:00.0000000Z"
                          }
                        }
                      ],
                      "TotalRecordCount": 2
                    }
                """.trimIndent(),
                status = HttpStatusCode.OK,
                headers = headersOf(HttpHeaders.ContentType, "application/json"),
            )
        }
        val (db, d) = newInMemoryPhoebeDatabase()
        driver = d
        val repo = PlayHistoryRepository(db)
        repository = repo
        val syncer = JellyfinPlayHistorySyncer(
            jellyfinClient = JellyfinClient(testHttpClient(engine)),
            embyClient = EmbyClient(testHttpClient(engine)),
            playHistoryRepository = repo,
        )

        val first = assertIs<JellyfinPlayHistorySyncResult.Synced>(syncer.sync(embySession(), embyCatalog()))
        val second = assertIs<JellyfinPlayHistorySyncResult.Synced>(syncer.sync(embySession(), embyCatalog()))

        assertEquals(2, first.seen)
        assertEquals(1, first.imported)
        assertEquals(1, second.imported)
        assertEquals(listOf<String?>("0", "0"), starts)
        assertEquals(listOf<String?>(JellyfinPlayHistorySyncer.PageSize.toString(), JellyfinPlayHistorySyncer.PageSize.toString()), limits)
        val counts = repo.playCountsByTrack.first { it["emby:track-1"] == 2L }
        assertEquals(2L, counts["emby:track-1"])
    }

    private fun embySession(): PlexSession = PlexSession(
        token = "token",
        selectedServer = PlexServer("emby-server", "Emby", "https://emby.example/emby", owned = true),
        selectedLibrary = MusicLibrary("music", "Music"),
        providerType = MediaProviderType.Emby,
        userId = "user-1",
    )

    private fun embyCatalog(): CatalogSnapshot = CatalogSnapshot(
        tracksByParent = mapOf(
            "emby:album-1" to listOf(Track("emby:track-1", "Night Signals", "North Lake", "Radio House", 1_000L, "stream", "")),
        ),
    )
}
