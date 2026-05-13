package com.phoebe.app.player

import androidx.media3.common.MediaItem
import com.phoebe.app.data.CatalogRepository
import com.phoebe.app.data.SessionRepository
import com.phoebe.app.db.PhoebeDatabase
import com.phoebe.app.domain.Album
import com.phoebe.app.domain.Playlist
import com.phoebe.app.domain.Track
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

interface CatalogBrowseSource {
    suspend fun getLibraryRoot(): MediaItem
    suspend fun getChildren(parentId: String): List<MediaItem>
    suspend fun getItem(mediaId: String): MediaItem?
    suspend fun resolveTracks(mediaItems: List<MediaItem>): List<Track>
    suspend fun expandPlayableItem(mediaItem: MediaItem): List<Track>
}

internal class CatalogBrowseSourceImpl(
    database: PhoebeDatabase,
    private val catalogRepository: CatalogRepository,
    private val sessionRepository: SessionRepository,
) : CatalogBrowseSource {
    private val tree = AndroidAutoBrowseTree(database)

    override suspend fun getLibraryRoot(): MediaItem =
        browseFolderItem(BrowseMediaIds.ROOT, "Phoebe")

    override suspend fun getChildren(parentId: String): List<MediaItem> = withContext(Dispatchers.IO) {
        val cached = tree.getChildren(parentId)
        if (cached.isNotEmpty() || parentId == BrowseMediaIds.SIGN_IN || parentId == BrowseMediaIds.ROOT) {
            return@withContext cached
        }

        val session = sessionRepository.session.value
        BrowseMediaIds.parseAlbumId(parentId)?.let { albumId ->
            val album = tree.getAlbum(albumId) ?: return@withContext emptyList()
            val tracks = catalogRepository.tracksForAlbum(session, album)
            return@withContext tracks.map { browseTrackItem(it) }
        }
        BrowseMediaIds.parsePlaylistId(parentId)?.let { playlistId ->
            val playlist = tree.getPlaylist(playlistId) ?: return@withContext emptyList()
            val tracks = catalogRepository.tracksForPlaylist(session, playlist)
            return@withContext tracks.map { browseTrackItem(it) }
        }
        emptyList()
    }

    override suspend fun getItem(mediaId: String): MediaItem? = withContext(Dispatchers.IO) {
        tree.getItem(mediaId)
    }

    override suspend fun resolveTracks(mediaItems: List<MediaItem>): List<Track> = withContext(Dispatchers.IO) {
        mediaItems.mapNotNull { item ->
            if (item.mediaId.isNotBlank()) {
                tree.trackById(item.mediaId)
            } else {
                null
            }
        }
    }

    override suspend fun expandPlayableItem(mediaItem: MediaItem): List<Track> = withContext(Dispatchers.IO) {
        val metadata = mediaItem.mediaMetadata
        if (metadata.isBrowsable == true) {
            val children = getChildren(mediaItem.mediaId)
            return@withContext children.mapNotNull { tree.trackById(it.mediaId) }
        }
        tree.trackById(mediaItem.mediaId)?.let { listOf(it) } ?: emptyList()
    }
}
