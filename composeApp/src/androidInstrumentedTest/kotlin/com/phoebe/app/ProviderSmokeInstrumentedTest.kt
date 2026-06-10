package com.phoebe.app

import android.app.Application
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.phoebe.app.platform.PlatformStorage
import com.phoebe.app.testing.ProviderSmokeHarness
import com.phoebe.app.testing.SmokeSource
import com.phoebe.app.testing.newAndroidTestPhoebeDatabase
import com.phoebe.app.testing.runCatalogRefreshSmoke
import com.phoebe.app.testing.testHttpClient
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

@RunWith(AndroidJUnit4::class)
class ProviderSmokeInstrumentedTest {
    private lateinit var app: Application
    private lateinit var storageOverride: File
    private var dbName: String? = null
    private var driver: app.cash.sqldelight.driver.android.AndroidSqliteDriver? = null

    @Before
    fun setup() {
        app = ApplicationProvider.getApplicationContext()
        AndroidContextHolder.application = app
        storageOverride = File(app.cacheDir, "phoebe-provider-smoke-${System.nanoTime()}").apply { mkdirs() }
        System.setProperty("phoebe.storage.root", storageOverride.absolutePath)
    }

    @After
    fun tearDown() {
        driver?.close()
        driver = null
        dbName?.let { app.deleteDatabase(it) }
        dbName = null
        storageOverride.deleteRecursively()
        System.clearProperty("phoebe.storage.root")
    }

    @Test
    fun plexCatalogRefreshSmoke() = runProviderCatalogSmoke(SmokeSource.Plex)

    @Test
    fun jellyfinCatalogRefreshSmoke() = runProviderCatalogSmoke(SmokeSource.Jellyfin)

    @Test
    fun embyCatalogRefreshSmoke() = runProviderCatalogSmoke(SmokeSource.Emby)

    @Test
    fun navidromeCatalogRefreshSmoke() = runProviderCatalogSmoke(SmokeSource.Navidrome)

    @Test
    fun musicAssistantCatalogRefreshSmoke() = runProviderCatalogSmoke(SmokeSource.MusicAssistant)

    private fun runProviderCatalogSmoke(source: SmokeSource) = runBlocking {
        val testDb = newAndroidTestPhoebeDatabase(app)
        driver = testDb.driver
        dbName = testDb.sqliteName
        val http = testHttpClient(ProviderSmokeHarness.mockEngineFor(source))
        ProviderSmokeHarness.runCatalogRefreshSmoke(
            source = source,
            database = testDb.database,
            storage = PlatformStorage(),
            http = http,
        )
    }
}
