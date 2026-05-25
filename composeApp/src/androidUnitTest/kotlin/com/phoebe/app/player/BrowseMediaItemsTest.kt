package com.phoebe.app.player

import android.os.Bundle
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import com.phoebe.app.domain.Track
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class BrowseMediaItemsTest {
    @Test
    fun playbackMediaItemIncludesCarDisplayMetadata() {
        val item = playbackMediaItem(
            Track(
                id = "track-1",
                title = "Foam",
                artist = "Divine Sweater",
                album = "Down Deep",
                durationMs = 188_000,
                streamUrl = "https://example.test/audio.mp3",
                downloadUrl = "https://example.test/download.mp3",
                thumbUrl = "https://example.test/art.jpg",
            ),
        )

        val metadata = item.mediaMetadata

        assertEquals("track-1", item.mediaId)
        assertEquals("Foam", metadata.title.toString())
        assertEquals("Foam", metadata.displayTitle.toString())
        assertEquals("Divine Sweater", metadata.artist.toString())
        assertEquals("Divine Sweater", metadata.albumArtist.toString())
        assertEquals("Divine Sweater", metadata.subtitle.toString())
        assertEquals("Down Deep", metadata.albumTitle.toString())
        assertEquals("Divine Sweater - Down Deep", metadata.description.toString())
        assertEquals(188_000, metadata.durationMs)
        assertTrue(metadata.isPlayable == true)
        assertFalse(metadata.isBrowsable == true)
        assertEquals(MediaMetadata.MEDIA_TYPE_MUSIC, metadata.mediaType)
    }

    @Test
    fun pagedPlaylistSelectionExpandsToFullParentQueue() = runTest {
        val parentId = BrowseMediaIds.playlist("playlist-1")
        val tracks = (0 until 150).map { index ->
            Track(
                id = "track-$index",
                title = "Track $index",
                artist = "Artist",
                album = "Album",
                durationMs = 180_000,
                streamUrl = "https://example.test/$index.mp3",
                downloadUrl = "",
            )
        }
        val firstPage = tracks.take(100).map { track ->
            browseTrackItem(track, BrowseMediaIds.track(parentId, track.id))
        }
        val source = FakeCatalogBrowseSource(tracks)

        val expanded = expandPlaybackMediaItems(source, firstPage, startIndex = 99)

        assertEquals(150, expanded?.tracks?.size)
        assertEquals(150, expanded?.items?.size)
        assertEquals(99, expanded?.startIndex)
        assertEquals("track-149", expanded?.tracks?.last()?.id)
    }

    @Test
    fun ordinaryMultiItemQueuesAreNotExpanded() = runTest {
        val tracks = (0 until 3).map { index ->
            Track(
                id = "track-$index",
                title = "Track $index",
                artist = "Artist",
                album = "Album",
                durationMs = 180_000,
                streamUrl = "https://example.test/$index.mp3",
                downloadUrl = "",
            )
        }
        val searchResultItems = tracks.map { browseTrackItem(it) }
        val source = FakeCatalogBrowseSource(tracks)

        val expanded = expandPlaybackMediaItems(source, searchResultItems, startIndex = 1)

        assertNull(expanded)
    }
}

private class FakeCatalogBrowseSource(
    private val tracks: List<Track>,
) : CatalogBrowseSource {
    override suspend fun getLibraryRoot() = browseFolderItem(BrowseMediaIds.ROOT, "Phoebe")

    override suspend fun getChildren(parentId: String) = emptyList<MediaItem>()

    override suspend fun getItem(mediaId: String) = null

    override suspend fun resolveTracks(mediaItems: List<MediaItem>): List<Track> =
        mediaItems.mapNotNull { item ->
            val trackId = BrowseMediaIds.parseTrackId(item.mediaId)?.trackId ?: item.mediaId
            tracks.firstOrNull { it.id == trackId }
        }

    override suspend fun expandPlayableItem(mediaItem: MediaItem): List<Track> =
        if (BrowseMediaIds.parseTrackId(mediaItem.mediaId) != null) {
            tracks
        } else {
            resolveTracks(listOf(mediaItem))
        }

    override fun startIndexForMediaItem(
        mediaItem: MediaItem,
        tracks: List<Track>,
        fallback: Int,
    ): Int {
        val trackId = BrowseMediaIds.parseTrackId(mediaItem.mediaId)?.trackId ?: mediaItem.mediaId
        return tracks.indexOfFirst { it.id == trackId }
            .takeIf { it >= 0 }
            ?: fallback.takeIf { it in tracks.indices }
            ?: 0
    }

    override suspend fun searchTracks(
        query: String,
        extras: Bundle?,
    ): List<Track> = tracks
}
