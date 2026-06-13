package com.phoebe.app

import com.phoebe.app.data.lookupTracksByIds
import com.phoebe.app.domain.CatalogSnapshot
import com.phoebe.app.domain.Track
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

class PlayHistoryLookupDesktopTest {

    @Test
    fun testLookupTracksByIdsNormalizedLookups() {
        val track1 = Track(id = "plex:123", title = "Song 1", artist = "Artist 1", album = "Album 1", durationMs = 0L, streamUrl = "", downloadUrl = "")
        val track2 = Track(id = "456", title = "Song 2", artist = "Artist 2", album = "Album 2", durationMs = 0L, streamUrl = "", downloadUrl = "")
        
        val catalog = CatalogSnapshot(
            tracksByParent = mapOf(
                "parent1" to listOf(track1, track2)
            )
        )

        // 1. Exact lookup
        val resolvedExact = lookupTracksByIds(catalog, setOf("plex:123"))
        assertEquals(track1, resolvedExact["plex:123"])

        // 2. Bare query resolves to prefixed catalog track
        val resolvedBareQuery = lookupTracksByIds(catalog, setOf("123"))
        assertEquals(track1, resolvedBareQuery["123"])

        // 3. Prefixed query resolves to bare catalog track
        val resolvedPrefixedQuery = lookupTracksByIds(catalog, setOf("navidrome:456", "plex:456"))
        assertEquals(track2, resolvedPrefixedQuery["navidrome:456"])
        assertEquals(track2, resolvedPrefixedQuery["plex:456"])
    }
}
