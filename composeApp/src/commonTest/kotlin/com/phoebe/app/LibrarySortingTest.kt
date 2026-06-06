package com.phoebe.app

import com.phoebe.app.domain.LibrarySortBy
import com.phoebe.app.domain.Track
import com.phoebe.app.ui.libraryTrackScrollIndex
import com.phoebe.app.ui.monthYearLabelFromEpochMs
import com.phoebe.app.ui.filterTracksByQuery
import com.phoebe.app.ui.playbackQueueForVisibleTrack
import com.phoebe.app.ui.railIndexLabel
import com.phoebe.app.ui.sampleForRailLabels
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

    @Test
    fun libraryScrollIndexUsesSortFieldForTrackArtists() {
        val tracks = sortTracksForLibrary(
            listOf(
                track("beta", "A Song", artist = "Beta Artist"),
                track("alpha", "Z Song", artist = "Alpha Artist"),
            ),
            LibrarySortBy.Artist,
            ascending = true,
        )

        val index = libraryTrackScrollIndex(tracks, LibrarySortBy.Artist, ascending = true)

        assertEquals(0, index.single { it.label == "A" }.itemIndex)
        assertEquals(1, index.single { it.label == "B" }.itemIndex)
    }

    @Test
    fun libraryScrollIndexGroupsDateAddedByMonthYear() {
        val tracks = sortTracksForLibrary(
            listOf(
                track("feb", "February", dateAddedMs = 1_707_523_200_000L),
                track("jan", "January", dateAddedMs = 1_705_276_800_000L),
            ),
            LibrarySortBy.DateAdded,
            ascending = true,
        )

        val index = libraryTrackScrollIndex(tracks, LibrarySortBy.DateAdded, ascending = true)

        assertEquals(listOf("Jan 2024", "Feb 2024"), index.map { it.label })
        assertEquals(listOf(0, 1), index.map { it.itemIndex })
    }

    @Test
    fun monthYearLabelUsesUtcCalendarMonth() {
        assertEquals("Dec 2023", monthYearLabelFromEpochMs(1_701_388_800_000L))
        assertEquals("Jan 2024", monthYearLabelFromEpochMs(1_705_276_800_000L))
    }

    @Test
    fun railLabelSamplingKeepsFirstAndLastBreakpoints() {
        val index = ('A'..'Z').mapIndexed { index, label ->
            com.phoebe.app.ui.LibraryScrollIndexEntry(label.toString(), index)
        }

        val sampled = index.sampleForRailLabels(maxVisibleLabels = 8)

        assertEquals("A", sampled.first().label)
        assertEquals("Z", sampled.last().label)
        assertEquals(true, sampled.size <= 10)
    }

    @Test
    fun railLabelCompactsMonthYearButLeavesYearsWhole() {
        assertEquals("Jan\n24", "Jan 2024".railIndexLabel())
        assertEquals("2024", "2024".railIndexLabel())
    }

    private fun track(
        id: String,
        title: String,
        artist: String = "Artist",
        dateAddedMs: Long? = null,
    ): Track =
        Track(
            id = id,
            title = title,
            artist = artist,
            album = "Album",
            durationMs = 180_000L,
            streamUrl = "https://example.com/$id",
            downloadUrl = "",
            dateAddedMs = dateAddedMs,
        )
}
