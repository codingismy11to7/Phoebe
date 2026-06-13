package com.phoebe.app.playlists

import com.phoebe.app.domain.Track
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class PlaylistExporterTest {

    @Test
    fun m3u8IncludesExtm3uHeaderAndLocalPaths() {
        val tracks = listOf(sampleTrack("alpha"), sampleTrack("beta"))
        val exported = PlaylistExporter.export(tracks, PlaylistExportFormat.M3U8)
        assertTrue(exported.startsWith("#EXTM3U"))
        assertTrue(exported.contains("#EXTINF:"))
        assertTrue(exported.contains("file:///music/alpha.mp3"))
        assertTrue(exported.contains("file:///music/beta.mp3"))
    }

    @Test
    fun textListsArtistAndTitlePerLine() {
        val tracks = listOf(sampleTrack("alpha"))
        val exported = PlaylistExporter.export(tracks, PlaylistExportFormat.Text)
        assertEquals("Local Artist - alpha", exported)
    }

    @Test
    fun csvIncludesHeaderAndQuotedCells() {
        val tracks = listOf(sampleTrack("alpha"))
        val exported = PlaylistExporter.export(tracks, PlaylistExportFormat.Csv)
        assertEquals(
            """
            title,artist,album,duration_ms,path
            "alpha","Local Artist","Local Album",180000,"file:///music/alpha.mp3"
            """.trimIndent(),
            exported,
        )
    }

    private fun sampleTrack(title: String) = Track(
        id = "local:test:$title",
        title = title,
        artist = "Local Artist",
        album = "Local Album",
        durationMs = 180_000,
        streamUrl = "",
        downloadUrl = "",
        localUri = "file:///music/$title.mp3",
    )
}
