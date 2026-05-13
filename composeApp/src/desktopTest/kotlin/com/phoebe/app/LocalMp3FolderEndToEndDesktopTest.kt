package com.phoebe.app

import app.cash.sqldelight.db.SqlDriver
import com.phoebe.app.data.CatalogRepository
import com.phoebe.app.data.MediaSourcesRepository
import com.phoebe.app.data.PlexClient
import com.phoebe.app.platform.PlatformStorage
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
import kotlin.test.assertTrue

class LocalMp3FolderEndToEndDesktopTest {
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
    fun addingFolderWithMp3FilesRefreshesLocalCatalog() = runTest {
        val music = temp.newFolder("music")
        File(music, "alpha.mp3").writeMinimalMp3Bytes()
        File(music, "beta.mp3").writeMinimalMp3Bytes()
        File(music, "notes.txt").writeText("not audio")

        val (db, sqlDriver) = newInMemoryPhoebeDatabase()
        driver = sqlDriver
        val http = testHttpClient(MockEngine { respond("", HttpStatusCode.NotFound) })
        val mediaSources = MediaSourcesRepository(db, PlatformStorage())
        val catalog = CatalogRepository(
            plexClient = PlexClient(http),
            database = db,
            storage = PlatformStorage(),
            httpClient = http,
            mediaSourcesRepository = mediaSources,
        )

        mediaSources.addLocalFolder(music.toURI().toString(), "Test MP3s")
        catalog.refreshAggregated(session = null)

        val tracks = catalog.catalog.value.tracksByParent.values.flatten()
        assertEquals(listOf("alpha", "beta"), tracks.map { it.title }.sorted())
        assertTrue(tracks.all { it.localUri?.endsWith(".mp3") == true })
        assertEquals(listOf("Test MP3s"), mediaSources.state.value.localFolders.map { it.label })
    }
}

private fun File.writeMinimalMp3Bytes() {
    writeBytes(byteArrayOf(0x49, 0x44, 0x33, 0x04, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00))
}
