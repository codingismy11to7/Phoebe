package com.phoebe.app.sources

import androidx.compose.runtime.Composable

data class AudioMetadata(
    val title: String?,
    val artist: String?,
    val album: String?,
    val durationMs: Long,
    val year: Int? = null,
    val genre: String? = null,
    val bitrateKbps: Int? = null,
    val audioCodec: String? = null,
)

expect object LocalLibraryIO {
    suspend fun listAudioUris(rootUri: String): List<String>
    suspend fun fileExists(uri: String): Boolean
    suspend fun readAudioMetadata(uri: String): AudioMetadata
}

/** Returns a lambda to invoke from UI (e.g. button) to pick a folder; calls [onPicked] with a URI string or null. */
@Composable
expect fun rememberPickLocalFolder(onPicked: (String?) -> Unit): () -> Unit
