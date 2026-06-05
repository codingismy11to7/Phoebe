package com.phoebe.app

import app.cash.sqldelight.db.SqlDriver
import com.phoebe.app.data.CatalogRepository
import com.phoebe.app.data.MediaSourcesRepository
import com.phoebe.app.data.PlexClient
import com.phoebe.app.data.JellyfinClient
import com.phoebe.app.domain.CatalogSnapshot
import com.phoebe.app.domain.CatalogSyncPhase
import com.phoebe.app.domain.CollectionEntry
import com.phoebe.app.domain.CollectionFacet
import com.phoebe.app.domain.CollectionTarget
import com.phoebe.app.domain.JellyfinSyncMode
import com.phoebe.app.domain.MediaProviderType
import com.phoebe.app.domain.MusicLibrary
import com.phoebe.app.domain.PlexServer
import com.phoebe.app.domain.PlexSession
import com.phoebe.app.domain.Track
import com.phoebe.app.platform.PlatformStorage
import com.phoebe.app.testing.minimalMp3Bytes
import com.phoebe.app.testing.newInMemoryPhoebeDatabase
import com.phoebe.app.testing.testHttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.MockRequestHandleScope
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class CatalogRepositoryRefreshDesktopTest {
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
    fun refreshAggregatedWithNoSessionAndNoFoldersYieldsEmptyCatalog() = runTest {
        val (db, d) = newInMemoryPhoebeDatabase()
        driver = d
        val engine = MockEngine { respond("", HttpStatusCode.NotFound) }
        val http = testHttpClient(engine)
        val media = MediaSourcesRepository(db, PlatformStorage())
        val repo = CatalogRepository(
            plexClient = PlexClient(http),
            database = db,
            storage = PlatformStorage(),
            httpClient = http,
            mediaSourcesRepository = media,
        )
        repo.refreshAggregated(session = null)
        assertFalse(repo.catalogRefreshing.value)
        assertEquals(0, repo.catalog.value.artists.size)
        assertEquals(0, repo.catalog.value.albums.size)
    }

    @Test
    fun restoreCachedCatalogSkipsLocalFolderRowsWhenSourceWasCleared() = runTest {
        val (db, d) = newInMemoryPhoebeDatabase()
        driver = d
        db.transaction {
            db.catalogQueries.upsertArtist("plex:artist", "Plex Artist", null, 1L, 0L, 0L, null, null, null, null, null, 0L)
            db.catalogQueries.upsertAlbum("plex:album", "Plex Album", "Plex Artist", 2024L, null, 0L, null, null, null, null, null, 0L)
            db.catalogQueries.upsertArtist("local_lf-old:artist:1", "Local Artist", null, 1L, 1L, 1L, 123L, null, null, null, null, 0L)
            db.catalogQueries.upsertAlbum("local_lf-old:album:1", "Local Album", "Local Artist", 2024L, null, 1L, 123L, null, null, null, null, 0L)
            db.catalogQueries.upsertTrack(
                id = "local_lf-old:track:1",
                title = "Local Song",
                artist = "Local Artist",
                album = "Local Album",
                durationMs = 1_000L,
                streamUrl = "",
                downloadUrl = "",
                thumbUrl = null,
                localArtworkUri = null,
                localUri = "file:///stale/local-song.mp3",
                year = 2024L,
                genre = null,
                mood = null,
                style = null,
                filepath = "local-song.mp3",
                audioCodec = null,
                bitrateKbps = null,
                dateAddedMs = 123L,
                rating = null,
                parentAlbumId = "local_lf-old:album:1",
            )
            db.catalogQueries.upsertTrackParent("local_lf-old:album:1", "local_lf-old:track:1", 0L, null)
        }
        val engine = MockEngine { respond("", HttpStatusCode.NotFound) }
        val http = testHttpClient(engine)
        val media = MediaSourcesRepository(db, PlatformStorage())
        val repo = CatalogRepository(
            plexClient = PlexClient(http),
            database = db,
            storage = PlatformStorage(),
            httpClient = http,
            mediaSourcesRepository = media,
        )

        repo.restoreCachedCatalog()

        assertEquals(listOf("plex:artist"), repo.catalog.value.artists.map { it.id })
        assertEquals(listOf("plex:album"), repo.catalog.value.albums.map { it.id })
        assertTrue(repo.catalog.value.tracksByParent.values.flatten().none { it.id.startsWith("local_") })
    }

    @Test
    fun refreshLocalFoldersOnlyReportsScanCompletion() = runTest {
        val music = temp.newFolder("local-scan")
        File(music, "alpha.mp3").writeBytes(minimalMp3Bytes())
        val (db, d) = newInMemoryPhoebeDatabase()
        driver = d
        val engine = MockEngine { respond("", HttpStatusCode.NotFound) }
        val http = testHttpClient(engine)
        val media = MediaSourcesRepository(db, PlatformStorage())
        val repo = CatalogRepository(
            plexClient = PlexClient(http),
            database = db,
            storage = PlatformStorage(),
            httpClient = http,
            mediaSourcesRepository = media,
        )
        media.addLocalFolder(music.toURI().toString(), "Local Scan")

        repo.refreshLocalFoldersOnly(session = null)

        assertFalse(repo.catalogRefreshing.value)
        assertEquals(CatalogSyncPhase.Complete, repo.catalogSyncState.value.phase)
        assertEquals("Local folders scanned.", repo.catalogSyncState.value.message)
        assertEquals(listOf("alpha"), repo.catalog.value.tracksByParent.values.flatten().map { it.title })
    }

    @Test
    fun refreshPublishesPlexMetadataBeforeAlbumTracksFinish() = runTest {
        val (db, d) = newInMemoryPhoebeDatabase()
        driver = d
        val childrenStarted = CompletableDeferred<Unit>()
        val releaseChildren = CompletableDeferred<Unit>()
        val engine = MockEngine { request ->
            when (request.url.encodedPath) {
                "/library/sections/1/all" -> respondJson(artistsJson())
                "/library/sections/1/albums" -> respondJson(albumsJson())
                "/playlists" -> respondJson(playlistsJson(trackCount = 1))
                "/library/metadata/a1/children" -> {
                    childrenStarted.complete(Unit)
                    releaseChildren.await()
                    respondJson(albumTracksJson())
                }
                else -> respond("", HttpStatusCode.NotFound)
            }
        }
        val media = MediaSourcesRepository(db, PlatformStorage())
        val repo = CatalogRepository(
            plexClient = PlexClient(testHttpClient(engine)),
            database = db,
            storage = PlatformStorage(),
            httpClient = testHttpClient(engine),
            mediaSourcesRepository = media,
        )

        val refresh = async { repo.refreshAggregated(testSession()) }
        childrenStarted.await()

        assertFalse(repo.catalogRefreshing.value)
        assertEquals(CatalogSyncPhase.LoadingSongs, repo.catalogSyncState.value.phase)
        assertFalse(repo.catalogSyncState.value.showGlobalProgress)
        assertEquals(listOf("plex:artist1"), repo.catalog.value.artists.map { it.id })
        assertEquals(listOf("plex:a1"), repo.catalog.value.albums.map { it.id })
        assertEquals(listOf("plex:p1"), repo.catalog.value.playlists.map { it.id })
        assertEquals(emptyMap(), repo.catalog.value.tracksByParent)

        releaseChildren.complete(Unit)
        refresh.await()

        assertFalse(repo.catalogRefreshing.value)
        assertEquals(CatalogSyncPhase.Complete, repo.catalogSyncState.value.phase)
        assertEquals(listOf("plex:t1"), repo.catalog.value.tracksByParent["plex:a1"].orEmpty().map { it.id })
    }

    @Test
    fun refreshDoesNotProbeIdentityBeforeLoadingLibrary() = runTest {
        val (db, d) = newInMemoryPhoebeDatabase()
        driver = d
        val engine = MockEngine { request ->
            when (request.url.encodedPath) {
                "/identity" -> awaitCancellation()
                "/library/sections/1/all" -> if (request.url.parameters["type"] == "10") {
                    respondJson(trackPageJson())
                } else {
                    respondJson(artistsJson())
                }
                "/library/sections/1/albums" -> respondJson(albumsJson())
                "/library/metadata/a1/children" -> respondJson(albumTracksJson())
                "/playlists" -> respondJson(playlistsJson(trackCount = 0))
                else -> respond("", HttpStatusCode.NotFound)
            }
        }
        val http = testHttpClient(engine)
        val media = MediaSourcesRepository(db, PlatformStorage())
        val repo = CatalogRepository(
            plexClient = PlexClient(http),
            database = db,
            storage = PlatformStorage(),
            httpClient = http,
            mediaSourcesRepository = media,
        )

        repo.refreshAggregated(testSession())

        assertEquals(listOf("plex:a1"), repo.catalog.value.albums.map { it.id })
    }

    @Test
    fun refreshPublishesPlexMetadataAfterCollectionCapableMetadataCompletes() = runTest {
        val (db, d) = newInMemoryPhoebeDatabase()
        driver = d
        val artistsStarted = CompletableDeferred<Unit>()
        val releaseArtists = CompletableDeferred<Unit>()
        val engine = MockEngine { request ->
            when (request.url.encodedPath) {
                "/library/sections/1/all" -> if (request.url.parameters["type"] == "10") {
                    respondJson(trackPageJson())
                } else {
                    artistsStarted.complete(Unit)
                    releaseArtists.await()
                    respondJson(artistsJson())
                }
                "/library/sections/1/albums" -> respondJson(albumsJson())
                "/playlists" -> respondJson(playlistsJson(trackCount = 1))
                else -> respond("", HttpStatusCode.NotFound)
            }
        }
        val http = testHttpClient(engine)
        val media = MediaSourcesRepository(db, PlatformStorage())
        val repo = CatalogRepository(
            plexClient = PlexClient(http),
            database = db,
            storage = PlatformStorage(),
            httpClient = http,
            mediaSourcesRepository = media,
        )

        val refresh = async { repo.refreshAggregated(testSession()) }
        artistsStarted.await()

        assertTrue(repo.catalogRefreshing.value)
        assertEquals(CatalogSyncPhase.LoadingLibrary, repo.catalogSyncState.value.phase)
        assertEquals(emptyList(), repo.catalog.value.albums.map { it.id })
        assertEquals(emptyList(), repo.catalog.value.playlists.map { it.id })

        releaseArtists.complete(Unit)
        refresh.await()

        assertEquals(listOf("plex:a1"), repo.catalog.value.albums.map { it.id })
        assertEquals(listOf("plex:p1"), repo.catalog.value.playlists.map { it.id })
    }

    @Test
    fun metadataPublishPreservesCachedTracksUntilFreshTracksArrive() = runTest {
        val (db, d) = newInMemoryPhoebeDatabase()
        driver = d
        db.transaction {
            db.catalogQueries.upsertArtist("plex:artist1", "Artist One", null, 1, 0, 0, null, null, null, null, null, 0)
            db.catalogQueries.upsertAlbum("plex:a1", "Album One", "Artist One", null, null, 0, null, null, null, null, null, 0)
            db.catalogQueries.upsertTrack(
                id = "plex:old",
                title = "Cached Song",
                artist = "Artist One",
                album = "Album One",
                durationMs = 10,
                streamUrl = "https://plex.example/old?X-Plex-Token=token",
                downloadUrl = "https://plex.example/old?X-Plex-Token=token&download=1",
                thumbUrl = null,
                localArtworkUri = null,
                localUri = null,
                year = null,
                genre = null,
                mood = null,
                style = null,
                filepath = null,
                audioCodec = null,
                bitrateKbps = null,
                dateAddedMs = null,
                rating = null,
                parentAlbumId = null,
            )
            db.catalogQueries.upsertTrackParent("plex:a1", "plex:old", 0, null)
        }

        val childrenStarted = CompletableDeferred<Unit>()
        val releaseChildren = CompletableDeferred<Unit>()
        val engine = MockEngine { request ->
            when (request.url.encodedPath) {
                "/library/sections/1/all" -> respondJson(artistsJson())
                "/library/sections/1/albums" -> respondJson(albumsJson())
                "/playlists" -> respondJson(playlistsJson(trackCount = 0))
                "/library/metadata/a1/children" -> {
                    childrenStarted.complete(Unit)
                    releaseChildren.await()
                    respondJson(albumTracksJson())
                }
                else -> respond("", HttpStatusCode.NotFound)
            }
        }
        val media = MediaSourcesRepository(db, PlatformStorage())
        val repo = CatalogRepository(
            plexClient = PlexClient(testHttpClient(engine)),
            database = db,
            storage = PlatformStorage(),
            httpClient = testHttpClient(engine),
            mediaSourcesRepository = media,
        )
        repo.restoreCachedCatalog()

        val refresh = async { repo.refreshAggregated(testSession()) }
        childrenStarted.await()

        assertEquals(listOf("plex:old"), repo.catalog.value.tracksByParent["plex:a1"].orEmpty().map { it.id })

        releaseChildren.complete(Unit)
        refresh.await()

        assertEquals(listOf("plex:t1"), repo.catalog.value.tracksByParent["plex:a1"].orEmpty().map { it.id })
    }

    @Test
    fun refreshPreservesExistingFirstSeenDateWhenSourceOmitsAddedAt() = runTest {
        val (db, d) = newInMemoryPhoebeDatabase()
        driver = d
        db.transaction {
            db.catalogQueries.upsertArtist("plex:artist1", "Artist One", null, 1, 0, 0, 41L, null, null, null, null, 0)
            db.catalogQueries.upsertAlbum("plex:a1", "Album One", "Artist One", null, null, 0, 41L, null, null, null, null, 0)
            db.catalogQueries.upsertTrack(
                id = "plex:t1",
                title = "Cached Song",
                artist = "Artist One",
                album = "Album One",
                durationMs = 10,
                streamUrl = "https://plex.example/t1?X-Plex-Token=token",
                downloadUrl = "https://plex.example/t1?X-Plex-Token=token&download=1",
                thumbUrl = null,
                localArtworkUri = null,
                localUri = null,
                year = null,
                genre = null,
                mood = null,
                style = null,
                filepath = null,
                audioCodec = null,
                bitrateKbps = null,
                dateAddedMs = 41L,
                rating = null,
                parentAlbumId = null,
            )
            db.catalogQueries.upsertTrackParent("plex:a1", "plex:t1", 0, null)
        }
        val engine = MockEngine { request ->
            when (request.url.encodedPath) {
                "/library/sections/1/all" -> respondJson(artistsJson())
                "/library/sections/1/albums" -> respondJson(albumsJson())
                "/playlists" -> respondJson(playlistsJson(trackCount = 0))
                "/library/metadata/a1/children" -> respondJson(albumTracksJson())
                else -> respond("", HttpStatusCode.NotFound)
            }
        }
        val media = MediaSourcesRepository(db, PlatformStorage())
        val repo = CatalogRepository(
            plexClient = PlexClient(testHttpClient(engine)),
            database = db,
            storage = PlatformStorage(),
            httpClient = testHttpClient(engine),
            mediaSourcesRepository = media,
        )
        repo.restoreCachedCatalog()
        repo.refreshAggregated(testSession())

        assertEquals(41L, repo.catalog.value.tracksByParent["plex:a1"].orEmpty().single().dateAddedMs)
    }

    @Test
    fun lightweightRemoteSyncUpdatesPlexShellWithoutIndexingTracks() = runTest {
        val (db, d) = newInMemoryPhoebeDatabase()
        driver = d
        db.transaction {
            db.catalogQueries.upsertArtist("plex:artist1", "Old Artist", null, 1, 0, 0, null, null, null, null, null, 0)
            db.catalogQueries.upsertAlbum("plex:a1", "Old Album", "Old Artist", null, null, 0, null, null, null, null, null, 0)
            db.catalogQueries.upsertPlaylist("plex:p1", "Old Playlist", 1, "/playlists/p1/items", null, 0, null, 0)
            db.catalogQueries.upsertTrack(
                id = "plex:t1",
                title = "Cached Song",
                artist = "Old Artist",
                album = "Old Album",
                durationMs = 10,
                streamUrl = "https://plex.example/t1?X-Plex-Token=token",
                downloadUrl = "https://plex.example/t1?X-Plex-Token=token&download=1",
                thumbUrl = null,
                localArtworkUri = null,
                localUri = null,
                year = null,
                genre = null,
                mood = null,
                style = null,
                filepath = null,
                audioCodec = null,
                bitrateKbps = null,
                dateAddedMs = 41L,
                rating = null,
                parentAlbumId = "plex:a1",
            )
            db.catalogQueries.upsertTrackParent("plex:a1", "plex:t1", 0, null)
        }
        val requestedPaths = mutableListOf<String>()
        val engine = MockEngine { request ->
            requestedPaths += request.url.encodedPath + "?" + request.url.encodedQuery
            when {
                request.url.encodedPath == "/identity" -> respondJson(identityJson())
                request.url.encodedPath == "/library/sections/1/all" &&
                    request.url.parameters["type"] == "10" -> error("Lightweight sync should not index tracks.")
                request.url.encodedPath.startsWith("/library/metadata/") -> error("Lightweight sync should not load album children.")
                request.url.encodedPath == "/library/sections/1/all" -> respondJson(favoriteArtistsJson())
                request.url.encodedPath == "/library/sections/1/albums" -> respondJson(favoriteAlbumsJson())
                request.url.encodedPath == "/playlists" -> respondJson(playlistsJson(trackCount = 2))
                else -> respond("", HttpStatusCode.NotFound)
            }
        }
        val http = testHttpClient(engine)
        val media = MediaSourcesRepository(db, PlatformStorage())
        val repo = CatalogRepository(
            plexClient = PlexClient(http),
            database = db,
            storage = PlatformStorage(),
            httpClient = http,
            mediaSourcesRepository = media,
        )
        repo.restoreCachedCatalog()

        repo.syncLightweightRemoteState(testSession())

        assertEquals("Artist One", repo.catalog.value.artists.single { it.id == "plex:artist1" }.title)
        assertTrue(repo.catalog.value.artists.single { it.id == "plex:artist1" }.favorite)
        assertEquals("Album One", repo.catalog.value.albums.single { it.id == "plex:a1" }.title)
        assertTrue(repo.catalog.value.albums.single { it.id == "plex:a1" }.favorite)
        assertEquals(2, repo.catalog.value.playlists.single { it.id == "plex:p1" }.trackCount)
        assertEquals(listOf("plex:t1"), repo.catalog.value.tracksByParent["plex:a1"].orEmpty().map { it.id })
        assertFalse(requestedPaths.any { it.contains("type=10") })
        assertFalse(requestedPaths.any { it.startsWith("/library/metadata/") })
    }

    @Test
    fun fullRefreshClearsStalePlexAlbumFavoriteWhenServerNoLongerReportsIt() = runTest {
        val (db, d) = newInMemoryPhoebeDatabase()
        driver = d
        db.transaction {
            db.catalogQueries.upsertArtist("plex:artist1", "Artist One", null, 1, 0, 0, null, null, null, null, null, 0)
            db.catalogQueries.upsertAlbum("plex:a1", "Album One", "Artist One", null, null, 0, null, null, null, null, null, 1)
        }
        val engine = MockEngine { request ->
            when {
                request.url.encodedPath == "/library/sections/1/all" &&
                    request.url.parameters["type"] == "10" -> respondJson(trackPageJson())
                request.url.encodedPath == "/library/sections/1/all" -> respondJson(artistsJson())
                request.url.encodedPath == "/library/sections/1/albums" -> respondJson(albumsJson())
                request.url.encodedPath == "/playlists" -> respondJson(playlistsJson(trackCount = 0))
                else -> respond("", HttpStatusCode.NotFound)
            }
        }
        val http = testHttpClient(engine)
        val media = MediaSourcesRepository(db, PlatformStorage())
        val repo = CatalogRepository(
            plexClient = PlexClient(http),
            database = db,
            storage = PlatformStorage(),
            httpClient = http,
            mediaSourcesRepository = media,
        )
        repo.restoreCachedCatalog()
        assertTrue(repo.catalog.value.albums.single { it.id == "plex:a1" }.favorite)

        repo.refreshAggregated(testSession())

        assertFalse(repo.catalog.value.albums.single { it.id == "plex:a1" }.favorite)
    }

    @Test
    fun refreshIndexesPagedTracksIntoAlbumParentsAndRestoresThem() = runTest {
        val (db, d) = newInMemoryPhoebeDatabase()
        driver = d
        val engine = MockEngine { request ->
            when (request.url.encodedPath) {
                "/library/sections/1/all" -> if (request.url.parameters["type"] == "10") {
                    respondJson(trackPageJson())
                } else {
                    respondJson(artistsJson())
                }
                "/library/sections/1/albums" -> respondJson(albumsJson())
                "/playlists" -> respondJson(playlistsJson(trackCount = 0))
                else -> respond("", HttpStatusCode.NotFound)
            }
        }
        val http = testHttpClient(engine)
        val media = MediaSourcesRepository(db, PlatformStorage())
        val repo = CatalogRepository(
            plexClient = PlexClient(http),
            database = db,
            storage = PlatformStorage(),
            httpClient = http,
            mediaSourcesRepository = media,
        )

        repo.refreshAggregated(testSession())

        assertEquals(listOf("plex:t1"), repo.catalog.value.tracksByParent["plex:a1"].orEmpty().map { it.id })
        assertEquals("a1", repo.catalog.value.tracksByParent["plex:a1"].orEmpty().single().parentAlbumId)

        val restored = CatalogRepository(
            plexClient = PlexClient(http),
            database = db,
            storage = PlatformStorage(),
            httpClient = http,
            mediaSourcesRepository = media,
        )
        restored.restoreCachedCatalog()

        val restoredAlbum = restored.catalog.value.albums.single { it.id == "plex:a1" }
        assertEquals(listOf("plex:t1"), restored.catalog.value.tracksByParent["plex:a1"].orEmpty().map { it.id })
        assertEquals(listOf("plex:t1"), restored.tracksForAlbum(testSession(), restoredAlbum).map { it.id })
        assertEquals("a1", restored.tracksForAlbum(testSession(), restoredAlbum).single().parentAlbumId)
    }

    @Test
    fun albumMoodItemsFallBackToIndexedTrackMoodWhenPlexFilterReturnsEmpty() = runTest {
        val (db, d) = newInMemoryPhoebeDatabase()
        driver = d
        db.transaction {
            db.catalogQueries.upsertAlbum("plex:a1", "Album One", "Artist One", null, null, 0, null, null, null, null, null, 0)
            db.catalogQueries.upsertTrack(
                id = "plex:t1",
                title = "Angry Song",
                artist = "Artist One",
                album = "Album One",
                durationMs = 10,
                streamUrl = "https://plex.example/t1?X-Plex-Token=token",
                downloadUrl = "https://plex.example/t1?X-Plex-Token=token&download=1",
                thumbUrl = null,
                localArtworkUri = null,
                localUri = null,
                year = null,
                genre = null,
                mood = "Angry",
                style = null,
                filepath = null,
                audioCodec = null,
                bitrateKbps = null,
                dateAddedMs = null,
                rating = null,
                parentAlbumId = null,
            )
            db.catalogQueries.upsertTrackParent("plex:a1", "plex:t1", 0, null)
            db.catalogQueries.upsertCollectionValue("Albums", "Mood", "Angry", "999", null, "album.mood", 0)
        }
        val engine = MockEngine { request ->
            when (request.url.encodedPath) {
                "/library/sections/1/all",
                "/library/sections/1/albums" -> respondJson("""{ "MediaContainer": { "Metadata": [] } }""")
                else -> respond("", HttpStatusCode.NotFound)
            }
        }
        val http = testHttpClient(engine)
        val media = MediaSourcesRepository(db, PlatformStorage())
        val repo = CatalogRepository(
            plexClient = PlexClient(http),
            database = db,
            storage = PlatformStorage(),
            httpClient = http,
            mediaSourcesRepository = media,
        )

        repo.restoreCachedCatalog()
        repo.ensureCollectionItems(testSession(), CollectionEntry(CollectionTarget.Albums, CollectionFacet.Mood), "Angry")

        assertEquals(listOf("plex:a1"), repo.catalog.value.collectionTags.map { it.itemId })
    }

    @Test
    fun collectionItemLoadRefreshesStaleCachedFilterChoice() = runTest {
        val (db, d) = newInMemoryPhoebeDatabase()
        driver = d
        db.transaction {
            db.catalogQueries.upsertAlbum("plex:a1", "Album One", "Artist One", null, null, 0, null, null, null, null, null, 0)
            db.catalogQueries.upsertCollectionValue("Albums", "Mood", "Angry", "stale", "/library/sections/1/all?type=9&album.mood=stale", "album.mood", 0)
        }
        val engine = MockEngine { request ->
            when (request.url.encodedPath) {
                "/library/sections/1/album.mood" -> respondJson(
                    """
                        {
                          "MediaContainer": {
                            "Directory": [
                              { "key": "999", "title": "Angry" }
                            ]
                          }
                        }
                    """.trimIndent(),
                )
                "/library/sections/1/all" -> if (request.url.parameters["album.mood"] == "999") {
                    respondJson(
                        """
                            {
                              "MediaContainer": {
                                "Metadata": [
                                  { "ratingKey": "a1", "title": "Album One", "type": "album" }
                                ]
                              }
                            }
                        """.trimIndent(),
                    )
                } else {
                    respondJson("""{ "MediaContainer": { "Metadata": [] } }""")
                }
                "/library/sections/1/albums" -> respondJson("""{ "MediaContainer": { "Metadata": [] } }""")
                else -> respond("", HttpStatusCode.NotFound)
            }
        }
        val http = testHttpClient(engine)
        val media = MediaSourcesRepository(db, PlatformStorage())
        val repo = CatalogRepository(
            plexClient = PlexClient(http),
            database = db,
            storage = PlatformStorage(),
            httpClient = http,
            mediaSourcesRepository = media,
        )

        repo.restoreCachedCatalog()
        repo.ensureCollectionItems(testSession(), CollectionEntry(CollectionTarget.Albums, CollectionFacet.Mood), "Angry")

        assertEquals(listOf("plex:a1"), repo.catalog.value.collectionTags.map { it.itemId })
        assertEquals("999", repo.catalog.value.collectionValues.single().key)
    }

    @Test
    fun collectionValuesReloadWhenOnlyEmptyLoadMarkerWasCached() = runTest {
        val (db, d) = newInMemoryPhoebeDatabase()
        driver = d
        db.transaction {
            db.catalogQueries.upsertCollectionValueLoad("Albums", "Mood")
        }
        val engine = MockEngine { request ->
            when (request.url.encodedPath) {
                "/library/sections/1/album.mood" -> respondJson(
                    """
                        {
                          "MediaContainer": {
                            "Directory": [
                              { "key": "999", "title": "Angry" }
                            ]
                          }
                        }
                    """.trimIndent(),
                )
                else -> respond("", HttpStatusCode.NotFound)
            }
        }
        val http = testHttpClient(engine)
        val media = MediaSourcesRepository(db, PlatformStorage())
        val repo = CatalogRepository(
            plexClient = PlexClient(http),
            database = db,
            storage = PlatformStorage(),
            httpClient = http,
            mediaSourcesRepository = media,
        )

        repo.restoreCachedCatalog()
        repo.ensureCollectionValues(testSession(), CollectionEntry(CollectionTarget.Albums, CollectionFacet.Mood))

        assertEquals(listOf("Angry"), repo.catalog.value.collectionValues.map { it.value })
    }

    @Test
    fun recentAlbumWarmPersistsTracksWithoutClearingCollectionMetadata() = runTest {
        val (db, d) = newInMemoryPhoebeDatabase()
        driver = d
        val engine = MockEngine { request ->
            when (request.url.encodedPath) {
                "/library/metadata/a1/children" -> respondJson(albumTracksJson())
                else -> respond("", HttpStatusCode.NotFound)
            }
        }
        val http = testHttpClient(engine)
        val media = MediaSourcesRepository(db, PlatformStorage())
        db.transaction {
            db.catalogQueries.upsertArtist("plex:artist1", "Artist One", null, 1, 1, 0, null, null, null, null, null, 0)
            db.catalogQueries.upsertAlbum("plex:a1", "Album One", "Artist One", null, null, 0, 2_000, null, null, null, null, 0)
            db.catalogQueries.upsertCollectionValue("Albums", "Mood", "Angry", "999", null, "album.mood", 0)
            db.catalogQueries.upsertCollectionTag("Albums", "Mood", "plex:a1", "Angry")
        }
        val repo = CatalogRepository(
            plexClient = PlexClient(http),
            database = db,
            storage = PlatformStorage(),
            httpClient = http,
            mediaSourcesRepository = media,
        )

        repo.restoreCachedCatalog()
        repo.dropInMemoryCollectionMetadataForTest()
        assertTrue(repo.catalog.value.tracksByParent["plex:a1"].isNullOrEmpty())

        repo.warmRecentAlbumTracks(testSession(), cutoffMs = 1L, maxAlbums = 10)

        assertEquals(listOf("Angry"), db.catalogQueries.selectCollectionValues().executeAsList().map { it.value_ })
        assertEquals(listOf("plex:a1"), db.catalogQueries.selectCollectionTags().executeAsList().map { it.itemId })
        assertEquals(listOf("plex:t1"), db.catalogQueries.selectTracksForParent("plex:a1").executeAsList().map { it.id })
    }

    @Test
    fun collectionItemLoadStoresCanonicalPlexAlbumIds() = runTest {
        val (db, d) = newInMemoryPhoebeDatabase()
        driver = d
        db.transaction {
            db.catalogQueries.upsertAlbum("plex:a1", "Album One", "Artist One", null, null, 0, null, null, null, null, null, 0)
            db.catalogQueries.upsertCollectionValue("Albums", "Mood", "Angry", "999", null, "mood", 0)
        }
        val engine = MockEngine { request ->
            when (request.url.encodedPath) {
                "/library/sections/1/all" -> if (request.url.parameters["mood"] == "999") {
                    respondJson(
                        """
                            {
                              "MediaContainer": {
                                "Metadata": [
                                  { "ratingKey": "a1", "title": "Album One", "type": "album" }
                                ]
                              }
                            }
                        """.trimIndent(),
                    )
                } else {
                    respondJson("""{ "MediaContainer": { "Metadata": [] } }""")
                }
                "/library/sections/1/albums" -> respondJson("""{ "MediaContainer": { "Metadata": [] } }""")
                else -> respond("", HttpStatusCode.NotFound)
            }
        }
        val http = testHttpClient(engine)
        val media = MediaSourcesRepository(db, PlatformStorage())
        val repo = CatalogRepository(
            plexClient = PlexClient(http),
            database = db,
            storage = PlatformStorage(),
            httpClient = http,
            mediaSourcesRepository = media,
        )

        repo.restoreCachedCatalog()
        repo.ensureCollectionItems(testSession(), CollectionEntry(CollectionTarget.Albums, CollectionFacet.Mood), "Angry")

        assertEquals(listOf("plex:a1"), repo.catalog.value.collectionTags.map { it.itemId })
    }

    @Test
    fun collectionItemLoadRefetchesWhenCachedTagsDoNotMatchCatalogIds() = runTest {
        val (db, d) = newInMemoryPhoebeDatabase()
        driver = d
        db.transaction {
            db.catalogQueries.upsertAlbum("plex:a1", "Album One", "Artist One", null, null, 0, null, null, null, null, null, 0)
            db.catalogQueries.upsertCollectionValue("Albums", "Mood", "Angry", "999", null, "mood", 1)
            db.catalogQueries.upsertCollectionTag("Albums", "Mood", "plex:track1", "Angry")
        }
        val engine = MockEngine { request ->
            when (request.url.encodedPath) {
                "/library/sections/1/all" -> if (request.url.parameters["mood"] == "999") {
                    respondJson(
                        """
                            {
                              "MediaContainer": {
                                "Metadata": [
                                  { "ratingKey": "a1", "title": "Album One", "type": "album" }
                                ]
                              }
                            }
                        """.trimIndent(),
                    )
                } else {
                    respondJson("""{ "MediaContainer": { "Metadata": [] } }""")
                }
                "/library/sections/1/albums" -> respondJson("""{ "MediaContainer": { "Metadata": [] } }""")
                else -> respond("", HttpStatusCode.NotFound)
            }
        }
        val http = testHttpClient(engine)
        val media = MediaSourcesRepository(db, PlatformStorage())
        val repo = CatalogRepository(
            plexClient = PlexClient(http),
            database = db,
            storage = PlatformStorage(),
            httpClient = http,
            mediaSourcesRepository = media,
        )

        repo.restoreCachedCatalog()
        repo.ensureCollectionItems(testSession(), CollectionEntry(CollectionTarget.Albums, CollectionFacet.Mood), "Angry")

        assertEquals(listOf("plex:a1"), repo.catalog.value.collectionTags.map { it.itemId })
    }

    @Test
    fun addTracksToPlaylistRefetchesWhenPlaylistTracksAreNotCached() = runTest {
        val (db, d) = newInMemoryPhoebeDatabase()
        driver = d
        val engine = MockEngine { request ->
            when (request.url.encodedPath) {
                "/library/sections/1/all" -> respondJson(artistsJson())
                "/library/sections/1/albums" -> respondJson(albumsJson())
                "/playlists" -> respondJson(playlistsJson(trackCount = 2, thumb = "/playlists/p1/art"))
                "/library/metadata/a1/children" -> respondJson(albumTracksJson())
                "/playlists/p1/items" -> when (request.method.value) {
                    "PUT" -> respondJson(playlistAddResponseJson(leafCount = 3))
                    else -> respondJson(playlistTracksJson())
                }
                "/identity" -> respondJson(identityJson())
                else -> respond("", HttpStatusCode.NotFound)
            }
        }
        val media = MediaSourcesRepository(db, PlatformStorage())
        val repo = CatalogRepository(
            plexClient = PlexClient(testHttpClient(engine)),
            database = db,
            storage = PlatformStorage(),
            httpClient = testHttpClient(engine),
            mediaSourcesRepository = media,
        )
        repo.refreshAggregated(testSession())

        val playlist = repo.catalog.value.playlists.single()
        assertEquals(2, playlist.trackCount)
        assertTrue(repo.catalog.value.tracksByParent[playlist.id].isNullOrEmpty())

        val newTrack = Track(
            id = "plex:t3",
            title = "Added Song",
            artist = "Artist One",
            album = "Album One",
            durationMs = 2_000,
            streamUrl = "https://plex.example/t3?X-Plex-Token=token",
            downloadUrl = "https://plex.example/t3?X-Plex-Token=token&download=1",
        )
        repo.addTracksToPlaylist(testSession(), playlist, listOf(newTrack))

        val updated = repo.catalog.value.playlists.single { it.id == playlist.id }
        assertEquals(3, updated.trackCount)
        assertEquals(
            listOf("plex:t3", "plex:t1", "plex:t2"),
            repo.catalog.value.tracksByParent[playlist.id].orEmpty().map { it.id },
        )
    }

    @Test
    fun addTracksToPlaylistMovesSyncedPlexTrackToTop() = runTest {
        val (db, d) = newInMemoryPhoebeDatabase()
        driver = d
        var addSucceeded = false
        var moveCalled = false
        val engine = MockEngine { request ->
            when (request.url.encodedPath) {
                "/library/sections/1/all" -> respondJson(artistsJson())
                "/library/sections/1/albums" -> respondJson(albumsJson())
                "/playlists" -> respondJson(playlistsJson(trackCount = 2, thumb = "/playlists/p1/art"))
                "/library/metadata/a1/children" -> respondJson(albumTracksJson())
                "/playlists/p1/items" -> when (request.method.value) {
                    "PUT" -> {
                        addSucceeded = true
                        respondJson(playlistAddResponseJson(leafCount = 3))
                    }
                    else -> respondJson(if (addSucceeded) playlistTracksWithAddedAtEndJson() else playlistTracksJson())
                }
                "/playlists/p1/items/103/move" -> {
                    moveCalled = true
                    assertEquals(null, request.url.parameters["after"])
                    respondJson(playlistAddResponseJson(leafCount = 3))
                }
                "/identity" -> respondJson(identityJson())
                else -> respond("", HttpStatusCode.NotFound)
            }
        }
        val http = testHttpClient(engine)
        val media = MediaSourcesRepository(db, PlatformStorage())
        val repo = CatalogRepository(
            plexClient = PlexClient(http),
            database = db,
            storage = PlatformStorage(),
            httpClient = http,
            mediaSourcesRepository = media,
        )
        repo.refreshAggregated(testSession())

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

        repo.addTracksToPlaylist(testSession(), playlist, listOf(newTrack))

        assertTrue(moveCalled)
        assertEquals(
            listOf("plex:t3", "plex:t1", "plex:t2"),
            repo.catalog.value.tracksByParent[playlist.id].orEmpty().map { it.id },
        )
    }

    @Test
    fun tracksForPlaylistRefreshesIncompleteCacheBeforeReturning() = runTest {
        val (db, d) = newInMemoryPhoebeDatabase()
        driver = d
        db.transaction {
            db.catalogQueries.upsertPlaylist("plex:p1", "Playlist One", 2, "/playlists/p1/items", null, 0, null, 0)
            db.catalogQueries.upsertTrack(
                id = "plex:t1",
                title = "Cached Playlist Song",
                artist = "Artist One",
                album = "Album One",
                durationMs = 1000,
                streamUrl = "https://plex.example/t1?X-Plex-Token=token",
                downloadUrl = "https://plex.example/t1?X-Plex-Token=token&download=1",
                thumbUrl = null,
                localArtworkUri = null,
                localUri = null,
                year = null,
                genre = null,
                mood = null,
                style = null,
                filepath = null,
                audioCodec = null,
                bitrateKbps = null,
                dateAddedMs = null,
                rating = null,
                parentAlbumId = null,
            )
            db.catalogQueries.upsertTrackParent("plex:p1", "plex:t1", 0, null)
        }
        var playlistFetches = 0
        val engine = MockEngine { request ->
            when (request.url.encodedPath) {
                "/playlists/p1/items" -> {
                    playlistFetches++
                    respondJson(playlistTracksJson())
                }
                else -> respond("", HttpStatusCode.NotFound)
            }
        }
        val http = testHttpClient(engine)
        val media = MediaSourcesRepository(db, PlatformStorage())
        val repo = CatalogRepository(
            plexClient = PlexClient(http),
            database = db,
            storage = PlatformStorage(),
            httpClient = http,
            mediaSourcesRepository = media,
        )

        repo.restoreCachedCatalog()
        val playlist = repo.catalog.value.playlists.single()

        assertEquals(listOf("plex:t1"), repo.catalog.value.tracksByParent[playlist.id].orEmpty().map { it.id })

        val tracks = repo.tracksForPlaylist(testSession(), playlist)

        assertEquals(listOf("plex:t1", "plex:t2"), tracks.map { it.id })
        assertEquals(listOf("plex:t1", "plex:t2"), repo.catalog.value.tracksByParent[playlist.id].orEmpty().map { it.id })
        assertEquals(1, playlistFetches)
    }

    @Test
    fun publishingPlayablePlexTrackReplacesMetadataOnlyPlaceholder() = runTest {
        val (db, d) = newInMemoryPhoebeDatabase()
        driver = d
        db.transaction {
            db.catalogQueries.upsertArtist("plex:artist-1", "Artist One", null, 1, 0, 0, null, null, null, null, null, 0)
            db.catalogQueries.upsertAlbum("plex:a1", "Album One", "Artist One", null, null, 0, null, null, null, null, null, 0)
        }
        val engine = MockEngine { respond("", HttpStatusCode.NotFound) }
        val http = testHttpClient(engine)
        val media = MediaSourcesRepository(db, PlatformStorage())
        val repo = CatalogRepository(
            plexClient = PlexClient(http),
            database = db,
            storage = PlatformStorage(),
            httpClient = http,
            mediaSourcesRepository = media,
        )
        repo.restoreCachedCatalog()

        repo.publishPlexTracks(
            listOf(
                Track(
                    id = "plex:t1",
                    title = "History Only",
                    artist = "Artist One",
                    album = "Album One",
                    durationMs = 0,
                    streamUrl = "",
                    downloadUrl = "",
                    parentAlbumId = "plex:a1",
                ),
            ),
        )
        repo.publishPlexTracks(
            listOf(
                Track(
                    id = "plex:t1",
                    title = "Playable Song",
                    artist = "Artist One",
                    album = "Album One",
                    durationMs = 1_000,
                    streamUrl = "https://plex.example/t1?X-Plex-Token=token",
                    downloadUrl = "https://plex.example/t1?X-Plex-Token=token&download=1",
                    parentAlbumId = "plex:a1",
                ),
            ),
        )

        val track = repo.catalog.value.tracksByParent["plex:a1"].orEmpty().single()
        assertEquals("Playable Song", track.title)
        assertEquals("https://plex.example/t1?X-Plex-Token=token", track.streamUrl)
        assertEquals(
            "https://plex.example/t1?X-Plex-Token=token",
            db.catalogQueries.selectTrackById("plex:t1").executeAsOne().streamUrl,
        )
    }

    @Test
    fun publishingPlexTracksDoesNotClearCachedCatalogShellWhenMemoryShellIsEmpty() = runTest {
        val (db, d) = newInMemoryPhoebeDatabase()
        driver = d
        db.transaction {
            db.catalogQueries.upsertArtist("plex:artist-1", "Artist One", null, 1, 0, 0, null, null, null, null, null, 0)
            db.catalogQueries.upsertAlbum("plex:a1", "Album One", "Artist One", null, null, 0, null, null, null, null, null, 0)
            db.catalogQueries.upsertPlaylist("plex:p1", "Playlist One", 1, "/playlists/p1/items", null, 0, null, 0)
        }
        val engine = MockEngine { respond("", HttpStatusCode.NotFound) }
        val http = testHttpClient(engine)
        val media = MediaSourcesRepository(db, PlatformStorage())
        val repo = CatalogRepository(
            plexClient = PlexClient(http),
            database = db,
            storage = PlatformStorage(),
            httpClient = http,
            mediaSourcesRepository = media,
        )

        repo.publishPlexTracks(
            listOf(
                Track(
                    id = "plex:t1",
                    title = "History Warmed Song",
                    artist = "Artist One",
                    album = "Album One",
                    durationMs = 1_000,
                    streamUrl = "https://plex.example/t1?X-Plex-Token=token",
                    downloadUrl = "https://plex.example/t1?X-Plex-Token=token&download=1",
                    parentAlbumId = "plex:a1",
                ),
            ),
        )

        assertEquals(listOf("plex:artist-1"), db.catalogQueries.selectArtists().executeAsList().map { it.id })
        assertEquals(listOf("plex:a1"), db.catalogQueries.selectAlbums().executeAsList().map { it.id })
        assertEquals(listOf("plex:p1"), db.catalogQueries.selectPlaylists().executeAsList().map { it.id })
        assertEquals("History Warmed Song", db.catalogQueries.selectTrackById("plex:t1").executeAsOne().title)
    }

    @Test
    fun resolvingTracksFromDatabaseDoesNotClearCachedCatalogShellWhenMemoryShellIsEmpty() = runTest {
        val (db, d) = newInMemoryPhoebeDatabase()
        driver = d
        db.transaction {
            db.catalogQueries.upsertArtist("plex:artist-1", "Artist One", null, 1, 0, 0, null, null, null, null, null, 0)
            db.catalogQueries.upsertAlbum("plex:a1", "Album One", "Artist One", null, null, 0, null, null, null, null, null, 0)
            db.catalogQueries.upsertPlaylist("plex:p1", "Playlist One", 1, "/playlists/p1/items", null, 0, null, 0)
            db.catalogQueries.upsertTrack(
                id = "plex:t1",
                title = "Resolved Song",
                artist = "Artist One",
                album = "Album One",
                durationMs = 1_000,
                streamUrl = "https://plex.example/t1?X-Plex-Token=token",
                downloadUrl = "https://plex.example/t1?X-Plex-Token=token&download=1",
                thumbUrl = null,
                localArtworkUri = null,
                localUri = null,
                year = null,
                genre = null,
                mood = null,
                style = null,
                filepath = null,
                audioCodec = null,
                bitrateKbps = null,
                dateAddedMs = null,
                rating = null,
                parentAlbumId = "plex:a1",
            )
            db.catalogQueries.upsertTrackParent("plex:a1", "plex:t1", 0, null)
        }
        val engine = MockEngine { respond("", HttpStatusCode.NotFound) }
        val http = testHttpClient(engine)
        val media = MediaSourcesRepository(db, PlatformStorage())
        val repo = CatalogRepository(
            plexClient = PlexClient(http),
            database = db,
            storage = PlatformStorage(),
            httpClient = http,
            mediaSourcesRepository = media,
        )

        val resolved = repo.resolveTracksByIds(listOf("plex:t1"))
        repo.awaitDatabaseIdle()

        assertEquals("Resolved Song", resolved["plex:t1"]?.title)
        assertEquals(listOf("plex:t1"), repo.catalog.value.tracksByParent["plex:a1"].orEmpty().map { it.id })
        assertEquals(listOf("plex:artist-1"), db.catalogQueries.selectArtists().executeAsList().map { it.id })
        assertEquals(listOf("plex:a1"), db.catalogQueries.selectAlbums().executeAsList().map { it.id })
        assertEquals(listOf("plex:p1"), db.catalogQueries.selectPlaylists().executeAsList().map { it.id })
    }

    @Test
    fun restoreIgnoresLoneSyntheticLikedSongsPlaylist() = runTest {
        val (db, d) = newInMemoryPhoebeDatabase()
        driver = d
        db.transaction {
            db.catalogQueries.upsertPlaylist("plex:liked-songs", "Liked Songs", 0, null, null, 0, null, 0)
        }
        val engine = MockEngine { respond("", HttpStatusCode.NotFound) }
        val http = testHttpClient(engine)
        val media = MediaSourcesRepository(db, PlatformStorage())
        val repo = CatalogRepository(
            plexClient = PlexClient(http),
            database = db,
            storage = PlatformStorage(),
            httpClient = http,
            mediaSourcesRepository = media,
        )

        repo.restoreCachedCatalog()

        assertTrue(repo.catalog.value.playlists.isEmpty())
        assertTrue(repo.catalog.value.artists.isEmpty())
        assertTrue(repo.catalog.value.albums.isEmpty())
    }

    @Test
    fun ensureLikedSongsOnEmptyCatalogDoesNotPersistFakeCatalogShell() = runTest {
        val (db, d) = newInMemoryPhoebeDatabase()
        driver = d
        val engine = MockEngine { respond("", HttpStatusCode.NotFound) }
        val http = testHttpClient(engine)
        val media = MediaSourcesRepository(db, PlatformStorage())
        val repo = CatalogRepository(
            plexClient = PlexClient(http),
            database = db,
            storage = PlatformStorage(),
            httpClient = http,
            mediaSourcesRepository = media,
        )

        val playlist = repo.ensureLocalLikedSongsPlaylist(testSession())
        repo.awaitDatabaseIdle()

        assertEquals("plex:liked-songs", playlist.id)
        assertEquals(listOf("plex:liked-songs"), repo.catalog.value.playlists.map { it.id })
        assertTrue(db.catalogQueries.selectPlaylists().executeAsList().isEmpty())
    }

    @Test
    fun togglingLikedTrackDoesNotClearCachedCatalogShellWhenMemoryShellIsEmpty() = runTest {
        val (db, d) = newInMemoryPhoebeDatabase()
        driver = d
        db.transaction {
            db.catalogQueries.upsertArtist("plex:artist-1", "Artist One", null, 1, 0, 0, null, null, null, null, null, 0)
            db.catalogQueries.upsertAlbum("plex:a1", "Album One", "Artist One", null, null, 0, null, null, null, null, null, 0)
            db.catalogQueries.upsertPlaylist("plex:p1", "Playlist One", 1, "/playlists/p1/items", null, 0, null, 0)
        }
        val engine = MockEngine { respond("", HttpStatusCode.NotFound) }
        val http = testHttpClient(engine)
        val media = MediaSourcesRepository(db, PlatformStorage())
        val repo = CatalogRepository(
            plexClient = PlexClient(http),
            database = db,
            storage = PlatformStorage(),
            httpClient = http,
            mediaSourcesRepository = media,
        )
        val track = Track(
            id = "plex:t1",
            title = "Liked Song",
            artist = "Artist One",
            album = "Album One",
            durationMs = 1_000,
            streamUrl = "https://plex.example/t1?X-Plex-Token=token",
            downloadUrl = "https://plex.example/t1?X-Plex-Token=token&download=1",
            parentAlbumId = "plex:a1",
        )

        assertTrue(repo.toggleLikedTrackLocally(testSession(), track))
        repo.awaitDatabaseIdle()

        assertEquals(listOf("plex:artist-1"), db.catalogQueries.selectArtists().executeAsList().map { it.id })
        assertEquals(listOf("plex:a1"), db.catalogQueries.selectAlbums().executeAsList().map { it.id })
        assertEquals(
            setOf("plex:liked-songs", "plex:p1"),
            db.catalogQueries.selectPlaylists().executeAsList().map { it.id }.toSet(),
        )
        assertEquals("Liked Song", db.catalogQueries.selectTrackById("plex:t1").executeAsOne().title)
    }

    @Test
    fun refreshRefetchesPlaylistWhenPlexReportsFewerTracksThanCache() = runTest {
        val (db, d) = newInMemoryPhoebeDatabase()
        driver = d
        var playlistTrackCount = 2
        val engine = MockEngine { request ->
            when (request.url.encodedPath) {
                "/library/sections/1/all" -> if (request.url.parameters["type"] == "10") {
                    respondJson(trackPageJson())
                } else {
                    respondJson(artistsJson())
                }
                "/library/sections/1/albums" -> respondJson(albumsJson())
                "/playlists" -> respondJson(playlistsJson(trackCount = playlistTrackCount))
                "/playlists/p1/items" -> respondJson(
                    if (playlistTrackCount == 1) playlistTracksAfterServerDeletionJson() else playlistTracksJson(),
                )
                else -> respond("", HttpStatusCode.NotFound)
            }
        }
        val http = testHttpClient(engine)
        val media = MediaSourcesRepository(db, PlatformStorage())
        val repo = CatalogRepository(
            plexClient = PlexClient(http),
            database = db,
            storage = PlatformStorage(),
            httpClient = http,
            mediaSourcesRepository = media,
        )

        repo.refreshAggregated(testSession())
        val playlist = repo.catalog.value.playlists.single()
        assertEquals(listOf("plex:t1", "plex:t2"), repo.tracksForPlaylist(testSession(), playlist).map { it.id })

        playlistTrackCount = 1
        repo.refreshAggregated(testSession())

        val refreshedPlaylist = repo.catalog.value.playlists.single { it.id == playlist.id }
        assertEquals(1, refreshedPlaylist.trackCount)
        assertEquals(listOf("plex:t1"), repo.catalog.value.tracksByParent[playlist.id].orEmpty().map { it.id })
    }

    @Test
    fun jellyfinQuickRefreshPublishesFirstPageWithoutWaitingForAllPlaylists() = runTest {
        val (db, d) = newInMemoryPhoebeDatabase()
        driver = d
        val requestKinds = java.util.Collections.synchronizedList(mutableListOf<String>())
        val playlistsStarted = CompletableDeferred<Unit>()
        val releasePlaylists = CompletableDeferred<Unit>()
        val engine = MockEngine { request ->
            when (request.url.encodedPath) {
                "/Artists/AlbumArtists" -> {
                    requestKinds += "artists"
                    respondJson(jellyfinArtistPageJson(total = 600))
                }
                "/Items" -> when {
                    request.url.parameters["includeItemTypes"] == "MusicAlbum" -> {
                        requestKinds += "albums"
                        respondJson(jellyfinAlbumPageJson(total = 600))
                    }
                    request.url.parameters["includeItemTypes"] == "Audio" -> {
                        requestKinds += "tracks"
                        respondJson(jellyfinTrackPageJson(total = 600))
                    }
                    request.url.parameters["includeItemTypes"] == "Playlist" -> {
                        requestKinds += "playlists"
                        playlistsStarted.complete(Unit)
                        releasePlaylists.await()
                        respondJson(jellyfinPlaylistsJson())
                    }
                    request.url.parameters["isFavorite"] == "true" -> respondJson("""{ "TotalRecordCount": 0, "Items": [] }""")
                    else -> respond("", HttpStatusCode.NotFound)
                }
                else -> respond("", HttpStatusCode.NotFound)
            }
        }
        val http = testHttpClient(engine)
        val media = MediaSourcesRepository(db, PlatformStorage())
        val repo = jellyfinCatalogRepository(db, media, http)

        val refresh = async { repo.refreshAggregated(jellyfinSession()) }
        playlistsStarted.await()
        waitForCatalogState {
            repo.catalog.value.remotePageInfo.loadedTrackPages == setOf(0) &&
                repo.catalog.value.tracksByParent["jellyfin:album-1"].orEmpty().map { it.id } == listOf("jellyfin:track-1") &&
                repo.catalogSyncState.value.phase == CatalogSyncPhase.Idle &&
                !repo.catalogRefreshing.value
        }

        assertTrue("artists" in requestKinds)
        assertTrue("albums" in requestKinds)
        assertTrue("tracks" in requestKinds)
        assertTrue("playlists" in requestKinds)
        assertEquals(listOf("jellyfin:artist-1"), repo.catalog.value.artists.map { it.id })
        assertEquals(listOf("jellyfin:album-1"), repo.catalog.value.albums.map { it.id })
        assertEquals(listOf("jellyfin:track-1"), repo.catalog.value.tracksByParent["jellyfin:album-1"].orEmpty().map { it.id })
        assertFalse(repo.catalog.value.playlists.any { it.id == "jellyfin:playlist-1" })
        assertEquals(600, repo.catalog.value.remotePageInfo.trackTotal)
        assertEquals(setOf(0), repo.catalog.value.remotePageInfo.loadedTrackPages)
        assertEquals(CatalogSyncPhase.Idle, repo.catalogSyncState.value.phase)
        assertFalse(repo.catalogRefreshing.value)

        releasePlaylists.complete(Unit)
        refresh.await()
        assertEquals(listOf("jellyfin:album-1"), repo.catalog.value.albums.map { it.id })
        assertEquals(listOf("jellyfin:track-1"), repo.catalog.value.tracksByParent["jellyfin:album-1"].orEmpty().map { it.id })
    }

    @Test
    fun jellyfinFullRefreshPublishesMetadataBeforeTrackIndexingFinishes() = runTest {
        val (db, d) = newInMemoryPhoebeDatabase()
        driver = d
        val tracksStarted = CompletableDeferred<Unit>()
        val releaseTracks = CompletableDeferred<Unit>()
        val engine = MockEngine { request ->
            when (request.url.encodedPath) {
                "/Artists/AlbumArtists" -> respondJson(jellyfinArtistPageJson())
                "/Items" -> when {
                    request.url.parameters["includeItemTypes"] == "MusicAlbum" -> respondJson(jellyfinAlbumCatalogJson())
                    request.url.parameters["includeItemTypes"] == "Audio" -> {
                        tracksStarted.complete(Unit)
                        val start = request.url.parameters["startIndex"]?.toIntOrNull() ?: 0
                        releaseTracks.await()
                        respondJson(jellyfinMultiTrackPageJson(start = start))
                    }
                    request.url.parameters["includeItemTypes"] == "Playlist" -> respondJson(jellyfinPlaylistsJson())
                    request.url.parameters["isFavorite"] == "true" -> respondJson("""{ "TotalRecordCount": 0, "Items": [] }""")
                    else -> respond("", HttpStatusCode.NotFound)
                }
                else -> respond("", HttpStatusCode.NotFound)
            }
        }
        val http = testHttpClient(engine)
        val media = MediaSourcesRepository(db, PlatformStorage())
        val repo = jellyfinCatalogRepository(db, media, http)

        val refresh = async { repo.refreshAggregated(jellyfinSession(JellyfinSyncMode.Full)) }
        tracksStarted.await()
        waitForCatalogState {
            repo.catalog.value.albums.map { it.id } == listOf("jellyfin:album-1")
        }

        assertFalse(repo.catalogSyncState.value.showGlobalProgress)
        assertEquals(listOf("jellyfin:album-1"), repo.catalog.value.albums.map { it.id })
        assertTrue(repo.catalog.value.tracksByParent.isEmpty())

        releaseTracks.complete(Unit)
        refresh.await()

        assertEquals(listOf("jellyfin:track-1", "jellyfin:track-2"), repo.catalog.value.tracksByParent["jellyfin:album-1"].orEmpty().map { it.id })
    }

    @Test
    fun jellyfinQuickPartialRefreshDoesNotDeleteCachedTracksFromUnloadedPages() = runTest {
        val (db, d) = newInMemoryPhoebeDatabase()
        driver = d
        db.transaction {
            db.catalogQueries.upsertArtist("jellyfin:artist-1", "Artist One", null, 1, 0, 0, null, null, null, null, null, 0)
            db.catalogQueries.upsertAlbum("jellyfin:album-1", "Album One", "Artist One", null, null, 0, null, null, null, null, null, 0)
            db.catalogQueries.upsertTrack(
                id = "jellyfin:track-old",
                title = "Cached Song",
                artist = "Artist One",
                album = "Album One",
                durationMs = 10,
                streamUrl = "https://jellyfin.example/track-old",
                downloadUrl = "https://jellyfin.example/track-old",
                thumbUrl = null,
                localArtworkUri = null,
                localUri = null,
                year = null,
                genre = null,
                mood = null,
                style = null,
                filepath = null,
                audioCodec = null,
                bitrateKbps = null,
                dateAddedMs = null,
                rating = null,
                parentAlbumId = "album-1",
            )
            db.catalogQueries.upsertTrackParent("jellyfin:album-1", "jellyfin:track-old", 0, null)
        }
        val engine = MockEngine { request ->
            when (request.url.encodedPath) {
                "/Artists/AlbumArtists" -> respondJson(jellyfinArtistPageJson(total = 250))
                "/Items" -> when {
                    request.url.parameters["includeItemTypes"] == "MusicAlbum" -> respondJson(jellyfinAlbumPageJson(total = 250))
                    request.url.parameters["includeItemTypes"] == "Audio" -> respondJson(jellyfinTrackPageJson(total = 250))
                    request.url.parameters["includeItemTypes"] == "Playlist" -> respondJson(jellyfinPlaylistsJson())
                    request.url.parameters["isFavorite"] == "true" -> respondJson("""{ "TotalRecordCount": 0, "Items": [] }""")
                    else -> respond("", HttpStatusCode.NotFound)
                }
                else -> respond("", HttpStatusCode.NotFound)
            }
        }
        val http = testHttpClient(engine)
        val media = MediaSourcesRepository(db, PlatformStorage())
        val repo = jellyfinCatalogRepository(db, media, http)
        repo.restoreCachedCatalog()

        repo.refreshAggregated(jellyfinSession())

        val albumTracks = repo.catalog.value.tracksByParent["jellyfin:album-1"].orEmpty().map { it.id }
        assertTrue("jellyfin:track-old" in albumTracks)
        assertTrue("jellyfin:track-1" in albumTracks)
        assertTrue(db.catalogQueries.selectAllTrackIds().executeAsList().contains("jellyfin:track-old"))
    }

    @Test
    fun backgroundPlaylistWarmDoesNotLeaveSyncStateStuck() = runTest {
        val (db, d) = newInMemoryPhoebeDatabase()
        driver = d
        val engine = MockEngine { request ->
            when (request.url.encodedPath) {
                "/library/sections/1/all" -> if (request.url.parameters["type"] == "10") {
                    respondJson(trackPageJson())
                } else {
                    respondJson(artistsJson())
                }
                "/library/sections/1/albums" -> respondJson(albumsJson())
                "/playlists" -> respondJson(playlistsJson(trackCount = 2))
                "/playlists/p1/items" -> respondJson(playlistTracksJson())
                else -> respond("", HttpStatusCode.NotFound)
            }
        }
        val http = testHttpClient(engine)
        val media = MediaSourcesRepository(db, PlatformStorage())
        val repo = CatalogRepository(
            plexClient = PlexClient(http),
            database = db,
            storage = PlatformStorage(),
            httpClient = http,
            mediaSourcesRepository = media,
        )

        repo.refreshAggregated(testSession())
        assertEquals(CatalogSyncPhase.Complete, repo.catalogSyncState.value.phase)

        repo.warmPlaylistTracks(testSession())

        assertEquals(CatalogSyncPhase.Complete, repo.catalogSyncState.value.phase)
        assertFalse(repo.catalogSyncState.value.isRefreshingPlaylists)
    }

    private fun MockRequestHandleScope.respondJson(content: String) = respond(
        content = content,
        status = HttpStatusCode.OK,
        headers = headersOf(HttpHeaders.ContentType, "application/json"),
    )

    @OptIn(ExperimentalCoroutinesApi::class)
    private fun TestScope.waitForCatalogState(condition: () -> Boolean) {
        val deadline = System.nanoTime() + 2_000_000_000L
        while (!condition()) {
            runCurrent()
            if (System.nanoTime() >= deadline) {
                assertTrue(condition())
            }
            Thread.sleep(1)
        }
    }

    @Suppress("UNCHECKED_CAST")
    private fun CatalogRepository.dropInMemoryCollectionMetadataForTest() {
        val field = CatalogRepository::class.java.getDeclaredField("mutableCatalog")
        field.isAccessible = true
        val state = field.get(this) as MutableStateFlow<CatalogSnapshot>
        state.value = state.value.copy(
            collectionValues = emptyList(),
            collectionValueLoads = emptyList(),
            collectionTags = emptyList(),
        )
    }

    private fun testSession(
        server: PlexServer = PlexServer("server", "Plex", "https://plex.example:32400", owned = true),
    ): PlexSession = PlexSession(
        token = "token",
        selectedServer = server,
        selectedLibrary = MusicLibrary("1", "Music"),
    )

    private fun jellyfinCatalogRepository(
        db: com.phoebe.app.db.PhoebeDatabase,
        media: MediaSourcesRepository,
        http: io.ktor.client.HttpClient,
    ): CatalogRepository = CatalogRepository(
        plexClient = PlexClient(http),
        jellyfinClient = JellyfinClient(http),
        database = db,
        storage = PlatformStorage(),
        httpClient = http,
        mediaSourcesRepository = media,
    )

    private fun jellyfinSession(
        syncMode: JellyfinSyncMode = JellyfinSyncMode.Quick,
    ): PlexSession = PlexSession(
        token = "token",
        userId = "user-1",
        providerType = MediaProviderType.Jellyfin,
        selectedServer = PlexServer("jellyfin:test", "Jellyfin", "https://jellyfin.example", owned = true),
        selectedLibrary = MusicLibrary("music", "Music"),
        jellyfinSyncMode = syncMode,
    )

    private fun jellyfinArtistPageJson(total: Int = 1): String = """
        {
          "Items": [
            { "Id": "artist-1", "Type": "MusicArtist", "Name": "Artist One" }
          ],
          "TotalRecordCount": $total
        }
    """.trimIndent()

    private fun jellyfinAlbumPageJson(total: Int = 1): String = """
        {
          "Items": [
            { "Id": "album-1", "Type": "MusicAlbum", "Name": "Album One", "AlbumArtist": "Artist One" }
          ],
          "TotalRecordCount": $total
        }
    """.trimIndent()

    private fun jellyfinAlbumCatalogJson(): String = """
        {
          "Items": [
            { "Id": "album-1", "Type": "MusicAlbum", "Name": "Album One", "AlbumArtist": "Artist One" }
          ],
          "TotalRecordCount": 1
        }
    """.trimIndent()

    private fun jellyfinTrackPageJson(total: Int = 1): String = """
        {
          "Items": [
            { "Id": "track-1", "Type": "Audio", "Name": "Fresh Song", "Album": "Album One", "AlbumId": "album-1", "Artists": ["Artist One"], "RunTimeTicks": 10000000 }
          ],
          "TotalRecordCount": $total
        }
    """.trimIndent()

    private fun jellyfinMultiTrackPageJson(start: Int): String {
        val items = when (start) {
            0 -> """{ "Id": "track-1", "Type": "Audio", "Name": "Song One", "Album": "Album One", "AlbumId": "album-1", "Artists": ["Artist One"], "RunTimeTicks": 10000000 }"""
            JellyfinClient.JellyfinPageSize -> """{ "Id": "track-2", "Type": "Audio", "Name": "Song Two", "Album": "Album One", "AlbumId": "album-1", "Artists": ["Artist One"], "RunTimeTicks": 10000000 }"""
            else -> ""
        }
        return """{ "Items": [ $items ], "TotalRecordCount": ${JellyfinClient.JellyfinPageSize + 1} }"""
    }

    private fun jellyfinPlaylistsJson(): String = """
        {
          "Items": [
            { "Id": "playlist-1", "Type": "Playlist", "Name": "Road Mix" }
          ],
          "TotalRecordCount": 1
        }
    """.trimIndent()

    private fun artistsJson(): String = """
        {
          "MediaContainer": {
            "Metadata": [
              { "ratingKey": "artist1", "type": "artist", "title": "Artist One", "leafCount": 1 }
            ]
          }
        }
    """.trimIndent()

    private fun albumsJson(): String = """
        {
          "MediaContainer": {
            "Metadata": [
              { "ratingKey": "a1", "title": "Album One", "parentTitle": "Artist One", "librarySectionID": 1 }
            ]
          }
        }
    """.trimIndent()

    private fun favoriteArtistsJson(): String = """
        {
          "MediaContainer": {
            "Metadata": [
              {
                "ratingKey": "artist1",
                "type": "artist",
                "title": "Artist One",
                "leafCount": 1,
                "Collection": [ { "tag": "Favorite Artists" } ]
              }
            ]
          }
        }
    """.trimIndent()

    private fun favoriteAlbumsJson(): String = """
        {
          "MediaContainer": {
            "Metadata": [
              {
                "ratingKey": "a1",
                "title": "Album One",
                "parentTitle": "Artist One",
                "librarySectionID": 1,
                "Collection": [ { "tag": "Favorite Albums" } ]
              }
            ]
          }
        }
    """.trimIndent()

    private fun playlistsJson(trackCount: Int, thumb: String? = null): String {
        val thumbJson = thumb?.let { """, "thumb": "$it"""" }.orEmpty()
        return """
        {
          "MediaContainer": {
            "Metadata": [
              { "ratingKey": "p1", "title": "Playlist One", "leafCount": $trackCount, "key": "/playlists/p1/items"$thumbJson }
            ]
          }
        }
    """.trimIndent()
    }

    private fun albumTracksJson(): String = """
        {
          "MediaContainer": {
            "Metadata": [
              {
                "ratingKey": "t1",
                "title": "Fresh Song",
                "grandparentTitle": "Artist One",
                "parentTitle": "Album One",
                "duration": 1000,
                "Media": [
                  { "Part": [ { "key": "/library/parts/t1/file.mp3", "file": "file.mp3" } ] }
                ]
              }
            ]
          }
        }
    """.trimIndent()

    private fun trackPageJson(): String = """
        {
          "MediaContainer": {
            "size": 1,
            "offset": 0,
            "totalSize": 1,
            "Metadata": [
              {
                "ratingKey": "t1",
                "parentRatingKey": "a1",
                "title": "Fresh Song",
                "grandparentTitle": "Artist One",
                "parentTitle": "Album One",
                "parentYear": 1995,
                "duration": 1000,
                "addedAt": 1700000200,
                "Media": [
                  { "Part": [ { "key": "/library/parts/t1/file.mp3", "file": "file.mp3" } ] }
                ]
              }
            ]
          }
        }
    """.trimIndent()

    private fun playlistTracksJson(): String = """
        {
          "MediaContainer": {
            "Metadata": [
              {
                "ratingKey": "t1",
                "title": "Playlist Song One",
                "grandparentTitle": "Artist One",
                "parentTitle": "Album One",
                "duration": 1000,
                "Media": [
                  { "Part": [ { "key": "/library/parts/t1/file.mp3", "file": "one.mp3" } ] }
                ]
              },
              {
                "ratingKey": "t2",
                "title": "Playlist Song Two",
                "grandparentTitle": "Artist One",
                "parentTitle": "Album One",
                "duration": 2000,
                "Media": [
                  { "Part": [ { "key": "/library/parts/t2/file.mp3", "file": "two.mp3" } ] }
                ]
              }
            ]
          }
        }
    """.trimIndent()

    private fun playlistTracksAfterServerDeletionJson(): String = """
        {
          "MediaContainer": {
            "Metadata": [
              {
                "ratingKey": "t1",
                "title": "Playlist Song One",
                "grandparentTitle": "Artist One",
                "parentTitle": "Album One",
                "duration": 1000,
                "Media": [
                  { "Part": [ { "key": "/library/parts/t1/file.mp3", "file": "one.mp3" } ] }
                ]
              }
            ]
          }
        }
    """.trimIndent()

    private fun playlistTracksWithAddedAtEndJson(): String = """
        {
          "MediaContainer": {
            "Metadata": [
              {
                "ratingKey": "t1",
                "playlistItemID": 101,
                "title": "Playlist Song One",
                "grandparentTitle": "Artist One",
                "parentTitle": "Album One",
                "duration": 1000,
                "Media": [
                  { "Part": [ { "key": "/library/parts/t1/file.mp3", "file": "one.mp3" } ] }
                ]
              },
              {
                "ratingKey": "t2",
                "playlistItemID": 102,
                "title": "Playlist Song Two",
                "grandparentTitle": "Artist One",
                "parentTitle": "Album One",
                "duration": 2000,
                "Media": [
                  { "Part": [ { "key": "/library/parts/t2/file.mp3", "file": "two.mp3" } ] }
                ]
              },
              {
                "ratingKey": "t3",
                "playlistItemID": 103,
                "title": "Added Song",
                "grandparentTitle": "Artist One",
                "parentTitle": "Album One",
                "duration": 2000,
                "Media": [
                  { "Part": [ { "key": "/library/parts/t3/file.mp3", "file": "three.mp3" } ] }
                ]
              }
            ]
          }
        }
    """.trimIndent()

    private fun identityJson(): String = """
        {
          "MediaContainer": {
            "machineIdentifier": "server"
          }
        }
    """.trimIndent()

    private fun playlistAddResponseJson(leafCount: Int): String = """
        {
          "MediaContainer": {
            "leafCountAdded": 1,
            "Metadata": [
              { "ratingKey": "p1", "title": "Playlist One", "leafCount": $leafCount, "key": "/playlists/p1/items" }
            ]
          }
        }
    """.trimIndent()
}
