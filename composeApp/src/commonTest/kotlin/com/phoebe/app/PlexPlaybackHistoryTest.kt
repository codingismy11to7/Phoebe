package com.phoebe.app

import com.phoebe.app.data.PlexClient
import com.phoebe.app.domain.MusicLibrary
import com.phoebe.app.domain.PlexServer
import com.phoebe.app.testing.testHttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import kotlin.test.Test
import kotlin.test.assertEquals

class PlexPlaybackHistoryTest {
    @Test
    fun playbackHistoryPageSendsFiltersAndMapsViewedAt() = kotlinx.coroutines.test.runTest {
        var capturedPath = ""
        var capturedLibrary: String? = null
        var capturedSort: String? = null
        var capturedViewedAt: String? = null
        var capturedStart: String? = null
        var capturedSize: String? = null
        val engine = MockEngine { request ->
            capturedPath = request.url.encodedPath
            capturedLibrary = request.url.parameters["librarySectionID"]
            capturedSort = request.url.parameters["sort"]
            capturedViewedAt = request.url.parameters["viewedAt"]
            capturedStart = request.url.parameters["X-Plex-Container-Start"]
            capturedSize = request.url.parameters["X-Plex-Container-Size"]
            respond(
                content = """
                    {
                      "MediaContainer": {
                        "offset": 25,
                        "size": 1,
                        "totalSize": 26,
                        "Metadata": [
                          {
                            "ratingKey": "t1",
                            "historyKey": "/status/sessions/history/metadata/900",
                            "type": "track",
                            "librarySectionID": "1",
                            "title": "Song",
                            "parentTitle": "Album",
                            "grandparentTitle": "Artist",
                            "viewedAt": 1700000000
                          }
                        ]
                      }
                    }
                """.trimIndent(),
                status = HttpStatusCode.OK,
                headers = headersOf(HttpHeaders.ContentType, "application/json"),
            )
        }
        val client = PlexClient(testHttpClient(engine))
        val page = client.playbackHistoryPage(
            server = PlexServer("server", "Plex", "https://plex.example:32400", owned = true),
            token = "token",
            library = MusicLibrary("1", "Music"),
            minViewedAtMs = 1699999400000L,
            start = 25,
            size = 100,
        )

        assertEquals("/status/sessions/history/all", capturedPath)
        assertEquals("1", capturedLibrary)
        assertEquals("viewedAt:desc", capturedSort)
        assertEquals("viewedAt>=1699999400", capturedViewedAt)
        assertEquals("25", capturedStart)
        assertEquals("100", capturedSize)
        assertEquals(25, page.offset)
        assertEquals(26, page.totalSize)
        assertEquals(1700000000000L, page.entries.single().viewedAtMs)
        assertEquals("t1", page.entries.single().ratingKey)
    }

    @Test
    fun playbackHistoryPageKeepsEntriesWithoutHistoryKey() = kotlinx.coroutines.test.runTest {
        val engine = MockEngine {
            respond(
                content = """
                    {
                      "MediaContainer": {
                        "size": 1,
                        "Metadata": [
                          {
                            "ratingKey": "t1",
                            "type": "track",
                            "librarySectionID": 1,
                            "title": "Song",
                            "parentTitle": "Album",
                            "grandparentTitle": "Artist",
                            "lastViewedAt": 1700000000
                          }
                        ]
                      }
                    }
                """.trimIndent(),
                status = HttpStatusCode.OK,
                headers = headersOf(HttpHeaders.ContentType, "application/json"),
            )
        }
        val client = PlexClient(testHttpClient(engine))

        val page = client.playbackHistoryPage(
            server = PlexServer("server", "Plex", "https://plex.example:32400", owned = true),
            token = "token",
            library = MusicLibrary("1", "Music"),
            minViewedAtMs = null,
            start = 0,
            size = 100,
        )

        assertEquals(1, page.entries.size)
        assertEquals("plex:t1:1700000000", page.entries.single().historyKey)
        assertEquals(1700000000000L, page.entries.single().viewedAtMs)
        assertEquals("1", page.entries.single().librarySectionId)
    }
}
