package com.phoebe.app.ui

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.Crossfade
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import phoebe.composeapp.generated.resources.Res
import phoebe.composeapp.generated.resources.phoebe_bird
import phoebe.composeapp.generated.resources.phoebe_icon_rounded
import org.jetbrains.compose.resources.painterResource
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.composed
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.zIndex
import com.phoebe.app.AppState
import com.phoebe.app.data.catalogAlbumsForArtist
import com.phoebe.app.data.catalogTracksForArtist
import com.phoebe.app.domain.Album
import com.phoebe.app.domain.AppScreen
import com.phoebe.app.domain.Artist
import com.phoebe.app.domain.LibraryColumnVisibility
import com.phoebe.app.domain.LibrarySortBy
import com.phoebe.app.domain.LibraryUiPreferences
import com.phoebe.app.domain.CatalogSnapshot
import com.phoebe.app.domain.LocalFolderMediaSourceConfig
import com.phoebe.app.domain.MediaSourcesState
import com.phoebe.app.domain.MusicLibrary
import com.phoebe.app.domain.PlexServer
import com.phoebe.app.domain.PlexSession
import com.phoebe.app.domain.Playlist
import com.phoebe.app.domain.RepeatMode
import com.phoebe.app.domain.Track
import com.phoebe.app.data.cachedArtworkPathForUrl
import com.phoebe.app.data.applyEmbyFamilyArtworkAuth
import com.phoebe.app.data.isEmbyFamilyArtworkUrl
import com.phoebe.app.domain.isLocalMediaPlayback
import com.phoebe.app.domain.isRemoteLibraryTrack
import com.phoebe.app.domain.supportsPlexPlaylists
import com.phoebe.app.platform.createPlatformHttpClient
import com.phoebe.app.platform.currentTimeMs
import com.phoebe.app.platform.PlatformStorage
import com.phoebe.app.platform.prefersReducedArtworkEffects
import com.phoebe.app.platform.remoteArtworkCacheMaxEstimatedBytes
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlin.concurrent.Volatile
import com.phoebe.app.sources.rememberPickLocalFolder
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.get
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.yield
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.sin

internal const val ThumbnailArtworkMaxDecodeDimension = 160

/** Default decode cap for list/grid tiles (~48–92dp). Hero art should pass a larger value explicitly. */
internal const val ListArtworkMaxDecodeDimension = 256

/** Decode cap for single, large artwork surfaces such as the mobile full-screen player. */
internal const val HeroArtworkMaxDecodeDimension = 1024

@Composable
internal fun SectionLabel(label: String, color: Color) {
    Text(label.uppercase(), color = color, fontSize = 11.sp, fontWeight = FontWeight.Bold, letterSpacing = 0.08.em)
}

@Composable
internal fun ArtworkImage(
    seed: String,
    thumbUrl: String?,
    modifier: Modifier = Modifier,
    radius: Dp = 10.dp,
    shape: Shape = RoundedCornerShape(radius),
    elevated: Boolean = true,
    maxDecodeDimension: Int = ListArtworkMaxDecodeDimension,
    fallbackThumbUrl: String? = null,
    artworkStaggerMs: Long = 0L,
    alignment: Alignment = Alignment.Center,
) {
    val imageState = rememberRemoteImageState(thumbUrl, maxDecodeDimension, fallbackThumbUrl, artworkStaggerMs)
    val imageModifier = when {
        !elevated || prefersReducedArtworkEffects() -> modifier.clip(shape)
        else -> modifier
            .shadow(18.dp, shape, ambientColor = Color.Black.copy(alpha = 0.38f))
            .clip(shape)
    }

    Crossfade(targetState = imageState, label = "artwork-load-state") { state ->
        when (state) {
            is RemoteImageLoadState.Ready -> {
                Image(
                    bitmap = state.image,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    alignment = alignment,
                    modifier = imageModifier,
                )
            }
            is RemoteImageLoadState.Preview -> {
                Image(
                    bitmap = state.image,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    alignment = alignment,
                    modifier = imageModifier,
                )
            }
            RemoteImageLoadState.Loading -> {
                ArtworkLoadingSlot(modifier, radius, shape = shape, elevated = elevated)
            }
            RemoteImageLoadState.Missing -> {
                AlbumArtwork(seed, modifier, radius, shape = shape, elevated = elevated)
            }
        }
    }
}

@Composable
internal fun TrackArtworkImage(
    track: Track,
    modifier: Modifier = Modifier,
    radius: Dp = 10.dp,
    shape: Shape = RoundedCornerShape(radius),
    elevated: Boolean = true,
    maxDecodeDimension: Int = ListArtworkMaxDecodeDimension,
    artworkStaggerMs: Long = 0L,
    alignment: Alignment = Alignment.Center,
) {
    ArtworkImage(
        seed = track.album,
        thumbUrl = track.localArtworkUri,
        modifier = modifier,
        radius = radius,
        shape = shape,
        elevated = elevated,
        maxDecodeDimension = maxDecodeDimension,
        fallbackThumbUrl = track.thumbUrl,
        artworkStaggerMs = artworkStaggerMs,
        alignment = alignment,
    )
}

@Composable
internal fun rememberRemoteImage(
    url: String?,
    maxDecodeDimension: Int = ListArtworkMaxDecodeDimension,
    fallbackUrl: String? = null,
    artworkStaggerMs: Long = 0L,
): ImageBitmap? = rememberRemoteImageState(url, maxDecodeDimension, fallbackUrl, artworkStaggerMs).image

private sealed interface RemoteImageLoadState {
    val image: ImageBitmap?

    data object Loading : RemoteImageLoadState {
        override val image: ImageBitmap? = null
    }

    data object Missing : RemoteImageLoadState {
        override val image: ImageBitmap? = null
    }

    data class Preview(override val image: ImageBitmap) : RemoteImageLoadState
    data class Ready(override val image: ImageBitmap) : RemoteImageLoadState
}

@Composable
private fun rememberRemoteImageState(
    url: String?,
    maxDecodeDimension: Int = ListArtworkMaxDecodeDimension,
    fallbackUrl: String? = null,
    artworkStaggerMs: Long = 0L,
): RemoteImageLoadState {
    val primary = url?.takeIf { it.isNotBlank() }
    val fallbackSource = fallbackUrl?.takeIf { it.isNotBlank() }
    val target = primary ?: fallbackSource ?: return RemoteImageLoadState.Missing
    val fallback = fallbackSource?.takeIf { it != target }
    val artworkLoadsEnabled = LocalArtworkLoadingEnabled.current
    val previewDecodeDimensions = progressivePreviewDecodeDimensions(maxDecodeDimension)
    return produceState(
        initialValue = cachedStateForDisplay(target, maxDecodeDimension, fallback),
        target,
        fallback,
        maxDecodeDimension,
        artworkLoadsEnabled,
        artworkStaggerMs,
    ) {
        value = cachedStateForDisplay(target, maxDecodeDimension, fallback)
        while (isActive) {
            RemoteArtworkCache.cachedRequested(target, maxDecodeDimension, fallback)?.let {
                value = RemoteImageLoadState.Ready(it)
                return@produceState
            }
            if (!artworkLoadsEnabled) {
                delay(250L)
                continue
            }
            val delayMs = if (previewDecodeDimensions.isEmpty()) {
                RemoteArtworkCache.staggerDelayMs(target) + artworkStaggerMs
            } else {
                artworkStaggerMs
            }
            if (delayMs > 0) delay(delayMs)
            if (value == RemoteImageLoadState.Loading) {
                value = RemoteArtworkCache.awaitPreview(target, fallback, previewDecodeDimensions)?.let {
                    RemoteImageLoadState.Preview(it)
                } ?: cachedStateForDisplay(target, maxDecodeDimension, fallback)
            }
            val requested = RemoteArtworkCache.awaitLoad(target, maxDecodeDimension)
                ?: fallback?.let { RemoteArtworkCache.awaitLoad(it, maxDecodeDimension) }
                ?: RemoteArtworkCache.cachedRequested(target, maxDecodeDimension, fallback)
            if (requested != null) {
                value = RemoteImageLoadState.Ready(requested)
                return@produceState
            }
            val displayState = cachedStateForDisplay(target, maxDecodeDimension, fallback)
            value = when {
                displayState !is RemoteImageLoadState.Loading -> displayState
                value == RemoteImageLoadState.Loading -> RemoteImageLoadState.Missing
                else -> value
            }
            delay(if (value == RemoteImageLoadState.Missing) 10_000L else 10L * 60L * 1000L)
        }
    }.value
}

private fun cachedStateForDisplay(url: String, maxDecodeDimension: Int, fallbackUrl: String? = null): RemoteImageLoadState {
    RemoteArtworkCache.cachedRequested(url, maxDecodeDimension, fallbackUrl)?.let {
        return RemoteImageLoadState.Ready(it)
    }
    progressivePreviewDecodeDimensions(maxDecodeDimension).forEach { previewDimension ->
        RemoteArtworkCache.cachedRequested(url, previewDimension, fallbackUrl)?.let {
            return RemoteImageLoadState.Preview(it)
        }
    }
    return RemoteImageLoadState.Loading
}

private fun progressivePreviewDecodeDimensions(maxDecodeDimension: Int): List<Int> {
    val requested = maxDecodeDimension.takeIf { it > 0 } ?: Int.MAX_VALUE
    if (requested <= ListArtworkMaxDecodeDimension) return emptyList()
    return listOf(ListArtworkMaxDecodeDimension, ThumbnailArtworkMaxDecodeDimension)
        .filter { it < requested }
        .distinct()
}

internal data class ArtworkCacheStats(
    val imageCount: Int,
    val estimatedBytes: Long,
    val inFlightCount: Int,
)

internal object RemoteArtworkCache {
    private const val DefaultMaxEntries = 300
    private const val FailedLoadRetryMs = 10L * 60L * 1000L
    private const val DefaultLoadPermits = 2
    private const val PacedMinIntervalMs = 72L
    private const val BurstMinIntervalMs = 24L
    private const val DownloadModeMaxEntries = 32
    private const val DownloadModeMaxEstimatedBytes = 4L * 1024L * 1024L

    @Volatile
    private var pacingEnabled: Boolean = false

    @Volatile
    private var lastLoadStartMs = 0L

    private data class CacheKey(
        val url: String,
        val maxDecodeDimension: Int,
    )

    private val images = mutableMapOf<CacheKey, ImageBitmap>()

    internal val httpClient: HttpClient by lazy { createPlatformHttpClient() }
    private val storage: PlatformStorage by lazy { PlatformStorage() }
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private var gate = Semaphore(permits = DefaultLoadPermits)
    private val mutex = Mutex()
    private val inFlight = mutableMapOf<CacheKey, Deferred<ImageBitmap?>>()
    private val estimatedBytesByKey = mutableMapOf<CacheKey, Long>()
    private val accessOrder = LinkedHashMap<CacheKey, Unit>()
    private val recentFailures = mutableMapOf<CacheKey, Long>()
    private val platformMaxEstimatedBytes = remoteArtworkCacheMaxEstimatedBytes().coerceAtLeast(4L * 1024L * 1024L)
    private var maxEntries = DefaultMaxEntries
    private var maxEstimatedBytes = platformMaxEstimatedBytes
    private var estimatedBytes = 0L

    fun cached(url: String, maxDecodeDimension: Int = ListArtworkMaxDecodeDimension): ImageBitmap? {
        val key = CacheKey(url, maxDecodeDimension.normalizedDecodeDimension())
        val image = images[key] ?: return null
        touch(key)
        return image
    }

    fun cachedRequested(url: String, maxDecodeDimension: Int, fallbackUrl: String? = null): ImageBitmap? =
        cached(url, maxDecodeDimension)
            ?: fallbackUrl?.let { cached(it, maxDecodeDimension) }
            ?: cachedListArtworkFromAnyDimension(url, maxDecodeDimension)
            ?: fallbackUrl?.let { cachedListArtworkFromAnyDimension(it, maxDecodeDimension) }

    fun cachedForDisplay(url: String, maxDecodeDimension: Int, fallbackUrl: String? = null): ImageBitmap? {
        cachedRequested(url, maxDecodeDimension, fallbackUrl)?.let { return it }
        progressivePreviewDecodeDimensions(maxDecodeDimension).forEach { previewDimension ->
            cachedRequested(url, previewDimension, fallbackUrl)?.let { return it }
        }
        return null
    }

    fun staggerDelayMs(url: String): Long = (url.hashCode() and 0x3F).toLong() * 12L

    /** Spreads decode/network work across frames — use after a large catalog snapshot lands in the UI. */
    fun configurePacingEnabled(enabled: Boolean) {
        pacingEnabled = enabled
    }

    fun configureDownloadMemoryMode(enabled: Boolean) {
        if (enabled) {
            maxEntries = DownloadModeMaxEntries
            maxEstimatedBytes = minOf(platformMaxEstimatedBytes, DownloadModeMaxEstimatedBytes)
        } else {
            maxEntries = DefaultMaxEntries
            maxEstimatedBytes = platformMaxEstimatedBytes
        }
        trimToLimits()
    }

    private suspend fun paceBeforeLoad() {
        val minInterval = if (pacingEnabled) PacedMinIntervalMs else BurstMinIntervalMs
        val now = currentTimeMs()
        val wait = minInterval - (now - lastLoadStartMs)
        if (wait > 0) delay(wait)
        lastLoadStartMs = currentTimeMs()
    }

    suspend fun awaitLoad(url: String, maxDecodeDimension: Int = ListArtworkMaxDecodeDimension): ImageBitmap? {
        val key = CacheKey(url, maxDecodeDimension.normalizedDecodeDimension())
        cached(key)?.let { return it }
        if (!shouldRetryFailedLoad(key)) return null

        val job = mutex.withLock {
            cached(key)?.let { return it }
            if (!shouldRetryFailedLoad(key)) return null
            inFlight[key] ?: scope.async {
                try {
                    fetchAndDecode(key)
                } finally {
                    mutex.withLock { inFlight.remove(key) }
                }
            }.also { inFlight[key] = it }
        }
        return job.await()
    }

    suspend fun awaitPreview(url: String, fallbackUrl: String?, previewDecodeDimensions: List<Int>): ImageBitmap? {
        previewDecodeDimensions.forEach { previewDimension ->
            cachedRequested(url, previewDimension, fallbackUrl)?.let { return it }
        }
        previewDecodeDimensions.forEach { previewDimension ->
            awaitLoad(url, previewDimension)?.let { return it }
            fallbackUrl?.let { awaitLoad(it, previewDimension) }?.let { return it }
        }
        return null
    }

    private suspend fun fetchAndDecode(key: CacheKey): ImageBitmap? {
        cached(key)?.let { return it }
        return gate.withPermit {
            cached(key)?.let { return@withPermit it }
            paceBeforeLoad()
            val url = key.url
            val remote = url.startsWith("http://") || url.startsWith("https://")
            val decoded = runCatching {
                val fetchUrl = url.withRequestImageSize(key.maxDecodeDimension)
                val bytes: ByteArray = if (remote) {
                    runCatching {
                        httpClient.get(fetchUrl) {
                            applyEmbyFamilyArtworkAuth(url)
                        }.body<ByteArray>()
                    }.getOrElse {
                        storage.readBytes(cachedArtworkPathForUrl(url)) ?: return@runCatching null
                    }
                } else {
                    storage.readUriBytes(url) ?: return@runCatching null
                }
                yield()
                decodeImageBitmap(bytes, key.maxDecodeDimension)
            }.getOrNull()
            if (decoded != null) {
                put(key, decoded)
                decoded
            } else {
                recentFailures[key] = currentTimeMs()
                null
            }
        }
    }

    private fun cached(key: CacheKey): ImageBitmap? {
        val image = images[key] ?: return null
        touch(key)
        return image
    }

    private fun put(key: CacheKey, image: ImageBitmap) {
        val newBytes = image.estimatedBytes()
        estimatedBytes -= estimatedBytesByKey[key] ?: 0L
        images[key] = image
        estimatedBytesByKey[key] = newBytes
        estimatedBytes += newBytes
        clearFailuresForUrl(key.url)
        touch(key)
        trimToLimits()
    }

    private fun cachedListArtworkFromAnyDimension(url: String, maxDecodeDimension: Int): ImageBitmap? {
        val requested = maxDecodeDimension.normalizedDecodeDimension()
        if (requested > ListArtworkMaxDecodeDimension) return null
        val candidate = images.keys
            .asSequence()
            .filter { it.url == url }
            .sortedWith(
                compareBy<CacheKey> { if (it.maxDecodeDimension >= requested) 0 else 1 }
                    .thenBy { kotlin.math.abs(it.maxDecodeDimension - requested) },
            )
            .firstOrNull()
            ?: return null
        return cached(candidate)
    }

    private fun clearFailuresForUrl(url: String) {
        recentFailures.keys.removeAll { it.url == url }
    }

    private fun touch(key: CacheKey) {
        accessOrder.remove(key)
        accessOrder[key] = Unit
    }

    private fun trimToLimits() {
        while (images.size > maxEntries || estimatedBytes > maxEstimatedBytes) {
            val eldest = accessOrder.keys.firstOrNull() ?: return
            accessOrder.remove(eldest)
            images.remove(eldest)
            estimatedBytes -= estimatedBytesByKey.remove(eldest) ?: 0L
            recentFailures.remove(eldest)
        }
    }

    private fun shouldRetryFailedLoad(key: CacheKey): Boolean {
        val failedAt = recentFailures[key] ?: return true
        val retry = currentTimeMs() - failedAt >= FailedLoadRetryMs
        if (retry) recentFailures.remove(key)
        return retry
    }

    fun stats(): ArtworkCacheStats =
        ArtworkCacheStats(
            imageCount = images.size,
            estimatedBytes = estimatedBytes,
            inFlightCount = inFlight.size,
        )

    internal fun putForTest(url: String, maxDecodeDimension: Int, image: ImageBitmap) {
        put(CacheKey(url, maxDecodeDimension.normalizedDecodeDimension()), image)
    }

    internal fun clearForTest() {
        images.clear()
        inFlight.clear()
        estimatedBytesByKey.clear()
        accessOrder.clear()
        recentFailures.clear()
        estimatedBytes = 0L
        maxEntries = DefaultMaxEntries
        maxEstimatedBytes = platformMaxEstimatedBytes
    }

    internal fun configureLimitsForTest(maxEntries: Int = DefaultMaxEntries, maxEstimatedBytes: Long = platformMaxEstimatedBytes) {
        this.maxEntries = max(1, maxEntries)
        this.maxEstimatedBytes = max(1L, maxEstimatedBytes)
        trimToLimits()
    }

    private fun Int.normalizedDecodeDimension(): Int =
        takeIf { it > 0 } ?: Int.MAX_VALUE

    private fun ImageBitmap.estimatedBytes(): Long =
        width.toLong() * height.toLong() * 4L
}

/** Ask remote servers for a smaller JPEG when the URL supports sizing query params. */
private fun String.withRequestImageSize(maxDecodeDimension: Int): String {
    if (!startsWith("http://") && !startsWith("https://")) return this
    val pixels = maxDecodeDimension.coerceIn(64, HeroArtworkMaxDecodeDimension)
    val separator = if (contains('?')) "&" else "?"
    return when {
        contains("maxWidth=", ignoreCase = true) || contains("maxHeight=", ignoreCase = true) -> this
        contains("width=", ignoreCase = true) || contains("height=", ignoreCase = true) -> this
        isEmbyFamilyArtworkUrl() -> "$this${separator}maxWidth=$pixels&maxHeight=$pixels"
        else -> "$this${separator}width=$pixels&height=$pixels"
    }
}

@Composable
internal fun AlbumArtwork(
    seed: String,
    modifier: Modifier = Modifier,
    radius: Dp = 10.dp,
    shape: Shape = RoundedCornerShape(radius),
    elevated: Boolean = true,
) {
    if (!elevated || prefersReducedArtworkEffects()) {
        Box(
            modifier
                .clip(shape)
                .background(ArtworkBrush(seed)),
        )
        return
    }
    Box(
        modifier
            .shadow(18.dp, shape, ambientColor = Color.Black.copy(alpha = 0.38f))
            .clip(shape)
            .background(ArtworkBrush(seed)),
    ) {
        Canvas(Modifier.fillMaxSize()) {
            drawRect(Brush.verticalGradient(listOf(Color(0xFF17345E), Color(0xFF7F5C91), Color(0xFF162033))), alpha = 0.94f)
            drawCircle(Color.White.copy(alpha = 0.08f), radius = size.minDimension * 0.52f, center = Offset(size.width * 0.58f, size.height * 0.34f))
            drawRect(Color(0x33200630), topLeft = Offset(0f, size.height * 0.58f), size = Size(size.width, size.height * 0.42f))
            drawLine(
                color = Color.White.copy(alpha = 0.18f),
                start = Offset(0f, size.height * 0.61f),
                end = Offset(size.width, size.height * 0.61f),
                strokeWidth = 1.dp.toPx(),
            )
            repeat(28) { star ->
                val x = ((star * 47) % 100) / 100f * size.width
                val y = ((star * 29) % 48) / 100f * size.height
                drawCircle(Color.White.copy(alpha = 0.35f), radius = 0.8.dp.toPx(), center = Offset(x, y))
            }
            val figureX = size.width * 0.5f
            val groundY = size.height * 0.69f
            drawCircle(Color(0xFF050710), radius = size.width * 0.018f, center = Offset(figureX, groundY - size.height * 0.12f))
            drawRoundRect(
                color = Color(0xFF050710),
                topLeft = Offset(figureX - size.width * 0.018f, groundY - size.height * 0.105f),
                size = Size(size.width * 0.036f, size.height * 0.13f),
            )
            val reflection = Path().apply {
                moveTo(figureX, groundY + size.height * 0.02f)
                lineTo(figureX - size.width * 0.025f, size.height * 0.84f)
                lineTo(figureX + size.width * 0.012f, size.height * 0.84f)
                close()
            }
            drawPath(reflection, Color.Black.copy(alpha = 0.26f))
            drawRect(
                Brush.verticalGradient(listOf(Color.Transparent, Color.Black.copy(alpha = 0.26f))),
                topLeft = Offset.Zero,
                size = size,
            )
        }
    }
}

@Composable
private fun ArtworkLoadingSlot(
    modifier: Modifier = Modifier,
    radius: Dp = 10.dp,
    shape: Shape = RoundedCornerShape(radius),
    elevated: Boolean = true,
) {
    val borderTrackColor = Color.White.copy(alpha = 0.05f)
    val borderProgressColor = PhoebeUi.accentLight.copy(alpha = 0.86f)
    val slotModifier = when {
        !elevated || prefersReducedArtworkEffects() -> modifier.clip(shape)
        else -> modifier
            .shadow(18.dp, shape, ambientColor = Color.Black.copy(alpha = 0.24f))
            .clip(shape)
    }
    Box(
        slotModifier
            .background(
                Brush.verticalGradient(
                    listOf(
                        PhoebeUi.panel.copy(alpha = 0.82f),
                        PhoebeUi.canvasBackground.copy(alpha = 0.72f),
                    ),
                ),
            )
            .border(BorderStroke(1.dp, PhoebeUi.border.copy(alpha = 0.42f)), shape),
    ) {
        PhoebeLoadingBorder(
            modifier = Modifier.fillMaxSize(),
            radius = radius,
            trackColor = borderTrackColor,
            progressColor = borderProgressColor,
            label = "artwork-loading-border",
        )
    }
}

@Composable
internal fun PhoebeLoadingBorder(
    modifier: Modifier = Modifier,
    radius: Dp = 10.dp,
    trackColor: Color = Color.White.copy(alpha = 0.05f),
    progressColor: Color = PhoebeUi.accentLight.copy(alpha = 0.86f),
    strokeWidth: Dp = 2.dp,
    label: String = "loading-border",
) {
    val animatedProgress by rememberInfiniteTransition(label = label)
        .animateFloat(
            initialValue = 0f,
            targetValue = 1f,
            animationSpec = infiniteRepeatable(
                animation = tween(durationMillis = 1500, easing = LinearEasing),
            ),
            label = "$label-progress",
        )
    Canvas(modifier) {
        val strokePx = strokeWidth.toPx()
        val inset = strokePx / 2f
        val left = inset
        val top = inset
        val right = size.width - inset
        val bottom = size.height - inset
        if (right <= left || bottom <= top) return@Canvas

        val pathWidth = right - left
        val pathHeight = bottom - top
        val cornerRadius = radius.toPx().coerceIn(0f, minOf(pathWidth, pathHeight) / 2f)
        drawRoundRect(
            color = trackColor,
            topLeft = Offset(inset, inset),
            size = Size(size.width - strokePx, size.height - strokePx),
            cornerRadius = CornerRadius(cornerRadius, cornerRadius),
            style = Stroke(width = strokePx),
        )

        val perimeter = roundedRectPerimeter(pathWidth, pathHeight, cornerRadius)
        if (perimeter <= 0f) return@Canvas

        val segmentLength = perimeter * 0.28f
        val start = animatedProgress * perimeter
        val segmentPath = Path()
        val sampleStep = 2.dp.toPx().coerceAtLeast(1f)
        var traveled = 0f
        val firstPoint = roundedRectPointAt(
            distance = start,
            perimeter = perimeter,
            left = left,
            top = top,
            right = right,
            bottom = bottom,
            cornerRadius = cornerRadius,
        )

        segmentPath.moveTo(firstPoint.x, firstPoint.y)
        while (traveled < segmentLength) {
            traveled = minOf(segmentLength, traveled + sampleStep)
            val nextPoint = roundedRectPointAt(
                distance = start + traveled,
                perimeter = perimeter,
                left = left,
                top = top,
                right = right,
                bottom = bottom,
                cornerRadius = cornerRadius,
            )
            segmentPath.lineTo(nextPoint.x, nextPoint.y)
        }
        drawPath(
            path = segmentPath,
            color = progressColor,
            style = Stroke(width = strokePx, cap = StrokeCap.Round, join = StrokeJoin.Round),
        )
    }
}

private fun roundedRectPerimeter(width: Float, height: Float, cornerRadius: Float): Float {
    val horizontal = (width - cornerRadius * 2f).coerceAtLeast(0f)
    val vertical = (height - cornerRadius * 2f).coerceAtLeast(0f)
    return (horizontal + vertical) * 2f + (PI.toFloat() * cornerRadius * 2f)
}

private fun roundedRectPointAt(
    distance: Float,
    perimeter: Float,
    left: Float,
    top: Float,
    right: Float,
    bottom: Float,
    cornerRadius: Float,
): Offset {
    val width = right - left
    val height = bottom - top
    val radius = cornerRadius.coerceIn(0f, minOf(width, height) / 2f)
    val horizontal = (width - radius * 2f).coerceAtLeast(0f)
    val vertical = (height - radius * 2f).coerceAtLeast(0f)
    val arcLength = PI.toFloat() * radius / 2f
    val d = ((distance % perimeter) + perimeter) % perimeter
    var cursor = horizontal

    if (d <= cursor) return Offset(left + radius + d, top)
    if (arcLength > 0f && d <= cursor + arcLength) {
        return roundedRectArcPoint(
            center = Offset(right - radius, top + radius),
            radius = radius,
            startRadians = -PI / 2.0,
            sweepRadians = PI / 2.0,
            distance = d - cursor,
            arcLength = arcLength,
        )
    }
    cursor += arcLength

    if (d <= cursor + vertical) return Offset(right, top + radius + d - cursor)
    cursor += vertical
    if (arcLength > 0f && d <= cursor + arcLength) {
        return roundedRectArcPoint(
            center = Offset(right - radius, bottom - radius),
            radius = radius,
            startRadians = 0.0,
            sweepRadians = PI / 2.0,
            distance = d - cursor,
            arcLength = arcLength,
        )
    }
    cursor += arcLength

    if (d <= cursor + horizontal) return Offset(right - radius - (d - cursor), bottom)
    cursor += horizontal
    if (arcLength > 0f && d <= cursor + arcLength) {
        return roundedRectArcPoint(
            center = Offset(left + radius, bottom - radius),
            radius = radius,
            startRadians = PI / 2.0,
            sweepRadians = PI / 2.0,
            distance = d - cursor,
            arcLength = arcLength,
        )
    }
    cursor += arcLength

    if (d <= cursor + vertical) return Offset(left, bottom - radius - (d - cursor))
    cursor += vertical
    if (arcLength > 0f) {
        return roundedRectArcPoint(
            center = Offset(left + radius, top + radius),
            radius = radius,
            startRadians = PI,
            sweepRadians = PI / 2.0,
            distance = d - cursor,
            arcLength = arcLength,
        )
    }
    return Offset(left, top)
}

private fun roundedRectArcPoint(
    center: Offset,
    radius: Float,
    startRadians: Double,
    sweepRadians: Double,
    distance: Float,
    arcLength: Float,
): Offset {
    val angle = startRadians + sweepRadians * (distance / arcLength).toDouble()
    return Offset(
        x = center.x + cos(angle).toFloat() * radius,
        y = center.y + sin(angle).toFloat() * radius,
    )
}


internal fun ArtworkBrush(seed: String): Brush {
    val hash = seed.fold(0) { acc, char -> acc * 31 + char.code }
    val palettes = listOf(
        listOf(Color(0xFF123969), Color(0xFFB97596), Color(0xFF061323)),
        listOf(Color(0xFF1B234F), Color(0xFFED704C), Color(0xFF111827)),
        listOf(Color(0xFF14395B), Color(0xFF5C8F55), Color(0xFF10151F)),
        listOf(Color(0xFF11243A), Color(0xFF9B4DFF), Color(0xFF0A0D14)),
    )
    return Brush.linearGradient(palettes[kotlin.math.abs(hash) % palettes.size])
}

internal fun formatDuration(ms: Long): String {
    val totalSeconds = (ms / 1000).coerceAtLeast(0)
    val minutes = totalSeconds / 60
    val seconds = totalSeconds % 60
    return "$minutes:${seconds.toString().padStart(2, '0')}"
}
