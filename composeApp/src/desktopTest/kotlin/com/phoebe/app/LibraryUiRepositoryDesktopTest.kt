package com.phoebe.app

import app.cash.sqldelight.db.SqlDriver
import com.phoebe.app.data.LibraryUiRepository
import com.phoebe.app.domain.PersonalMixPreferences
import com.phoebe.app.platform.PlatformStorage
import com.phoebe.app.testing.newInMemoryPhoebeDatabase
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import kotlin.test.assertEquals

class LibraryUiRepositoryDesktopTest {
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
    fun personalMixPreferencesPersistAndRestore() = runTest {
        val (db, d) = newInMemoryPhoebeDatabase()
        driver = d
        val storage = PlatformStorage()
        val saved = PersonalMixPreferences(
            limit = 80,
            heavyRotationWeight = 40,
            recentWeight = 20,
            mostPlayedWeight = 15,
            similarWeight = 10,
            discoveryWeight = 15,
        )

        LibraryUiRepository(db, storage).setPersonalMix(saved)
        val restored = LibraryUiRepository(db, storage).apply { restore() }

        assertEquals(saved, restored.preferences.value.personalMix)
    }
}
