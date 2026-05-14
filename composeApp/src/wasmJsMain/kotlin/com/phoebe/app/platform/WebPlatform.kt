package com.phoebe.app.platform

import com.phoebe.app.data.PlexClient
import io.ktor.client.HttpClient
import io.ktor.client.engine.js.Js
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.serialization.kotlinx.json.json
import kotlinx.browser.window

actual fun createPlatformHttpClient(): HttpClient = HttpClient(Js) {
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
    actual suspend fun readText(name: String): String? =
        window.localStorage.getItem(storageKey(name))

    actual suspend fun writeText(name: String, value: String) {
        window.localStorage.setItem(storageKey(name), value)
    }

    actual suspend fun delete(name: String) {
        window.localStorage.removeItem(storageKey(name))
    }

    actual suspend fun writeBytes(name: String, bytes: ByteArray): String {
        val encoded = window.btoa(bytes.toBinaryString())
        window.localStorage.setItem(storageKey(name), encoded)
        return "web-storage://${encodeURIComponent(name)}"
    }

    private fun storageKey(name: String): String = "phoebe:$name"
}

actual fun openExternalUrl(url: String) {
    window.open(url, target = "_blank")
}

actual fun currentTimeMs(): Long = jsDateNow().toLong()

actual fun prefersReducedArtworkEffects(): Boolean = true

actual fun catalogTrackPrefetchAlbumCount(): Int = 6

actual fun catalogTrackPrefetchParallelism(): Int = 2

actual fun isDebugBuild(): Boolean = wasmDebugBuildEnabled()

@OptIn(ExperimentalWasmJsInterop::class)
@JsFun(
    """
    () => {
      if (typeof globalThis.PHOEBE_DEBUG === 'boolean') return globalThis.PHOEBE_DEBUG;
      if (typeof location !== 'undefined') {
        const host = location.hostname;
        if (host === 'localhost' || host === '127.0.0.1') return true;
      }
      return false;
    }
    """,
)
private external fun wasmDebugBuildEnabled(): Boolean

internal actual fun platformLog(tag: String, message: String) {
    println("[$tag] $message")
}

private fun ByteArray.toBinaryString(): String =
    joinToString(separator = "") { (it.toInt() and 0xff).toChar().toString() }

@OptIn(ExperimentalWasmJsInterop::class)
@JsFun("() => Date.now()")
private external fun jsDateNow(): Double

@OptIn(ExperimentalWasmJsInterop::class)
@JsFun("(value) => encodeURIComponent(value)")
private external fun encodeURIComponent(value: String): String
