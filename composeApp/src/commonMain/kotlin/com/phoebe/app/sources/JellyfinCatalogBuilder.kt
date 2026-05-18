package com.phoebe.app.sources

import com.phoebe.app.data.JellyfinClient
import com.phoebe.app.domain.CatalogSnapshot
import com.phoebe.app.domain.MusicLibrary
import com.phoebe.app.domain.PlexServer
import com.phoebe.app.domain.Track
import com.phoebe.app.data.enrichArtistAlbumCountsOnly
import com.phoebe.app.data.enrichArtistArtwork

class JellyfinCatalogBuilder(
    private val jellyfinClient: JellyfinClient,
) {
    suspend fun buildCatalog(server: PlexServer, library: MusicLibrary, token: String, userId: String): CatalogSnapshot {
        val artists = jellyfinClient.artists(server, library, token, userId)
        val albums = jellyfinClient.albums(server, library, token, userId)
        val albumsById = albums.associateBy { it.id }
        val allTracks = jellyfinClient.tracks(server, library, token, userId, includeMediaDetails = false)
            .map { track ->
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
        val playlists = jellyfinClient.playlists(server, library, token, userId)
        val tracksByAlbum = allTracks
            .groupBy { it.parentAlbumId?.takeIf { id -> id.isNotBlank() } ?: albumIdByTitle(albums = albums, track = it) }
            .filterKeys { it.isNotBlank() }
        return CatalogSnapshot(
            artists = enrichArtistAlbumCountsOnly(enrichArtistArtwork(artists, albums), albums),
            albums = albums,
            playlists = playlists,
            tracksByParent = tracksByAlbum,
        )
    }

    private fun albumIdByTitle(albums: List<com.phoebe.app.domain.Album>, track: Track): String =
        albums.firstOrNull {
            it.title.equals(track.album, ignoreCase = true) &&
                it.artist.equals(track.artist, ignoreCase = true)
        }?.id.orEmpty()

}
