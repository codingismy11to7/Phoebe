package com.phoebe.app.ui

import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.ImageBitmapConfig
import androidx.compose.ui.graphics.colorspace.ColorSpace
import androidx.compose.ui.graphics.colorspace.ColorSpaces
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.test.runTest
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class RemoteArtworkCacheTest {
    @AfterTest
    fun tearDown() {
        RemoteArtworkCache.clearForTest()
    }

    @Test
    fun evictsLeastRecentlyUsedImageWhenEntryLimitIsReached() {
        RemoteArtworkCache.configureLimitsForTest(maxEntries = 2, maxEstimatedBytes = Long.MAX_VALUE)

        RemoteArtworkCache.putForTest("a", 128, testImageBitmap(10, 10))
        RemoteArtworkCache.putForTest("b", 128, testImageBitmap(10, 10))
        assertNotNull(RemoteArtworkCache.cached("a", 128))
        RemoteArtworkCache.putForTest("c", 128, testImageBitmap(10, 10))

        assertNotNull(RemoteArtworkCache.cached("a", 128))
        assertNull(RemoteArtworkCache.cached("b", 128))
        assertNotNull(RemoteArtworkCache.cached("c", 128))
        assertEquals(2, RemoteArtworkCache.stats().imageCount)
    }

    @Test
    fun evictsImagesWhenEstimatedByteLimitIsReached() {
        val large = testImageBitmap(20, 20)
        RemoteArtworkCache.configureLimitsForTest(
            maxEntries = 10,
            maxEstimatedBytes = large.width.toLong() * large.height.toLong() * 4L,
        )

        RemoteArtworkCache.putForTest("small", 128, testImageBitmap(10, 10))
        RemoteArtworkCache.putForTest("large", 128, large)

        assertNull(RemoteArtworkCache.cached("small", 128))
        assertNotNull(RemoteArtworkCache.cached("large", 128))
        assertEquals(1, RemoteArtworkCache.stats().imageCount)
    }

    @Test
    fun keepsSeparateEntriesForDifferentDecodeSizes() {
        RemoteArtworkCache.configureLimitsForTest(maxEntries = 10, maxEstimatedBytes = Long.MAX_VALUE)

        RemoteArtworkCache.putForTest("art", 64, testImageBitmap(64, 64))
        RemoteArtworkCache.putForTest("art", 256, testImageBitmap(256, 256))

        assertEquals(64, RemoteArtworkCache.cached("art", 64)?.width)
        assertEquals(256, RemoteArtworkCache.cached("art", 256)?.width)
        assertEquals(2, RemoteArtworkCache.stats().imageCount)
    }

    @Test
    fun listArtworkCanReuseLargerCachedImageForSameUrl() {
        RemoteArtworkCache.configureLimitsForTest(maxEntries = 10, maxEstimatedBytes = Long.MAX_VALUE)

        RemoteArtworkCache.putForTest("art", HeroArtworkMaxDecodeDimension, testImageBitmap(1024, 1024))

        assertEquals(
            1024,
            RemoteArtworkCache.cachedRequested("art", ThumbnailArtworkMaxDecodeDimension)?.width,
        )
    }

    @Test
    fun displayCacheFallsBackToLowerResolutionPreviewForHeroRequests() {
        RemoteArtworkCache.configureLimitsForTest(maxEntries = 10, maxEstimatedBytes = Long.MAX_VALUE)

        RemoteArtworkCache.putForTest("art", ThumbnailArtworkMaxDecodeDimension, testImageBitmap(160, 160))

        assertEquals(
            160,
            RemoteArtworkCache.cachedForDisplay("art", HeroArtworkMaxDecodeDimension)?.width,
        )

        RemoteArtworkCache.putForTest("art", HeroArtworkMaxDecodeDimension, testImageBitmap(1024, 1024))

        assertEquals(
            1024,
            RemoteArtworkCache.cachedForDisplay("art", HeroArtworkMaxDecodeDimension)?.width,
        )
    }

    @Test
    fun trimForMemoryPressureEvictsCachedImages() {
        RemoteArtworkCache.configureLimitsForTest(maxEntries = 40, maxEstimatedBytes = Long.MAX_VALUE)
        repeat(24) { index ->
            RemoteArtworkCache.putForTest("art-$index", 128, testImageBitmap(32, 32))
        }
        assertEquals(24, RemoteArtworkCache.stats().imageCount)

        RemoteArtworkCache.trimForMemoryPressure(aggressive = true)

        assertTrue(RemoteArtworkCache.stats().imageCount <= 10)
    }

    @Test
    fun trimForMemoryPressureDoesNotPermanentlyLowerCacheLimits() {
        RemoteArtworkCache.configureLimitsForTest(maxEntries = 40, maxEstimatedBytes = Long.MAX_VALUE)
        repeat(24) { index ->
            RemoteArtworkCache.putForTest("before-$index", 128, testImageBitmap(32, 32))
        }

        RemoteArtworkCache.trimForMemoryPressure(aggressive = true)
        repeat(24) { index ->
            RemoteArtworkCache.putForTest("after-$index", 128, testImageBitmap(32, 32))
        }

        assertTrue(RemoteArtworkCache.stats().imageCount > 10)
    }

    @Test
    fun clearUnderMemoryPressureDropsAllDecodedImages() {
        RemoteArtworkCache.configureLimitsForTest(maxEntries = 20, maxEstimatedBytes = Long.MAX_VALUE)
        RemoteArtworkCache.putForTest("art", 128, testImageBitmap(32, 32))
        assertEquals(1, RemoteArtworkCache.stats().imageCount)

        RemoteArtworkCache.clearUnderMemoryPressure()

        assertEquals(0, RemoteArtworkCache.stats().imageCount)
        assertNull(RemoteArtworkCache.cached("art", 128))
    }

    @Test
    fun clearUnderMemoryPressureDoesNotPermanentlyLowerCacheLimits() {
        RemoteArtworkCache.configureLimitsForTest(maxEntries = 20, maxEstimatedBytes = Long.MAX_VALUE)

        RemoteArtworkCache.clearUnderMemoryPressure()
        repeat(12) { index ->
            RemoteArtworkCache.putForTest("after-clear-$index", 128, testImageBitmap(32, 32))
        }

        assertEquals(12, RemoteArtworkCache.stats().imageCount)
    }

    @Test
    fun concurrentReadsAndWritesDoNotThrow() {
        runTest {
            RemoteArtworkCache.configureLimitsForTest(maxEntries = 50, maxEstimatedBytes = Long.MAX_VALUE)

            coroutineScope {
                val jobs = List(24) { worker ->
                    async(Dispatchers.Default) {
                        repeat(40) { index ->
                            val url = "art-$worker-$index"
                            RemoteArtworkCache.putForTest(url, 128, testImageBitmap(64, 64))
                            RemoteArtworkCache.cachedRequested(url, ThumbnailArtworkMaxDecodeDimension)
                            RemoteArtworkCache.cachedForDisplay(url, HeroArtworkMaxDecodeDimension)
                        }
                    }
                }
                jobs.awaitAll()
            }
        }
    }

    @Test
    fun downloadMemoryModeTrimsDecodedArtworkCache() {
        RemoteArtworkCache.configureLimitsForTest(maxEntries = 100, maxEstimatedBytes = Long.MAX_VALUE)

        repeat(80) { index ->
            RemoteArtworkCache.putForTest("art-$index", 256, testImageBitmap(256, 256))
        }

        RemoteArtworkCache.configureDownloadMemoryMode(true)

        assertTrue(RemoteArtworkCache.stats().imageCount <= 32)
        assertTrue(RemoteArtworkCache.stats().estimatedBytes <= 4L * 1024L * 1024L)
    }

    @Test
    fun remoteArtworkRequestUrlsFallBackToOriginalUrlAfterSizedUrl() {
        val urls = remoteArtworkRequestUrls(
            "https://images.example/cover.jpg?token=abc",
            maxDecodeDimension = 160,
        )

        assertEquals(
            listOf(
                "https://images.example/cover.jpg?token=abc&width=160&height=160",
                "https://images.example/cover.jpg?token=abc",
            ),
            urls,
        )
    }
}

private fun testImageBitmap(width: Int, height: Int): ImageBitmap = TestImageBitmap(width, height)

private class TestImageBitmap(
    override val width: Int,
    override val height: Int,
) : ImageBitmap {
    override val colorSpace: ColorSpace = ColorSpaces.Srgb
    override val hasAlpha: Boolean = true
    override val config: ImageBitmapConfig = ImageBitmapConfig.Argb8888

    override fun readPixels(
        buffer: IntArray,
        startX: Int,
        startY: Int,
        width: Int,
        height: Int,
        bufferOffset: Int,
        stride: Int,
    ) = Unit

    override fun prepareToDraw() = Unit
}
