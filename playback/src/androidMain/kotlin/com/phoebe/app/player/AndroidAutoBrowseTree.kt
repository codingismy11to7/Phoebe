package com.phoebe.app.player

import android.content.res.Resources
import android.net.Uri
import com.phoebe.app.AndroidContextHolder
import com.phoebe.app.db.PhoebeDatabase
import com.phoebe.app.domain.Album
import com.phoebe.app.domain.Playlist
import com.phoebe.app.domain.Track
import androidx.media3.common.MediaItem

/**
 * Android Auto / MediaLibrary adapter over [CatalogBrowseTree].
 */
internal class AndroidAutoBrowseTree(
    database: PhoebeDatabase,
) {
    private val tree = CatalogBrowseTree(database)

    fun rootChildren(hasCachedCatalog: Boolean): List<MediaItem> =
        tree.rootChildren(hasCachedCatalog).map { it.toMediaItem() }

    fun getChildren(parentId: String): List<MediaItem> =
        tree.getChildren(parentId).map { it.toMediaItem() }

    fun getAlbum(albumId: String): Album? = tree.getAlbum(albumId)

    fun getPlaylist(playlistId: String): Playlist? = tree.getPlaylist(playlistId)

    fun getItem(mediaId: String): MediaItem? = tree.getItem(mediaId)?.toMediaItem()

    fun trackById(trackId: String): Track? = tree.trackById(trackId)

    fun tracksForPlayableMediaId(mediaId: String): List<Track> = tree.tracksForPlayableMediaId(mediaId)

    fun startIndexForMediaId(mediaId: String, tracks: List<Track>, fallback: Int): Int =
        tree.startIndexForMediaId(mediaId, tracks, fallback)

    fun searchTracks(
        query: String,
        title: String? = null,
        artist: String? = null,
        album: String? = null,
        playlist: String? = null,
        genre: String? = null,
    ): List<Track> = tree.searchTracks(
        query = query,
        title = title,
        artist = artist,
        album = album,
        playlist = playlist,
        genre = genre,
    )

    private fun BrowseNode.toMediaItem(): MediaItem =
        when {
            isPlayable && track != null -> browseTrackItem(track, mediaId)
            isPlayable -> browsePlayableActionItem(
                mediaId = mediaId,
                title = title,
                subtitle = subtitle,
                artworkUri = drawableArtUri(android.R.drawable.ic_menu_agenda),
            )
            mediaId == BrowseMediaIds.ARTISTS -> browseFolderItem(
                mediaId = mediaId,
                title = title,
                artworkUri = drawableArtUri(android.R.drawable.ic_menu_myplaces),
            )
            mediaId == BrowseMediaIds.ALBUMS -> browseFolderItem(
                mediaId = mediaId,
                title = title,
                artworkUri = drawableArtUri(android.R.drawable.ic_menu_gallery),
            )
            mediaId == BrowseMediaIds.PLAYLISTS -> browseFolderItem(
                mediaId = mediaId,
                title = title,
                artworkUri = drawableArtUri(android.R.drawable.ic_menu_agenda),
            )
            else -> browseFolderItem(
                mediaId = mediaId,
                title = title,
                // Individual albums, artists and playlists: serve their artwork
                // through the provider, since the car cannot fetch remote URLs.
                artworkUri = thumbUrl?.takeIf { it.isNotBlank() }?.let {
                    val entity = BrowseMediaIds.parseAlbumId(mediaId)?.let { id -> ArtworkType.ALBUM to id }
                        ?: BrowseMediaIds.parseArtistId(mediaId)?.let { id -> ArtworkType.ARTIST to id }
                        ?: BrowseMediaIds.parsePlaylistId(mediaId)?.let { id -> ArtworkType.PLAYLIST to id }
                    entity?.let { (type, id) -> artworkUri(runningPackageName(), type, id) }
                },
            )
        }

}

/**
 * Builds an `android.resource://` URI for a framework drawable in the *name*
 * form (`android.resource://android/drawable/ic_menu_gallery`) rather than the
 * numeric-id form (`android.resource://android/17301566`).
 *
 * Android Auto resolves both, but Android Automotive OS resolves only the name
 * form. AAOS's `UriUtils.getIconResource` rebuilds a resource name as
 * `authority + path.replaceFirst("/", ":")` and hands it to
 * `Resources.getIdentifier`, which returns 0 for a numeric path. AAOS then
 * calls `getDrawable(0)` on a background thread, throws
 * `Resources$NotFoundException: Resource ID #0x0`, and the uncaught crash kills
 * the whole Media Center process — so a bad URI here takes down the car's media
 * UI, not just our icon.
 */
internal fun drawableArtUri(drawableRes: Int): Uri {
    val resources = Resources.getSystem()
    val type = resources.getResourceTypeName(drawableRes)
    val entry = resources.getResourceEntryName(drawableRes)
    return Uri.parse("android.resource://android/$type/$entry")
}
