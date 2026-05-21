package com.phoebe.app

import com.phoebe.app.data.LibraryUiRepository
import com.phoebe.app.data.db.createPhoebeDatabase
import com.phoebe.app.data.db.libraryPrefsSchemaCompatible
import com.phoebe.app.platform.PlatformStorage
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
    fun staleRevisionWithoutGridColumnsColumnIsRebuilt() = runTest {
        val root = File(System.getProperty("phoebe.storage.root"))
        val dbFile = File(root, "phoebe-debug.db")
        val revFile = File(root, "phoebe-debug.db.rev")

        createLegacyLibraryPrefsDatabase(dbFile)
        revFile.writeText("17")

        assertTrue(dbFile.exists())
        assertEquals(false, libraryPrefsSchemaCompatible(dbFile))

        val database = createPhoebeDatabase()
        LibraryUiRepository(database, PlatformStorage()).restore()

        assertTrue(libraryPrefsSchemaCompatible(dbFile))
        assertEquals("18", revFile.readText().trim())
    }

    @Test
    fun revisionMarkerAheadOfSchemaIsRebuilt() = runTest {
        val root = File(System.getProperty("phoebe.storage.root"))
        val dbFile = File(root, "phoebe-debug.db")
        val revFile = File(root, "phoebe-debug.db.rev")

        createLegacyLibraryPrefsDatabase(dbFile)
        revFile.writeText("18")

        assertEquals(false, libraryPrefsSchemaCompatible(dbFile))

        val database = createPhoebeDatabase()
        LibraryUiRepository(database, PlatformStorage()).restore()

        assertTrue(libraryPrefsSchemaCompatible(dbFile))
    }

    private fun createLegacyLibraryPrefsDatabase(dbFile: File) {
        DriverManager.getConnection("jdbc:sqlite:${dbFile.absolutePath}").use { connection ->
            connection.createStatement().use { statement ->
                statement.execute(
                    """
                    CREATE TABLE LibraryPrefsRow (
                        id INTEGER NOT NULL PRIMARY KEY CHECK (id = 1) DEFAULT 1,
                        sortBy TEXT NOT NULL,
                        ascending INTEGER NOT NULL,
                        colYear INTEGER NOT NULL,
                        colGenre INTEGER NOT NULL,
                        colFilepath INTEGER NOT NULL,
                        colAudioCodec INTEGER NOT NULL,
                        colBitrate INTEGER NOT NULL,
                        colDuration INTEGER NOT NULL,
                        colSampleRate INTEGER NOT NULL,
                        colFileType INTEGER NOT NULL,
                        colDateAdded INTEGER NOT NULL,
                        colRating INTEGER NOT NULL DEFAULT 1,
                        colFavorite INTEGER NOT NULL DEFAULT 1,
                        homeSections TEXT NOT NULL DEFAULT '',
                        personalMix TEXT NOT NULL DEFAULT '{}'
                    )
                    """.trimIndent(),
                )
                statement.execute(
                    """
                    INSERT INTO LibraryPrefsRow(
                        id, sortBy, ascending,
                        colYear, colGenre, colFilepath, colAudioCodec, colBitrate,
                        colDuration, colSampleRate, colFileType, colDateAdded, colRating, colFavorite,
                        homeSections, personalMix
                    ) VALUES (
                        1, 'Name', 1,
                        1, 1, 1, 1, 1,
                        1, 1, 1, 1, 1, 1,
                        'Mixes', '{}'
                    )
                    """.trimIndent(),
                )
            }
        }
    }
}
