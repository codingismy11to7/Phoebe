package com.phoebe.app.player

import com.phoebe.app.db.PhoebeDatabase
import com.phoebe.app.domain.Album
import com.phoebe.app.domain.Artist
import com.phoebe.app.domain.Playlist
import com.phoebe.app.domain.Track

/**
 * Reads the cached SQLDelight catalog for in-car / remote browse UIs.
 */
class CatalogBrowseTree(
    private val database: PhoebeDatabase,
) {
    fun rootChildren(hasCachedCatalog: Boolean): List<BrowseNode> {
        if (!hasCachedCatalog) {
            return listOf(
                browseFolder(
                    mediaId = BrowseMediaIds.SIGN_IN,
                    title = "Open Phoebe and sign in to Plex",
                ),
            )
        }
        return listOf(
            browseFolder(BrowseMediaIds.ARTISTS, "Artists"),
            browseFolder(BrowseMediaIds.ALBUMS, "Albums"),
            browseFolder(BrowseMediaIds.PLAYLISTS, "Playlists"),
        )
    }

    fun getChildren(parentId: String): List<BrowseNode> =
        when (parentId) {
            BrowseMediaIds.ROOT -> rootChildren(hasCachedCatalog())
            BrowseMediaIds.ARTISTS -> artists().map { it.toBrowseNode() }
            BrowseMediaIds.ALBUMS -> albums().map { it.toBrowseNode() }
            BrowseMediaIds.PLAYLISTS -> playlists().map { it.toBrowseNode() }
            BrowseMediaIds.SIGN_IN -> emptyList()
            else -> {
                BrowseMediaIds.parseArtistId(parentId)?.let { artistId ->
                    val artist = artists().find { it.id == artistId } ?: return emptyList()
                    return albumsForArtist(artist.title).map { it.toBrowseNode() }
                }
                BrowseMediaIds.parseAlbumId(parentId)?.let { albumId ->
                    return tracksForParent(albumId).map { it.toBrowseNode() }
                }
                BrowseMediaIds.parsePlaylistId(parentId)?.let { playlistId ->
                    return tracksForParent(playlistId).map { it.toBrowseNode() }
                }
                emptyList()
            }
        }

    fun getAlbum(albumId: String): Album? =
        albums().find { it.id == albumId }

    fun getPlaylist(playlistId: String): Playlist? =
        playlists().find { it.id == playlistId }

    fun getItem(mediaId: String): BrowseNode? {
        getChildren(BrowseMediaIds.ROOT).find { it.mediaId == mediaId }?.let { return it }
        artists().find { BrowseMediaIds.artist(it.id) == mediaId }?.toBrowseNode()?.let { return it }
        albums().find { BrowseMediaIds.album(it.id) == mediaId }?.toBrowseNode()?.let { return it }
        playlists().find { BrowseMediaIds.playlist(it.id) == mediaId }?.toBrowseNode()?.let { return it }
        trackById(mediaId)?.toBrowseNode()?.let { return it }
        return null
    }

    fun trackById(trackId: String): Track? {
        val row = database.catalogQueries.selectAllTracks().executeAsList().find { it.id == trackId }
            ?: return null
        return row.toTrack()
    }

    private fun hasCachedCatalog(): Boolean =
        database.catalogQueries.selectArtists().executeAsList().isNotEmpty() ||
            database.catalogQueries.selectAlbums().executeAsList().isNotEmpty() ||
            database.catalogQueries.selectPlaylists().executeAsList().isNotEmpty()

    private fun artists(): List<Artist> =
        database.catalogQueries.selectArtists().executeAsList().map {
            Artist(
                id = it.id,
                title = it.title,
                thumbUrl = it.thumbUrl,
                albumCount = it.albumCount.toInt(),
                songCount = it.songCount.toInt(),
            )
        }

    private fun albums(): List<Album> =
        database.catalogQueries.selectAlbums().executeAsList().map {
            Album(
                id = it.id,
                title = it.title,
                artist = it.artist,
                year = it.year?.toInt(),
                thumbUrl = it.thumbUrl,
            )
        }

    private fun playlists(): List<Playlist> =
        database.catalogQueries.selectPlaylists().executeAsList().map {
            Playlist(
                id = it.id,
                title = it.title,
                trackCount = it.trackCount.toInt(),
                key = it.plKey,
                thumbUrl = it.thumbUrl,
            )
        }

    private fun albumsForArtist(artistTitle: String): List<Album> =
        albums().filter { it.artist.equals(artistTitle, ignoreCase = true) }

    private fun tracksForParent(parentId: String): List<Track> {
        val trackRows = database.catalogQueries.selectAllTracks().executeAsList()
        val tracksById = trackRows.associate { it.id to it.toTrack() }
        return database.catalogQueries.selectTracksForParent(parentId).executeAsList()
            .mapNotNull { tracksById[it.id] }
    }

    private fun com.phoebe.app.db.TrackRow.toTrack(): Track =
        Track(
            id = id,
            title = title,
            artist = artist,
            album = album,
            durationMs = durationMs,
            streamUrl = streamUrl,
            downloadUrl = downloadUrl,
            thumbUrl = thumbUrl,
            localArtworkUri = localArtworkUri,
            localUri = localUri,
            year = year?.toInt(),
            genre = genre,
            filepath = filepath,
            audioCodec = audioCodec,
            bitrateKbps = bitrateKbps?.toInt(),
            dateAddedMs = dateAddedMs,
            parentAlbumId = parentAlbumId,
        )

    private fun browseFolder(
        mediaId: String,
        title: String,
        thumbUrl: String? = null,
    ): BrowseNode = BrowseNode(
        mediaId = mediaId,
        title = title,
        isBrowsable = true,
        isPlayable = false,
        thumbUrl = thumbUrl,
    )

    private fun Artist.toBrowseNode(): BrowseNode = BrowseNode(
        mediaId = BrowseMediaIds.artist(id),
        title = title,
        isBrowsable = true,
        isPlayable = false,
        thumbUrl = thumbUrl,
    )

    private fun Album.toBrowseNode(): BrowseNode = BrowseNode(
        mediaId = BrowseMediaIds.album(id),
        title = title,
        subtitle = artist,
        isBrowsable = true,
        isPlayable = true,
        thumbUrl = thumbUrl,
    )

    private fun Playlist.toBrowseNode(): BrowseNode = BrowseNode(
        mediaId = BrowseMediaIds.playlist(id),
        title = title,
        isBrowsable = true,
        isPlayable = true,
        thumbUrl = thumbUrl,
    )

    private fun Track.toBrowseNode(): BrowseNode = BrowseNode(
        mediaId = id,
        title = title,
        subtitle = listOf(artist, album).filter { it.isNotBlank() }.distinct().joinToString(" • "),
        isBrowsable = false,
        isPlayable = true,
        thumbUrl = thumbUrl,
        track = this,
    )
}
