package com.phoebe.app

import com.phoebe.app.data.enrichArtistAlbumCountsOnly
import com.phoebe.app.data.enrichArtistArtwork
import com.phoebe.app.data.enrichJellyfinCatalogArtwork
import com.phoebe.app.domain.Album
import com.phoebe.app.domain.Artist
import com.phoebe.app.domain.CatalogSnapshot
import com.phoebe.app.domain.Track
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.measureTime

class ArtistCountsTest {

    @Test
    fun enrichJellyfinCatalogArtworkCopiesAlbumThumbsOntoTracks() {
        val snapshot = CatalogSnapshot(
            artists = listOf(Artist(id = "jellyfin:artist-1", title = "Artist One")),
            albums = listOf(
                Album(
                    id = "jellyfin:album-1",
                    title = "Album One",
                    artist = "Artist One",
                    thumbUrl = "http://jellyfin.example/album.jpg",
                ),
            ),
            tracksByParent = mapOf(
                "jellyfin:album-1" to listOf(
                    Track(
                        id = "jellyfin:track-1",
                        title = "Song",
                        artist = "Unknown artist",
                        album = "Unknown album",
                        durationMs = 1,
                        streamUrl = "http://jellyfin.example/stream",
                        downloadUrl = "http://jellyfin.example/download",
                        parentAlbumId = "jellyfin:album-1",
                    ),
                ),
            ),
        )

        val enriched = enrichJellyfinCatalogArtwork(snapshot)

        assertEquals("http://jellyfin.example/album.jpg", enriched.tracksByParent["jellyfin:album-1"]?.single()?.thumbUrl)
        assertEquals("Artist One", enriched.tracksByParent["jellyfin:album-1"]?.single()?.artist)
        assertEquals("http://jellyfin.example/album.jpg", enriched.artists.single().thumbUrl)
    }

    @Test
    fun enrichArtistArtworkUsesFirstAlbumThumbInSinglePass() {
        val artists = listOf(Artist(id = "a1", title = "Artist One"))
        val albums = listOf(
            Album(id = "al1", title = "Later", artist = "Artist One", thumbUrl = "https://example/1.jpg"),
            Album(id = "al2", title = "Earlier", artist = "Artist One", thumbUrl = "https://example/2.jpg"),
        )
        val enriched = enrichArtistArtwork(artists, albums)
        assertEquals("https://example/1.jpg", enriched.single().thumbUrl)
    }

    @Test
    fun enrichArtistAlbumCountsOnlyPreservesServerCountsWithoutScanning() {
        val artists = List(100) { index ->
            Artist(id = "a$index", title = "Artist $index", albumCount = index + 1)
        }
        val albums = List(500) { index ->
            Album(id = "al$index", title = "Album $index", artist = "Someone Else")
        }
        val elapsed = measureTime {
            val enriched = enrichArtistAlbumCountsOnly(artists, albums)
            assertEquals(1, enriched.first().albumCount)
            assertEquals(100, enriched.last().albumCount)
        }
        assertTrue(elapsed.inWholeMilliseconds < 500, "expected fast path, took ${elapsed.inWholeMilliseconds}ms")
    }

    @Test
    fun enrichArtistAlbumCountsOnlyDerivesExactMatchesForMissingCounts() {
        val artists = listOf(
            Artist(id = "a1", title = "Artist One"),
            Artist(id = "a2", title = "Artist Two", albumCount = 3),
        )
        val albums = listOf(
            Album(id = "al1", title = "One", artist = "Artist One"),
            Album(id = "al2", title = "One B", artist = "Artist One"),
            Album(id = "al3", title = "Two", artist = "Artist Two"),
        )
        val enriched = enrichArtistAlbumCountsOnly(artists, albums)
        assertEquals(2, enriched[0].albumCount)
        assertEquals(3, enriched[1].albumCount)
    }

    @Test
    fun enrichArtistAlbumCountsOnlyMatchesFeatArtistStrings() {
        val artists = listOf(Artist(id = "a1", title = "Artist One"))
        val albums = listOf(
            Album(id = "al1", title = "Collab", artist = "Artist One feat. Guest"),
        )
        val enriched = enrichArtistAlbumCountsOnly(artists, albums)
        assertEquals(1, enriched.single().albumCount)
    }

    @Test
    fun enrichArtistArtworkLeavesExistingThumbUntouched() {
        val artists = listOf(Artist(id = "a1", title = "Artist One", thumbUrl = "https://example/existing.jpg"))
        val albums = listOf(Album(id = "al1", title = "Album", artist = "Artist One", thumbUrl = "https://example/album.jpg"))
        assertEquals("https://example/existing.jpg", enrichArtistArtwork(artists, albums).single().thumbUrl)
    }
}
