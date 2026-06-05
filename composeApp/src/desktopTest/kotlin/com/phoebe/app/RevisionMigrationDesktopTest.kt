package com.phoebe.app

import app.cash.sqldelight.async.coroutines.awaitAsOne
import com.phoebe.app.data.db.createPhoebeDatabase
import com.phoebe.app.db.PhoebeDatabase
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
    fun sqlDelightMigrationPreservesSessionWhenLegacyRevisionMarkerIsStale() = runTest {
        val root = File(System.getProperty("phoebe.storage.root"))
        val dbFile = File(root, "phoebe-debug.db")
        val revFile = File(root, "phoebe-debug.db.rev")

        createVersion21Database(dbFile)
        revFile.writeText("18")

        val database = createPhoebeDatabase()

        val session = database.sessionQueries.selectCurrent().awaitAsOne()
        assertEquals("fixture-token", session.token)
        assertEquals("Plex listener", session.userName)
        assertEquals("Plex", session.providerType)
        assertEquals("Quick", session.jellyfinSyncMode)

        val settings = database.appSettingsQueries.selectCurrent().awaitAsOne()
        assertTrue(settings.listenBrainzSettings.contains("\"enabled\":false"))
        assertEquals("Artwork", settings.nowPlayingVisualizerPreset)
        assertEquals(1L, settings.blurredArtworkAppearance)
        assertEquals(PhoebeDatabase.Schema.version, readUserVersion(dbFile))
        assertEquals("18", revFile.readText().trim())
    }

    private fun createVersion21Database(dbFile: File) {
        DriverManager.getConnection("jdbc:sqlite:${dbFile.absolutePath}").use { connection ->
            connection.createStatement().use { statement ->
                statement.execute(
                    """
                    CREATE TABLE SessionRow (
                        id INTEGER NOT NULL PRIMARY KEY CHECK (id = 1) DEFAULT 1,
                        providerType TEXT NOT NULL DEFAULT 'Plex',
                        token TEXT NOT NULL,
                        userName TEXT NOT NULL,
                        userId TEXT,
                        selectedServerId TEXT,
                        selectedServerName TEXT,
                        selectedServerUri TEXT,
                        selectedServerOwned INTEGER,
                        selectedServerConnectionUris TEXT,
                        selectedServerAdvertisedConnectionUris TEXT,
                        selectedServerLocalConnectionUris TEXT,
                        selectedServerAccessToken TEXT,
                        selectedServerHttpsRequired INTEGER,
                        selectedLibraryKey TEXT,
                        selectedLibraryTitle TEXT,
                        jellyfinSyncMode TEXT NOT NULL DEFAULT 'Quick'
                    )
                    """.trimIndent(),
                )
                statement.execute(
                    """
                    INSERT INTO SessionRow(
                        id, providerType, token, userName, userId,
                        selectedServerId, selectedServerName, selectedServerUri, selectedServerOwned,
                        selectedServerConnectionUris, selectedServerAdvertisedConnectionUris,
                        selectedServerLocalConnectionUris, selectedServerAccessToken, selectedServerHttpsRequired,
                        selectedLibraryKey, selectedLibraryTitle, jellyfinSyncMode
                    ) VALUES (1, 'Plex', 'fixture-token', 'Plex listener', NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, 'Quick')
                    """.trimIndent(),
                )
                statement.execute(
                    """
                    CREATE TABLE AppSettingsRow (
                        id INTEGER NOT NULL PRIMARY KEY CHECK (id = 1) DEFAULT 1,
                        crossfadeSeconds INTEGER NOT NULL DEFAULT 0,
                        scanLibraryOnLaunch INTEGER NOT NULL DEFAULT 0,
                        notifyWhenDownloadFinishes INTEGER NOT NULL DEFAULT 0,
                        persistEqualizerSettings INTEGER NOT NULL DEFAULT 0,
                        equalizerProfile TEXT NOT NULL DEFAULT '{"enabled":false,"bandCount":10,"gainsDb":[]}'
                    )
                    """.trimIndent(),
                )
                statement.execute(
                    """
                    INSERT INTO AppSettingsRow(
                        id,
                        crossfadeSeconds,
                        scanLibraryOnLaunch,
                        notifyWhenDownloadFinishes,
                        persistEqualizerSettings,
                        equalizerProfile
                    ) VALUES (1, 0, 0, 0, 0, '{"enabled":false,"bandCount":10,"gainsDb":[]}')
                    """.trimIndent(),
                )
                statement.execute(
                    """
                    CREATE TABLE TrackParentRow (
                        parentId TEXT NOT NULL,
                        trackId TEXT NOT NULL,
                        position INTEGER NOT NULL,
                        PRIMARY KEY (parentId, trackId)
                    )
                    """.trimIndent(),
                )
                statement.execute("CREATE INDEX TrackParentRow_parent_position ON TrackParentRow(parentId, position)")
                statement.execute("PRAGMA user_version = 21")
            }
        }
    }

    private fun readUserVersion(dbFile: File): Long =
        DriverManager.getConnection("jdbc:sqlite:${dbFile.absolutePath}").use { connection ->
            connection.createStatement().use { statement ->
                statement.executeQuery("PRAGMA user_version").use { rows ->
                    check(rows.next()) { "PRAGMA user_version returned no rows." }
                    rows.getLong(1)
                }
            }
        }
}
