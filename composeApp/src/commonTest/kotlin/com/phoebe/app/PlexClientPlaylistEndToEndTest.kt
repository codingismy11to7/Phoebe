package com.phoebe.app

import com.phoebe.app.data.PlexClient
import com.phoebe.app.domain.MusicLibrary
import com.phoebe.app.domain.PlexServer
import com.phoebe.app.testing.createdPlaylistJson
import com.phoebe.app.testing.identityJson
import com.phoebe.app.testing.playlistAddResponseJson
import com.phoebe.app.testing.playlistTracksJson
import com.phoebe.app.testing.playlistsJson
import com.phoebe.app.testing.testHttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class PlexClientPlaylistEndToEndTest {
    @Test
    fun createPlaylistPostsToMockPlex() = runTest {
        var capturedMethod: String? = null
        val engine = MockEngine { request ->
            when (request.url.encodedPath) {
                "/playlists" -> {
                    capturedMethod = request.method.value
                    respond(
                        content = createdPlaylistJson(),
                        status = HttpStatusCode.OK,
                        headers = headersOf(HttpHeaders.ContentType, "application/json"),
                    )
                }
                "/identity" -> respond(
                    content = identityJson(),
                    status = HttpStatusCode.OK,
                    headers = headersOf(HttpHeaders.ContentType, "application/json"),
                )
                else -> respond("", HttpStatusCode.NotFound)
            }
        }
        val client = PlexClient(testHttpClient(engine))
        val server = PlexServer("server", "Plex", "https://plex.example:32400", owned = true)
        val playlist = client.createPlaylist(
            server = server,
            token = "token",
            library = MusicLibrary("1", "Music"),
            machineIdentifier = "server",
            title = "New Mix",
            ratingKeys = listOf("t1"),
        )

        assertEquals("POST", capturedMethod)
        assertEquals("p99", playlist.id)
        assertEquals("New Mix", playlist.title)
    }

    @Test
    fun addTracksToPlaylistUsesPutWithRatingKeys() = runTest {
        var capturedMethod: String? = null
        var capturedUri: String? = null
        val engine = MockEngine { request ->
            when (request.url.encodedPath) {
                "/playlists/p1/items" -> {
                    capturedMethod = request.method.value
                    capturedUri = request.url.parameters["uri"]
                    respond(
                        content = playlistAddResponseJson(leafCount = 3),
                        status = HttpStatusCode.OK,
                        headers = headersOf(HttpHeaders.ContentType, "application/json"),
                    )
                }
                else -> respond("", HttpStatusCode.NotFound)
            }
        }
        val client = PlexClient(testHttpClient(engine))
        val server = PlexServer("server", "Plex", "https://plex.example:32400", owned = true)
        val added = client.addTracksToPlaylist(
            server = server,
            token = "token",
            machineIdentifier = "server",
            playlistRatingKey = "p1",
            ratingKeys = listOf("t3"),
        )

        assertEquals(HttpMethod.Put.value, capturedMethod)
        assertNotNull(capturedUri)
        assertTrue(capturedUri!!.contains("t3"))
        assertEquals(1, added)
    }

    @Test
    fun playlistTracksParsesItemsFromMockPlex() = runTest {
        val engine = MockEngine { request ->
            when (request.url.encodedPath) {
                "/playlists" -> respond(
                    content = playlistsJson(trackCount = 2),
                    status = HttpStatusCode.OK,
                    headers = headersOf(HttpHeaders.ContentType, "application/json"),
                )
                "/playlists/p1/items" -> respond(
                    content = playlistTracksJson(),
                    status = HttpStatusCode.OK,
                    headers = headersOf(HttpHeaders.ContentType, "application/json"),
                )
                else -> respond("", HttpStatusCode.NotFound)
            }
        }
        val client = PlexClient(testHttpClient(engine))
        val server = PlexServer("server", "Plex", "https://plex.example:32400", owned = true)
        val playlists = client.playlists(server, "token")
        val tracks = client.playlistTracks(server, playlists.single(), "token")

        assertEquals(listOf("t1", "t2"), tracks.map { it.id })
        assertEquals(listOf("Playlist Song One", "Playlist Song Two"), tracks.map { it.title })
    }
}
