package com.phoebe.app

import com.phoebe.app.data.SubsonicClient
import com.phoebe.app.data.md5Hex
import com.phoebe.app.domain.MusicLibrary
import com.phoebe.app.domain.PlexServer
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

class SubsonicClientTest {
    @Test
    fun md5MatchesSubsonicTokenExampleShape() {
        assertEquals("5ebe2294ecd0e0f08eab7690d2a6ee69", md5Hex("secret"))
    }

    @Test
    fun signInPingsServerAndStoresPasswordForTokenAuth() = runTest {
        val seen = mutableListOf<String>()
        val engine = MockEngine { request ->
            seen += request.url.encodedPath
            when (request.url.encodedPath) {
                "/rest/ping.view" -> {
                    assertEquals("ada", request.url.parameters["u"])
                    assertEquals("1.16.1", request.url.parameters["v"])
                    assertEquals("phoebe", request.url.parameters["c"])
                    assertEquals("json", request.url.parameters["f"])
                    assertTrue(request.url.parameters["t"].orEmpty().isNotBlank())
                    assertTrue(request.url.parameters["s"].orEmpty().isNotBlank())
                    respondJson("""{ "subsonic-response": { "status": "ok" } }""")
                }
                else -> respond("", HttpStatusCode.NotFound)
            }
        }
        val client = SubsonicClient(testHttpClient(engine))

        val session = client.signIn("https://navidrome.example/", "ada", "secret")

        assertEquals("secret", session.token)
        assertEquals("ada", session.userName)
        assertEquals("https://navidrome.example", session.selectedServer!!.uri)
        assertEquals(listOf("/rest/ping.view"), seen)
    }

    @Test
    fun buildsCatalogFromSubsonicWrappers() = runTest {
        val server = PlexServer("navidrome:test", "Navidrome", "https://navidrome.example", owned = true)
        val library = MusicLibrary("all", "All Music")
        val engine = MockEngine { request ->
            when (request.url.encodedPath) {
                "/rest/getArtists.view" -> respondJson(
                    """
                    { "subsonic-response": { "status": "ok", "artists": { "index": [
                      { "artist": [{ "id": "ar1", "name": "North Lake", "albumCount": 1 }] }
                    ] } } }
                    """.trimIndent(),
                )
                "/rest/getAlbumList2.view" -> respondJson(
                    """
                    { "subsonic-response": { "status": "ok", "albumList2": { "album": [
                      { "id": "al1", "name": "Radio House", "artist": "North Lake", "artistId": "ar1", "songCount": 1, "created": "2026-05-16T10:15:30Z", "year": 2025, "genre": "Electronic" }
                    ] } } }
                    """.trimIndent(),
                )
                "/rest/getAlbum.view" -> respondJson(
                    """
                    { "subsonic-response": { "status": "ok", "album": {
                      "id": "al1", "name": "Radio House", "artist": "North Lake", "song": [
                        { "id": "tr1", "title": "Night Signals", "album": "Radio House", "albumId": "al1", "artist": "North Lake", "duration": 245, "coverArt": "track-art", "bitRate": 920, "suffix": "flac", "userRating": 4 }
                      ]
                    } } }
                    """.trimIndent(),
                )
                "/rest/getPlaylists.view" -> respondJson(
                    """{ "subsonic-response": { "status": "ok", "playlists": { "playlist": [{ "id": "pl1", "name": "Late", "songCount": 1 }] } } }""",
                )
                "/rest/getStarred2.view" -> respondJson(
                    """
                    { "subsonic-response": { "status": "ok", "starred2": {
                      "artist": [{ "id": "ar1", "name": "North Lake" }],
                      "album": [{ "id": "al1", "name": "Radio House", "artist": "North Lake" }],
                      "song": [
                        { "id": "tr1", "title": "Night Signals", "album": "Radio House", "albumId": "al1", "artist": "North Lake", "duration": 245, "coverArt": "track-art" }
                      ]
                    } } }
                    """.trimIndent(),
                )
                else -> respond("", HttpStatusCode.NotFound)
            }
        }
        val client = SubsonicClient(testHttpClient(engine))

        val catalog = client.buildCatalog(server, library, "ada", "secret")

        val artist = catalog.artists.single()
        val album = catalog.albums.single()
        assertEquals("North Lake", artist.title)
        assertEquals("Radio House", album.title)
        assertTrue(artist.favorite)
        assertTrue(album.favorite)
        assertEquals(1_778_926_530_000L, album.dateAddedMs)
        assertTrue(album.thumbUrl.orEmpty().contains("/rest/getCoverArt.view"))
        assertTrue(album.thumbUrl.orEmpty().contains("id=track-art"))
        assertEquals(album.thumbUrl, artist.thumbUrl)
        assertTrue(catalog.playlists.any { it.title == "Late" })
        assertEquals("Liked Songs", catalog.playlists.first().title)
        assertEquals("Night Signals", catalog.tracksByParent["liked-songs"]!!.single().title)
        val track = catalog.tracksByParent["al1"]!!.single()
        assertEquals("Night Signals", track.title)
        assertTrue(track.streamUrl.contains("/rest/stream.view"))
        assertTrue(track.downloadUrl.contains("/rest/download.view"))
        assertEquals(920, track.bitrateKbps)
    }

    @Test
    fun favoritesUseSubsonicItemSpecificParameters() = runTest {
        val server = PlexServer("navidrome:test", "Navidrome", "https://navidrome.example", owned = true)
        val seen = mutableListOf<Pair<String, Map<String, String>>>()
        val engine = MockEngine { request ->
            seen += request.url.encodedPath to request.url.parameters.entries().associate { it.key to it.value.single() }
            respondJson("""{ "subsonic-response": { "status": "ok" } }""")
        }
        val client = SubsonicClient(testHttpClient(engine))

        client.setFavorite(server, "ada", "secret", "ar1", true, com.phoebe.app.data.ProviderItemKind.Artist)
        client.setFavorite(server, "ada", "secret", "al1", true, com.phoebe.app.data.ProviderItemKind.Album)
        client.setFavorite(server, "ada", "secret", "tr1", true, com.phoebe.app.data.ProviderItemKind.Track)

        assertEquals("ar1", seen[0].second["artistId"])
        assertEquals("al1", seen[1].second["albumId"])
        assertEquals("tr1", seen[2].second["id"])
    }

    @Test
    fun fullCatalogDerivesArtistsMissingFromNavidromeArtistEndpoint() = runTest {
        val server = PlexServer("navidrome:test", "Navidrome", "https://navidrome.example", owned = true)
        val library = MusicLibrary("all", "All Music")
        val engine = MockEngine { request ->
            when (request.url.encodedPath) {
                "/rest/getArtists.view" -> respondJson(
                    """
                    { "subsonic-response": { "status": "ok", "artists": { "index": [
                      { "artist": [{ "id": "ar1", "name": "First Artist", "albumCount": 1 }] }
                    ] } } }
                    """.trimIndent(),
                )
                "/rest/getAlbumList2.view" -> respondJson(
                    """
                    { "subsonic-response": { "status": "ok", "albumList2": { "album": [
                      { "id": "al1", "name": "One", "artist": "First Artist", "songCount": 1 },
                      { "id": "al2", "name": "Two", "artist": "Missing Artist", "songCount": 1 }
                    ], "totalCount": 2 } } }
                    """.trimIndent(),
                )
                "/rest/getAlbum.view" -> respondJson(
                    """
                    { "subsonic-response": { "status": "ok", "album": {
                      "id": "${request.url.parameters["id"]}", "name": "Album", "artist": "Artist", "song": []
                    } } }
                    """.trimIndent(),
                )
                "/rest/getPlaylists.view" -> respondJson(
                    """{ "subsonic-response": { "status": "ok", "playlists": { "playlist": [] } } }""",
                )
                else -> respond("", HttpStatusCode.NotFound)
            }
        }
        val client = SubsonicClient(testHttpClient(engine))

        val catalog = client.buildCatalog(server, library, "ada", "secret")

        assertEquals(
            listOf("First Artist", "Missing Artist"),
            catalog.artists.map { it.title }.sorted(),
        )
    }
}

private fun MockRequestHandleScope.respondJson(content: String) = respond(
    content = content,
    status = HttpStatusCode.OK,
    headers = headersOf(HttpHeaders.ContentType, "application/json"),
)
