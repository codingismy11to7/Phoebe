package com.phoebe.app.playlists

import com.phoebe.app.domain.Track

enum class PlaylistExportFormat(val fileExtension: String, val mimeType: String) {
    M3U8("m3u8", "audio/x-mpegurl"),
    Text("txt", "text/plain"),
    Csv("csv", "text/csv"),
}

object PlaylistExporter {
    fun export(tracks: List<Track>, format: PlaylistExportFormat): String = when (format) {
        PlaylistExportFormat.M3U8 -> exportM3u8(tracks)
        PlaylistExportFormat.Text -> exportText(tracks)
        PlaylistExportFormat.Csv -> exportCsv(tracks)
    }

    fun suggestedFileName(playlistTitle: String, format: PlaylistExportFormat): String {
        val stem = playlistTitle
            .trim()
            .ifBlank { "playlist" }
            .replace(Regex("""[^\w\- .]+"""), "_")
            .replace(Regex("""\s+"""), " ")
            .trim()
        return "$stem.${format.fileExtension}"
    }

    private fun exportM3u8(tracks: List<Track>): String = buildString {
        appendLine("#EXTM3U")
        tracks.forEach { track ->
            val seconds = (track.durationMs / 1000L).coerceAtLeast(-1L)
            appendLine("#EXTINF:$seconds,${track.artist} - ${track.title}")
            appendLine(trackPath(track))
        }
    }.trimEnd()

    private fun exportText(tracks: List<Track>): String = tracks.joinToString("\n") { track ->
        "${track.artist} - ${track.title}"
    }

    private fun exportCsv(tracks: List<Track>): String = buildString {
        appendLine("title,artist,album,duration_ms,path")
        tracks.forEach { track ->
            appendLine(
                listOf(
                    csvCell(track.title),
                    csvCell(track.artist),
                    csvCell(track.album),
                    track.durationMs.toString(),
                    csvCell(trackPath(track)),
                ).joinToString(","),
            )
        }
    }.trimEnd()

    private fun trackPath(track: Track): String =
        track.localUri?.takeIf { it.isNotBlank() }
            ?: track.filepath?.takeIf { it.isNotBlank() }
            ?: track.streamUrl

    private fun csvCell(value: String): String {
        val escaped = value.replace("\"", "\"\"")
        return "\"$escaped\""
    }
}
