package com.phoebe.app.sources

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember

actual object LocalLibraryIO {
    actual suspend fun listAudioUris(rootUri: String): List<String> {
        if (!rootUri.startsWith(TestRootPrefix)) return emptyList()
        val files = rootUri.substringAfter("?files=", missingDelimiterValue = "")
            .split('|')
            .map { it.trim('/') }
            .filter { it.isNotBlank() }
            .filter { it.substringAfterLast('.', "").lowercase() in audioExt }
        val root = rootUri.substringBefore('?').trimEnd('/')
        return files.map { "$root/$it" }
    }

    actual suspend fun fileExists(uri: String): Boolean =
        uri.startsWith(TestRootPrefix) && uri.substringAfterLast('/').contains('.')

    actual suspend fun readAudioMetadata(uri: String): AudioMetadata {
        val name = uri.substringBefore('?').substringAfterLast('/').substringBeforeLast('.')
        if (uri.startsWith(TestRootPrefix)) {
            return AudioMetadata(
                title = name.ifBlank { null },
                artist = "Web test files",
                album = "Web MP3 folder",
                durationMs = 0L,
                audioCodec = "mp3",
            )
        }
        return AudioMetadata(
            title = null,
            artist = null,
            album = null,
            durationMs = 0L,
        )
    }

    private const val TestRootPrefix = "phoebe-test://"
    private val audioExt = setOf("mp3", "m4a", "flac", "wav", "aac", "ogg", "opus")
}

@Composable
actual fun rememberPickLocalFolder(onPicked: (String?) -> Unit): () -> Unit =
    remember(onPicked) {
        { onPicked(null) }
    }
