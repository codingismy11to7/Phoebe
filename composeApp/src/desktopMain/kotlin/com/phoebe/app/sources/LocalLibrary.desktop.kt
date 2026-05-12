package com.phoebe.app.sources

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.jaudiotagger.audio.AudioFileIO
import org.jaudiotagger.tag.FieldKey
import java.net.URI
import java.nio.file.Files
import java.nio.file.Paths
import javax.swing.JFileChooser
import javax.swing.SwingUtilities
import javax.swing.filechooser.FileSystemView

private val audioExt = setOf("mp3", "m4a", "flac", "wav", "aac", "ogg", "opus")

actual object LocalLibraryIO {
    actual suspend fun listAudioUris(rootUri: String): List<String> = withContext(Dispatchers.IO) {
        val uri = runCatching { URI(rootUri) }.getOrNull() ?: return@withContext emptyList()
        val path = Paths.get(uri)
        if (!Files.exists(path) || !Files.isDirectory(path)) return@withContext emptyList()
        Files.walk(path).use { stream ->
            stream.filter { Files.isRegularFile(it) }
                .filter {
                    val n = it.fileName.toString()
                    audioExt.contains(n.substringAfterLast('.', "").lowercase())
                }
                .map { it.toUri().toString() }
                .toList()
        }
    }

    actual suspend fun fileExists(uri: String): Boolean = withContext(Dispatchers.IO) {
        runCatching {
            val p = Paths.get(URI(uri))
            Files.isRegularFile(p)
        }.getOrDefault(false)
    }

    actual suspend fun readAudioMetadata(uri: String): AudioMetadata = withContext(Dispatchers.IO) {
        val path = runCatching { Paths.get(URI(uri)) }.getOrNull()
        if (path == null || !Files.isRegularFile(path)) {
            return@withContext AudioMetadata(title = null, artist = null, album = null, durationMs = 0L, year = null, genre = null, bitrateKbps = null, audioCodec = null)
        }
        runCatching {
            val audioFile = AudioFileIO.read(path.toFile())
            val tag = audioFile.tag
            fun first(key: FieldKey) = tag?.getFirst(key)?.trim()?.takeIf { it.isNotEmpty() }
            val title = first(FieldKey.TITLE)
            val artist = first(FieldKey.ARTIST) ?: first(FieldKey.ALBUM_ARTIST)
            val album = first(FieldKey.ALBUM)
            val header = audioFile.audioHeader
            val precise = header.preciseTrackLength
            val durationMs = when {
                precise.isFinite() && precise > 0.0 -> (precise * 1000.0).toLong()
                header.trackLength > 0 -> header.trackLength * 1000L
                else -> 0L
            }
            val year = first(FieldKey.YEAR)?.filter { it.isDigit() }?.take(4)?.toIntOrNull()
            val genre = first(FieldKey.GENRE)
            val bitrateStr = header.bitRate
            val bitrateKbps = bitrateStr?.filter { it.isDigit() }?.toIntOrNull()?.takeIf { it > 0 }
            val audioCodec = header.format?.substringBefore(' ')?.takeIf { it.isNotBlank() }
            AudioMetadata(
                title = title,
                artist = artist,
                album = album,
                durationMs = durationMs.coerceAtLeast(0L),
                year = year,
                genre = genre,
                bitrateKbps = bitrateKbps,
                audioCodec = audioCodec,
            )
        }.getOrElse {
            AudioMetadata(title = null, artist = null, album = null, durationMs = 0L, year = null, genre = null, bitrateKbps = null, audioCodec = null)
        }
    }
}

@Composable
actual fun rememberPickLocalFolder(onPicked: (String?) -> Unit): () -> Unit =
    remember(onPicked) {
        {
            SwingUtilities.invokeLater {
                val chooser = JFileChooser(FileSystemView.getFileSystemView().homeDirectory).apply {
                    fileSelectionMode = JFileChooser.DIRECTORIES_ONLY
                    dialogTitle = "Choose music folder"
                    isAcceptAllFileFilterUsed = false
                }
                val ok = chooser.showOpenDialog(null) == JFileChooser.APPROVE_OPTION
                val file = chooser.selectedFile
                onPicked(if (ok && file != null) file.toURI().toString() else null)
            }
        }
    }
