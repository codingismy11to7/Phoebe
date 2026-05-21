package com.phoebe.app

import com.phoebe.app.data.SearchHistoryRepository
import com.phoebe.app.domain.Album
import com.phoebe.app.domain.Artist
import com.phoebe.app.domain.RecentSearchItem
import com.phoebe.app.domain.Track
import com.phoebe.app.platform.PlatformStorage
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import kotlin.test.assertEquals

class SearchHistoryRepositoryDesktopTest {
    @get:Rule
    val temp = TemporaryFolder()

    @Before
    fun storageRoot() {
        System.setProperty("phoebe.storage.root", temp.newFolder("kv").absolutePath)
    }

    @After
    fun cleanup() {
        System.clearProperty("phoebe.storage.root")
    }

    @Test
    fun entityHitsPersistAcrossRestore() = runTest {
        val storage = PlatformStorage()
        val repository = SearchHistoryRepository(storage)

        repository.prepend(RecentSearchItem.ArtistHit(Artist(id = "plex:a1", title = "Artist One")))
        repository.prepend(RecentSearchItem.AlbumHit(Album(id = "plex:al1", title = "Album One", artist = "Artist One")))
        repository.prepend(RecentSearchItem.TrackHit(
            Track(id = "plex:t1", title = "Track One", artist = "Artist One", album = "Album One", durationMs = 1_000L, streamUrl = "", downloadUrl = ""),
        ))

        val restored = SearchHistoryRepository(storage)
        restored.restore()

        assertEquals(
            listOf("Track One", "Album One", "Artist One"),
            restored.items.value.map { item ->
                when (item) {
                    is RecentSearchItem.Query -> item.text
                    is RecentSearchItem.ArtistHit -> item.artist.title
                    is RecentSearchItem.AlbumHit -> item.album.title
                    is RecentSearchItem.TrackHit -> item.track.title
                }
            },
        )
    }

    @Test
    fun textQueriesAreNotStored() = runTest {
        val storage = PlatformStorage()
        val repository = SearchHistoryRepository(storage)

        repository.prepend(RecentSearchItem.Query("moon"))
        repository.prepend(RecentSearchItem.ArtistHit(Artist(id = "plex:a1", title = "Artist One")))

        assertEquals(1, repository.items.value.size)
        assertEquals("Artist One", (repository.items.value.single() as RecentSearchItem.ArtistHit).artist.title)
    }
}
