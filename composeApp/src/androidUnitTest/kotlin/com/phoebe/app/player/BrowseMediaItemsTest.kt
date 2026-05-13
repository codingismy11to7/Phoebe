package com.phoebe.app.player

import androidx.media3.common.MediaMetadata
import com.phoebe.app.domain.Track
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
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
}
