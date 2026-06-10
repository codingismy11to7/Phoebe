package com.phoebe.app

import app.cash.sqldelight.db.SqlDriver
import com.phoebe.app.platform.PlatformStorage
import com.phoebe.app.testing.ProviderSmokeHarness
import com.phoebe.app.testing.SmokeSource
import com.phoebe.app.testing.newInMemoryPhoebeDatabase
import com.phoebe.app.testing.runCatalogRefreshSmoke
import com.phoebe.app.testing.testHttpClient
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class ProviderSmokeIntegrationDesktopTest {
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
    fun plexCatalogRefreshSmoke() = runCatalogSmoke(SmokeSource.Plex)

    @Test
    fun jellyfinCatalogRefreshSmoke() = runCatalogSmoke(SmokeSource.Jellyfin)

    @Test
    fun embyCatalogRefreshSmoke() = runCatalogSmoke(SmokeSource.Emby)

    @Test
    fun navidromeCatalogRefreshSmoke() = runCatalogSmoke(SmokeSource.Navidrome)

    @Test
    fun musicAssistantCatalogRefreshSmoke() = runCatalogSmoke(SmokeSource.MusicAssistant)

    private fun runCatalogSmoke(source: SmokeSource) = runTest {
        val (db, sqlDriver) = newInMemoryPhoebeDatabase()
        driver = sqlDriver
        val http = testHttpClient(ProviderSmokeHarness.mockEngineFor(source))
        ProviderSmokeHarness.runCatalogRefreshSmoke(
            source = source,
            database = db,
            storage = PlatformStorage(),
            http = http,
        )
    }
}
