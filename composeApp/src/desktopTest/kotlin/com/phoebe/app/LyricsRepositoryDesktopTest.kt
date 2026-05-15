package com.phoebe.app

import app.cash.sqldelight.db.SqlDriver
import com.phoebe.app.data.LyricsRepository
import com.phoebe.app.domain.LyricsLoadState
import com.phoebe.app.domain.Track
import com.phoebe.app.testing.newInMemoryPhoebeDatabase
import com.phoebe.app.testing.testHttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

class LyricsRepositoryDesktopTest {
    private var driver: SqlDriver? = null

    @After
    fun cleanup() {
        driver?.close()
        driver = null
    }

    @Test
    fun fetchesSyncedLyricsFromLrclibAndCachesThem() = runTest {
        val (db, sqlDriver) = newInMemoryPhoebeDatabase()
        driver = sqlDriver
        var calls = 0
        val http = testHttpClient(
            MockEngine {
                calls++
                respond(
                    content = """{"id":1,"instrumental":false,"plainLyrics":"Hello\nWorld","syncedLyrics":"[00:01.00] Hello\n[00:02.00] World"}""",
                    status = HttpStatusCode.OK,
                    headers = headersOf(HttpHeaders.ContentType, "application/json"),
                )
            },
        )
        val repo = LyricsRepository(db, http)

        val first = assertIs<LyricsLoadState.Loaded>(repo.lyricsFor(track()))
        val second = assertIs<LyricsLoadState.Loaded>(repo.lyricsFor(track()))

        assertEquals(1, calls)
        assertTrue(first.document.synced)
        assertEquals(listOf(1_000L, 2_000L), first.document.lines.map { it.startMs })
        assertEquals(first.document.lines.map { it.text }, second.document.lines.map { it.text })
    }

    @Test
    fun returnsNotFoundForLrclib404() = runTest {
        val (db, sqlDriver) = newInMemoryPhoebeDatabase()
        driver = sqlDriver
        val repo = LyricsRepository(
            db,
            testHttpClient(MockEngine { respond("", HttpStatusCode.NotFound) }),
        )

        assertIs<LyricsLoadState.NotFound>(repo.lyricsFor(track()))
    }

    private fun track(): Track = Track(
        id = "local:test",
        title = "Test Song",
        artist = "Test Artist",
        album = "Test Album",
        durationMs = 123_000L,
        streamUrl = "",
        downloadUrl = "",
    )
}
