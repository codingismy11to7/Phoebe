package com.phoebe.app

import com.phoebe.app.data.MusicAssistantClient
import com.phoebe.app.domain.MediaProviderType
import com.phoebe.app.testing.testHttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.MockRequestHandleScope
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class MusicAssistantClientTest {
    @Test
    fun tokenEntryCreatesMusicAssistantSession() = runTest {
        val client = MusicAssistantClient(testHttpClient(MockEngine { respondJson("""{}""") }))

        val session = client.signIn("https://ma.example/", "token", "ma-token")

        assertEquals(MediaProviderType.MusicAssistant, session.providerType)
        assertEquals("ma-token", session.token)
        assertEquals("https://ma.example", session.selectedServer!!.uri)
        assertEquals("music-assistant", session.selectedLibrary!!.key)
    }

    @Test
    fun tokenEntryAcceptsTokenInUsernameField() = runTest {
        val client = MusicAssistantClient(testHttpClient(MockEngine { respondJson("""{}""") }))

        val session = client.signIn("ma.example:8095/", "ma-token", "")

        assertEquals("ma-token", session.token)
        assertEquals("http://ma.example:8095", session.selectedServer!!.uri)
        assertEquals("Music Assistant listener", session.userName)
    }

    @Test
    fun passwordLoginUsesMusicAssistantBuiltinProviderPayload() = runTest {
        val paths = mutableListOf<String>()
        val engine = MockEngine { request ->
            paths += request.url.encodedPath
            assertEquals("/auth/login", request.url.encodedPath)
            respondJson(
                """
                {
                  "success": true,
                  "token": "login-token",
                  "user": { "username": "ada" }
                }
                """.trimIndent(),
            )
        }
        val client = MusicAssistantClient(testHttpClient(engine))

        val session = client.signIn("https://ma.example/", "ada", "secret")

        assertEquals("login-token", session.token)
        assertEquals("ada", session.userName)
        assertEquals(listOf("/auth/login"), paths)
    }

    @Test
    fun buildsLibraryAndLeavesNativeStreamUrlsBlankForQueueControlProvider() = runTest {
        val paths = mutableListOf<String>()
        val engine = MockEngine { request ->
            paths += request.url.encodedPath
            when (request.url.encodedPath) {
                "/api" -> respondJson(
                    """
                    [
                      {
                        "item_id": "tr1",
                        "provider": "library",
                        "name": "Night Signals",
                        "uri": "library://track/tr1",
                        "duration": 245,
                        "favorite": true,
                        "date_added": "2026-05-16T10:15:30+00:00",
                        "image": { "path": "album/art.jpg", "provider": "filesystem_local", "type": "thumb", "remotely_accessible": false },
                        "artists": [{ "item_id": "ar1", "name": "North Lake", "uri": "library://artist/ar1" }],
                        "album": { "item_id": "al1", "name": "Radio House", "uri": "library://album/al1" },
                        "metadata": { "genres": ["Electronic"], "images": [{ "path": "/imageproxy/track-art" }] }
                      }
                    ]
                    """.trimIndent(),
                )
                else -> respond("", HttpStatusCode.NotFound)
            }
        }
        val client = MusicAssistantClient(testHttpClient(engine))
        val session = client.signIn("https://ma.example", "token", "ma-token")

        val catalog = client.buildCatalog(session.selectedServer!!, session.token)

        val track = catalog.tracksByParent["library://album/al1"]!!.single()
        assertEquals("Night Signals", track.title)
        assertEquals("", track.streamUrl)
        assertEquals("", track.downloadUrl)
        assertEquals("https://ma.example/imageproxy?provider=filesystem_local&size=0&path=album%252Fart.jpg", track.thumbUrl)
        assertEquals(1_778_926_530_000L, track.dateAddedMs)
        assertEquals(listOf("/api", "/api", "/api", "/api"), paths)
    }

    @Test
    fun derivesArtistsWhenMusicAssistantArtistLibraryIsEmpty() = runTest {
        var apiCall = 0
        val engine = MockEngine {
            apiCall += 1
            when (apiCall) {
                1 -> respondJson("""[]""")
                2 -> respondJson(
                    """
                    [
                      {
                        "item_id": "al1",
                        "name": "Radio House",
                        "uri": "library://album/al1",
                        "date_added": "2026-05-16T10:15:30+00:00",
                        "artists": [{ "item_id": "ar1", "name": "North Lake", "uri": "library://artist/ar1" }],
                        "metadata": { "genres": ["Electronic"], "images": [{ "path": "/imageproxy/album-art" }] }
                      }
                    ]
                    """.trimIndent(),
                )
                3 -> respondJson(
                    """
                    [
                      {
                        "item_id": "tr1",
                        "name": "Night Signals",
                        "uri": "library://track/tr1",
                        "date_added": "2026-05-15T10:15:30+00:00",
                        "artists": [{ "item_id": "ar1", "name": "North Lake", "uri": "library://artist/ar1" }],
                        "album": { "item_id": "al1", "name": "Radio House", "uri": "library://album/al1" }
                      }
                    ]
                    """.trimIndent(),
                )
                else -> respondJson("""[]""")
            }
        }
        val client = MusicAssistantClient(testHttpClient(engine))
        val session = client.signIn("https://ma.example", "token", "ma-token")

        val catalog = client.buildCatalog(session.selectedServer!!, session.token)

        val artist = catalog.artists.single()
        assertEquals("North Lake", artist.title)
        assertEquals(1, artist.albumCount)
        assertEquals(1, artist.songCount)
        assertEquals(1_778_926_530_000L, artist.dateAddedMs)
    }

    @Test
    fun keepsMusicAssistantItemsWithNullableFields() = runTest {
        var apiCall = 0
        val engine = MockEngine {
            apiCall += 1
            when (apiCall) {
                1 -> respondJson("""[]""")
                2 -> respondJson(
                    """
                    [
                      {
                        "item_id": 1,
                        "name": "Sparse Album",
                        "uri": "library://album/al1",
                        "favorite": null,
                        "artists": null,
                        "metadata": { "genres": null, "images": null },
                        "provider_mappings": null
                      }
                    ]
                    """.trimIndent(),
                )
                3 -> respondJson(
                    """
                    [
                      {
                        "item_id": 2,
                        "name": "Sparse Track",
                        "uri": "library://track/tr1",
                        "favorite": null,
                        "duration": 12.5,
                        "artists": null,
                        "album": { "item_id": "al1", "name": "Sparse Album", "uri": "library://album/al1" },
                        "metadata": { "genres": null, "images": null },
                        "provider_mappings": null
                      }
                    ]
                    """.trimIndent(),
                )
                else -> respondJson("""[]""")
            }
        }
        val client = MusicAssistantClient(testHttpClient(engine))
        val session = client.signIn("https://ma.example", "token", "ma-token")

        val catalog = client.buildCatalog(session.selectedServer!!, session.token)

        assertEquals("Sparse Album", catalog.albums.single().title)
        assertEquals("Sparse Track", catalog.tracksByParent["library://album/al1"]!!.single().title)
        assertEquals(12500, catalog.tracksByParent["library://album/al1"]!!.single().durationMs)
    }

    @Test
    fun playsMediaOnDefaultAvailableQueue() = runTest {
        var apiCall = 0
        val engine = MockEngine { request ->
            assertEquals("/api", request.url.encodedPath)
            apiCall += 1
            when (apiCall) {
                1 -> respondJson(
                    """
                    [
                      { "queue_id": "offline", "display_name": "Offline", "active": false, "available": false },
                      { "queue_id": "living-room", "display_name": "Living Room", "active": false, "available": true }
                    ]
                    """.trimIndent(),
                )
                2 -> respondJson("""{}""")
                else -> respond("", HttpStatusCode.NotFound)
            }
        }
        val client = MusicAssistantClient(testHttpClient(engine))
        val session = client.signIn("https://ma.example", "token", "ma-token")

        val target = client.playMediaOnDefaultQueue(session.selectedServer!!, session.token, "library://track/tr1")

        assertEquals("Living Room", target)
        assertEquals(2, apiCall)
    }

    @Test
    fun resolvesLocalPlaybackStreamUrlWithoutStartingRemotePlayback() = runTest {
        var apiCall = 0
        val engine = MockEngine { request ->
            assertEquals("/api", request.url.encodedPath)
            apiCall += 1
            when (apiCall) {
                1 -> respondJson(
                    """
                    [
                      { "queue_id": "living-room", "display_name": "Living Room", "session_id": "ma-session", "active": false, "available": true }
                    ]
                    """.trimIndent(),
                )
                2 -> respondJson("""[]""")
                3 -> respondJson("""{}""")
                4 -> respondJson(
                    """
                    [
                      { "queue_item_id": "qi-1", "uri": "library://track/tr1" }
                    ]
                    """.trimIndent(),
                )
                5 -> respondJson(""""192.168.1.10"""")
                6 -> respondJson("""8097""")
                else -> respond("", HttpStatusCode.NotFound)
            }
        }
        val client = MusicAssistantClient(testHttpClient(engine))
        val session = client.signIn("https://ma.example", "token", "ma-token")

        val streamUrl = client.streamUrlForLocalPlayback(session.selectedServer!!, session.token, "library://track/tr1")

        assertEquals("http://192.168.1.10:8097/single/ma-session/living-room/qi-1/living-room.mp3", streamUrl)
        assertEquals(6, apiCall)
    }
}

private fun MockRequestHandleScope.respondJson(content: String) = respond(
    content = content,
    status = HttpStatusCode.OK,
    headers = headersOf(HttpHeaders.ContentType, "application/json"),
)
