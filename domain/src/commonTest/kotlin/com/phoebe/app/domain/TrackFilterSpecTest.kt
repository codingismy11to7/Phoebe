package com.phoebe.app.domain

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class TrackFilterSpecTest {
    @Test
    fun matchesAllRules() {
        val spec = TrackFilterSpec(
            rules = listOf(
                TrackFilterRule(FilterField.Artist, FilterOperator.Contains, "phoebe"),
                TrackFilterRule(FilterField.Year, FilterOperator.Between, "1990..1999"),
                TrackFilterRule(FilterField.Rating, FilterOperator.GreaterThanOrEquals, "4"),
            ),
        )

        assertTrue(spec.matches(track(artist = "Phoebe Sounds", year = 1996, rating = 4.5f)))
        assertFalse(spec.matches(track(artist = "Someone Else", year = 1996, rating = 4.5f)))
    }

    @Test
    fun matchesContextFields() {
        val context = TrackFilterContext(
            favoriteTrackIds = setOf("track-1"),
            downloadedTrackIds = setOf("track-1"),
            playCountByTrackId = mapOf("track-1" to 12),
            providerByTrackId = mapOf("track-1" to MediaProviderType.Plex),
        )
        val spec = TrackFilterSpec(
            rules = listOf(
                TrackFilterRule(FilterField.Favorite, FilterOperator.IsTrue),
                TrackFilterRule(FilterField.Downloaded, FilterOperator.IsTrue),
                TrackFilterRule(FilterField.PlayCount, FilterOperator.GreaterThan, "10"),
                TrackFilterRule(FilterField.Provider, FilterOperator.Equals, "plex"),
            ),
        )

        assertTrue(spec.matches(track(id = "track-1"), context))
        assertFalse(spec.matches(track(id = "track-2"), context))
    }

    @Test
    fun anyMatchAcceptsOneRule() {
        val spec = TrackFilterSpec(
            match = TrackFilterMatch.Any,
            rules = listOf(
                TrackFilterRule(FilterField.Codec, FilterOperator.Equals, "flac"),
                TrackFilterRule(FilterField.BitDepth, FilterOperator.GreaterThanOrEquals, "24"),
            ),
        )

        assertTrue(spec.matches(track(audioCodec = "mp3", bitDepth = 24)))
        assertFalse(spec.matches(track(audioCodec = "aac", bitDepth = 16)))
    }

    @Test
    fun sortsBySortFieldsWhenPresent() {
        val tracks = listOf(
            track(id = "b", title = "The Branch", titleSort = "Branch"),
            track(id = "a", title = "Amber"),
        )

        assertEquals(listOf("a", "b"), tracks.sortedWith(FilterSort(FilterField.Title)).map { it.id })
    }

    private fun track(
        id: String = "track-1",
        title: String = "Song",
        artist: String = "Artist",
        album: String = "Album",
        year: Int? = null,
        rating: Float? = null,
        audioCodec: String? = null,
        bitDepth: Int? = null,
        titleSort: String? = null,
    ): Track =
        Track(
            id = id,
            title = title,
            artist = artist,
            album = album,
            durationMs = 180_000,
            streamUrl = "https://example.test/$id",
            downloadUrl = "https://example.test/$id/download",
            year = year,
            rating = rating,
            audioCodec = audioCodec,
            bitDepth = bitDepth,
            titleSort = titleSort,
        )
}
