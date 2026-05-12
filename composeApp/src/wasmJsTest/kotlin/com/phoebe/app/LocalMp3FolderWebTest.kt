package com.phoebe.app

import com.phoebe.app.domain.LocalFolderMediaSourceConfig
import com.phoebe.app.sources.LocalFolderCatalogBuilder
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class LocalMp3FolderWebTest {

    @Test
    fun testLocalFolderUriIndexesMp3FilesOnWasm() = runTest {
        val snapshot = LocalFolderCatalogBuilder.build(
            LocalFolderMediaSourceConfig(
                id = "web-test",
                rootUri = "phoebe-test://music?files=alpha.mp3|nested/beta.mp3|notes.txt",
                label = "Web MP3s",
                enabled = true,
            ),
        )

        val tracks = snapshot.tracksByParent.values.flatten()
        assertEquals(listOf("alpha", "beta"), tracks.map { it.title }.sorted())
        assertTrue(tracks.any { it.localUri?.endsWith("alpha.mp3") == true })
    }
}
