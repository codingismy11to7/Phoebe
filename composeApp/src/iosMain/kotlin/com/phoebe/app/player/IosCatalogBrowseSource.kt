package com.phoebe.app.player

import com.phoebe.app.data.CatalogRepository
import com.phoebe.app.data.SessionRepository
import com.phoebe.app.db.PhoebeDatabase
import com.phoebe.app.domain.Track
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

data class CarPlayBrowseItem(
    val mediaId: String,
    val title: String,
    val subtitle: String?,
    val isBrowsable: Boolean,
    val imageUrl: String?,
)

internal class IosCatalogBrowseSource(
    database: PhoebeDatabase,
    private val catalogRepository: CatalogRepository,
    private val sessionRepository: SessionRepository,
) {
    private val tree = CatalogBrowseTree(database)

    suspend fun getBrowseItems(parentId: String): List<CarPlayBrowseItem> = withContext(Dispatchers.Default) {
        val cached = tree.getChildren(parentId).map { it.toCarPlayItem() }
        if (cached.isNotEmpty() || parentId == BrowseMediaIds.SIGN_IN || parentId == BrowseMediaIds.ROOT) {
            return@withContext cached
        }

        val session = sessionRepository.session.value
        BrowseMediaIds.parseAlbumId(parentId)?.let { albumId ->
            val album = tree.getAlbum(albumId) ?: return@withContext emptyList()
            val tracks = catalogRepository.tracksForAlbum(session, album)
            return@withContext tracks.map { it.toBrowseNode().toCarPlayItem() }
        }
        BrowseMediaIds.parsePlaylistId(parentId)?.let { playlistId ->
            val playlist = tree.getPlaylist(playlistId) ?: return@withContext emptyList()
            val tracks = catalogRepository.tracksForPlaylist(session, playlist)
            return@withContext tracks.map { it.toBrowseNode().toCarPlayItem() }
        }
        emptyList()
    }

    suspend fun expandForPlayback(mediaId: String): List<Track> = withContext(Dispatchers.Default) {
        val node = tree.getItem(mediaId)
        if (node != null) {
            if (node.isBrowsable && node.track == null) {
                return@withContext getBrowseItems(mediaId).mapNotNull { tree.trackById(it.mediaId) }
            }
            node.track?.let { return@withContext listOf(it) }
        }
        tree.trackById(mediaId)?.let { return@withContext listOf(it) }
        emptyList()
    }

    private fun BrowseNode.toCarPlayItem(): CarPlayBrowseItem = CarPlayBrowseItem(
        mediaId = mediaId,
        title = title,
        subtitle = subtitle,
        isBrowsable = isBrowsable,
        imageUrl = thumbUrl,
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
