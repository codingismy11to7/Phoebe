package com.phoebe.app

import app.cash.sqldelight.db.SqlDriver
import com.phoebe.app.data.CatalogRepository
import com.phoebe.app.data.MediaSourcesRepository
import com.phoebe.app.data.PlexClient
import com.phoebe.app.domain.isLocalPlaylist
import com.phoebe.app.platform.PlatformStorage
import com.phoebe.app.playlists.PlaylistExportFormat
import com.phoebe.app.playlists.PlaylistExporter
import com.phoebe.app.testing.minimalMp3Bytes
import com.phoebe.app.testing.newInMemoryPhoebeDatabase
import com.phoebe.app.testing.testHttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpStatusCode
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class LocalPlaylistEndToEndDesktopTest {
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
    fun createAddPersistAndExportLocalPlaylist() = runTest {
        val music = temp.newFolder("music")
        File(music, "alpha.mp3").writeBytes(minimalMp3Bytes())
        File(music, "beta.mp3").writeBytes(minimalMp3Bytes())

        val storageRoot = File(System.getProperty("phoebe.storage.root"))
        val (db, sqlDriver) = newInMemoryPhoebeDatabase()
        driver = sqlDriver
        val http = testHttpClient(MockEngine { respond("", HttpStatusCode.NotFound) })
        val storage = PlatformStorage()
        val mediaSources = MediaSourcesRepository(db, storage)
        val catalog = CatalogRepository(
            plexClient = PlexClient(http),
            database = db,
            storage = storage,
            httpClient = http,
            mediaSourcesRepository = mediaSources,
        )

        mediaSources.addLocalFolder(music.toURI().toString(), "Playlist MP3s")
        catalog.refreshAggregated(session = null)
        val tracks = catalog.catalog.value.tracksByParent.values.flatten().sortedBy { it.title }
        val alpha = tracks.first { it.title == "alpha" }

        val created = catalog.createLocalPlaylist("Road Mix", listOf(alpha))
        assertNotNull(created)
        assertTrue(created.isLocalPlaylist())

        val beta = tracks.first { it.title == "beta" }
        catalog.addTracksToPlaylist(null, created, listOf(beta))

        catalog.refreshAggregated(session = null)
        val playlist = catalog.catalog.value.playlists.single { it.title == "Road Mix" }
        assertEquals(2, playlist.trackCount)
        val playlistTracks = catalog.tracksForPlaylist(null, playlist)
        assertEquals(listOf("alpha", "beta"), playlistTracks.map { it.title })

        val m3u8 = PlaylistExporter.export(playlistTracks, PlaylistExportFormat.M3U8)
        val m3u8File = storageRoot.resolve("exports/Road Mix.m3u8")
        m3u8File.parentFile?.mkdirs()
        m3u8File.writeText(m3u8)
        assertTrue(m3u8File.readText().startsWith("#EXTM3U"))
        assertTrue(m3u8File.readText().contains("alpha.mp3"))

        val textFile = storageRoot.resolve("exports/Road Mix.txt")
        textFile.writeText(PlaylistExporter.export(playlistTracks, PlaylistExportFormat.Text))
        assertEquals(2, textFile.readText().lines().size)

        val csvFile = storageRoot.resolve("exports/Road Mix.csv")
        csvFile.writeText(PlaylistExporter.export(playlistTracks, PlaylistExportFormat.Csv))
        assertTrue(csvFile.readText().startsWith("title,artist,album,duration_ms,path"))
        assertTrue(csvFile.readText().contains("alpha"))
    }
}
