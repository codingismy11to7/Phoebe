package com.phoebe.app.platform

import androidx.compose.runtime.Composable

expect fun createPlatformHttpClient(): io.ktor.client.HttpClient

expect class PlatformStorage() {
    suspend fun readText(name: String): String?
    suspend fun writeText(name: String, value: String)
    suspend fun delete(name: String)
    suspend fun deleteUri(uri: String)
    suspend fun readBytes(name: String): ByteArray?
    suspend fun readUriBytes(uri: String): ByteArray?
    suspend fun writeBytes(name: String, bytes: ByteArray): String
    suspend fun readDownloadDirectory(): String?
    suspend fun writeDownloadDirectory(uri: String?)
    fun defaultDownloadDirectoryLabel(): String
}

@Composable
expect fun rememberPickDownloadDirectory(onPicked: (String?) -> Unit): () -> Unit

expect fun openExternalUrl(url: String)

/** Current wall-clock time, expressed as Unix millis. Platform-specific because
 * `System.currentTimeMillis()` is JVM-only and `kotlinx-datetime` isn't on the
 * classpath. */
expect fun currentTimeMs(): Long

/** Browser canvas rendering is much more sensitive to repeated shadows / custom draws. */
expect fun prefersReducedArtworkEffects(): Boolean

/** Number of album track lists to eagerly fetch while building the first catalog snapshot. */
expect fun catalogTrackPrefetchAlbumCount(): Int

/** Maximum number of catalog prefetch requests to transform at once. */
expect fun catalogTrackPrefetchParallelism(): Int
