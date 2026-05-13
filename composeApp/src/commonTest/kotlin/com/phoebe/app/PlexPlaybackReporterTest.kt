package com.phoebe.app

import com.phoebe.app.data.PlexPlaybackReporter
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class PlexPlaybackReporterTest {
    @Test
    fun plexRatingKeyStripsPrefix() {
        assertEquals("46171", PlexPlaybackReporter.plexRatingKey("plex:46171"))
    }

    @Test
    fun plexRatingKeyIgnoresNonPlexIds() {
        assertNull(PlexPlaybackReporter.plexRatingKey("local:track-1"))
    }
}
