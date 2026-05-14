package com.phoebe.app.player

import com.phoebe.app.platform.createPlatformHttpClient
import io.ktor.client.call.body
import io.ktor.client.request.get
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.usePinned
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import platform.Foundation.NSData
import platform.Foundation.create
import platform.UIKit.UIImage

@OptIn(ExperimentalForeignApi::class)
internal object IosArtworkLoader {
    private val httpClient by lazy { createPlatformHttpClient() }
    private val cache = mutableMapOf<String, UIImage>()

    suspend fun load(url: String): UIImage? {
        cache[url]?.let { return it }
        return withContext(Dispatchers.Default) {
            runCatching {
                val bytes: ByteArray = httpClient.get(url).body()
                val data = bytes.toNSData()
                val image = UIImage.imageWithData(data) ?: return@runCatching null
                cache[url] = image
                image
            }.getOrNull()
        }
    }

    fun clear() {
        cache.clear()
    }
}

@OptIn(ExperimentalForeignApi::class)
private fun ByteArray.toNSData(): NSData = usePinned { pinned ->
    NSData.create(bytes = pinned.addressOf(0), length = size.toULong())
}
