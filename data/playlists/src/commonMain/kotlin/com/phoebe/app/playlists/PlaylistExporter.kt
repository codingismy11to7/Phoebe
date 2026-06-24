package com.phoebe.app.playlists

import com.phoebe.app.domain.Track
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

enum class PlaylistExportFormat(val fileExtension: String, val mimeType: String) {
    M3U8("m3u8", "audio/x-mpegurl"),
    Text("txt", "text/plain"),
    Csv("csv", "text/csv"),
    Json("json", "application/json"),
}

object PlaylistExporter {
    private val JsonFormat = Json {
        encodeDefaults = true
        prettyPrint = true
    }

    fun export(tracks: List<Track>, format: PlaylistExportFormat): String = when (format) {
        PlaylistExportFormat.M3U8 -> exportM3u8(tracks)
        PlaylistExportFormat.Text -> exportText(tracks)
        PlaylistExportFormat.Csv -> exportCsv(tracks)
        PlaylistExportFormat.Json -> exportJson(tracks)
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

    private fun exportJson(tracks: List<Track>): String =
        JsonFormat.encodeToString(
            PlaylistJsonExport(
                tracks = tracks.map { track ->
                    PlaylistJsonTrack(
                        id = track.id,
                        title = track.title,
                        artist = track.artist,
                        album = track.album,
                        durationMs = track.durationMs,
                        path = trackPath(track),
                    )
                },
            ),
        )

    private fun trackPath(track: Track): String =
        track.localUri?.takeIf { it.isNotBlank() }
            ?: track.filepath?.takeIf { it.isNotBlank() }
            ?: track.streamUrl

    private fun csvCell(value: String): String {
        val escaped = value.replace("\"", "\"\"")
        return "\"$escaped\""
    }

    @Serializable
    private data class PlaylistJsonExport(
        val version: Int = 1,
        val tracks: List<PlaylistJsonTrack>,
    )

    @Serializable
    private data class PlaylistJsonTrack(
        val id: String,
        val title: String,
        val artist: String,
        val album: String,
        val durationMs: Long,
        val path: String,
    )
}

data class PlaylistImportPreview(
    val matchedTracks: List<Track>,
    val matchedCount: Int,
    val skippedCount: Int,
    val duplicateCount: Int,
)

object PlaylistImporter {
    fun preview(content: String, availableTracks: List<Track>, format: PlaylistImportFormat = PlaylistImportFormat.detect(content)): PlaylistImportPreview {
        val entries = when (format) {
            PlaylistImportFormat.M3U -> parseM3u(content)
            PlaylistImportFormat.Csv -> parseCsv(content)
            PlaylistImportFormat.Text -> parseText(content)
        }
        val tracksById = availableTracks.associateBy { it.id }
        val tracksByPath = availableTracks
            .flatMap { track -> listOfNotNull(track.localUri, track.filepath, track.streamUrl).map { it to track } }
            .toMap()
        val usedIds = mutableSetOf<String>()
        val matched = mutableListOf<Track>()
        var skipped = 0
        var duplicates = 0
        entries.forEach { entry ->
            val track = entry.providerId?.let { tracksById[it] }
                ?: entry.path?.let { tracksByPath[it] }
                ?: availableTracks.firstOrNull { candidate ->
                    candidate.title.equals(entry.title.orEmpty(), ignoreCase = true) &&
                        candidate.artist.equals(entry.artist.orEmpty(), ignoreCase = true) &&
                        entry.durationMs?.let { duration -> kotlin.math.abs(candidate.durationMs - duration) <= 3_000L } != false
                }
            when {
                track == null -> skipped++
                track.id in usedIds -> duplicates++
                else -> {
                    usedIds += track.id
                    matched += track
                }
            }
        }
        return PlaylistImportPreview(
            matchedTracks = matched,
            matchedCount = matched.size,
            skippedCount = skipped,
            duplicateCount = duplicates,
        )
    }

    private fun parseM3u(content: String): List<PlaylistImportEntry> {
        var pendingTitle: String? = null
        var pendingArtist: String? = null
        var pendingDuration: Long? = null
        return content.lineSequence().mapNotNull { raw ->
            val line = raw.trim()
            when {
                line.isBlank() || line == "#EXTM3U" -> null
                line.startsWith("#EXTINF:", ignoreCase = true) -> {
                    val comma = line.indexOf(',')
                    pendingDuration = line.substring(8, comma.takeIf { it > 8 } ?: line.length).trim().toLongOrNull()?.times(1000L)
                    val label = if (comma >= 0) line.substring(comma + 1).trim() else ""
                    val separator = label.indexOf(" - ")
                    if (separator >= 0) {
                        pendingArtist = label.substring(0, separator).trim()
                        pendingTitle = label.substring(separator + 3).trim()
                    } else {
                        pendingTitle = label.takeIf { it.isNotBlank() }
                    }
                    null
                }
                line.startsWith("#") -> null
                else -> PlaylistImportEntry(
                    path = line,
                    title = pendingTitle,
                    artist = pendingArtist,
                    durationMs = pendingDuration,
                ).also {
                    pendingTitle = null
                    pendingArtist = null
                    pendingDuration = null
                }
            }
        }.toList()
    }

    private fun parseCsv(content: String): List<PlaylistImportEntry> {
        val rows = content.lineSequence().filter { it.isNotBlank() }.map(::csvCells).toList()
        if (rows.isEmpty()) return emptyList()
        val header = rows.first().map { it.lowercase() }
        val data = if ("title" in header || "path" in header || "id" in header) rows.drop(1) else rows
        fun List<String>.cell(name: String): String? =
            header.indexOf(name).takeIf { it >= 0 }?.let { getOrNull(it) }?.takeIf { it.isNotBlank() }
        return data.map { row ->
            PlaylistImportEntry(
                providerId = row.cell("id") ?: row.cell("provider_id"),
                title = row.cell("title"),
                artist = row.cell("artist"),
                durationMs = row.cell("duration_ms")?.toLongOrNull(),
                path = row.cell("path") ?: row.cell("uri"),
            )
        }
    }

    private fun parseText(content: String): List<PlaylistImportEntry> =
        content.lineSequence().mapNotNull { raw ->
            val line = raw.trim().takeIf { it.isNotBlank() } ?: return@mapNotNull null
            val separator = line.indexOf(" - ")
            if (separator >= 0) {
                PlaylistImportEntry(
                    artist = line.substring(0, separator).trim(),
                    title = line.substring(separator + 3).trim(),
                    path = line.takeIf { it.contains("://") },
                )
            } else {
                PlaylistImportEntry(path = line, title = line)
            }
        }.toList()

    private fun csvCells(line: String): List<String> {
        val cells = mutableListOf<String>()
        val current = StringBuilder()
        var quoted = false
        var index = 0
        while (index < line.length) {
            val char = line[index]
            when {
                char == '"' && quoted && line.getOrNull(index + 1) == '"' -> {
                    current.append('"')
                    index++
                }
                char == '"' -> quoted = !quoted
                char == ',' && !quoted -> {
                    cells += current.toString()
                    current.clear()
                }
                else -> current.append(char)
            }
            index++
        }
        cells += current.toString()
        return cells
    }
}

enum class PlaylistImportFormat {
    M3U,
    Csv,
    Text,
    ;

    companion object {
        fun detect(content: String): PlaylistImportFormat =
            when {
                content.lineSequence().firstOrNull()?.startsWith("#EXTM3U", ignoreCase = true) == true -> M3U
                content.lineSequence().firstOrNull().orEmpty().contains(",") -> Csv
                else -> Text
            }
    }
}

private data class PlaylistImportEntry(
    val providerId: String? = null,
    val path: String? = null,
    val title: String? = null,
    val artist: String? = null,
    val durationMs: Long? = null,
)
