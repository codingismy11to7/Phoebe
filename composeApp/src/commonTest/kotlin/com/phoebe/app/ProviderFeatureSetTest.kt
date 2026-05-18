package com.phoebe.app

import com.phoebe.app.domain.CollectionEntry
import com.phoebe.app.domain.CollectionFacet
import com.phoebe.app.domain.CollectionTarget
import com.phoebe.app.domain.MediaProviderType
import com.phoebe.app.domain.PlexSession
import com.phoebe.app.domain.supportedCollectionEntries
import com.phoebe.app.domain.supportsCollectionEntry
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ProviderFeatureSetTest {
    @Test
    fun plexSupportsMoodStyleAndGenreCollections() {
        val session = PlexSession(token = "token", providerType = MediaProviderType.Plex)

        assertTrue(session.supportsCollectionEntry(CollectionEntry(CollectionTarget.Artists, CollectionFacet.Mood)))
        assertTrue(session.supportsCollectionEntry(CollectionEntry(CollectionTarget.Albums, CollectionFacet.Style)))
        assertTrue(session.supportsCollectionEntry(CollectionEntry(CollectionTarget.Albums, CollectionFacet.Genre)))
    }

    @Test
    fun navidromeSupportsOnlyGenreCollections() {
        val session = PlexSession(token = "password", providerType = MediaProviderType.Navidrome)

        assertFalse(session.supportsCollectionEntry(CollectionEntry(CollectionTarget.Artists, CollectionFacet.Mood)))
        assertFalse(session.supportsCollectionEntry(CollectionEntry(CollectionTarget.Albums, CollectionFacet.Style)))
        assertTrue(session.supportsCollectionEntry(CollectionEntry(CollectionTarget.Artists, CollectionFacet.Genre)))
        assertTrue(session.supportsCollectionEntry(CollectionEntry(CollectionTarget.Albums, CollectionFacet.Genre)))
    }

    @Test
    fun navidromeSupportedCollectionEntriesExcludeMoodAndStyle() {
        val entries = PlexSession(token = "password", providerType = MediaProviderType.Navidrome)
            .supportedCollectionEntries()

        assertFalse(entries.any { it.facet == CollectionFacet.Mood })
        assertFalse(entries.any { it.facet == CollectionFacet.Style })
        assertTrue(CollectionEntry(CollectionTarget.Artists, CollectionFacet.Genre) in entries)
        assertTrue(CollectionEntry(CollectionTarget.Albums, CollectionFacet.Genre) in entries)
    }
}
