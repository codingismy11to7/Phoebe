package com.phoebe.app.data

import com.phoebe.app.domain.Album
import com.phoebe.app.domain.Artist
import com.phoebe.app.domain.CatalogSnapshot
import com.phoebe.app.domain.MediaProviderType
import com.phoebe.app.domain.MusicLibrary
import com.phoebe.app.domain.PlexRadioStation
import com.phoebe.app.domain.PlexRadioStationCategory
import com.phoebe.app.domain.PlexServer
import com.phoebe.app.domain.PlexSession
import com.phoebe.app.domain.Playlist
import com.phoebe.app.domain.Track
import com.phoebe.app.domain.TrackMetadataUpdate
import com.phoebe.app.platform.currentTimeMs
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

class JellyfinProviderAdapter(
    private val client: JellyfinClient,
) : MusicProviderAdapter {
    override val providerType = MediaProviderType.Jellyfin
    override val capabilities = ProviderCapabilities(
        quickConnect = true,
        pagedCatalog = true,
        metadataEdit = true,
        itemRadio = true,
    )

    override suspend fun signIn(serverUrl: String, username: String, password: String): PlexSession {
        val auth = client.authenticate(serverUrl, username, password)
        return PlexSession(
            token = auth.token,
            userName = auth.userName,
            selectedServer = auth.server,
            providerType = providerType,
            userId = auth.userId,
        )
    }

    override suspend fun libraries(session: PlexSession, server: PlexServer): List<MusicLibrary> =
        client.libraries(server, session.token, session.userId ?: return emptyList())

    override suspend fun buildCatalog(session: PlexSession): CatalogSnapshot =
        client.buildEmbyFamilyCatalog(session)

    override suspend fun albumTracks(session: PlexSession, album: Album): List<Track> =
        client.albumTracks(session.selectedServer ?: return emptyList(), album, session.token, session.userId ?: return emptyList())

    override suspend fun playlistTracks(session: PlexSession, playlist: Playlist): List<Track> =
        client.playlistTracks(session.selectedServer ?: return emptyList(), playlist, session.token, session.userId ?: return emptyList())

    override suspend fun createPlaylist(session: PlexSession, title: String, initialTracks: List<Track>): Playlist? =
        client.createPlaylist(
            server = session.selectedServer ?: return null,
            token = session.token,
            userId = session.userId ?: return null,
            title = title,
            itemIds = initialTracks.map { it.id.removePrefix("jellyfin:") },
        )

    override suspend fun addTracksToPlaylist(session: PlexSession, playlist: Playlist, tracks: List<Track>) {
        client.addTracksToPlaylist(
            server = session.selectedServer ?: return,
            token = session.token,
            userId = session.userId ?: return,
            playlistId = playlist.id.removePrefix("jellyfin:"),
            itemIds = tracks.map { it.id.removePrefix("jellyfin:") },
        )
    }

    override suspend fun setFavorite(session: PlexSession, itemId: String, favorite: Boolean, kind: ProviderItemKind): Boolean =
        runCatching {
            client.setFavorite(session.selectedServer ?: return false, session.token, itemId.removePrefix("jellyfin:"), favorite)
        }.isSuccess

    override suspend fun rateItem(session: PlexSession, itemId: String, rating: Float?): Boolean =
        runCatching {
            client.rateItem(session.selectedServer ?: return false, session.token, itemId.removePrefix("jellyfin:"), rating)
        }.isSuccess

    override suspend fun editTrackMetadata(session: PlexSession, itemId: String, original: Track, update: TrackMetadataUpdate): Boolean =
        runCatching {
            client.editTrackMetadata(session.selectedServer ?: return false, session.token, itemId.removePrefix("jellyfin:"), original, update)
        }.isSuccess

    override suspend fun reportPlayback(session: PlexSession, track: Track, positionMs: Long, isPaused: Boolean, event: JellyfinPlaybackEvent) {
        client.reportPlayback(session.selectedServer ?: return, session.token, track.id.removePrefix("jellyfin:"), positionMs, isPaused, event)
    }

    override suspend fun markPlayed(session: PlexSession, track: Track, playedAtMs: Long) {
        client.markPlayed(
            server = session.selectedServer ?: return,
            token = session.token,
            userId = session.userId ?: return,
            itemId = track.id.removePrefix("jellyfin:"),
        )
    }

    override suspend fun radioStation(session: PlexSession, artist: Artist): PlexRadioStation? =
        PlexRadioStation(
            id = "jellyfin-artist-radio-${artist.id}",
            title = "${artist.title} Radio",
            subtitle = "Jellyfin Instant Mix",
            key = artist.id,
            thumbUrl = artist.thumbUrl,
            category = PlexRadioStationCategory.Artist,
        )
}

class EmbyProviderAdapter(
    private val client: EmbyClient,
) : MusicProviderAdapter {
    override val providerType = MediaProviderType.Emby
    override val capabilities = ProviderCapabilities(
        pagedCatalog = true,
        metadataEdit = true,
        itemRadio = true,
    )

    override suspend fun signIn(serverUrl: String, username: String, password: String): PlexSession {
        val auth = client.authenticate(serverUrl, username, password)
        return PlexSession(
            token = auth.token,
            userName = auth.userName,
            selectedServer = auth.server,
            providerType = providerType,
            userId = auth.userId,
        )
    }

    override suspend fun libraries(session: PlexSession, server: PlexServer): List<MusicLibrary> =
        client.libraries(server, session.token, session.userId ?: return emptyList())

    override suspend fun buildCatalog(session: PlexSession): CatalogSnapshot =
        client.buildEmbyFamilyCatalog(session)

    override suspend fun setFavorite(session: PlexSession, itemId: String, favorite: Boolean, kind: ProviderItemKind): Boolean =
        runCatching {
            client.setFavorite(
                server = session.selectedServer ?: return false,
                token = session.token,
                userId = session.userId ?: return false,
                itemId = itemId.removePrefix("emby:"),
                favorite = favorite,
            )
        }.isSuccess

    override suspend fun rateItem(session: PlexSession, itemId: String, rating: Float?): Boolean =
        runCatching {
            client.rateItem(
                server = session.selectedServer ?: return false,
                token = session.token,
                userId = session.userId ?: return false,
                itemId = itemId.removePrefix("emby:"),
                rating = rating,
            )
        }.isSuccess

    override suspend fun reportPlayback(session: PlexSession, track: Track, positionMs: Long, isPaused: Boolean, event: JellyfinPlaybackEvent) {
        client.reportPlayback(session.selectedServer ?: return, session.token, track.id.removePrefix("emby:"), positionMs, isPaused, event)
    }

    override suspend fun markPlayed(session: PlexSession, track: Track, playedAtMs: Long) {
        client.markPlayed(
            server = session.selectedServer ?: return,
            token = session.token,
            userId = session.userId ?: return,
            itemId = track.id.removePrefix("emby:"),
        )
    }
}

class NavidromeProviderAdapter(
    private val client: SubsonicClient,
) : MusicProviderAdapter {
    private val scrobbleMutex = Mutex()
    private var activeScrobbleKey: NavidromeScrobbleKey? = null
    private var submittedScrobbleKey: NavidromeScrobbleKey? = null
    private var submittedScrobbleAtMs: Long = 0L

    override val providerType = MediaProviderType.Navidrome
    override val capabilities = ProviderCapabilities(
        pagedCatalog = true,
        playlists = true,
        playlistMutation = true,
        favorites = true,
        ratings = true,
        nativeStreaming = true,
        itemRadio = true,
    )

    override suspend fun signIn(serverUrl: String, username: String, password: String): PlexSession =
        client.signIn(serverUrl, username, password)

    override suspend fun libraries(session: PlexSession, server: PlexServer): List<MusicLibrary> =
        client.libraries(server, session.userName, session.token)

    override suspend fun buildCatalog(session: PlexSession): CatalogSnapshot =
        client.buildCatalog(
            server = session.selectedServer ?: return CatalogSnapshot(),
            library = session.selectedLibrary ?: NavidromeAllMusicLibrary,
            username = session.userName,
            password = session.token,
        )

    override suspend fun quickCatalog(session: PlexSession): CatalogSnapshot? =
        client.quickCatalog(
            server = session.selectedServer ?: return null,
            username = session.userName,
            password = session.token,
        )

    override suspend fun albumPageCatalog(session: PlexSession, pageIndex: Int): Pair<CatalogSnapshot, ProviderItemPage<Album>>? {
        val server = session.selectedServer ?: return null
        val page = client.albumPage(server, session.userName, session.token, pageIndex)
        return CatalogSnapshot(albums = page.items) to page
    }

    override suspend fun albumTracks(session: PlexSession, album: Album): List<Track> =
        client.albumTracks(session.selectedServer ?: return emptyList(), album, session.userName, session.token)

    override suspend fun playlistTracks(session: PlexSession, playlist: Playlist): List<Track> =
        client.playlistTracks(session.selectedServer ?: return emptyList(), playlist, session.userName, session.token)

    override suspend fun createPlaylist(session: PlexSession, title: String, initialTracks: List<Track>): Playlist? =
        client.createPlaylist(
            server = session.selectedServer ?: return null,
            username = session.userName,
            password = session.token,
            title = title,
            itemIds = initialTracks.map { it.id.removePrefix("navidrome:") },
        )

    override suspend fun addTracksToPlaylist(session: PlexSession, playlist: Playlist, tracks: List<Track>) {
        client.addTracksToPlaylist(
            server = session.selectedServer ?: return,
            username = session.userName,
            password = session.token,
            playlistId = playlist.id.removePrefix("navidrome:"),
            itemIds = tracks.map { it.id.removePrefix("navidrome:") },
        )
    }

    override suspend fun replacePlaylistTracks(session: PlexSession, playlist: Playlist, tracks: List<Track>): Boolean =
        runCatching {
            client.replacePlaylistTracks(
                server = session.selectedServer ?: return false,
                username = session.userName,
                password = session.token,
                playlistId = playlist.id.removePrefix("navidrome:"),
                itemIds = tracks.map { it.id.removePrefix("navidrome:") },
            )
        }.isSuccess

    override suspend fun setFavorite(session: PlexSession, itemId: String, favorite: Boolean, kind: ProviderItemKind): Boolean =
        runCatching {
            client.setFavorite(session.selectedServer ?: return false, session.userName, session.token, itemId.removePrefix("navidrome:"), favorite, kind)
        }.isSuccess

    override suspend fun rateItem(session: PlexSession, itemId: String, rating: Float?): Boolean =
        runCatching {
            client.rateItem(session.selectedServer ?: return false, session.userName, session.token, itemId.removePrefix("navidrome:"), rating)
        }.isSuccess

    override suspend fun reportPlayback(session: PlexSession, track: Track, positionMs: Long, isPaused: Boolean, event: JellyfinPlaybackEvent) {
        val server = session.selectedServer ?: return
        val itemId = track.id.removePrefix("navidrome:").takeIf { it.isNotBlank() } ?: return
        val scrobbleKey = NavidromeScrobbleKey(server.uri, session.userName, itemId)
        scrobbleMutex.withLock {
            if (activeScrobbleKey != scrobbleKey) {
                activeScrobbleKey = scrobbleKey
                submittedScrobbleKey = null
                submittedScrobbleAtMs = 0L
            } else if (event == JellyfinPlaybackEvent.Start && !wasScrobbledJustBeforePlaybackStart(scrobbleKey)) {
                submittedScrobbleKey = null
                submittedScrobbleAtMs = 0L
            }

            val reachedPlayedThreshold = track.hasReachedSubsonicScrobbleThreshold(positionMs)
            if (event != JellyfinPlaybackEvent.Stop && !reachedPlayedThreshold) return@withLock
            if (submittedScrobbleKey == scrobbleKey) return@withLock

            client.scrobble(
                server = server,
                username = session.userName,
                password = session.token,
                itemId = itemId,
                submission = true,
                timeMs = estimatedPlaybackStartMs(positionMs),
            )
            submittedScrobbleKey = scrobbleKey
            submittedScrobbleAtMs = currentTimeMs()
        }
    }

    override suspend fun markPlayed(session: PlexSession, track: Track, playedAtMs: Long) {
        val server = session.selectedServer ?: return
        val itemId = track.id.removePrefix("navidrome:").takeIf { it.isNotBlank() } ?: return
        val scrobbleKey = NavidromeScrobbleKey(server.uri, session.userName, itemId)
        scrobbleMutex.withLock {
            activeScrobbleKey = scrobbleKey
            if (submittedScrobbleKey == scrobbleKey) return@withLock
            client.scrobble(
                server = server,
                username = session.userName,
                password = session.token,
                itemId = itemId,
                submission = true,
                timeMs = playedAtMs,
            )
            submittedScrobbleKey = scrobbleKey
            submittedScrobbleAtMs = currentTimeMs()
        }
    }

    private fun wasScrobbledJustBeforePlaybackStart(scrobbleKey: NavidromeScrobbleKey): Boolean =
        submittedScrobbleKey == scrobbleKey &&
            submittedScrobbleAtMs > 0L &&
            currentTimeMs() - submittedScrobbleAtMs <= ImmediateScrobbleStartGraceMs
}

private data class NavidromeScrobbleKey(
    val serverUri: String,
    val username: String,
    val itemId: String,
)

private fun Track.hasReachedSubsonicScrobbleThreshold(positionMs: Long): Boolean {
    val duration = durationMs.takeIf { it > 0L }
    val played = positionMs.coerceAtLeast(0L)
    val threshold = duration
        ?.let { (it * SubsonicScrobblePlayedFraction).toLong().coerceAtMost(SubsonicScrobbleMaxThresholdMs) }
        ?: SubsonicScrobbleMaxThresholdMs
    return played >= threshold
}

private fun estimatedPlaybackStartMs(positionMs: Long): Long =
    (currentTimeMs() - positionMs.coerceAtLeast(0L)).coerceAtLeast(0L)

private const val SubsonicScrobblePlayedFraction = 0.5
private const val SubsonicScrobbleMaxThresholdMs = 4L * 60L * 1000L
private const val ImmediateScrobbleStartGraceMs = 5_000L

class MusicAssistantProviderAdapter(
    private val client: MusicAssistantClient,
) : MusicProviderAdapter {
    override val providerType = MediaProviderType.MusicAssistant
    override val capabilities = ProviderCapabilities(
        playlists = true,
        playlistMutation = true,
        favorites = true,
        ratings = false,
        nativeStreaming = false,
        remotePlayerControl = true,
        libraryRadio = true,
    )

    override suspend fun signIn(serverUrl: String, username: String, password: String): PlexSession =
        client.signIn(serverUrl, username, password)

    override suspend fun libraries(session: PlexSession, server: PlexServer): List<MusicLibrary> =
        client.libraries(server, session.token)

    override suspend fun buildCatalog(session: PlexSession): CatalogSnapshot =
        client.buildCatalog(session.selectedServer ?: return CatalogSnapshot(), session.token)

    override suspend fun albumTracks(session: PlexSession, album: Album): List<Track> =
        client.albumTracks(session.selectedServer ?: return emptyList(), session.token, album)

    override suspend fun playlistTracks(session: PlexSession, playlist: Playlist): List<Track> =
        client.playlistTracks(session.selectedServer ?: return emptyList(), session.token, playlist)

    override suspend fun createPlaylist(session: PlexSession, title: String, initialTracks: List<Track>): Playlist? =
        client.createPlaylist(
            server = session.selectedServer ?: return null,
            token = session.token,
            title = title,
            itemUris = initialTracks.map { it.id.removePrefix("music-assistant:") },
        )

    override suspend fun addTracksToPlaylist(session: PlexSession, playlist: Playlist, tracks: List<Track>) {
        client.addTracksToPlaylist(
            server = session.selectedServer ?: return,
            token = session.token,
            playlistId = playlist.id.removePrefix("music-assistant:"),
            itemUris = tracks.map { it.id.removePrefix("music-assistant:") },
        )
    }

    override suspend fun setFavorite(session: PlexSession, itemId: String, favorite: Boolean, kind: ProviderItemKind): Boolean =
        runCatching {
            client.setFavorite(session.selectedServer ?: return false, session.token, itemId.removePrefix("music-assistant:"), favorite)
        }.isSuccess

    override suspend fun playRemote(session: PlexSession, tracks: List<Track>, index: Int): String? {
        val server = session.selectedServer ?: return null
        val itemUri = tracks.getOrNull(index)?.id?.removePrefix("music-assistant:")?.takeIf { it.isNotBlank() } ?: return null
        return client.playMediaOnDefaultQueue(server, session.token, itemUri)
    }

    override suspend fun streamUrl(session: PlexSession, track: Track): String? {
        val server = session.selectedServer ?: return null
        val itemUri = track.id.removePrefix("music-assistant:").takeIf { it.isNotBlank() } ?: return null
        return client.streamUrlForLocalPlayback(server, session.token, itemUri)
    }
}

private suspend fun JellyfinClient.buildEmbyFamilyCatalog(session: PlexSession): CatalogSnapshot {
    val server = session.selectedServer ?: return CatalogSnapshot()
    val library = session.selectedLibrary ?: return CatalogSnapshot()
    val userId = session.userId ?: return CatalogSnapshot()
    val artists = artists(server, library, session.token, userId)
    val albums = albums(server, library, session.token, userId)
    val albumsById = albums.associateBy { it.id }
    val tracks = tracks(server, library, session.token, userId, includeMediaDetails = false).map { track ->
        val album = track.parentAlbumId?.let(albumsById::get)
        if (album == null) {
            track
        } else {
            track.copy(
                album = track.album.takeUnless { it == "Unknown album" } ?: album.title,
                artist = track.artist.takeUnless { it == "Unknown artist" } ?: album.artist,
                thumbUrl = track.thumbUrl ?: album.thumbUrl,
            )
        }
    }
    return CatalogSnapshot(
        artists = artists,
        albums = albums,
        playlists = playlists(server, library, session.token, userId),
        tracksByParent = tracks
            .groupBy { it.parentAlbumId?.takeIf(String::isNotBlank) ?: embyFamilyAlbumIdByTitle(albums, it) }
            .filterKeys(String::isNotBlank),
    )
}

private fun embyFamilyAlbumIdByTitle(albums: List<Album>, track: Track): String =
    albums.firstOrNull {
        it.title.equals(track.album, ignoreCase = true) &&
            it.artist.equals(track.artist, ignoreCase = true)
    }?.id.orEmpty()
