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
import java.net.URLDecoder

private val audioExt = setOf("mp3", "m4a", "flac", "wav", "aac", "ogg", "opus")

actual object LocalLibraryIO {
    actual suspend fun listAudioUris(rootUri: String): List<String> = withContext(Dispatchers.IO) {
        val ctx = AndroidContextHolder.application
        val treeUri = Uri.parse(rootUri)
        val root = DocumentFile.fromTreeUri(ctx, treeUri) ?: return@withContext emptyList()
        val out = mutableListOf<String>()
        fun walk(dir: DocumentFile) {
            for (f in dir.listFiles() ?: emptyArray()) {
                if (f.isDirectory) walk(f)
                else if (f.isFile && f.name?.substringAfterLast('.', "")?.lowercase() in audioExt) {
                    out.add(f.uri.toString())
                }
            }
        }
        walk(root)
        out
    }

    actual suspend fun fileExists(uri: String): Boolean = withContext(Dispatchers.IO) {
        val ctx = AndroidContextHolder.application
        DocumentFile.fromSingleUri(ctx, Uri.parse(uri))?.exists() == true
    }

    actual suspend fun readAudioMetadata(uri: String): AudioMetadata = withContext(Dispatchers.IO) {
        val ctx = AndroidContextHolder.application
        val parsed = Uri.parse(uri)
        val retriever = MediaMetadataRetriever()
        try {
            retriever.setDataSource(ctx, parsed)
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
                bitrateKbps = bitrateKbps,
                audioCodec = null,
            )
        } catch (_: Throwable) {
            AudioMetadata(title = null, artist = null, album = null, durationMs = 0L)
        } finally {
            runCatching { retriever.release() }
        }
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
