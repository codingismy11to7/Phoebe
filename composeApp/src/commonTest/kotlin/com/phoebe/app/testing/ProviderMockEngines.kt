package com.phoebe.app.testing

import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.MockRequestHandleScope
import io.ktor.client.engine.mock.respond
import io.ktor.client.request.HttpRequestData
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.content.OutgoingContent
import io.ktor.http.headersOf

fun jellyfinSmokeMockEngine(): MockEngine = MockEngine { request ->
    when (request.url.encodedPath) {
        "/Users/AuthenticateByName" -> respondJson(
            """{ "User": { "Id": "user-1", "Name": "Ada" }, "AccessToken": "jf-token" }""",
        )
        "/UserViews" -> respondJson(
            """
            {
              "Items": [
                { "Id": "music", "Name": "Music", "CollectionType": "music" }
              ]
            }
            """.trimIndent(),
        )
        "/Artists/AlbumArtists" -> respondJson(
            """{ "Items": [{ "Id": "artist-1", "Type": "MusicArtist", "Name": "Artist One" }], "TotalRecordCount": 1 }""",
        )
        "/Items" -> when (request.url.parameters["includeItemTypes"]) {
            "MusicAlbum" -> respondJson(
                """{ "Items": [{ "Id": "album-1", "Type": "MusicAlbum", "Name": "Album One", "AlbumArtist": "Artist One" }], "TotalRecordCount": 1 }""",
            )
            "Audio" -> when {
                request.url.parameters["isFavorite"] == "true" ->
                    respondJson("""{ "TotalRecordCount": 0, "Items": [] }""")
                request.url.parameters["parentId"] != null -> respondJson(
                    """
                    {
                      "Items": [
                        {
                          "Id": "track-1",
                          "Type": "Audio",
                          "Name": "Fresh Song",
                          "Album": "Album One",
                          "AlbumId": "album-1",
                          "Artists": ["Artist One"],
                          "RunTimeTicks": 10000000
                        }
                      ],
                      "TotalRecordCount": 1
                    }
                    """.trimIndent(),
                )
                else -> respondJson(
                    """
                    {
                      "Items": [
                        {
                          "Id": "track-1",
                          "Type": "Audio",
                          "Name": "Fresh Song",
                          "Album": "Album One",
                          "AlbumId": "album-1",
                          "Artists": ["Artist One"],
                          "RunTimeTicks": 10000000
                        }
                      ],
                      "TotalRecordCount": 1
                    }
                    """.trimIndent(),
                )
            }
            "Playlist" -> respondJson(
                """{ "Items": [{ "Id": "playlist-1", "Type": "Playlist", "Name": "Road Mix" }], "TotalRecordCount": 1 }""",
            )
            else -> respond("", HttpStatusCode.NotFound)
        }
        "/Playlists" -> when (request.method.value) {
            "POST" -> respondJson("""{ "Id": "playlist-new", "Name": "Smoke Mix" }""")
            else -> respond("", HttpStatusCode.NotFound)
        }
        "/Playlists/playlist-new/Items" -> respondJson("{}")
        "/UserFavoriteItems/track-1" -> respondJson("{}")
        "/UserItems/track-1/Rating" -> respondJson("{}")
        "/Sessions/Playing/Progress" -> respondJson("{}")
        "/Items/track-1" -> when (request.method.value) {
            "POST" -> respondJson(
                """{ "Id": "track-1", "Type": "Audio", "Name": "Renamed Song", "Album": "Album One", "Artists": ["Artist One"] }""",
            )
            else -> respondJson(
                """
                {
                  "Id": "track-1",
                  "Type": "Audio",
                  "Name": "Fresh Song",
                  "Album": "Album One",
                  "AlbumId": "album-1",
                  "Artists": ["Artist One"],
                  "ProductionYear": 2020,
                  "Genres": ["Electronic"],
                  "RunTimeTicks": 10000000
                }
                """.trimIndent(),
            )
        }
        else -> respond("", HttpStatusCode.NotFound)
    }
}

fun embySmokeMockEngine(): MockEngine = MockEngine { request ->
    when (request.url.encodedPath) {
        "/emby/Users/AuthenticateByName" -> respondJson(
            """{ "User": { "Id": "user-1", "Name": "Ada" }, "AccessToken": "emby-token" }""",
        )
        "/emby/Users/user-1/Views" -> respondJson(
            """
            {
              "Items": [
                { "Id": "music", "Name": "Music", "CollectionType": "Music" }
              ]
            }
            """.trimIndent(),
        )
        "/emby/Artists/AlbumArtists" -> respondJson(
            """{ "Items": [{ "Id": "artist-1", "Type": "MusicArtist", "Name": "Artist One" }], "TotalRecordCount": 1 }""",
        )
        "/emby/Items", "/emby/Users/user-1/Items" -> when (request.url.parameters["includeItemTypes"]) {
            "MusicAlbum" -> respondJson(
                """{ "Items": [{ "Id": "album-1", "Type": "MusicAlbum", "Name": "Album One", "AlbumArtist": "Artist One" }], "TotalRecordCount": 1 }""",
            )
            "Audio" -> when {
                request.url.parameters["isFavorite"] == "true" ->
                    respondJson("""{ "TotalRecordCount": 0, "Items": [] }""")
                request.url.parameters["parentId"] != null -> respondJson(
                    """
                    {
                      "Items": [
                        {
                          "Id": "track-1",
                          "Type": "Audio",
                          "Name": "Fresh Song",
                          "Album": "Album One",
                          "AlbumId": "album-1",
                          "Artists": ["Artist One"],
                          "RunTimeTicks": 10000000
                        }
                      ],
                      "TotalRecordCount": 1
                    }
                    """.trimIndent(),
                )
                else -> respondJson(
                    """
                    {
                      "Items": [
                        {
                          "Id": "track-1",
                          "Type": "Audio",
                          "Name": "Fresh Song",
                          "Album": "Album One",
                          "AlbumId": "album-1",
                          "Artists": ["Artist One"],
                          "RunTimeTicks": 10000000
                        }
                      ],
                      "TotalRecordCount": 1
                    }
                    """.trimIndent(),
                )
            }
            "Playlist" -> respondJson(
                """{ "Items": [{ "Id": "playlist-1", "Type": "Playlist", "Name": "Road Mix" }], "TotalRecordCount": 1 }""",
            )
            else -> respond("", HttpStatusCode.NotFound)
        }
        "/emby/Playlists" -> when (request.method.value) {
            "POST" -> respondJson("""{ "Id": "playlist-new", "Name": "Smoke Mix" }""")
            else -> respond("", HttpStatusCode.NotFound)
        }
        "/emby/Playlists/playlist-new/Items" -> respondJson("{}")
        "/emby/Users/user-1/FavoriteItems/track-1" -> respondJson("{}")
        "/emby/Users/user-1/Items/track-1/Rating" -> respondJson("{}")
        "/emby/Sessions/Playing/Progress" -> respondJson("{}")
        else -> respond("", HttpStatusCode.NotFound)
    }
}

fun navidromeSmokeMockEngine(): MockEngine = MockEngine { request ->
    when (request.url.encodedPath) {
        "/rest/ping.view" -> respondJson("""{ "subsonic-response": { "status": "ok" } }""")
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
              { "id": "al1", "name": "Radio House", "artist": "North Lake", "artistId": "ar1", "songCount": 1, "year": 2025, "genre": "Electronic" }
            ] } } }
            """.trimIndent(),
        )
        "/rest/getAlbum.view" -> respondJson(
            """
            { "subsonic-response": { "status": "ok", "album": {
              "id": "al1", "name": "Radio House", "artist": "North Lake", "song": [
                { "id": "tr1", "title": "Night Signals", "album": "Radio House", "albumId": "al1", "artist": "North Lake", "duration": 245, "suffix": "mp3", "userRating": 4 }
              ]
            } } }
            """.trimIndent(),
        )
        "/rest/getPlaylists.view" -> respondJson(
            """
            { "subsonic-response": { "status": "ok", "playlists": { "playlist": [
              { "id": "pl1", "name": "Road Mix", "songCount": 1 }
            ] } } }
            """.trimIndent(),
        )
        "/rest/getPlaylist.view" -> respondJson(
            """
            { "subsonic-response": { "status": "ok", "playlist": {
              "id": "pl1", "name": "Road Mix", "entry": [
                { "id": "tr1", "title": "Night Signals", "album": "Radio House", "albumId": "al1", "artist": "North Lake", "duration": 245, "suffix": "mp3" }
              ]
            } } }
            """.trimIndent(),
        )
        "/rest/createPlaylist.view" -> respondJson(
            """{ "subsonic-response": { "status": "ok", "playlist": { "id": "pl-new", "name": "Smoke Mix" } } }""",
        )
        "/rest/updatePlaylist.view" -> respondJson("""{ "subsonic-response": { "status": "ok" } }""")
        "/rest/star.view" -> respondJson("""{ "subsonic-response": { "status": "ok" } }""")
        "/rest/setRating.view" -> respondJson("""{ "subsonic-response": { "status": "ok" } }""")
        "/rest/scrobble.view" -> respondJson("""{ "subsonic-response": { "status": "ok" } }""")
        "/rest/getStarred2.view" -> respondJson("""{ "subsonic-response": { "status": "ok", "starred2": {} } }""")
        else -> respond("", HttpStatusCode.NotFound)
    }
}

fun musicAssistantSmokeMockEngine(): MockEngine = MockEngine { request ->
    when (request.url.encodedPath) {
        "/auth/login" -> respondJson(
            """
            {
              "success": true,
              "token": "ma-token",
              "user": { "username": "ada" }
            }
            """.trimIndent(),
        )
        "/info" -> respondJson("""{ "server_id": "ma-smoke" }""")
        "/api" -> {
            val body = request.bodyText()
            when {
                body.contains("music/artists/library_items") -> respondJson(
                    """
                    {
                      "result": [
                        { "item_id": "ar1", "name": "North Lake", "uri": "library://artist/ar1" }
                      ]
                    }
                    """.trimIndent(),
                )
                body.contains("music/albums/library_items") -> respondJson(
                    """
                    {
                      "result": [
                        {
                          "item_id": "al1",
                          "name": "Radio House",
                          "uri": "library://album/al1",
                          "artists": [{ "item_id": "ar1", "name": "North Lake", "uri": "library://artist/ar1" }]
                        }
                      ]
                    }
                    """.trimIndent(),
                )
                body.contains("music/tracks/library_items") -> respondJson(
                    """
                    {
                      "result": [
                        {
                          "item_id": "tr1",
                          "name": "Night Signals",
                          "uri": "library://track/tr1",
                          "duration": 245,
                          "artists": [{ "item_id": "ar1", "name": "North Lake", "uri": "library://artist/ar1" }],
                          "album": { "item_id": "al1", "name": "Radio House", "uri": "library://album/al1" }
                        }
                      ]
                    }
                    """.trimIndent(),
                )
                body.contains("music/playlists/library_items") -> respondJson(
                    """
                    {
                      "result": [
                        { "item_id": "pl1", "name": "Road Mix", "uri": "library://playlist/pl1" }
                      ]
                    }
                    """.trimIndent(),
                )
                body.contains("music/playlists/create_playlist") -> respondJson(
                    """
                    {
                      "result": {
                        "item_id": "pl-new",
                        "name": "Smoke Mix",
                        "uri": "library://playlist/pl-new"
                      }
                    }
                    """.trimIndent(),
                )
                body.contains("music/playlists/add_playlist_tracks") -> respondJson("""{ "result": {} }""")
                body.contains("music/albums/album_tracks") -> respondJson(
                    """
                    {
                      "result": [
                        {
                          "item_id": "tr1",
                          "name": "Night Signals",
                          "uri": "library://track/tr1",
                          "duration": 245,
                          "artists": [{ "item_id": "ar1", "name": "North Lake", "uri": "library://artist/ar1" }],
                          "album": { "item_id": "al1", "name": "Radio House", "uri": "library://album/al1" }
                        }
                      ]
                    }
                    """.trimIndent(),
                )
                body.contains("music/playlists/playlist_tracks") -> respondJson(
                    """
                    {
                      "result": [
                        {
                          "item_id": "tr1",
                          "name": "Night Signals",
                          "uri": "library://track/tr1",
                          "duration": 245,
                          "artists": [{ "item_id": "ar1", "name": "North Lake", "uri": "library://artist/ar1" }],
                          "album": { "item_id": "al1", "name": "Radio House", "uri": "library://album/al1" }
                        }
                      ]
                    }
                    """.trimIndent(),
                )
                body.contains("music/favorites/add_item") -> respondJson("""{ "result": {} }""")
                else -> respondJson("""{ "result": [] }""")
            }
        }
        else -> respond("", HttpStatusCode.NotFound)
    }
}

private fun MockRequestHandleScope.respondJson(content: String) = respond(
    content = content,
    status = HttpStatusCode.OK,
    headers = headersOf(HttpHeaders.ContentType, "application/json"),
)

private fun HttpRequestData.bodyText(): String =
    when (val content = body) {
        is OutgoingContent.ByteArrayContent -> content.bytes().decodeToString()
        is OutgoingContent.NoContent -> ""
        else -> content.toString()
    }
