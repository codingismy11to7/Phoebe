package com.phoebe.app.data

import com.phoebe.app.domain.Album
import com.phoebe.app.domain.Artist
import com.phoebe.app.domain.CatalogSnapshot
import com.phoebe.app.domain.LIKED_SONGS_PLAYLIST_TITLE
import com.phoebe.app.domain.MusicLibrary
import com.phoebe.app.domain.Playlist
import com.phoebe.app.domain.PlexServer
import com.phoebe.app.domain.PlexSession
import com.phoebe.app.domain.Track
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.get
import io.ktor.client.request.parameter
import io.ktor.http.encodeURLParameter
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

internal val NavidromeAllMusicLibrary = MusicLibrary("all", "All Music")

class SubsonicClient(
    private val httpClient: HttpClient,
) {
    suspend fun signIn(serverUrl: String, username: String, password: String): PlexSession {
        val base = serverUrl.trimEnd('/')
        request<SubsonicRoot>(base, username, password, "ping")
        return PlexSession(
            token = password,
            userName = username,
            userId = username,
            selectedServer = PlexServer(
                id = "navidrome:${base.hashCode().toUInt().toString(16)}",
                name = base.removePrefix("https://").removePrefix("http://"),
                uri = base,
                owned = true,
            ),
            selectedLibrary = NavidromeAllMusicLibrary,
            providerType = com.phoebe.app.domain.MediaProviderType.Navidrome,
        )
    }

    suspend fun libraries(server: PlexServer, username: String, password: String): List<MusicLibrary> {
        runCatching { request<SubsonicMusicFoldersRoot>(server.uri, username, password, "getMusicFolders") }
        return listOf(NavidromeAllMusicLibrary)
    }

    suspend fun buildCatalog(server: PlexServer, library: MusicLibrary, username: String, password: String): CatalogSnapshot {
        val artistsFromEndpoint = artists(server, library, username, password)
        val albums = albums(server, library, username, password)
        val starred = starred(server, username, password)
        val albumsById = albums.associateBy { it.id }
        val tracksByAlbum = albums.associate { album ->
            album.id to albumTracks(server, album, username, password).map { track ->
                val knownAlbum = albumsById[album.id]
                if (knownAlbum == null) track else track.copy(
                    album = knownAlbum.title,
                    artist = track.artist.takeUnless { it == "Unknown artist" } ?: knownAlbum.artist,
                    thumbUrl = track.thumbUrl ?: knownAlbum.thumbUrl,
                    parentAlbumId = album.id,
                )
            }
        }
        val albumsWithArtwork = enrichAlbumsFromTracks(albums, tracksByAlbum)
            .map { album -> if (album.id.removePrefix("navidrome:") in starred.albumIds) album.copy(favorite = true) else album }
        val artists = mergeArtistsFromAlbums(
            artistsFromEndpoint.map { artist ->
                if (artist.id.removePrefix("navidrome:") in starred.artistIds) artist.copy(favorite = true) else artist
            },
            albumsWithArtwork,
        )
        val likedTracks = starred.tracks
        val playlists = playlists(server, username, password).withLikedSongs(likedTracks)
        return CatalogSnapshot(
            artists = artists,
            albums = albumsWithArtwork,
            playlists = playlists,
            tracksByParent = tracksByAlbum.withLikedSongs(likedTracks),
        )
    }

    suspend fun quickCatalog(server: PlexServer, username: String, password: String): CatalogSnapshot {
        val artistsFromEndpoint = artists(server, NavidromeAllMusicLibrary, username, password)
        val albumPage = albumPage(server, username, password, pageIndex = 0)
        val starred = starred(server, username, password)
        val tracksByAlbum = albumPage.items.associate { album ->
            album.id to albumTracks(server, album, username, password).map { track ->
                track.copy(
                    album = track.album.takeUnless { it == "Unknown album" } ?: album.title,
                    artist = track.artist.takeUnless { it == "Unknown artist" } ?: album.artist,
                    thumbUrl = track.thumbUrl ?: album.thumbUrl,
                    parentAlbumId = album.id,
                )
            }
        }
        val albumsWithArtwork = enrichAlbumsFromTracks(albumPage.items, tracksByAlbum)
            .map { album -> if (album.id.removePrefix("navidrome:") in starred.albumIds) album.copy(favorite = true) else album }
        val artists = mergeArtistsFromAlbums(
            artistsFromEndpoint.map { artist ->
                if (artist.id.removePrefix("navidrome:") in starred.artistIds) artist.copy(favorite = true) else artist
            },
            albumsWithArtwork,
        )
        val likedTracks = starred.tracks
        return CatalogSnapshot(
            artists = artists,
            albums = albumsWithArtwork,
            playlists = playlists(server, username, password).withLikedSongs(likedTracks),
            tracksByParent = tracksByAlbum.withLikedSongs(likedTracks),
            remotePageInfo = com.phoebe.app.domain.CatalogPageInfo(
                pageSize = albumPage.pageSize,
                artistTotal = artists.size,
                loadedArtistPages = setOf(0),
                albumTotal = albumPage.total,
                loadedAlbumPages = if (albumPage.items.isNotEmpty()) setOf(0) else emptySet(),
            ),
        )
    }

    suspend fun artists(server: PlexServer, library: MusicLibrary, username: String, password: String): List<Artist> {
        val response = request<SubsonicArtistsRoot>(server.uri, username, password, "getArtists")
        return response.response.artists?.index.orEmpty()
            .flatMap { it.artist }
            .map {
                Artist(
                    id = it.id,
                    title = it.name,
                    albumCount = it.albumCount ?: 0,
                    thumbUrl = it.coverArt?.let { cover -> coverArtUrl(server.uri, username, password, cover) },
                    favorite = it.starred != null,
                )
            }
    }

    suspend fun albums(server: PlexServer, library: MusicLibrary, username: String, password: String): List<Album> {
        val albums = mutableListOf<Album>()
        var offset = 0
        while (true) {
            val response = request<SubsonicAlbumListRoot>(server.uri, username, password, "getAlbumList2") {
                parameter("type", "alphabeticalByArtist")
                parameter("size", SubsonicPageSize)
                parameter("offset", offset)
            }
            val page = response.response.albumList2?.album.orEmpty()
            if (page.isEmpty()) break
            albums += page.map { it.toAlbum(server.uri, username, password) }
            if (page.size < SubsonicPageSize) break
            offset += page.size
        }
        return albums
    }

    suspend fun albumPage(server: PlexServer, username: String, password: String, pageIndex: Int): ProviderItemPage<Album> {
        val response = request<SubsonicAlbumListRoot>(server.uri, username, password, "getAlbumList2") {
            parameter("type", "alphabeticalByArtist")
            parameter("size", SubsonicPageSize)
            parameter("offset", pageIndex * SubsonicPageSize)
        }
        val albums = response.response.albumList2?.album.orEmpty().map { it.toAlbum(server.uri, username, password) }
        val total = response.response.albumList2?.totalCount ?: response.response.albumList2?.total ?: when {
            albums.size < SubsonicPageSize -> pageIndex * SubsonicPageSize + albums.size
            else -> (pageIndex + 2) * SubsonicPageSize
        }
        return ProviderItemPage(
            items = albums,
            total = total,
            pageIndex = pageIndex,
            pageSize = SubsonicPageSize,
        )
    }

    suspend fun albumTracks(server: PlexServer, album: Album, username: String, password: String): List<Track> {
        val id = album.id.removePrefix("navidrome:")
        val response = request<SubsonicAlbumRoot>(server.uri, username, password, "getAlbum") {
            parameter("id", id)
        }
        return response.response.album?.song.orEmpty().map { it.toTrack(server.uri, username, password) }
    }

    suspend fun playlistTracks(server: PlexServer, playlist: Playlist, username: String, password: String): List<Track> {
        val id = playlist.id.removePrefix("navidrome:")
        val response = request<SubsonicPlaylistRoot>(server.uri, username, password, "getPlaylist") {
            parameter("id", id)
        }
        return response.response.playlist?.entry.orEmpty().map { it.toTrack(server.uri, username, password) }
    }

    suspend fun playlists(server: PlexServer, username: String, password: String): List<Playlist> {
        val response = request<SubsonicPlaylistsRoot>(server.uri, username, password, "getPlaylists")
        return response.response.playlists?.playlist.orEmpty().map {
            Playlist(
                id = it.id,
                title = it.name,
                trackCount = it.songCount ?: 0,
                thumbUrl = it.coverArt?.let { cover -> coverArtUrl(server.uri, username, password, cover) },
                favorite = false,
            )
        }
    }

    private suspend fun starred(server: PlexServer, username: String, password: String): SubsonicStarredItems =
        runCatching {
            val response = request<SubsonicStarred2Root>(server.uri, username, password, "getStarred2")
            val starred = response.response.starred2 ?: return@runCatching SubsonicStarredItems()
            SubsonicStarredItems(
                artistIds = starred.artist.map { it.id }.toSet(),
                albumIds = starred.album.map { it.id }.toSet(),
                tracks = starred.song.map { it.toTrack(server.uri, username, password) },
            )
        }.getOrDefault(SubsonicStarredItems())

    suspend fun createPlaylist(server: PlexServer, username: String, password: String, title: String, itemIds: List<String>): Playlist {
        val response = request<SubsonicPlaylistRoot>(server.uri, username, password, "createPlaylist") {
            parameter("name", title)
            itemIds.forEach { parameter("songId", it.removePrefix("navidrome:")) }
        }
        val playlist = response.response.playlist ?: return Playlist(id = title, title = title, trackCount = itemIds.size)
        return Playlist(id = playlist.id, title = playlist.name, trackCount = playlist.songCount ?: itemIds.size)
    }

    suspend fun addTracksToPlaylist(server: PlexServer, username: String, password: String, playlistId: String, itemIds: List<String>) {
        if (itemIds.isEmpty()) return
        request<SubsonicRoot>(server.uri, username, password, "updatePlaylist") {
            parameter("playlistId", playlistId.removePrefix("navidrome:"))
            itemIds.forEach { parameter("songIdToAdd", it.removePrefix("navidrome:")) }
        }
    }

    suspend fun setFavorite(server: PlexServer, username: String, password: String, itemId: String, favorite: Boolean, kind: ProviderItemKind = ProviderItemKind.Unknown) {
        request<SubsonicRoot>(server.uri, username, password, if (favorite) "star" else "unstar") {
            when (kind) {
                ProviderItemKind.Artist -> parameter("artistId", itemId.removePrefix("navidrome:"))
                ProviderItemKind.Album -> parameter("albumId", itemId.removePrefix("navidrome:"))
                else -> parameter("id", itemId.removePrefix("navidrome:"))
            }
        }
    }

    suspend fun rateItem(server: PlexServer, username: String, password: String, itemId: String, rating: Float?) {
        request<SubsonicRoot>(server.uri, username, password, "setRating") {
            parameter("id", itemId.removePrefix("navidrome:"))
            parameter("rating", rating?.toInt()?.coerceIn(0, 5) ?: 0)
        }
    }

    suspend fun scrobble(server: PlexServer, username: String, password: String, itemId: String, submission: Boolean) {
        request<SubsonicRoot>(server.uri, username, password, "scrobble") {
            parameter("id", itemId.removePrefix("navidrome:"))
            parameter("submission", submission)
        }
    }

    suspend fun similarSongs(server: PlexServer, username: String, password: String, itemId: String, count: Int = 50): List<Track> {
        val response = request<SubsonicSimilarSongsRoot>(server.uri, username, password, "getSimilarSongs2") {
            parameter("id", itemId.removePrefix("navidrome:"))
            parameter("count", count)
        }
        return response.response.similarSongs2?.song.orEmpty().map { it.toTrack(server.uri, username, password) }
    }

    fun streamUrl(baseUrl: String, username: String, password: String, id: String): String =
        subsonicBinaryUrl(baseUrl, username, password, "stream", id)

    fun downloadUrl(baseUrl: String, username: String, password: String, id: String): String =
        subsonicBinaryUrl(baseUrl, username, password, "download", id)

    fun coverArtUrl(baseUrl: String, username: String, password: String, id: String): String =
        subsonicBinaryUrl(baseUrl, username, password, "getCoverArt", id)

    private suspend inline fun <reified T> request(
        baseUrl: String,
        username: String,
        password: String,
        method: String,
        crossinline block: io.ktor.client.request.HttpRequestBuilder.() -> Unit = {},
    ): T {
        val salt = newSalt()
        val token = md5Hex(password + salt)
        return httpClient.get("${baseUrl.trimEnd('/')}/rest/$method.view") {
            parameter("u", username)
            parameter("t", token)
            parameter("s", salt)
            parameter("v", SubsonicApiVersion)
            parameter("c", SubsonicClientName)
            parameter("f", "json")
            block()
        }.body<T>().also { root ->
            val status = (root as? SubsonicStatusCarrier)?.statusForCheck
            if (status == "failed") error("Subsonic $method failed.")
        }
    }

    private fun newSalt(): String =
        "phoebe${saltCounter++}${kotlin.random.Random.nextInt().toUInt().toString(16)}"

    private companion object {
        const val SubsonicApiVersion = "1.16.1"
        const val SubsonicClientName = "phoebe"
        const val SubsonicPageSize = 500
        var saltCounter = 1
    }
}

private fun subsonicBinaryUrl(baseUrl: String, username: String, password: String, method: String, id: String): String {
    val salt = "phoebe${kotlin.random.Random.nextInt().toUInt().toString(16)}"
    val token = md5Hex(password + salt)
    val params = listOf(
        "u" to username,
        "t" to token,
        "s" to salt,
        "v" to "1.16.1",
        "c" to "phoebe",
        "id" to id.removePrefix("navidrome:"),
    ).joinToString("&") { (k, v) -> "${k.encodeURLParameter()}=${v.encodeURLParameter()}" }
    return "${baseUrl.trimEnd('/')}/rest/$method.view?$params"
}

private interface SubsonicStatusCarrier {
    val statusForCheck: String?
}

@Serializable
private data class SubsonicRoot(
    @SerialName("subsonic-response") val response: SubsonicResponse = SubsonicResponse(),
) : SubsonicStatusCarrier {
    override val statusForCheck: String? get() = response.status
}

@Serializable
private data class SubsonicMusicFoldersRoot(@SerialName("subsonic-response") val response: SubsonicResponse = SubsonicResponse())

@Serializable
private data class SubsonicArtistsRoot(@SerialName("subsonic-response") val response: SubsonicResponse = SubsonicResponse())

@Serializable
private data class SubsonicAlbumListRoot(@SerialName("subsonic-response") val response: SubsonicResponse = SubsonicResponse())

@Serializable
private data class SubsonicAlbumRoot(@SerialName("subsonic-response") val response: SubsonicResponse = SubsonicResponse())

@Serializable
private data class SubsonicPlaylistsRoot(@SerialName("subsonic-response") val response: SubsonicResponse = SubsonicResponse())

@Serializable
private data class SubsonicPlaylistRoot(@SerialName("subsonic-response") val response: SubsonicResponse = SubsonicResponse())

@Serializable
private data class SubsonicSimilarSongsRoot(@SerialName("subsonic-response") val response: SubsonicResponse = SubsonicResponse())

@Serializable
private data class SubsonicStarred2Root(@SerialName("subsonic-response") val response: SubsonicResponse = SubsonicResponse())

@Serializable
private data class SubsonicResponse(
    val status: String? = null,
    val musicFolders: SubsonicMusicFolders? = null,
    val artists: SubsonicArtists? = null,
    val albumList2: SubsonicAlbumList? = null,
    val album: SubsonicAlbumDto? = null,
    val playlists: SubsonicPlaylists? = null,
    val playlist: SubsonicPlaylistDto? = null,
    val similarSongs2: SubsonicSongs? = null,
    val starred2: SubsonicStarred2? = null,
)

@Serializable
private data class SubsonicMusicFolders(val musicFolder: List<SubsonicMusicFolder> = emptyList())

@Serializable
private data class SubsonicMusicFolder(val id: String, val name: String)

@Serializable
private data class SubsonicArtists(val index: List<SubsonicArtistIndex> = emptyList())

@Serializable
private data class SubsonicArtistIndex(val artist: List<SubsonicArtistDto> = emptyList())

@Serializable
private data class SubsonicArtistDto(
    val id: String,
    val name: String,
    val albumCount: Int? = null,
    val coverArt: String? = null,
    val starred: String? = null,
)

@Serializable
private data class SubsonicAlbumList(
    val album: List<SubsonicAlbumDto> = emptyList(),
    @SerialName("totalCount") val totalCount: Int? = null,
    val total: Int? = null,
)

@Serializable
private data class SubsonicAlbumDto(
    val id: String,
    val name: String,
    val artist: String? = null,
    val artistId: String? = null,
    val coverArt: String? = null,
    val songCount: Int? = null,
    val duration: Int? = null,
    val created: String? = null,
    val year: Int? = null,
    val genre: String? = null,
    val starred: String? = null,
    val song: List<SubsonicSongDto> = emptyList(),
)

@Serializable
private data class SubsonicSongs(val song: List<SubsonicSongDto> = emptyList())

@Serializable
private data class SubsonicStarred2(
    val artist: List<SubsonicArtistDto> = emptyList(),
    val album: List<SubsonicAlbumDto> = emptyList(),
    val song: List<SubsonicSongDto> = emptyList(),
)

private data class SubsonicStarredItems(
    val artistIds: Set<String> = emptySet(),
    val albumIds: Set<String> = emptySet(),
    val tracks: List<Track> = emptyList(),
)

@Serializable
private data class SubsonicSongDto(
    val id: String,
    val title: String,
    val album: String? = null,
    val albumId: String? = null,
    val artist: String? = null,
    val artistId: String? = null,
    val duration: Int? = null,
    val coverArt: String? = null,
    val created: String? = null,
    val year: Int? = null,
    val genre: String? = null,
    val bitRate: Int? = null,
    val suffix: String? = null,
    val contentType: String? = null,
    val path: String? = null,
    val starred: String? = null,
    val userRating: Int? = null,
)

@Serializable
private data class SubsonicPlaylists(val playlist: List<SubsonicPlaylistDto> = emptyList())

@Serializable
private data class SubsonicPlaylistDto(
    val id: String,
    val name: String,
    val songCount: Int? = null,
    val coverArt: String? = null,
    val entry: List<SubsonicSongDto> = emptyList(),
)

private fun mergeArtistsFromAlbums(artists: List<Artist>, albums: List<Album>): List<Artist> {
    if (albums.isEmpty()) return artists
    val existing = artists.associateBy { it.title.normalizedArtistKey() }.toMutableMap()
    albums
        .groupBy { it.artist.normalizedArtistKey() }
        .forEach { (key, artistAlbums) ->
            if (key.isBlank() || key == "unknown artist") return@forEach
            val current = existing[key]
            val title = current?.title ?: artistAlbums.first().artist
            existing[key] = current?.copy(
                albumCount = maxOf(current.albumCount, artistAlbums.size),
                thumbUrl = current.thumbUrl ?: artistAlbums.firstNotNullOfOrNull { it.thumbUrl },
                favorite = current.favorite || artistAlbums.any { it.favorite },
            ) ?: Artist(
                id = "artist:${key.hashCode().toUInt().toString(16)}",
                title = title,
                albumCount = artistAlbums.size,
                thumbUrl = artistAlbums.firstNotNullOfOrNull { it.thumbUrl },
                favorite = artistAlbums.any { it.favorite },
            )
        }
    return existing.values.sortedBy { it.title.lowercase() }
}

private fun String.normalizedArtistKey(): String =
    trim().lowercase()

private fun enrichAlbumsFromTracks(albums: List<Album>, tracksByAlbum: Map<String, List<Track>>): List<Album> =
    albums.map { album ->
        if (!album.thumbUrl.isNullOrBlank()) {
            album
        } else {
            album.copy(thumbUrl = tracksByAlbum[album.id].orEmpty().firstNotNullOfOrNull { it.thumbUrl })
        }
    }

private fun List<Playlist>.withLikedSongs(tracks: List<Track>): List<Playlist> {
    if (tracks.isEmpty()) return this
    val liked = Playlist(
        id = "liked-songs",
        title = LIKED_SONGS_PLAYLIST_TITLE,
        trackCount = tracks.size,
        thumbUrl = tracks.firstNotNullOfOrNull { it.thumbUrl },
    )
    return listOf(liked) + filterNot { it.title == LIKED_SONGS_PLAYLIST_TITLE }
}

private fun Map<String, List<Track>>.withLikedSongs(tracks: List<Track>): Map<String, List<Track>> =
    if (tracks.isEmpty()) this else this + ("liked-songs" to tracks)

private fun SubsonicAlbumDto.toAlbum(baseUrl: String, username: String, password: String): Album =
    Album(
        id = id,
        title = name,
        artist = artist ?: "Unknown artist",
        year = year,
        genre = genre,
        dateAddedMs = created?.parseSubsonicTimestampMillis(),
        thumbUrl = coverArt?.let { subsonicBinaryUrl(baseUrl, username, password, "getCoverArt", it) },
        favorite = starred != null,
    )

private fun SubsonicSongDto.toTrack(baseUrl: String, username: String, password: String): Track =
    Track(
        id = id,
        title = title,
        artist = artist ?: "Unknown artist",
        album = album ?: "Unknown album",
        durationMs = (duration ?: 0) * 1000L,
        streamUrl = subsonicBinaryUrl(baseUrl, username, password, "stream", id),
        downloadUrl = subsonicBinaryUrl(baseUrl, username, password, "download", id),
        thumbUrl = coverArt?.let { subsonicBinaryUrl(baseUrl, username, password, "getCoverArt", it) },
        year = year,
        genre = genre,
        filepath = path,
        audioCodec = suffix?.uppercase() ?: contentType?.substringAfter('/')?.uppercase(),
        bitrateKbps = bitRate,
        dateAddedMs = created?.parseSubsonicTimestampMillis(),
        rating = userRating?.toFloat()?.coerceIn(0f, 5f),
        parentAlbumId = albumId,
    )

private fun String.parseSubsonicTimestampMillis(): Long? {
    val trimmed = trim()
    if (trimmed.length < 19) return null
    val year = trimmed.substringOrNull(0, 4)?.toIntOrNull() ?: return null
    val month = trimmed.substringOrNull(5, 7)?.toIntOrNull() ?: return null
    val day = trimmed.substringOrNull(8, 10)?.toIntOrNull() ?: return null
    val hour = trimmed.substringOrNull(11, 13)?.toIntOrNull() ?: return null
    val minute = trimmed.substringOrNull(14, 16)?.toIntOrNull() ?: return null
    val second = trimmed.substringOrNull(17, 19)?.toIntOrNull() ?: return null
    val offsetMinutes = when {
        trimmed.endsWith("Z") -> 0
        trimmed.length >= 25 && (trimmed[trimmed.length - 6] == '+' || trimmed[trimmed.length - 6] == '-') -> {
            val sign = if (trimmed[trimmed.length - 6] == '-') -1 else 1
            val offsetHour = trimmed.substringOrNull(trimmed.length - 5, trimmed.length - 3)?.toIntOrNull() ?: 0
            val offsetMinute = trimmed.substringOrNull(trimmed.length - 2, trimmed.length)?.toIntOrNull() ?: 0
            sign * (offsetHour * 60 + offsetMinute)
        }
        else -> 0
    }
    val epochDay = daysFromCivil(year, month, day)
    return (((epochDay * 24 + hour) * 60 + minute - offsetMinutes) * 60 + second) * 1000
}

private fun String.substringOrNull(startIndex: Int, endIndex: Int): String? =
    if (startIndex >= 0 && endIndex <= length && startIndex <= endIndex) substring(startIndex, endIndex) else null

private fun daysFromCivil(year: Int, month: Int, day: Int): Long {
    var y = year
    val m = month
    y -= if (m <= 2) 1 else 0
    val era = if (y >= 0) y else y - 399
    val eraDiv = era / 400
    val yoe = y - eraDiv * 400
    val mp = m + if (m > 2) -3 else 9
    val doy = (153 * mp + 2) / 5 + day - 1
    val doe = yoe * 365 + yoe / 4 - yoe / 100 + doy
    return eraDiv.toLong() * 146097L + doe - 719468L
}
