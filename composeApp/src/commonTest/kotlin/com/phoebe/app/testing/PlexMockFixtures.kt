package com.phoebe.app.testing

import com.phoebe.app.domain.MusicLibrary
import com.phoebe.app.domain.PlexServer
import com.phoebe.app.domain.PlexSession
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.MockRequestHandleScope
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf

fun plexCatalogMockEngine(
    playlistTrackCount: Int = 2,
    onPlaylistAdd: (() -> Unit)? = null,
): MockEngine = MockEngine { request ->
    when (request.url.encodedPath) {
        "/library/sections/1/all" -> respondPlexJson(artistsJson())
        "/library/sections/1/albums" -> respondPlexJson(albumsJson())
        "/playlists" -> when (request.method.value) {
            "POST" -> respondPlexJson(createdPlaylistJson())
            else -> respondPlexJson(playlistsJson(playlistTrackCount))
        }
        "/library/metadata/a1/children" -> respondPlexJson(albumTracksJson())
        "/playlists/p1/items" -> when (request.method.value) {
            "PUT" -> {
                onPlaylistAdd?.invoke()
                respondPlexJson(playlistAddResponseJson(leafCount = playlistTrackCount + 1))
            }
            else -> respondPlexJson(playlistTracksJson())
        }
        "/identity" -> respondPlexJson(identityJson())
        else -> respond("", HttpStatusCode.NotFound)
    }
}

fun testPlexSession(): PlexSession = PlexSession(
    token = "token",
    selectedServer = PlexServer("server", "Plex", "https://plex.example:32400", owned = true),
    selectedLibrary = MusicLibrary("1", "Music"),
)

private fun MockRequestHandleScope.respondPlexJson(content: String) = respond(
    content = content,
    status = HttpStatusCode.OK,
    headers = headersOf(HttpHeaders.ContentType, "application/json"),
)

fun artistsJson(): String = """
    {
      "MediaContainer": {
        "Metadata": [
          { "ratingKey": "artist1", "type": "artist", "title": "Artist One", "leafCount": 1 }
        ]
      }
    }
""".trimIndent()

fun albumsJson(): String = """
    {
      "MediaContainer": {
        "Metadata": [
          { "ratingKey": "a1", "title": "Album One", "parentTitle": "Artist One" }
        ]
      }
    }
""".trimIndent()

fun playlistsJson(trackCount: Int): String = """
    {
      "MediaContainer": {
        "Metadata": [
          { "ratingKey": "p1", "title": "Playlist One", "leafCount": $trackCount, "key": "/playlists/p1/items" }
        ]
      }
    }
""".trimIndent()

fun createdPlaylistJson(): String = """
    {
      "MediaContainer": {
        "Metadata": [
          { "ratingKey": "p99", "title": "New Mix", "leafCount": 1, "key": "/playlists/p99/items" }
        ]
      }
    }
""".trimIndent()

fun albumTracksJson(): String = """
    {
      "MediaContainer": {
        "Metadata": [
          {
            "ratingKey": "t1",
            "title": "Fresh Song",
            "grandparentTitle": "Artist One",
            "parentTitle": "Album One",
            "duration": 1000,
            "Media": [
              { "Part": [ { "key": "/library/parts/t1/file.mp3", "file": "file.mp3" } ] }
            ]
          }
        ]
      }
    }
""".trimIndent()

fun playlistTracksJson(): String = """
    {
      "MediaContainer": {
        "Metadata": [
          {
            "ratingKey": "t1",
            "title": "Playlist Song One",
            "grandparentTitle": "Artist One",
            "parentTitle": "Album One",
            "duration": 1000,
            "Media": [
              { "Part": [ { "key": "/library/parts/t1/file.mp3", "file": "one.mp3" } ] }
            ]
          },
          {
            "ratingKey": "t2",
            "title": "Playlist Song Two",
            "grandparentTitle": "Artist One",
            "parentTitle": "Album One",
            "duration": 2000,
            "Media": [
              { "Part": [ { "key": "/library/parts/t2/file.mp3", "file": "two.mp3" } ] }
            ]
          }
        ]
      }
    }
""".trimIndent()

fun identityJson(): String = """
    {
      "MediaContainer": {
        "machineIdentifier": "server"
      }
    }
""".trimIndent()

fun playlistAddResponseJson(leafCount: Int): String = """
    {
      "MediaContainer": {
        "leafCountAdded": 1,
        "Metadata": [
          { "ratingKey": "p1", "title": "Playlist One", "leafCount": $leafCount, "key": "/playlists/p1/items" }
        ]
      }
    }
""".trimIndent()
