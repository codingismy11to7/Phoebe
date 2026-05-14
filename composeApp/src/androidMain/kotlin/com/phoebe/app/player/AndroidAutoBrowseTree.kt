package com.phoebe.app.player

import android.net.Uri
import com.phoebe.app.AndroidContextHolder
import com.phoebe.app.R
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

    private fun BrowseNode.toMediaItem(): MediaItem =
        when {
            isPlayable && track != null -> browseTrackItem(track)
            mediaId == BrowseMediaIds.ARTISTS -> browseFolderItem(
                mediaId = mediaId,
                title = title,
                artworkUri = drawableArtUri(R.drawable.ic_aa_tab_artists),
            )
            mediaId == BrowseMediaIds.ALBUMS -> browseFolderItem(
                mediaId = mediaId,
                title = title,
                artworkUri = drawableArtUri(R.drawable.ic_aa_tab_albums),
            )
            mediaId == BrowseMediaIds.PLAYLISTS -> browseFolderItem(
                mediaId = mediaId,
                title = title,
                artworkUri = drawableArtUri(R.drawable.ic_aa_tab_playlists),
            )
            else -> browseFolderItem(
                mediaId = mediaId,
                title = title,
                artworkUri = thumbUrl?.let { Uri.parse(it) },
            )
        }

    private fun drawableArtUri(drawableRes: Int): Uri {
        val packageName = AndroidContextHolder.application.packageName
        return Uri.parse("android.resource://$packageName/$drawableRes")
    }
}
