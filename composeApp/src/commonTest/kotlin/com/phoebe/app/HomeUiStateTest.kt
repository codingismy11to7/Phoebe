package com.phoebe.app

import com.phoebe.app.domain.Album
import com.phoebe.app.domain.Artist
import com.phoebe.app.domain.CatalogSnapshot
import com.phoebe.app.domain.Track
import com.phoebe.app.ui.PlayHistorySnapshot
import com.phoebe.app.ui.availableDecades
import com.phoebe.app.ui.decadeMix
import com.phoebe.app.ui.defaultMixDecades
import com.phoebe.app.ui.deriveHomeUiState
import com.phoebe.app.ui.personalMix
import kotlin.test.Test
import kotlin.test.assertEquals

class HomeUiStateTest {
    @Test
    fun derivesRecentAndMostPlayedHomeSections() {
        val tracks = (1..12).map { index ->
            Track(
                id = "t$index",
                title = "Track $index",
                artist = "Artist ${index % 3}",
                album = "Album ${index % 4}",
                durationMs = 1_000L,
                streamUrl = "",
                downloadUrl = "",
                dateAddedMs = index.toLong(),
            )
        }
        val catalog = CatalogSnapshot(
            artists = (1..12).map { Artist("a$it", "Artist $it", dateAddedMs = it.toLong()) },
            albums = (1..12).map { Album("al$it", "Album $it", "Artist ${it % 3}", dateAddedMs = it.toLong()) },
            tracksByParent = mapOf("all" to tracks),
        )
        val state = deriveHomeUiState(
            catalog = catalog,
            playHistory = PlayHistorySnapshot(
                byTrack = mapOf("t2" to 200L, "t5" to 500L, "t1" to 100L),
                playCountByTrack = mapOf("t2" to 2L, "t5" to 9L, "t1" to 4L),
            ),
            randomArtistSeed = 1,
            randomAlbumSeed = 2,
            nowMs = 12L,
        )

        assertEquals((12 downTo 3).map { "t$it" }, state.recentlyAddedTracks.map { it.id })
        assertEquals((12 downTo 3).map { "a$it" }, state.recentlyAddedArtists.map { it.id })
        assertEquals((12 downTo 3).map { "al$it" }, state.recentlyAddedAlbums.map { it.id })
        assertEquals(listOf("t5", "t2", "t1"), state.recentlyPlayedTracks.map { it.track.id })
        assertEquals(listOf("t5", "t1", "t2"), state.mostPlayedTracks.map { it.track.id })
        assertEquals(10, state.randomArtists.size)
        assertEquals(10, state.randomAlbums.size)
    }

    @Test
    fun recentTracksFallBackToAlbumDateAdded() {
        val track = Track(
            id = "track-without-date",
            title = "Song Without Track Date",
            artist = "Artist",
            album = "Fresh Album",
            durationMs = 1_000L,
            streamUrl = "",
            downloadUrl = "",
            dateAddedMs = null,
        )
        val catalog = CatalogSnapshot(
            albums = listOf(Album("album", "Fresh Album", "Artist", dateAddedMs = 100L)),
            tracksByParent = mapOf("album" to listOf(track)),
        )

        val state = deriveHomeUiState(
            catalog = catalog,
            playHistory = PlayHistorySnapshot(),
            randomArtistSeed = 1,
            randomAlbumSeed = 2,
            nowMs = 100L,
        )

        assertEquals(listOf("track-without-date"), state.recentlyAddedTracks.map { it.id })
    }

    @Test
    fun decadeMixUsesLoadedTrackYears() {
        val catalog = CatalogSnapshot(
            tracksByParent = mapOf(
                "all" to listOf(
                    Track("a", "A", "Artist", "Album", 1_000L, "stream", "", year = 1991),
                    Track("b", "B", "Artist", "Album", 1_000L, "stream", "", year = 1999),
                    Track("c", "C", "Artist", "Album", 1_000L, "stream", "", year = 2001),
                ),
            ),
        )

        assertEquals(listOf(2000, 1990), availableDecades(catalog))
        assertEquals(setOf("a", "b"), decadeMix(catalog, 1990).map { it.id }.toSet())
    }

    @Test
    fun defaultMixDecadesCoverTwentiethCenturyThroughCurrentPickerRange() {
        assertEquals(2020, defaultMixDecades().first())
        assertEquals(1900, defaultMixDecades().last())
    }

    @Test
    fun personalMixFallsBackToLibraryWhenHistoryIsEmpty() {
        val tracks = (1..4).map {
            Track("t$it", "Track $it", "Artist", "Album", 1_000L, "stream", "", year = 2000 + it)
        }
        val catalog = CatalogSnapshot(tracksByParent = mapOf("all" to tracks))
        val state = deriveHomeUiState(catalog, PlayHistorySnapshot(), 1, 2, nowMs = 10L)

        assertEquals(tracks.map { it.id }.toSet(), personalMix(catalog, state, limit = 10).map { it.id }.toSet())
    }
}
