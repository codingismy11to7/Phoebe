package com.phoebe.app.player

import android.net.Uri
import com.phoebe.app.AndroidContextHolder
import com.phoebe.app.R
import com.phoebe.app.db.PhoebeDatabase
import com.phoebe.app.domain.Album
import com.phoebe.app.domain.Artist
import com.phoebe.app.domain.Playlist
import com.phoebe.app.domain.Track

/**
 * Reads the cached SQLDelight catalog for Android Auto / MediaLibrary browse callbacks.
 */
internal class AndroidAutoBrowseTree(
    private val database: PhoebeDatabase,
) {
    fun rootChildren(hasCachedCatalog: Boolean): List<androidx.media3.common.MediaItem> {
        if (!hasCachedCatalog) {
            return listOf(
                browseFolderItem(
                    mediaId = BrowseMediaIds.SIGN_IN,
                    title = "Open Phoebe and sign in to Plex",
                ),
            )
        }
        return listOf(
            browseFolderItem(
                BrowseMediaIds.ARTISTS,
                "Artists",
                artworkUri = drawableArtUri(R.drawable.ic_aa_tab_artists),
            ),
            browseFolderItem(
                BrowseMediaIds.ALBUMS,
                "Albums",
                artworkUri = drawableArtUri(R.drawable.ic_aa_tab_albums),
            ),
            browseFolderItem(
                BrowseMediaIds.PLAYLISTS,
                "Playlists",
                artworkUri = drawableArtUri(R.drawable.ic_aa_tab_playlists),
            ),
        )
    }

    fun getChildren(parentId: String): List<androidx.media3.common.MediaItem> =
        when (parentId) {
            BrowseMediaIds.ROOT -> rootChildren(hasCachedCatalog())
            BrowseMediaIds.ARTISTS -> artists().map { it.toBrowseItem() }
            BrowseMediaIds.ALBUMS -> albums().map { it.toBrowseItem() }
            BrowseMediaIds.PLAYLISTS -> playlists().map { it.toBrowseItem() }
            BrowseMediaIds.SIGN_IN -> emptyList()
            else -> {
                BrowseMediaIds.parseArtistId(parentId)?.let { artistId ->
                    val artist = artists().find { it.id == artistId } ?: return emptyList()
                    return albumsForArtist(artist.title).map { it.toBrowseItem() }
                }
                BrowseMediaIds.parseAlbumId(parentId)?.let { albumId ->
                    return tracksForParent(albumId).map { browseTrackItem(it) }
                }
                BrowseMediaIds.parsePlaylistId(parentId)?.let { playlistId ->
                    return tracksForParent(playlistId).map { browseTrackItem(it) }
                }
                emptyList()
            }
        }

    fun getAlbum(albumId: String): Album? =
        albums().find { it.id == albumId }

    fun getPlaylist(playlistId: String): Playlist? =
        playlists().find { it.id == playlistId }

    fun getItem(mediaId: String): androidx.media3.common.MediaItem? {
        getChildren(BrowseMediaIds.ROOT).find { it.mediaId == mediaId }?.let { return it }
        artists().find { BrowseMediaIds.artist(it.id) == mediaId }?.toBrowseItem()?.let { return it }
        albums().find { BrowseMediaIds.album(it.id) == mediaId }?.toBrowseItem()?.let { return it }
        playlists().find { BrowseMediaIds.playlist(it.id) == mediaId }?.toBrowseItem()?.let { return it }
        trackById(mediaId)?.let { return browseTrackItem(it) }
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
            localUri = localUri,
            year = year?.toInt(),
            genre = genre,
            filepath = filepath,
            audioCodec = audioCodec,
            bitrateKbps = bitrateKbps?.toInt(),
        )

    private fun drawableArtUri(drawableRes: Int): Uri {
        val packageName = AndroidContextHolder.application.packageName
        return Uri.parse("android.resource://$packageName/$drawableRes")
    }
}
