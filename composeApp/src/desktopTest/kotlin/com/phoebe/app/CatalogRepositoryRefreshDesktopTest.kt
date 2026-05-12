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
import kotlin.test.assertEquals
import kotlin.test.assertFalse

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
}
