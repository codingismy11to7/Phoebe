package com.phoebe.app

import com.phoebe.app.domain.LibrarySortBy
import com.phoebe.app.domain.Track
import com.phoebe.app.ui.filterTracksByQuery
import com.phoebe.app.ui.playbackQueueForVisibleTrack
import com.phoebe.app.ui.sortTracksForLibrary
import kotlin.test.Test
import kotlin.test.assertEquals

class LibrarySortingTest {
    @Test
    fun albumOrderPreservesSourceTrackOrder() {
        val tracks = listOf(
            track("three", "Track 03"),
            track("one", "Track 01"),
            track("two", "Track 02"),
        )

        assertEquals(
            listOf("three", "one", "two"),
            sortTracksForLibrary(tracks, LibrarySortBy.AlbumOrder, ascending = true).map { it.id },
        )
    }

    @Test
    fun albumOrderDescendingReversesSourceTrackOrder() {
        val tracks = listOf(
            track("three", "Track 03"),
            track("one", "Track 01"),
            track("two", "Track 02"),
        )

        assertEquals(
            listOf("two", "one", "three"),
            sortTracksForLibrary(tracks, LibrarySortBy.AlbumOrder, ascending = false).map { it.id },
        )
    }

    @Test
    fun filteredPlaylistPlaybackUsesUnfilteredQueueAndMappedIndex() {
        val tracks = listOf(
            track("one", "Track 01"),
            track("two", "Track 02"),
            track("three", "Track 03"),
        )
        val filtered = filterTracksByQuery(tracks, "Track 03")

        val (queue, index) = playbackQueueForVisibleTrack(tracks, filtered, visibleIndex = 0)

        assertEquals(listOf("one", "two", "three"), queue.map { it.id })
        assertEquals(2, index)
    }

    private fun track(id: String, title: String): Track =
        Track(
            id = id,
            title = title,
            artist = "Artist",
            album = "Album",
            durationMs = 180_000L,
            streamUrl = "https://example.com/$id",
            downloadUrl = "",
        )
}
