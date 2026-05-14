package com.phoebe.app

import com.phoebe.app.data.PlexClient
import com.phoebe.app.data.PlexDeviceDto
import com.phoebe.app.data.PlexMediaContainerResponse
import com.phoebe.app.domain.PlexServer
import com.phoebe.app.testing.testHttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals

class PlexMappingTest {
    @Test
    fun parsesMusicLibrariesFromPlexContainer() {
        val json = """
            {
              "MediaContainer": {
                "Directory": [
                  { "key": "1", "title": "Movies", "type": "movie" },
                  { "key": "2", "title": "Music", "type": "artist" }
                ]
              }
            }
        """.trimIndent()

        val response = PlexClient.PlexJson.decodeFromString<PlexMediaContainerResponse>(json)

        assertEquals("Music", response.mediaContainer.directories.last().title)
        assertEquals("artist", response.mediaContainer.directories.last().type)
    }

    @Test
    fun parsesPlexResourcesArray() {
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

        val devices = PlexClient.PlexJson.decodeFromString<List<PlexDeviceDto>>(json)

        assertEquals("plex", devices.single().name)
        assertEquals("server-id", devices.single().clientIdentifier)
        assertEquals("https://example.plex.direct:32400", devices.single().connections.single().uri)
    }

    @Test
    fun parsesArtistsFromMetadataWhenDirectoryEmpty() {
        val json = """
            {
              "MediaContainer": {
                "size": 2,
                "Metadata": [
                  {
                    "ratingKey": "101",
                    "title": "North Lake",
                    "type": "artist",
                    "leafCount": 4,
                    "thumb": "/library/metadata/101/thumb"
                  },
                  {
                    "ratingKey": "102",
                    "title": "South Echo",
                    "type": "artist",
                    "leafCount": 1
                  }
                ]
              }
            }
        """.trimIndent()

        val response = PlexClient.PlexJson.decodeFromString<PlexMediaContainerResponse>(json)

        assertEquals(0, response.mediaContainer.directories.size)
        assertEquals(2, response.mediaContainer.metadata.size)
        assertEquals("artist", response.mediaContainer.metadata.first().type)
    }

    @Test
    fun parsesPlaylistTrackCountAndKey() {
        val json = """
            {
              "MediaContainer": {
                "Metadata": [
                  {
                    "ratingKey": "42",
                    "key": "/playlists/42/items",
                    "title": "Favorites",
                    "leafCount": 19
                  }
                ]
              }
            }
        """.trimIndent()

        val response = PlexClient.PlexJson.decodeFromString<PlexMediaContainerResponse>(json)
        val playlist = response.mediaContainer.metadata.single()

        assertEquals("/playlists/42/items", playlist.key)
        assertEquals(19, playlist.leafCount)
    }

    @Test
    fun mapsPlexAddedAtOntoTracks() = runTest {
        val engine = MockEngine { request ->
            when (request.url.encodedPath) {
                "/library/metadata/a1/children" -> respond(
                    content = """
                        {
                          "MediaContainer": {
                            "Metadata": [
                              {
                                "ratingKey": "t1",
                                "title": "Fresh Song",
                                "grandparentTitle": "Artist One",
                                "parentTitle": "Album One",
                                "duration": 1000,
                                "addedAt": 1700000200,
                                "Media": [
                                  { "Part": [ { "key": "/library/parts/t1/file.mp3", "file": "file.mp3" } ] }
                                ]
                              }
                            ]
                          }
                        }
                    """.trimIndent(),
                    status = HttpStatusCode.OK,
                    headers = headersOf(HttpHeaders.ContentType, "application/json"),
                )
                else -> respond("", HttpStatusCode.NotFound)
            }
        }
        val client = PlexClient(testHttpClient(engine))
        val track = client.children(PlexServer("server", "Plex", "https://plex.example", owned = true), "a1", "token").single()

        assertEquals(1_700_000_200_000L, track.dateAddedMs)
    }
}
