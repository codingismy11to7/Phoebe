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
    if (Desktop.isDesktopSupported()) {
        Desktop.getDesktop().browse(URI(url))
    }
}

actual fun currentTimeMs(): Long = System.currentTimeMillis()

actual fun prefersReducedArtworkEffects(): Boolean = false

actual fun catalogTrackPrefetchAlbumCount(): Int = 24

actual fun catalogTrackPrefetchParallelism(): Int = 6
