package com.phoebe.app

import com.phoebe.app.domain.Album
import com.phoebe.app.domain.Artist
import com.phoebe.app.domain.CatalogSnapshot
import com.phoebe.app.domain.Track
import com.phoebe.app.sources.CatalogMerge
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class CatalogMergeTest {
    @Test
    fun withPrefixPrefixesIdsAndTrackMapKeys() {
        val inner = CatalogSnapshot(
            artists = listOf(Artist("a1", "A", null, 1)),
            albums = listOf(Album("al1", "Al", "A", null, null)),
            playlists = emptyList(),
            tracksByParent = mapOf("al1" to listOf(Track("t1", "T", "A", "Al", 1L, "", ""))),
            popularTracksByArtist = mapOf("a1" to listOf(Track("t2", "Top", "A", "Al", 1L, "", "", parentAlbumId = "al1"))),
            similarArtistsByArtist = mapOf("a1" to listOf(Artist("a2", "B", null, 1))),
            downloads = emptyList(),
        )
        val p = CatalogMerge.withPrefix("plex", inner)
        assertEquals("plex:a1", p.artists.single().id)
        assertEquals("plex:al1", p.albums.single().id)
        assertEquals("plex:t1", p.tracksByParent.keys.single().let { p.tracksByParent[it]!!.single().id })
        assertEquals("plex:t2", p.popularTracksByArtist["plex:a1"]!!.single().id)
        assertEquals("plex:al1", p.popularTracksByArtist["plex:a1"]!!.single().parentAlbumId)
        assertEquals("plex:a2", p.similarArtistsByArtist["plex:a1"]!!.single().id)
    }

    @Test
    fun withPrefixDoesNotDoublePrefixIds() {
        val inner = CatalogSnapshot(
            artists = listOf(Artist("jellyfin:a1", "A", null, 1)),
            albums = listOf(Album("jellyfin:al1", "Al", "A", null, null)),
            playlists = emptyList(),
            tracksByParent = mapOf("jellyfin:al1" to listOf(Track("jellyfin:t1", "T", "A", "Al", 1L, "", ""))),
            downloads = emptyList(),
        )

        val prefixed = CatalogMerge.withPrefix("jellyfin", inner)

        assertEquals("jellyfin:a1", prefixed.artists.single().id)
        assertEquals("jellyfin:al1", prefixed.albums.single().id)
        assertEquals("jellyfin:t1", prefixed.tracksByParent.values.single().single().id)
    }

    @Test
    fun mergeCombinesChildrenAndDownloadsDedupesByTrackId() {
        val a = CatalogSnapshot(
            artists = listOf(Artist("1", "One", null, 0)),
            albums = emptyList(),
            playlists = emptyList(),
            tracksByParent = mapOf("p1" to listOf(Track("t1", "T", "A", "Al", 1L, "", ""))),
            downloads = emptyList(),
        )
        val b = CatalogSnapshot(
            artists = listOf(Artist("2", "Two", null, 0)),
            albums = emptyList(),
            playlists = emptyList(),
            tracksByParent = mapOf("p2" to listOf(Track("t1", "T", "A", "Al", 1L, "", ""))),
            downloads = emptyList(),
        )
        val m = CatalogMerge.merge(a, b)
        assertEquals(2, m.artists.size)
        assertEquals(2, m.tracksByParent.size)
        assertTrue("p1" in m.tracksByParent && "p2" in m.tracksByParent)
    }

    @Test
    fun stripPlexIdRemovesKnownPrefix() {
        assertEquals("42", CatalogMerge.stripPlexId("plex:42"))
        assertEquals("local:x", CatalogMerge.stripPlexId("local:x"))
    }
}
