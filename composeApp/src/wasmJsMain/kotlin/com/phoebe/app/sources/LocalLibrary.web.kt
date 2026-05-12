package com.phoebe.app.sources

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember

actual object LocalLibraryIO {
    actual suspend fun listAudioUris(rootUri: String): List<String> = emptyList()

    actual suspend fun fileExists(uri: String): Boolean = false

    actual suspend fun readAudioMetadata(uri: String): AudioMetadata =
        AudioMetadata(
            title = null,
            artist = null,
            album = null,
            durationMs = 0L,
        )
}

@Composable
actual fun rememberPickLocalFolder(onPicked: (String?) -> Unit): () -> Unit =
    remember(onPicked) {
        { onPicked(null) }
    }
