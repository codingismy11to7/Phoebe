package com.phoebe.app.sources

import com.phoebe.app.data.PlexClient
import com.phoebe.app.data.MusicBrainzReleaseGroupSearchResponse
import com.phoebe.app.data.enrichArtistAlbumCountsOnly
import com.phoebe.app.data.enrichArtistArtwork
import com.phoebe.app.domain.Album
import com.phoebe.app.domain.Artist
import com.phoebe.app.domain.CatalogSnapshot
import com.phoebe.app.domain.MusicLibrary
import com.phoebe.app.domain.Playlist
import com.phoebe.app.domain.PlexServer
import com.phoebe.app.domain.Track
import com.phoebe.app.platform.catalogTrackPrefetchAlbumCount
import com.phoebe.app.platform.catalogTrackPrefetchParallelism
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.parameter
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.coroutines.yield

/**
 * Builds a Plex-only catalog snapshot (IDs are raw Plex keys; wrap with [CatalogMerge.withPrefix] before merging).
 */
class PlexCatalogBuilder(
    private val plexClient: PlexClient,
    private val httpClient: HttpClient,
) {
    suspend fun buildCatalog(server: PlexServer, library: MusicLibrary, token: String): CatalogSnapshot = coroutineScope {
        val metadata = buildMetadataCatalog(server, library, token)

        val tracksByParent = prefetchAlbumTracks(server, metadata.albums, token)

        yield()
        val albumsEnriched = enrichAlbumArtwork(metadata.albums, tracksByParent)
        yield()
        val playlistsEnriched = enrichPlaylistArtwork(metadata.playlists, tracksByParent)
        yield()
        val artistsFinal = enrichArtistAlbumCountsOnly(
            enrichArtistArtwork(metadata.artists, albumsEnriched).ifEmpty {
                albumsEnriched.groupBy { it.artist }.values.map { list ->
                    val first = list.first()
                    Artist(
                        id = "album-artist-${first.id}",
                        title = first.artist,
                        thumbUrl = first.thumbUrl,
                        albumCount = list.size,
                    )
                }
            },
            albumsEnriched,
        )

        CatalogSnapshot(
            artists = artistsFinal,
            albums = albumsEnriched,
            playlists = playlistsEnriched,
            tracksByParent = tracksByParent,
            downloads = emptyList(),
        )
    }

    suspend fun buildMetadataCatalog(server: PlexServer, library: MusicLibrary, token: String): CatalogSnapshot = coroutineScope {
        val artistsDeferred = async { plexClient.artists(server, library, token) }
        val albumsDeferred = async { plexClient.albums(server, library, token) }
        val playlistsDeferred = async { plexClient.playlists(server, token) }

        val rawAlbums = albumsDeferred.await()
        yield()
        val artists = artistsDeferred.await()
        yield()
        val playlistsRaw = playlistsDeferred.await()
        yield()

        val artistsWithArtwork = enrichArtistArtwork(artists, rawAlbums)
        val artistsResolved = enrichArtistAlbumCountsOnly(
            artistsWithArtwork.ifEmpty {
                rawAlbums.groupBy { it.artist }.values.map { list ->
                    val first = list.first()
                    Artist(
                        id = "album-artist-${first.id}",
                        title = first.artist,
                        thumbUrl = first.thumbUrl,
                        albumCount = list.size,
                    )
                }
            },
            rawAlbums,
        )

        CatalogSnapshot(
            artists = artistsResolved,
            albums = rawAlbums,
            playlists = playlistsRaw,
            tracksByParent = emptyMap(),
            downloads = emptyList(),
        )
    }

    suspend fun prefetchAlbumTracks(
        server: PlexServer,
        albums: List<Album>,
        token: String,
        onAlbumTracks: suspend (Album, List<Track>) -> Unit = { _, _ -> },
    ): Map<String, List<Track>> = coroutineScope {
        val albumsSlice = albums.take(catalogTrackPrefetchAlbumCount())
        val mutex = Mutex()
        val tracksAccum = mutableMapOf<String, List<Track>>()

        albumsSlice
            .chunked(catalogTrackPrefetchParallelism().coerceAtLeast(1))
            .forEach { albumChunk ->
                albumChunk.map { album ->
                    async {
                        val tracks = plexClient.children(server, album.id, token)
                        mutex.withLock {
                            tracksAccum[album.id] = tracks
                        }
                        onAlbumTracks(album, tracks)
                    }
                }.awaitAll()
                yield()
            }

        tracksAccum.toMap()
    }

    fun enrichWithTrackArtwork(snapshot: CatalogSnapshot): CatalogSnapshot {
        val tracksByParent = snapshot.tracksByParent
        val albumsEnriched = snapshot.albums.map { album ->
            if (!album.thumbUrl.isNullOrBlank()) {
                album
            } else {
                album.copy(thumbUrl = tracksByParent[album.id].orEmpty().firstNotNullOfOrNull { it.thumbUrl })
            }
        }
        val playlistsEnriched = enrichPlaylistArtwork(snapshot.playlists, tracksByParent)
        val artistsFinal = enrichArtistAlbumCountsOnly(
            enrichArtistArtwork(snapshot.artists, albumsEnriched).ifEmpty {
                albumsEnriched.groupBy { it.artist }.values.map { list ->
                    val first = list.first()
                    Artist(
                        id = "album-artist-${first.id}",
                        title = first.artist,
                        thumbUrl = first.thumbUrl,
                        albumCount = list.size,
                    )
                }
            },
            albumsEnriched,
        )
        return snapshot.copy(
            artists = artistsFinal,
            albums = albumsEnriched,
            playlists = playlistsEnriched,
        )
    }

    private suspend fun enrichAlbumArtwork(albums: List<Album>, tracksByParent: Map<String, List<Track>>): List<Album> = coroutineScope {
        val budget = LookupBudget(6)
        albums.map { album ->
            async {
                if (!album.thumbUrl.isNullOrBlank()) {
                    album
                } else {
                    val trackThumb = tracksByParent[album.id].orEmpty().firstNotNullOfOrNull { it.thumbUrl }
                    val lookedUp = if (trackThumb == null && budget.tryAcquire()) {
                        withTimeoutOrNull(1_500L) { lookupCoverArt(album) }
                    } else {
                        null
                    }
                    album.copy(thumbUrl = trackThumb ?: lookedUp)
                }
            }
        }.awaitAll()
    }

    private class LookupBudget(private var remaining: Int) {
        private val mtx = Mutex()
        suspend fun tryAcquire(): Boolean = mtx.withLock {
            if (remaining > 0) {
                remaining--
                true
            } else {
                false
            }
        }
    }

    private fun enrichPlaylistArtwork(playlists: List<Playlist>, tracksByParent: Map<String, List<Track>>): List<Playlist> =
        playlists.map { playlist ->
            if (!playlist.thumbUrl.isNullOrBlank()) {
                playlist
            } else {
                playlist.copy(thumbUrl = tracksByParent[playlist.id].orEmpty().firstNotNullOfOrNull { it.thumbUrl })
            }
        }

    private suspend fun lookupCoverArt(album: Album): String? = runCatching {
        val response: MusicBrainzReleaseGroupSearchResponse = httpClient.get("https://musicbrainz.org/ws/2/release-group/") {
            header("User-Agent", "Phoebe/0.1.0 (https://github.com/phoebe)")
            parameter("fmt", "json")
            parameter("limit", "1")
            parameter("query", """releasegroup:"${album.title}" AND artist:"${album.artist}"""")
        }.body()
        response.releaseGroups.firstOrNull()?.id?.let { releaseGroupId ->
            "https://coverartarchive.org/release-group/$releaseGroupId/front-250"
        }
    }.getOrNull()
}
