package com.phoebe.app.platform

import com.phoebe.app.data.PlexClient
import io.ktor.client.HttpClient
import io.ktor.client.engine.darwin.Darwin
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.serialization.kotlinx.json.json
import platform.Foundation.NSDate
import platform.Foundation.NSURL
import platform.Foundation.NSUserDefaults
import platform.Foundation.timeIntervalSince1970
import platform.UIKit.UIApplication

actual fun createPlatformHttpClient(): HttpClient = HttpClient(Darwin) {
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
    private val defaults = NSUserDefaults.standardUserDefaults

    actual suspend fun readText(name: String): String? = defaults.stringForKey(name)

    actual suspend fun writeText(name: String, value: String) {
        defaults.setObject(value, forKey = name)
    }

    actual suspend fun delete(name: String) {
        defaults.removeObjectForKey(name)
    }

    actual suspend fun writeBytes(name: String, bytes: ByteArray): String {
        defaults.setObject(bytes.size.toString(), forKey = name)
        return "phoebe://offline/$name"
    }
}

actual fun openExternalUrl(url: String) {
    NSURL.URLWithString(url)?.let { UIApplication.sharedApplication.openURL(it) }
}

actual fun currentTimeMs(): Long = (NSDate().timeIntervalSince1970 * 1000.0).toLong()

actual fun prefersReducedArtworkEffects(): Boolean = false

actual fun catalogTrackPrefetchAlbumCount(): Int = 24

actual fun catalogTrackPrefetchParallelism(): Int = 6
