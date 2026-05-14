package com.phoebe.app

import com.phoebe.app.domain.LocalFolderMediaSourceConfig
import com.phoebe.app.domain.Track
import com.phoebe.app.domain.canAddToLocalPlaylist
import com.phoebe.app.domain.canAddToPlexPlaylist
import com.phoebe.app.playlists.PlaylistExportFormat
import com.phoebe.app.playlists.PlaylistExporter
import com.phoebe.app.sources.LocalFolderCatalogBuilder
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class LocalPlaylistWebTest {

    @Test
    fun localTracksExportToM3u8TextAndCsvOnWasm() = runTest {
        val snapshot = LocalFolderCatalogBuilder.build(
            LocalFolderMediaSourceConfig(
                id = "web-playlist",
                rootUri = "phoebe-test://music?files=alpha.mp3|beta.mp3",
                label = "Web Playlist MP3s",
                enabled = true,
            ),
        )
        val tracks = snapshot.tracksByParent.values.flatten().sortedBy { it.title }
        assertEquals(listOf("alpha", "beta"), tracks.map { it.title })
        assertTrue(tracks.all { it.localUri?.contains(".mp3") == true })

        val playlistTracks = tracks.filter { it.title in setOf("alpha", "beta") }
        val m3u8 = PlaylistExporter.export(playlistTracks, PlaylistExportFormat.M3U8)
        assertTrue(m3u8.startsWith("#EXTM3U"))
        assertTrue(m3u8.contains("alpha.mp3"))

        val text = PlaylistExporter.export(playlistTracks, PlaylistExportFormat.Text)
        assertEquals(2, text.lines().size)

        val csv = PlaylistExporter.export(playlistTracks, PlaylistExportFormat.Csv)
        assertTrue(csv.startsWith("title,artist,album,duration_ms,path"))
    }

    @Test
    fun onlyLocalAudioTracksBelongInLocalPlaylists() {
        val local = Track(
            id = "local:1",
            title = "alpha",
            artist = "Artist",
            album = "Album",
            durationMs = 1_000,
            streamUrl = "",
            downloadUrl = "",
            localUri = "phoebe-test://music/alpha.mp3",
        )
        val plex = local.copy(
            id = "plex:1",
            localUri = null,
            streamUrl = "https://plex.example/stream",
            downloadUrl = "https://plex.example/download",
        )
        assertTrue(local.canAddToLocalPlaylist())
        assertTrue(!plex.canAddToLocalPlaylist())
        assertTrue(plex.canAddToPlexPlaylist())
    }
}
