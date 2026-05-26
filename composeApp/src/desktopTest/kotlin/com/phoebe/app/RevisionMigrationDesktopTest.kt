package com.phoebe.app

import com.phoebe.app.data.AppSettingsRepository
import com.phoebe.app.data.db.createPhoebeDatabase
import com.phoebe.app.data.db.appSettingsSchemaCompatible
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.sql.DriverManager
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class RevisionMigrationDesktopTest {
    @get:Rule
    val temp = TemporaryFolder()

    @Before
    fun storageRoot() {
        System.setProperty("phoebe.storage.root", temp.newFolder("kv").absolutePath)
    }

    @After
    fun cleanup() {
        System.clearProperty("phoebe.storage.root")
    }

    @Test
    fun staleRevisionWithoutEqualizerColumnIsRebuilt() = runTest {
        val root = File(System.getProperty("phoebe.storage.root"))
        val dbFile = File(root, "phoebe-debug.db")
        val revFile = File(root, "phoebe-debug.db.rev")

        createLegacyAppSettingsDatabase(dbFile)
        revFile.writeText("18")

        assertTrue(dbFile.exists())
        assertEquals(false, appSettingsSchemaCompatible(dbFile))

        val database = createPhoebeDatabase()
        AppSettingsRepository(database).restore()

        assertTrue(appSettingsSchemaCompatible(dbFile))
        assertEquals("20", revFile.readText().trim())
    }

    @Test
    fun revisionMarkerAheadOfSchemaIsRebuilt() = runTest {
        val root = File(System.getProperty("phoebe.storage.root"))
        val dbFile = File(root, "phoebe-debug.db")
        val revFile = File(root, "phoebe-debug.db.rev")

        createLegacyAppSettingsDatabase(dbFile)
        revFile.writeText("19")

        assertEquals(false, appSettingsSchemaCompatible(dbFile))

        val database = createPhoebeDatabase()
        AppSettingsRepository(database).restore()

        assertTrue(appSettingsSchemaCompatible(dbFile))
    }

    private fun createLegacyAppSettingsDatabase(dbFile: File) {
        DriverManager.getConnection("jdbc:sqlite:${dbFile.absolutePath}").use { connection ->
            connection.createStatement().use { statement ->
                statement.execute(
                    """
                    CREATE TABLE AppSettingsRow (
                        id INTEGER NOT NULL PRIMARY KEY CHECK (id = 1) DEFAULT 1,
                        crossfadeSeconds INTEGER NOT NULL DEFAULT 0,
                        scanLibraryOnLaunch INTEGER NOT NULL DEFAULT 0,
                        notifyWhenDownloadFinishes INTEGER NOT NULL DEFAULT 0
                    )
                    """.trimIndent(),
                )
                statement.execute(
                    """
                    INSERT INTO AppSettingsRow(
                        id, crossfadeSeconds, scanLibraryOnLaunch, notifyWhenDownloadFinishes
                    ) VALUES (1, 0, 0, 0)
                    """.trimIndent(),
                )
            }
        }
    }
}
