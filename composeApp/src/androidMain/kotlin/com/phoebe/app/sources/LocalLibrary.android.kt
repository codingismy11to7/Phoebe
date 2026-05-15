package com.phoebe.app.sources

import android.content.Intent
import android.media.MediaMetadataRetriever
import android.net.Uri
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.documentfile.provider.DocumentFile
import com.phoebe.app.AndroidContextHolder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

private val audioExt = setOf("mp3", "m4a", "flac", "wav", "aac", "ogg", "opus")

actual object LocalLibraryIO {
    actual suspend fun listAudioFiles(rootUri: String): List<LocalAudioFile> = withContext(Dispatchers.IO) {
        val fileRoot = rootUri.toFileOrNull()
        if (fileRoot != null) {
            if (!fileRoot.exists() || !fileRoot.isDirectory) return@withContext emptyList()
            return@withContext fileRoot.walkTopDown()
                .filter { it.isFile }
                .filter { it.extension.lowercase() in audioExt }
                .map {
                    LocalAudioFile(
                        uri = it.toURI().toString(),
                        sizeBytes = it.length().coerceAtLeast(0L),
                        modifiedAtMs = it.lastModified().coerceAtLeast(0L),
                        filepath = it.name,
                    )
                }
                .sortedBy { it.uri }
                .toList()
        }

        val ctx = AndroidContextHolder.application
        val treeUri = Uri.parse(rootUri)
        val root = DocumentFile.fromTreeUri(ctx, treeUri) ?: return@withContext emptyList()
        val out = mutableListOf<LocalAudioFile>()
        fun walk(dir: DocumentFile) {
            for (f in dir.listFiles() ?: emptyArray()) {
                if (f.isDirectory) {
                    walk(f)
                } else if (f.isFile && f.name?.substringAfterLast('.', "")?.lowercase() in audioExt) {
                    out.add(
                        LocalAudioFile(
                            uri = f.uri.toString(),
                            sizeBytes = f.length().coerceAtLeast(0L),
                            modifiedAtMs = f.lastModified().coerceAtLeast(0L),
                            filepath = f.name ?: f.uri.lastPathSegment.orEmpty(),
                        ),
                    )
                }
            }
        }
        walk(root)
        out.sortedBy { it.uri }
    }

    actual suspend fun listAudioUris(rootUri: String): List<String> = withContext(Dispatchers.IO) {
        listAudioFiles(rootUri).map { it.uri }
    }

    actual suspend fun fileExists(uri: String): Boolean = withContext(Dispatchers.IO) {
        uri.toFileOrNull()?.let { return@withContext it.isFile }

        val ctx = AndroidContextHolder.application
        DocumentFile.fromSingleUri(ctx, Uri.parse(uri))?.exists() == true
    }

    actual suspend fun readAudioMetadata(uri: String): AudioMetadata = withContext(Dispatchers.IO) {
        val ctx = AndroidContextHolder.application
        val parsed = Uri.parse(uri)
        val retriever = MediaMetadataRetriever()
        try {
            val file = uri.toFileOrNull()
            if (file != null) {
                retriever.setDataSource(file.absolutePath)
            } else {
                retriever.setDataSource(ctx, parsed)
            }
            fun meta(key: Int) = retriever.extractMetadata(key)?.trim()?.takeIf { it.isNotEmpty() }
            val title = meta(MediaMetadataRetriever.METADATA_KEY_TITLE)
            val artist = meta(MediaMetadataRetriever.METADATA_KEY_ARTIST)
                ?: meta(MediaMetadataRetriever.METADATA_KEY_ALBUMARTIST)
            val album = meta(MediaMetadataRetriever.METADATA_KEY_ALBUM)
            val durationStr = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)
            val durationMs = durationStr?.toLongOrNull()?.coerceAtLeast(0L) ?: 0L
            val year = meta(MediaMetadataRetriever.METADATA_KEY_YEAR)?.toIntOrNull()
            val genre = meta(MediaMetadataRetriever.METADATA_KEY_GENRE)
            val rateBits = meta(MediaMetadataRetriever.METADATA_KEY_BITRATE)?.toIntOrNull()
            val bitrateKbps = rateBits?.let { r ->
                when {
                    r <= 0 -> null
                    r >= 500_000 -> r / 1000
                    else -> r / 1000
                }
            }
            AudioMetadata(
                title = title,
                artist = artist,
                album = album,
                durationMs = durationMs,
                year = year,
                genre = genre,
                mood = null,
                style = null,
                bitrateKbps = bitrateKbps,
                audioCodec = null,
            )
        } catch (_: Throwable) {
            AudioMetadata(title = null, artist = null, album = null, durationMs = 0L)
        } finally {
            runCatching { retriever.release() }
        }
    }

    actual suspend fun readLyrics(uri: String): String? = withContext(Dispatchers.IO) {
        val file = uri.toFileOrNull() ?: return@withContext null
        if (!file.isFile) return@withContext null
        val base = file.nameWithoutExtension
        listOf("$base.lrc", "$base.txt")
            .map { File(file.parentFile, it) }
            .firstOrNull { it.isFile }
            ?.readText()
            ?.trim()
            ?.takeIf { it.isNotEmpty() }
    }

    private fun String.toFileOrNull(): File? {
        val parsed = runCatching { Uri.parse(this) }.getOrNull() ?: return null
        if (parsed.scheme != "file") return null
        return File(parsed.path ?: return null)
    }
}

@Composable
actual fun rememberPickLocalFolder(onPicked: (String?) -> Unit): () -> Unit {
    val activity = LocalContext.current as ComponentActivity
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
        if (uri != null) {
            try {
                activity.contentResolver.takePersistableUriPermission(
                    uri,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION,
                )
            } catch (_: SecurityException) {
            }
        }
        onPicked(uri?.toString())
    }
    return remember(launcher) {
        { launcher.launch(null) }
    }
}
