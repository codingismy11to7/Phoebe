package com.phoebe.app

import app.cash.sqldelight.async.coroutines.awaitAsList
import app.cash.sqldelight.db.SqlDriver
import com.phoebe.app.data.CatalogRepository
import com.phoebe.app.data.MediaSourcesRepository
import com.phoebe.app.data.PlexClient
import com.phoebe.app.domain.DownloadState
import com.phoebe.app.domain.Track
import com.phoebe.app.platform.PlatformStorage
import com.phoebe.app.testing.newInMemoryPhoebeDatabase
import com.phoebe.app.testing.plexCatalogMockEngine
import com.phoebe.app.testing.testHttpClient
import com.phoebe.app.testing.testPlexSession
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.util.concurrent.atomic.AtomicInteger
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

    @Test
    fun largeDownloadPreflightSkipsTracksWithoutDownloadUrls() = runTest {
        val (db, sqlDriver) = newInMemoryPhoebeDatabase()
        driver = sqlDriver
        val http = testHttpClient(plexCatalogMockEngine())
        val repo = catalogRepository(db, http)
        val tracks = (0 until 1_000).map { index ->
            Track(
                id = "plex:missing-$index",
                title = "Missing $index",
                artist = "Artist One",
                album = "Album One",
                durationMs = 1_000,
                streamUrl = "",
                downloadUrl = "",
            )
        }
        tracks.take(3).forEach { track ->
            db.downloadsQueries.upsert(
                trackId = track.id,
                title = track.title,
                artist = track.artist,
                dlState = DownloadState.Failed.name,
                progress = 0.0,
                localUri = null,
            )
        }

        val result = repo.downloadTracks(tracks)

        assertEquals(1_000, result.total)
        assertEquals(0, result.completed)
        assertEquals(0, result.failed)
        assertEquals(1_000, result.skipped)
        assertTrue(repo.catalog.value.downloads.isEmpty())
        val persisted = db.downloadsQueries.selectAll().awaitAsList()
        assertTrue(persisted.isEmpty())
    }

    @Test
    fun downloadTracksStreamsAudioToStorage() = runTest {
        val payload = ByteArray(160 * 1024) { index -> (index % 251).toByte() }
        var downloadRequests = 0
        var artworkRequests = 0
        val (db, sqlDriver) = newInMemoryPhoebeDatabase()
        driver = sqlDriver
        val engine = MockEngine { request ->
            when (request.url.encodedPath) {
                "/downloads/t1.mp3" -> {
                    downloadRequests++
                    respond(
                        content = payload,
                        status = HttpStatusCode.OK,
                        headers = headersOf(
                            HttpHeaders.ContentLength to listOf(payload.size.toString()),
                            HttpHeaders.ContentType to listOf("audio/mpeg"),
                        ),
                    )
                }
                "/art/t1.jpg" -> {
                    artworkRequests++
                    respond(
                        content = ByteArray(1024) { 7 },
                        status = HttpStatusCode.OK,
                        headers = headersOf(HttpHeaders.ContentType to listOf("image/jpeg")),
                    )
                }
                else -> respond("", HttpStatusCode.NotFound)
            }
        }
        val http = testHttpClient(engine)
        val repo = catalogRepository(db, http)
        PlatformStorage().writeDownloadDirectory(temp.newFolder("downloads").toURI().toString())
        val track = Track(
            id = "plex:t-stream",
            title = "Streamed Song",
            artist = "Artist One",
            album = "Album One",
            durationMs = 1_000,
            streamUrl = "https://plex.example/stream/t1.mp3",
            downloadUrl = "https://plex.example/downloads/t1.mp3",
            thumbUrl = "https://plex.example/art/t1.jpg",
        )

        val result = repo.downloadTracks(listOf(track))

        assertEquals(1, result.total)
        assertEquals(1, result.completed)
        assertEquals(0, result.failed)
        val downloaded = repo.catalog.value.downloads.single()
        assertEquals(DownloadState.Complete, downloaded.state)
        assertEquals(1f, downloaded.progress)
        val localUri = requireNotNull(downloaded.localUri)
        val stored = PlatformStorage().readUriBytes(localUri)
        assertNotNull(stored)
        assertTrue(payload.contentEquals(stored))
        val persisted = db.downloadsQueries.selectAll().awaitAsList().single()
        assertEquals(DownloadState.Complete.name, persisted.dlState)
        assertEquals(localUri, persisted.localUri)
        assertEquals(1, downloadRequests)
        assertEquals(0, artworkRequests)

        val retryResult = repo.downloadTracks(listOf(track))

        assertEquals(1, retryResult.total)
        assertEquals(1, retryResult.completed)
        assertEquals(0, retryResult.failed)
        assertEquals(1, downloadRequests)
        assertEquals(0, artworkRequests)
    }

    @Test
    fun downloadTracksRunsBatchDownloadsInParallel() = runTest {
        val payload = ByteArray(32 * 1024) { index -> (index % 251).toByte() }
        val inFlight = AtomicInteger(0)
        val maxInFlight = AtomicInteger(0)
        val (db, sqlDriver) = newInMemoryPhoebeDatabase()
        driver = sqlDriver
        val engine = MockEngine { request ->
            when {
                request.url.encodedPath.startsWith("/downloads/") -> {
                    val current = inFlight.incrementAndGet()
                    maxInFlight.updateAndGet { previous -> maxOf(previous, current) }
                    delay(100)
                    inFlight.decrementAndGet()
                    respond(
                        content = payload,
                        status = HttpStatusCode.OK,
                        headers = headersOf(
                            HttpHeaders.ContentLength to listOf(payload.size.toString()),
                            HttpHeaders.ContentType to listOf("audio/mpeg"),
                        ),
                    )
                }
                else -> respond("", HttpStatusCode.NotFound)
            }
        }
        val repo = catalogRepository(db, testHttpClient(engine))
        PlatformStorage().writeDownloadDirectory(temp.newFolder("parallel-downloads").toURI().toString())
        val tracks = (1..12).map { index ->
            Track(
                id = "plex:t-parallel-$index",
                title = "Parallel Song $index",
                artist = "Artist One",
                album = "Album One",
                durationMs = 1_000,
                streamUrl = "https://plex.example/stream/t$index.mp3",
                downloadUrl = "https://plex.example/downloads/t$index.mp3",
            )
        }

        val result = repo.downloadTracks(tracks)

        assertEquals(12, result.total)
        assertEquals(12, result.completed)
        assertEquals(0, result.failed)
        assertTrue(maxInFlight.get() > 4)
        assertTrue(maxInFlight.get() <= 8)
    }

    @Test
    fun downloadProgressDoesNotRewriteCatalogDownloadsUntilBatchSettles() = runTest {
        val payload = ByteArray(32 * 1024) { index -> (index % 251).toByte() }
        val requestStarted = CompletableDeferred<Unit>()
        val allowResponse = CompletableDeferred<Unit>()
        val (db, sqlDriver) = newInMemoryPhoebeDatabase()
        driver = sqlDriver
        val engine = MockEngine { request ->
            when (request.url.encodedPath) {
                "/downloads/t1.mp3" -> {
                    requestStarted.complete(Unit)
                    allowResponse.await()
                    respond(
                        content = payload,
                        status = HttpStatusCode.OK,
                        headers = headersOf(
                            HttpHeaders.ContentLength to listOf(payload.size.toString()),
                            HttpHeaders.ContentType to listOf("audio/mpeg"),
                        ),
                    )
                }
                else -> respond("", HttpStatusCode.NotFound)
            }
        }
        val repo = catalogRepository(db, testHttpClient(engine))
        PlatformStorage().writeDownloadDirectory(temp.newFolder("progress-downloads").toURI().toString())
        val track = Track(
            id = "plex:t-progress",
            title = "Progress Song",
            artist = "Artist One",
            album = "Album One",
            durationMs = 1_000,
            streamUrl = "https://plex.example/stream/t1.mp3",
            downloadUrl = "https://plex.example/downloads/t1.mp3",
        )

        val result = async { repo.downloadTracks(listOf(track)) }
        requestStarted.await()

        assertTrue(repo.downloads.value.any { it.trackId == track.id && it.state == DownloadState.Downloading })
        assertTrue(repo.catalog.value.downloads.isEmpty())

        allowResponse.complete(Unit)
        assertEquals(1, result.await().completed)
        assertEquals(DownloadState.Complete, repo.downloads.value.single().state)
        assertEquals(DownloadState.Complete, repo.catalog.value.downloads.single().state)
    }

    @Test
    fun downloadFailsWhenResponseEndsBeforeDeclaredContentLength() = runTest {
        val payload = ByteArray(96 * 1024) { index -> (index % 199).toByte() }
        val (db, sqlDriver) = newInMemoryPhoebeDatabase()
        driver = sqlDriver
        val engine = MockEngine { request ->
            when (request.url.encodedPath) {
                "/downloads/t1.mp3" -> respond(
                    content = payload,
                    status = HttpStatusCode.OK,
                    headers = headersOf(
                        HttpHeaders.ContentLength to listOf((payload.size + 1).toString()),
                        HttpHeaders.ContentType to listOf("audio/mpeg"),
                    ),
                )
                else -> respond("", HttpStatusCode.NotFound)
            }
        }
        val http = testHttpClient(engine)
        val repo = catalogRepository(db, http)
        PlatformStorage().writeDownloadDirectory(temp.newFolder("downloads-open-body").toURI().toString())
        val track = Track(
            id = "plex:t-open-body",
            title = "Short Body Song",
            artist = "Artist One",
            album = "Album One",
            durationMs = 1_000,
            streamUrl = "https://plex.example/stream/t1.mp3",
            downloadUrl = "https://plex.example/downloads/t1.mp3",
        )

        val result = repo.downloadTracks(listOf(track))

        assertEquals(1, result.total)
        assertEquals(0, result.completed)
        assertEquals(1, result.failed)
        val downloaded = repo.catalog.value.downloads.single()
        assertEquals(DownloadState.Failed, downloaded.state)
        assertEquals(null, downloaded.localUri)
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
