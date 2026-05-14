package com.phoebe.app

import com.phoebe.app.domain.LocalFolderMediaSourceConfig
import com.phoebe.app.domain.Track
import com.phoebe.app.sources.LocalFolderCatalogBuilder
import com.phoebe.app.sources.LocalLibraryIO
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

    @Test
    fun webLocalFileExistsAndPlaybackUsesLocalUri() = runTest {
        val uri = "phoebe-test://music/alpha.mp3"
        assertTrue(LocalLibraryIO.fileExists(uri))

        val track = Track(
            id = "local:alpha",
            title = "alpha",
            artist = "Web test files",
            album = "Web MP3 folder",
            durationMs = 0L,
            streamUrl = "https://stream.example/alpha",
            downloadUrl = "",
            localUri = uri,
        )
        val player = RecordingAudioPlayer()
        player.play(listOf(track), 0)

        assertEquals(uri, player.lastUri)
        assertTrue(player.state.value.isPlaying)
    }
}
