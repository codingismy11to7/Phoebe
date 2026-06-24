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
            lines(
                "title,artist,album,duration_ms,path",
                "\"alpha\",\"Local Artist\",\"Local Album\",180000,\"file:///music/alpha.mp3\"",
            ),
            exported,
        )
    }

    @Test
    fun jsonIncludesVersionAndTracks() {
        val exported = PlaylistExporter.export(listOf(sampleTrack("alpha")), PlaylistExportFormat.Json)

        assertTrue(exported.contains("\"version\""))
        assertTrue(exported.contains("1"))
        assertTrue(exported.contains("\"title\""))
        assertTrue(exported.contains("\"alpha\""))
    }

    @Test
    fun jsonEscapesControlCharacters() {
        val track = sampleTrack("line\tone").copy(artist = "Artist\bName", album = "Album\u000CName")
        val exported = PlaylistExporter.export(listOf(track), PlaylistExportFormat.Json)

        assertTrue(exported.contains("line\\tone"))
        assertTrue(exported.contains("Artist\\bName"))
        assertTrue(exported.contains("Album\\fName"))
    }

    @Test
    fun importerMatchesM3uByPathAndCountsDuplicates() {
        val tracks = listOf(sampleTrack("alpha"), sampleTrack("beta"))
        val preview = PlaylistImporter.preview(
            lines(
                "#EXTM3U",
                "#EXTINF:180,Local Artist - alpha",
                "file:///music/alpha.mp3",
                "#EXTINF:180,Local Artist - alpha",
                "file:///music/alpha.mp3",
                "#EXTINF:180,Missing - nope",
                "file:///music/nope.mp3",
            ),
            tracks,
        )

        assertEquals(listOf("local:test:alpha"), preview.matchedTracks.map { it.id })
        assertEquals(1, preview.matchedCount)
        assertEquals(1, preview.duplicateCount)
        assertEquals(1, preview.skippedCount)
    }

    @Test
    fun importerTrimsM3uDurationWhitespace() {
        val tracks = listOf(sampleTrack("alpha"))
        val preview = PlaylistImporter.preview(
            lines(
                "#EXTM3U",
                "#EXTINF: 180,Local Artist - alpha",
                "file:///music/alpha.mp3",
            ),
            tracks,
        )

        assertEquals(listOf("local:test:alpha"), preview.matchedTracks.map { it.id })
    }

    @Test
    fun importerMatchesCsvByTitleArtistDurationTolerance() {
        val tracks = listOf(sampleTrack("alpha"))
        val preview = PlaylistImporter.preview(
            lines(
                "title,artist,album,duration_ms,path",
                "alpha,Local Artist,Local Album,181000,",
            ),
            tracks,
        )

        assertEquals(listOf("local:test:alpha"), preview.matchedTracks.map { it.id })
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

    private fun lines(vararg value: String): String = value.joinToString("\n")
}
