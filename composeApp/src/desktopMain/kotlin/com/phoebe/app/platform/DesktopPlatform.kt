package com.phoebe.app.platform

import com.phoebe.app.data.PlexClient
import io.ktor.client.HttpClient
import io.ktor.client.engine.cio.CIO
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.serialization.kotlinx.json.json
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.awt.Desktop
import java.io.File
import java.net.URI

actual fun createPlatformHttpClient(): HttpClient = HttpClient(CIO) {
    install(HttpTimeout) {
        requestTimeoutMillis = 60_000
        connectTimeoutMillis = 15_000
        socketTimeoutMillis = 60_000
    }
    install(ContentNegotiation) {
        json(PlexClient.PlexJson)
    }
}

private val storageRoot: File by lazy {
    System.getProperty("phoebe.storage.root")?.let(::File)
        ?: File(System.getProperty("user.home"), ".phoebe")
}

actual class PlatformStorage actual constructor() {
    private val root = storageRoot.also { it.mkdirs() }

    actual suspend fun readText(name: String): String? = withContext(Dispatchers.IO) {
        root.resolve(name).takeIf { it.exists() }?.readText()
    }

    actual suspend fun writeText(name: String, value: String) = withContext(Dispatchers.IO) {
        root.resolve(name).apply {
            parentFile?.mkdirs()
            writeText(value)
        }
        Unit
    }

    actual suspend fun delete(name: String) = withContext(Dispatchers.IO) {
        root.resolve(name).takeIf { it.exists() }?.delete()
        Unit
    }

    actual suspend fun writeBytes(name: String, bytes: ByteArray): String = withContext(Dispatchers.IO) {
        root.resolve(name).apply {
            parentFile?.mkdirs()
            writeBytes(bytes)
        }.toURI().toString()
    }
}

actual fun openExternalUrl(url: String) {
    val desktop = runCatching { if (Desktop.isDesktopSupported()) Desktop.getDesktop() else null }.getOrNull()
    if (desktop != null && desktop.isSupported(Desktop.Action.BROWSE)) {
        desktop.browse(URI(url))
        return
    }
    openExternalUrlWithSystemHandler(url)
}

private fun openExternalUrlWithSystemHandler(url: String) {
    val os = System.getProperty("os.name").orEmpty().lowercase()
    val command = when {
        "mac" in os -> arrayOf("open", url)
        "win" in os -> arrayOf("rundll32", "url.dll,FileProtocolHandler", url)
        else -> arrayOf("xdg-open", url)
    }
    ProcessBuilder(*command).start()
}

actual fun currentTimeMs(): Long = System.currentTimeMillis()

actual fun prefersReducedArtworkEffects(): Boolean = false

actual fun catalogTrackPrefetchAlbumCount(): Int = 24

actual fun catalogTrackPrefetchParallelism(): Int = 6

actual fun isDebugBuild(): Boolean =
    System.getProperty("phoebe.debug")?.toBooleanStrictOrNull() ?: false

internal actual fun platformLog(tag: String, message: String) {
    println("[$tag] $message")
}
