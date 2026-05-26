package com.phoebe.app

import com.phoebe.app.platform.PlatformStorage
import com.phoebe.app.platform.resolveWebDownloadObjectUrl
import kotlinx.coroutines.test.runTest
import kotlin.coroutines.resume
import kotlin.coroutines.suspendCoroutine
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class WebDownloadStoragePlaybackTest {
    @Test
    fun streamedBrowserDownloadResolvesToOfflineObjectUrl() = runTest {
        val storage = PlatformStorage()
        val name = "downloads/web-offline-test/song.mp3"
        val bytes = ByteArray(256) { index -> index.toByte() }

        val uri = storage.writeByteStream(name) { sink ->
            sink.write(bytes, 0, bytes.size)
        }

        assertTrue(uri.startsWith("web-download://"))
        assertTrue(resolveDownloadObjectUrl(uri).startsWith("blob:"))

        storage.deleteUri(uri)

        assertEquals("", resolveDownloadObjectUrl(uri))
    }

    private suspend fun resolveDownloadObjectUrl(uri: String): String =
        suspendCoroutine { continuation ->
            resolveWebDownloadObjectUrl(uri) { resolved ->
                continuation.resume(resolved)
            }
        }
}
