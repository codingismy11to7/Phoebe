package com.phoebe.app.feature.search

import com.phoebe.app.domain.Album
import com.phoebe.app.domain.Artist
import com.phoebe.app.domain.CatalogSnapshot
import com.phoebe.app.domain.Track
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlinx.coroutines.test.runTest

class SearchViewModelTest {
    @Test
    fun emptyQueryKeepsResultsEmpty() = runTest {
        val viewModel = SearchViewModel(SearchResultsFactory())

        viewModel.updateCatalog(testCatalog(), catalogRefreshing = true)

        val state = viewModel.state.value
        assertEquals("", state.query)
        assertEquals(true, state.catalogRefreshing)
        assertEquals(0, state.results.tracks.size)
        assertNull(state.results.topTrack)
    }

    @Test
    fun queryUpdatesResultsFromCatalog() = runTest {
        val viewModel = SearchViewModel(SearchResultsFactory())
        viewModel.updateCatalog(testCatalog(), catalogRefreshing = false)

        viewModel.onQuery("moon")

        val state = viewModel.state.value
        assertEquals("moon", state.query)
        assertEquals(listOf("track-moon"), state.results.tracks.map { it.id })
        assertEquals("album-moon", state.results.topAlbum?.id)
        assertEquals("artist-moon", state.results.topArtist?.id)
    }

    @Test
    fun routeStateReflectsViewModelState() = runTest {
        val viewModel = SearchViewModel(SearchResultsFactory())
        val catalog = testCatalog()

        viewModel.updateCatalog(catalog, catalogRefreshing = true)
        viewModel.onQuery("moon")

        assertEquals(SearchDesktopRouteState(catalog, catalogRefreshing = true, query = "moon"), viewModel.routeState())
    }

    private fun testCatalog(): CatalogSnapshot {
        val moonArtist = Artist(
            id = "artist-moon",
            title = "Moon Unit",
            albumCount = 1,
            songCount = 1,
        )
        val otherArtist = Artist(
            id = "artist-sun",
            title = "Sun Room",
            albumCount = 1,
            songCount = 1,
        )
        val moonAlbum = Album(
            id = "album-moon",
            title = "Moon Phase",
            artist = moonArtist.title,
            year = 2024,
        )
        val otherAlbum = Album(
            id = "album-sun",
            title = "Solar Phase",
            artist = otherArtist.title,
            year = 2024,
        )
        val moonTrack = Track(
            id = "track-moon",
            title = "Moon Song",
            artist = moonArtist.title,
            album = moonAlbum.title,
            durationMs = 180_000L,
            streamUrl = "https://example.com/moon",
            downloadUrl = "https://example.com/moon.mp3",
        )
        val otherTrack = Track(
            id = "track-sun",
            title = "Solar Song",
            artist = otherArtist.title,
            album = otherAlbum.title,
            durationMs = 180_000L,
            streamUrl = "https://example.com/sun",
            downloadUrl = "https://example.com/sun.mp3",
        )
        return CatalogSnapshot(
            artists = listOf(moonArtist, otherArtist),
            albums = listOf(moonAlbum, otherAlbum),
            tracksByParent = mapOf(
                moonAlbum.id to listOf(moonTrack),
                otherAlbum.id to listOf(otherTrack),
            ),
        )
    }
}
