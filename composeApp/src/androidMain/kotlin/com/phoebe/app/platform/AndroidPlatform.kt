package com.phoebe.app.platform

import android.content.Context
import android.content.Intent
import android.net.Uri
import com.phoebe.app.AndroidContextHolder
import com.phoebe.app.data.PlexClient
import io.ktor.client.HttpClient
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.engine.okhttp.OkHttp
import io.ktor.serialization.kotlinx.json.json
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

actual fun createPlatformHttpClient(): HttpClient = HttpClient(OkHttp) {
    install(HttpTimeout) {
        requestTimeoutMillis = 60_000
        connectTimeoutMillis = 15_000
        socketTimeoutMillis = 60_000
    }
    install(ContentNegotiation) {
        json(PlexClient.PlexJson)
    }
}

actual class PlatformStorage actual constructor() {
    private val root: File
        get() {
            System.getProperty("phoebe.storage.root")?.let { return File(it).also { f -> f.mkdirs() } }
            return AndroidContextHolder.application.filesDir.resolve("phoebe").also { it.mkdirs() }
        }

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
    val context: Context = AndroidContextHolder.application
    val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    context.startActivity(intent)
}

actual fun currentTimeMs(): Long = System.currentTimeMillis()

actual fun prefersReducedArtworkEffects(): Boolean = false

actual fun catalogTrackPrefetchAlbumCount(): Int = 0

actual fun catalogTrackPrefetchParallelism(): Int = 1
