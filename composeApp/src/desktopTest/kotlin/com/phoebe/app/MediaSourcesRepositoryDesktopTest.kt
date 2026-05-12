package com.phoebe.app

import app.cash.sqldelight.db.SqlDriver
import com.phoebe.app.data.MediaSourcesRepository
import com.phoebe.app.platform.PlatformStorage
import com.phoebe.app.testing.newInMemoryPhoebeDatabase
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class MediaSourcesRepositoryDesktopTest {
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
    fun addLocalFolderPersistsAndExposesState() = runTest {
        val (db, d) = newInMemoryPhoebeDatabase()
        driver = d
        val repo = MediaSourcesRepository(db, PlatformStorage())
        repo.addLocalFolder("file:///music", "Home")
        val folders = repo.state.value.localFolders
        assertEquals(1, folders.size)
        assertEquals("file:///music", folders.single().rootUri)
        assertTrue(folders.single().enabled)
    }
}
