package com.phoebe.app.player

import android.app.Application
import android.net.Uri
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class)
class ArtworkUrisTest {
    @Test
    fun buildsUriFromPackageAndType() {
        val uri = artworkUri("com.phoebe.app.debug", ArtworkType.ALBUM, "12345")
        assertEquals("content://com.phoebe.app.debug.artwork/album/12345", uri.toString())
    }

    @Test
    fun roundTripsEveryType() {
        ArtworkType.entries.forEach { type ->
            val uri = artworkUri("com.phoebe.app", type, "id-1")
            assertEquals(type to "id-1", parseArtworkUri(uri))
        }
    }

    @Test
    fun roundTripsIdsNeedingEscaping() {
        val id = "a b/c?d#e"
        val uri = artworkUri("com.phoebe.app", ArtworkType.TRACK, id)
        assertEquals(ArtworkType.TRACK to id, parseArtworkUri(uri))
    }

    @Test
    fun rejectsMalformedUris() {
        assertNull(parseArtworkUri(Uri.parse("content://com.phoebe.app.artwork/album")))
        assertNull(parseArtworkUri(Uri.parse("content://com.phoebe.app.artwork/bogus/1")))
        assertNull(parseArtworkUri(Uri.parse("http://example.com/album/1")))
    }
}
