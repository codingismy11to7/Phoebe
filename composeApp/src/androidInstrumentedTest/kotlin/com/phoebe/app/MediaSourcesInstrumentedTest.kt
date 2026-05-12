package com.phoebe.app

import android.app.Application
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.phoebe.app.data.MediaSourcesRepository
import com.phoebe.app.platform.PlatformStorage
import com.phoebe.app.testing.newAndroidTestPhoebeDatabase
import app.cash.sqldelight.driver.android.AndroidSqliteDriver
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import kotlin.test.assertEquals

@RunWith(AndroidJUnit4::class)
class MediaSourcesInstrumentedTest {

    private lateinit var app: Application
    private var driver: AndroidSqliteDriver? = null
    private lateinit var storageOverride: File
    private var dbName: String? = null

    @Before
    fun setup() {
        app = ApplicationProvider.getApplicationContext()
        AndroidContextHolder.application = app
        storageOverride = File(app.cacheDir, "phoebe-test-storage-${System.nanoTime()}").apply { mkdirs() }
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
    fun addLocalFolder_roundTripsThroughDatabase() = runBlocking {
        val testDb = newAndroidTestPhoebeDatabase(app)
        driver = testDb.driver
        dbName = testDb.sqliteName
        val repo = MediaSourcesRepository(testDb.database, PlatformStorage())
        repo.addLocalFolder("content://tree/doc", "USB")
        assertEquals(1, repo.state.value.localFolders.size)
        assertEquals("content://tree/doc", repo.state.value.localFolders.single().rootUri)
    }
}
