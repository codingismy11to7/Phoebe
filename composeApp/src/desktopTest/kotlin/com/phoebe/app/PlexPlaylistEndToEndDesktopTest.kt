package com.phoebe.app

import app.cash.sqldelight.db.SqlDriver
import com.phoebe.app.data.CatalogRepository
import com.phoebe.app.data.MediaSourcesRepository
import com.phoebe.app.data.PlexClient
import com.phoebe.app.domain.Track
import com.phoebe.app.platform.PlatformStorage
import com.phoebe.app.testing.newInMemoryPhoebeDatabase
import com.phoebe.app.testing.plexCatalogMockEngine
import com.phoebe.app.testing.testHttpClient
import com.phoebe.app.testing.testPlexSession
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class PlexPlaylistEndToEndDesktopTest {
    @get:Rule
    val temp = TemporaryFolder()

    private var driver: SqlDriver? = null

    @Before
    fun storageRoot() {
        System.setProperty("phoebe.storage.root", temp.newFolder("kv").absolutePath)
    }

    @After
    fun cleanup() {
        driver?.close()
        driver = null
        System.clearProperty("phoebe.storage.root")
    }

    @Test
    fun createPlaylistSeedsMockPlexAndUpdatesCatalog() = runTest {
        val (db, sqlDriver) = newInMemoryPhoebeDatabase()
        driver = sqlDriver
        val engine = plexCatalogMockEngine()
        val http = testHttpClient(engine)
        val repo = catalogRepository(db, http)

        repo.refreshAggregated(testPlexSession())
        val seed = repo.catalog.value.tracksByParent["plex:a1"].orEmpty().single()
        val created = repo.createPlaylist(testPlexSession(), "New Mix", listOf(seed))

        assertNotNull(created)
        assertEquals("plex:p99", created.id)
        assertEquals("New Mix", created.title)
        assertTrue(repo.catalog.value.playlists.any { it.id == "plex:p99" })
    }

    @Test
    fun addTracksToPlaylistRefetchesAndSyncsToMockPlex() = runTest {
        var plexAddCalled = false
        val (db, sqlDriver) = newInMemoryPhoebeDatabase()
        driver = sqlDriver
        val engine = plexCatalogMockEngine(onPlaylistAdd = { plexAddCalled = true })
        val http = testHttpClient(engine)
        val repo = catalogRepository(db, http)

        repo.refreshAggregated(testPlexSession())
        val playlist = repo.catalog.value.playlists.single()
        val newTrack = Track(
            id = "plex:t3",
            title = "Added Song",
            artist = "Artist One",
            album = "Album One",
            durationMs = 2_000,
            streamUrl = "https://plex.example/t3?X-Plex-Token=token",
            downloadUrl = "https://plex.example/t3?X-Plex-Token=token&download=1",
        )
        repo.addTracksToPlaylist(testPlexSession(), playlist, listOf(newTrack))

        assertTrue(plexAddCalled)
        assertEquals(3, repo.catalog.value.playlists.single { it.id == playlist.id }.trackCount)
        assertEquals(
            listOf("plex:t3", "plex:t1", "plex:t2"),
            repo.catalog.value.tracksByParent[playlist.id].orEmpty().map { it.id },
        )
    }

    @Test
    fun tracksForPlaylistLoadsFromMockPlexWhenCacheEmpty() = runTest {
        val (db, sqlDriver) = newInMemoryPhoebeDatabase()
        driver = sqlDriver
        val http = testHttpClient(plexCatalogMockEngine(playlistThumb = "/playlists/p1/art"))
        val repo = catalogRepository(db, http)

        repo.refreshAggregated(testPlexSession())
        val playlist = repo.catalog.value.playlists.single()
        assertTrue(repo.catalog.value.tracksByParent[playlist.id].isNullOrEmpty())

        val tracks = repo.tracksForPlaylist(testPlexSession(), playlist)

        assertEquals(listOf("plex:t1", "plex:t2"), tracks.map { it.id })
    }

    @Test
    fun warmPlaylistTracksLoadsMissingTracksWithoutForegroundRefresh() = runTest {
        val (db, sqlDriver) = newInMemoryPhoebeDatabase()
        driver = sqlDriver
        val http = testHttpClient(plexCatalogMockEngine(playlistThumb = "/playlists/p1/art"))
        val repo = catalogRepository(db, http)

        repo.refreshAggregated(testPlexSession())
        val playlist = repo.catalog.value.playlists.single()
        assertTrue(repo.catalog.value.tracksByParent[playlist.id].isNullOrEmpty())

        repo.warmPlaylistTracks(testPlexSession())

        assertFalse(repo.catalogRefreshing.value)
        assertEquals(listOf("plex:t1", "plex:t2"), repo.catalog.value.tracksByParent[playlist.id].orEmpty().map { it.id })
    }

    @Test
    fun refreshAggregatedLoadsLikedSongsForGlobalLikeState() = runTest {
        val (db, sqlDriver) = newInMemoryPhoebeDatabase()
        driver = sqlDriver
        val http = testHttpClient(plexCatalogMockEngine(includeLikedPlaylist = true))
        val repo = catalogRepository(db, http)

        repo.refreshAggregated(testPlexSession())

        val liked = repo.catalog.value.playlists.single { it.title == "Liked Songs" }
        assertEquals(listOf("plex:t1"), repo.catalog.value.tracksByParent[liked.id].orEmpty().map { it.id })
        assertTrue(repo.isTrackLiked("plex:t1"))
    }

    @Test
    fun toggleLikedTrackFindsExistingLikedPlaylistAndRemovesItem() = runTest {
        var plexRemoveCalled = false
        val (db, sqlDriver) = newInMemoryPhoebeDatabase()
        driver = sqlDriver
        val http = testHttpClient(plexCatalogMockEngine(includeLikedPlaylist = true, onPlaylistRemove = { plexRemoveCalled = true }))
        val repo = catalogRepository(db, http)

        repo.refreshAggregated(testPlexSession())
        val track = repo.tracksForAlbum(testPlexSession(), repo.catalog.value.albums.single()).single()
        assertTrue(repo.toggleLikedTrack(testPlexSession(), track).not())

        assertTrue(plexRemoveCalled)
        val liked = repo.catalog.value.playlists.single { it.title == "Liked Songs" }
        assertTrue(repo.catalog.value.tracksByParent[liked.id].orEmpty().none { it.id == track.id })
    }

    @Test
    fun toggleLikedTrackCreatesLikedPlaylistWhenMissing() = runTest {
        val (db, sqlDriver) = newInMemoryPhoebeDatabase()
        driver = sqlDriver
        val http = testHttpClient(plexCatalogMockEngine())
        val repo = catalogRepository(db, http)

        repo.refreshAggregated(testPlexSession())
        val track = repo.tracksForAlbum(testPlexSession(), repo.catalog.value.albums.single()).single()
        val liked = repo.toggleLikedTrack(testPlexSession(), track)

        assertTrue(liked)
        assertTrue(repo.catalog.value.playlists.any { it.title == "Liked Songs" })
    }

    @Test
    fun copyPlexPlaylistIntoPlaylistSkipsSelfDrop() = runTest {
        val (db, sqlDriver) = newInMemoryPhoebeDatabase()
        driver = sqlDriver
        val http = testHttpClient(plexCatalogMockEngine())
        val repo = catalogRepository(db, http)

        repo.refreshAggregated(testPlexSession())
        val playlist = repo.catalog.value.playlists.single()

        assertEquals(0, repo.copyPlexPlaylistIntoPlaylist(testPlexSession(), playlist, playlist))
    }

    @Test
    fun tracksForDecadeFetchesMatchingAlbumTracks() = runTest {
        val (db, sqlDriver) = newInMemoryPhoebeDatabase()
        driver = sqlDriver
        val http = testHttpClient(plexCatalogMockEngine())
        val repo = catalogRepository(db, http)

        repo.refreshAggregated(testPlexSession())
        val tracks = repo.tracksForDecade(testPlexSession(), 1990)

        assertEquals(listOf("plex:t1"), tracks.map { it.id })
        assertEquals(1995, tracks.single().year)
    }

    private fun catalogRepository(db: com.phoebe.app.db.PhoebeDatabase, http: io.ktor.client.HttpClient): CatalogRepository {
        val mediaSources = MediaSourcesRepository(db, PlatformStorage())
        return CatalogRepository(
            plexClient = PlexClient(http),
            database = db,
            storage = PlatformStorage(),
            httpClient = http,
            mediaSourcesRepository = mediaSources,
        )
    }
}
