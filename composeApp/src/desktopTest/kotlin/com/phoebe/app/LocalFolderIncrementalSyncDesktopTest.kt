package com.phoebe.app

import app.cash.sqldelight.async.coroutines.awaitAsList
import app.cash.sqldelight.db.SqlDriver
import com.phoebe.app.data.LocalFileMetadataCache
import com.phoebe.app.domain.LocalFolderMediaSourceConfig
import com.phoebe.app.sources.AudioMetadata
import com.phoebe.app.sources.LocalAudioFile
import com.phoebe.app.sources.LocalAudioLibraryReader
import com.phoebe.app.sources.LocalFolderCatalogBuilder
import com.phoebe.app.testing.newInMemoryPhoebeDatabase
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class LocalFolderIncrementalSyncDesktopTest {
    private var driver: SqlDriver? = null

    @After
    fun cleanup() {
        driver?.close()
        driver = null
    }

    @Test
    fun unchangedLocalFilesReuseCachedMetadata() = runTest {
        val (db, sqlDriver) = newInMemoryPhoebeDatabase()
        driver = sqlDriver
        val cache = LocalFileMetadataCache(db)
        val reader = FakeLocalAudioLibraryReader(
            files = listOf(
                localFile("file:///music/alpha.mp3", size = 10, modified = 100),
                localFile("file:///music/beta.mp3", size = 20, modified = 200),
            ),
            metadata = mapOf(
                "file:///music/alpha.mp3" to AudioMetadata("Alpha", "Artist", "Album", 1_000),
                "file:///music/beta.mp3" to AudioMetadata("Beta", "Artist", "Album", 2_000),
            ),
        )
        val config = testConfig()

        val first = LocalFolderCatalogBuilder.build(config, cache, reader)
        assertEquals(2, reader.metadataReads)
        val firstTracks = first.tracksByParent.values.flatten().sortedBy { it.title }
        val firstAdded = firstTracks.associate { it.localUri to it.dateAddedMs }

        val second = LocalFolderCatalogBuilder.build(config, cache, reader)
        assertEquals(2, reader.metadataReads)
        val secondTracks = second.tracksByParent.values.flatten().sortedBy { it.title }

        assertEquals(firstTracks.map { it.id }, secondTracks.map { it.id })
        assertEquals(firstAdded, secondTracks.associate { it.localUri to it.dateAddedMs })
    }

    @Test
    fun changedRemovedAndAddedLocalFilesUpdateIncrementally() = runTest {
        val (db, sqlDriver) = newInMemoryPhoebeDatabase()
        driver = sqlDriver
        val cache = LocalFileMetadataCache(db)
        val reader = FakeLocalAudioLibraryReader(
            files = listOf(
                localFile("file:///music/alpha.mp3", size = 10, modified = 100),
                localFile("file:///music/beta.mp3", size = 20, modified = 200),
            ),
            metadata = mapOf(
                "file:///music/alpha.mp3" to AudioMetadata("Alpha", "Artist", "Album", 1_000),
                "file:///music/beta.mp3" to AudioMetadata("Beta", "Artist", "Album", 2_000),
            ),
        )
        val config = testConfig()
        val first = LocalFolderCatalogBuilder.build(config, cache, reader)
        val alphaBefore = first.tracksByParent.values.flatten().single { it.title == "Alpha" }
        val betaBefore = first.tracksByParent.values.flatten().single { it.title == "Beta" }
        assertEquals(2, reader.metadataReads)

        reader.files = listOf(
            localFile("file:///music/alpha.mp3", size = 11, modified = 101),
            localFile("file:///music/gamma.mp3", size = 30, modified = 300),
        )
        reader.metadata = mapOf(
            "file:///music/alpha.mp3" to AudioMetadata("Alpha Updated", "Artist", "Album", 1_500),
            "file:///music/gamma.mp3" to AudioMetadata("Gamma", "Artist", "Album", 3_000),
        )

        val second = LocalFolderCatalogBuilder.build(config, cache, reader)
        assertEquals(4, reader.metadataReads)
        val tracks = second.tracksByParent.values.flatten().sortedBy { it.title }

        assertEquals(listOf("Alpha Updated", "Gamma"), tracks.map { it.title })
        val alphaAfter = tracks.single { it.localUri == "file:///music/alpha.mp3" }
        assertEquals(alphaBefore.id, alphaAfter.id)
        assertEquals(alphaBefore.dateAddedMs, alphaAfter.dateAddedMs)
        assertTrue(tracks.none { it.id == betaBefore.id })
        assertEquals(
            listOf("file:///music/alpha.mp3", "file:///music/gamma.mp3"),
            db.catalogQueries.selectLocalFileMetadataCacheForFolder(config.id)
                .awaitAsList()
                .map { it.uri }
                .sorted(),
        )
    }

    private fun testConfig(): LocalFolderMediaSourceConfig =
        LocalFolderMediaSourceConfig(
            id = "lf-test",
            rootUri = "file:///music",
            label = "Test",
            enabled = true,
        )

    private fun localFile(uri: String, size: Long, modified: Long): LocalAudioFile =
        LocalAudioFile(
            uri = uri,
            sizeBytes = size,
            modifiedAtMs = modified,
            filepath = uri.substringAfterLast('/'),
        )
}

private class FakeLocalAudioLibraryReader(
    var files: List<LocalAudioFile>,
    var metadata: Map<String, AudioMetadata>,
) : LocalAudioLibraryReader {
    var metadataReads = 0

    override suspend fun listAudioFiles(rootUri: String): List<LocalAudioFile> = files

    override suspend fun readAudioMetadata(uri: String): AudioMetadata {
        metadataReads++
        return metadata[uri] ?: AudioMetadata(null, null, null, 0L)
    }
}
