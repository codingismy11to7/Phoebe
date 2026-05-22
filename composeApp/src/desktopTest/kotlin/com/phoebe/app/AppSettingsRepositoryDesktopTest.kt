package com.phoebe.app

import app.cash.sqldelight.db.SqlDriver
import com.phoebe.app.data.AppSettingsRepository
import com.phoebe.app.domain.AppSettings
import com.phoebe.app.domain.EqualizerProfile
import com.phoebe.app.testing.newInMemoryPhoebeDatabase
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class AppSettingsRepositoryDesktopTest {
    private var driver: SqlDriver? = null

    @After
    fun cleanup() {
        driver?.close()
        driver = null
    }

    @Test
    fun defaultsRestoreWhenNoRowExists() = runTest {
        val (db, d) = newInMemoryPhoebeDatabase()
        driver = d
        val repository = AppSettingsRepository(db)

        repository.restore()

        assertEquals(AppSettings.Default, repository.settings.value)
    }

    @Test
    fun settingsPersistAndRestore() = runTest {
        val (db, d) = newInMemoryPhoebeDatabase()
        driver = d

        AppSettingsRepository(db).run {
            setCrossfadeSeconds(7)
            setScanLibraryOnLaunch(true)
            setNotifyWhenDownloadFinishes(true)
            setPersistEqualizerSettings(true, EqualizerProfile.Default.normalized().withGain(7, 4.5f))
        }
        val restored = AppSettingsRepository(db).apply { restore() }

        assertEquals(7, restored.settings.value.crossfadeSeconds)
        assertTrue(restored.settings.value.scanLibraryOnLaunch)
        assertTrue(restored.settings.value.notifyWhenDownloadFinishes)
        assertTrue(restored.settings.value.persistEqualizerSettings)
        assertEquals(4.5f, restored.settings.value.equalizerProfile.gainsDb[7])
    }

    @Test
    fun crossfadeClampsToSupportedRange() = runTest {
        val (db, d) = newInMemoryPhoebeDatabase()
        driver = d
        val repository = AppSettingsRepository(db)

        repository.setCrossfadeSeconds(99)
        assertEquals(AppSettings.MaxCrossfadeSeconds, repository.settings.value.crossfadeSeconds)

        repository.setScanLibraryOnLaunch(false)
        repository.setNotifyWhenDownloadFinishes(false)
        assertFalse(repository.settings.value.scanLibraryOnLaunch)
        assertFalse(repository.settings.value.notifyWhenDownloadFinishes)
    }

    @Test
    fun equalizerProfileClampsBeforePersisting() = runTest {
        val (db, d) = newInMemoryPhoebeDatabase()
        driver = d
        val repository = AppSettingsRepository(db)

        repository.setEqualizerProfile(
            EqualizerProfile(
                enabled = true,
                bandCount = 31,
                gainsDb = listOf(99f),
            ),
        )

        assertEquals(31, repository.settings.value.equalizerProfile.bandCount)
        assertEquals(12f, repository.settings.value.equalizerProfile.gainsDb.first())
        assertEquals(31, repository.settings.value.equalizerProfile.gainsDb.size)
    }
}
