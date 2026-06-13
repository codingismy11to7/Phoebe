package com.phoebe.app.player

import android.app.SearchManager
import android.os.Bundle
import android.provider.MediaStore
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
    fun startIndexForMediaItem(mediaItem: MediaItem, tracks: List<Track>, fallback: Int): Int
    suspend fun searchTracks(query: String, extras: Bundle?): List<Track>
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
            return@withContext listOf(
                browsePlayableActionItem(
                    mediaId = BrowseMediaIds.albumPlay(albumId),
                    title = "Play album",
                ),
            ) + tracks.map { browseTrackItem(it, BrowseMediaIds.track(parentId, it.id)) }
        }
        BrowseMediaIds.parsePlaylistId(parentId)?.let { playlistId ->
            val playlist = tree.getPlaylist(playlistId) ?: return@withContext emptyList()
            val tracks = catalogRepository.tracksForPlaylist(session, playlist)
            return@withContext listOf(
                browsePlayableActionItem(
                    mediaId = BrowseMediaIds.playlistPlay(playlistId),
                    title = "Play playlist",
                ),
                browsePlayableActionItem(
                    mediaId = BrowseMediaIds.playlistShuffle(playlistId),
                    title = "Shuffle",
                    subtitle = "Play this playlist in random order",
                ),
            ) + tracks.map { browseTrackItem(it, BrowseMediaIds.track(parentId, it.id)) }
        }
        emptyList()
    }

    override suspend fun getItem(mediaId: String): MediaItem? = withContext(Dispatchers.IO) {
        tree.getItem(mediaId)
    }

    override suspend fun resolveTracks(mediaItems: List<MediaItem>): List<Track> = withContext(Dispatchers.IO) {
        mediaItems.mapNotNull { item ->
            if (item.mediaId.isBlank()) null else tree.trackById(item.mediaId)
        }
    }

    override suspend fun expandPlayableItem(mediaItem: MediaItem): List<Track> = withContext(Dispatchers.IO) {
        val metadata = mediaItem.mediaMetadata
        if (metadata.isBrowsable == true) {
            val children = getChildren(mediaItem.mediaId)
            return@withContext children
                .filter { it.mediaMetadata.isPlayable == true }
                .flatMap { child -> tracksForPlayableMediaId(child.mediaId) }
                .distinctBy { it.id }
        }
        tracksForPlayableMediaId(mediaItem.mediaId)
    }

    override fun startIndexForMediaItem(mediaItem: MediaItem, tracks: List<Track>, fallback: Int): Int =
        tree.startIndexForMediaId(mediaItem.mediaId, tracks, fallback)

    private suspend fun tracksForPlayableMediaId(mediaId: String): List<Track> {
        BrowseMediaIds.parseAlbumPlayId(mediaId)?.let { albumId ->
            val album = tree.getAlbum(albumId) ?: return emptyList()
            return catalogRepository.tracksForAlbum(sessionRepository.session.value, album)
        }

        BrowseMediaIds.parsePlaylistPlayId(mediaId)?.let { playlistId ->
            val playlist = tree.getPlaylist(playlistId) ?: return emptyList()
            return catalogRepository.tracksForPlaylist(sessionRepository.session.value, playlist)
        }

        BrowseMediaIds.parsePlaylistShuffleId(mediaId)?.let { playlistId ->
            val playlist = tree.getPlaylist(playlistId) ?: return emptyList()
            return catalogRepository.tracksForPlaylist(sessionRepository.session.value, playlist).shuffled()
        }

        BrowseMediaIds.parseTrackId(mediaId)?.let { browseTrack ->
            BrowseMediaIds.parseAlbumId(browseTrack.parentMediaId)?.let { albumId ->
                val album = tree.getAlbum(albumId) ?: return@let null
                val tracks = catalogRepository.tracksForAlbum(sessionRepository.session.value, album)
                if (tracks.any { it.id == browseTrack.trackId }) return tracks
            }
            BrowseMediaIds.parsePlaylistId(browseTrack.parentMediaId)?.let { playlistId ->
                val playlist = tree.getPlaylist(playlistId) ?: return@let null
                val tracks = catalogRepository.tracksForPlaylist(sessionRepository.session.value, playlist)
                if (tracks.any { it.id == browseTrack.trackId }) return tracks
            }
        }

        return tree.tracksForPlayableMediaId(mediaId)
    }

    override suspend fun searchTracks(query: String, extras: Bundle?): List<Track> = withContext(Dispatchers.IO) {
        val searchQuery = query.ifBlank { extras?.getString(SearchManager.QUERY).orEmpty() }
        val mediaFocus = extras?.getString(MediaStore.EXTRA_MEDIA_FOCUS)
        tree.searchTracks(
            query = searchQuery,
            title = extras?.getString(MediaStore.EXTRA_MEDIA_TITLE)
                ?: searchQuery.takeIf { mediaFocus == MediaStore.Audio.Media.ENTRY_CONTENT_TYPE },
            artist = extras?.getString(MediaStore.EXTRA_MEDIA_ARTIST)
                ?: searchQuery.takeIf { mediaFocus == MediaStore.Audio.Artists.ENTRY_CONTENT_TYPE },
            album = extras?.getString(MediaStore.EXTRA_MEDIA_ALBUM)
                ?: searchQuery.takeIf { mediaFocus == MediaStore.Audio.Albums.ENTRY_CONTENT_TYPE },
            playlist = extras?.getString(MediaStoreSearchExtras.EXTRA_MEDIA_PLAYLIST)
                ?: searchQuery.takeIf { mediaFocus == MediaStoreSearchExtras.PLAYLIST_ENTRY_CONTENT_TYPE },
            genre = extras?.getString(MediaStore.EXTRA_MEDIA_GENRE)
                ?: searchQuery.takeIf { mediaFocus == MediaStore.Audio.Genres.ENTRY_CONTENT_TYPE },
        )
    }
}
