package com.phoebe.app.ui

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
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
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
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
import androidx.compose.material3.CircularProgressIndicator
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
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
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
import kotlin.math.roundToInt
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
import com.phoebe.app.domain.isLocalMediaPlayback
import com.phoebe.app.domain.isRemoteLibraryTrack
import com.phoebe.app.domain.supportsPlexPlaylists
import com.phoebe.app.platform.createPlatformHttpClient
import com.phoebe.app.platform.currentTimeMs
import com.phoebe.app.platform.prefersReducedArtworkEffects
import kotlinx.coroutines.delay
import com.phoebe.app.sources.rememberPickLocalFolder
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.get
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.yield
import kotlin.math.max

private const val TimelineBufferFallbackTickMs = 500L
private const val TimelineBufferFallbackAdvanceMs = 2_000L

@Composable
internal fun PillTextField(
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    placeholder: String,
    contentDescription: String,
    leadingIcon: PhoebeIcon,
    showClearButton: Boolean = true,
    clearButtonContentDescription: String = "Clear",
) {
    val fieldTextStyle = TextStyle(color = PhoebeUi.primaryText, fontSize = 12.sp, lineHeight = 16.sp)
    Row(
        modifier
            .height(40.dp)
            .clip(CircleShape)
            .background(Color.Black.copy(alpha = 0.16f))
            .border(BorderStroke(1.dp, PhoebeUi.border), CircleShape)
            .padding(horizontal = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        PhoebeIconView(leadingIcon, tint = PhoebeUi.mutedText, modifier = Modifier.size(18.dp))
        BasicTextField(
            value = value,
            onValueChange = onValueChange,
            singleLine = true,
            textStyle = fieldTextStyle,
            cursorBrush = SolidColor(PhoebeUi.primaryText),
            modifier = Modifier
                .weight(1f)
                .fillMaxHeight()
                .trackDesktopTextInputFocus()
                .semantics { this.contentDescription = contentDescription },
            decorationBox = { innerTextField ->
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.CenterStart,
                ) {
                    if (value.isBlank()) {
                        Text(
                            placeholder,
                            color = PhoebeUi.mutedText,
                            style = fieldTextStyle,
                            maxLines = 1,
                        )
                    }
                    innerTextField()
                }
            },
        )
        if (showClearButton && value.isNotBlank()) {
            Box(
                Modifier
                    .size(26.dp)
                    .clip(CircleShape)
                    .clickable { onValueChange("") }
                    .semantics { this.contentDescription = clearButtonContentDescription },
                contentAlignment = Alignment.Center,
            ) {
                PhoebeIconView(PhoebeIcon.Close, tint = PhoebeUi.mutedText, modifier = Modifier.size(14.dp))
            }
        }
    }
}

@Composable
internal fun SearchPill(
    query: String,
    onQueryChange: (String) -> Unit,
    modifier: Modifier = Modifier.width(270.dp),
    placeholder: String = "Search songs, artists, albums",
) {
    PillTextField(
        value = query,
        onValueChange = onQueryChange,
        modifier = modifier,
        placeholder = placeholder,
        contentDescription = placeholder,
        leadingIcon = PhoebeIcon.Search,
        showClearButton = true,
        clearButtonContentDescription = "Clear search",
    )
}

@Composable
internal fun GlassIcon(icon: PhoebeIcon, description: String) {
    Box(
        Modifier
            .size(36.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(Color.Black.copy(alpha = 0.16f))
            .semantics { contentDescription = description },
        contentAlignment = Alignment.Center,
    ) {
        PhoebeIconView(icon, tint = PhoebeUi.secondaryText, modifier = Modifier.size(19.dp))
    }
}

@Composable
internal fun TransportIcon(icon: PhoebeIcon, description: String, onClick: () -> Unit = {}, active: Boolean = false) {
    Box(
        Modifier
            .size(44.dp)
            .clip(CircleShape)
            .background(if (active) PhoebeUi.accent.copy(alpha = 0.16f) else Color.Transparent)
            .clickable(onClick = onClick)
            .semantics { contentDescription = description },
        contentAlignment = Alignment.Center,
    ) {
        PhoebeIconView(icon, tint = if (active) PhoebeUi.accentLight else PhoebeUi.primaryText, modifier = Modifier.size(20.dp))
    }
}

@Composable
internal fun PlayButton(
    isPlaying: Boolean,
    isBuffering: Boolean,
    size: Dp,
    onClick: () -> Unit,
    enabled: Boolean = true,
) {
    val motionEnabled = LocalContinuousMotionEnabled.current
    val targetScale = if ((isPlaying || isBuffering) && enabled) 1f else 0.98f
    val scaleState = animateFloatAsState(
        targetScale,
        spring(),
        label = "play-button-scale",
    )
    val scale = if (motionEnabled) scaleState.value else targetScale
    val gradient = if (enabled) {
        Brush.linearGradient(listOf(PhoebeUi.accentLight, PhoebeUi.accent))
    } else {
        Brush.linearGradient(listOf(PhoebeUi.mutedText.copy(alpha = 0.28f), PhoebeUi.mutedText.copy(alpha = 0.38f)))
    }
    val iconSize = if (size > 52.dp) 24.dp else 21.dp
    val spinnerSize = iconSize + 2.dp
    val contentDescription = when {
        isBuffering -> "Loading"
        isPlaying -> "Pause"
        else -> "Play"
    }
    Box(
        Modifier
            .size(size)
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .then(
                if (enabled) {
                    Modifier.shadow(18.dp, CircleShape, ambientColor = PhoebeUi.accent.copy(alpha = 0.4f), spotColor = PhoebeUi.accent.copy(alpha = 0.38f))
                } else {
                    Modifier
                },
            )
            .clip(CircleShape)
            .background(gradient)
            .clickable(enabled = enabled && !isBuffering, onClick = onClick)
            .semantics { this.contentDescription = contentDescription },
        contentAlignment = Alignment.Center,
    ) {
        if (motionEnabled) {
            AnimatedContent(
                targetState = isBuffering,
                transitionSpec = {
                    fadeIn(tween(160)) togetherWith fadeOut(tween(120))
                },
                label = "play-button-icon",
            ) { loading ->
                PlayButtonGlyph(
                    loading = loading,
                    isPlaying = isPlaying,
                    enabled = enabled,
                    spinnerSize = spinnerSize,
                    iconSize = iconSize,
                )
            }
        } else {
            PlayButtonGlyph(
                loading = isBuffering,
                isPlaying = isPlaying,
                enabled = enabled,
                spinnerSize = spinnerSize,
                iconSize = iconSize,
            )
        }
    }
}

@Composable
private fun PlayButtonGlyph(
    loading: Boolean,
    isPlaying: Boolean,
    enabled: Boolean,
    spinnerSize: Dp,
    iconSize: Dp,
) {
    if (loading) {
        CircularProgressIndicator(
            modifier = Modifier.size(spinnerSize),
            color = PhoebeUi.primaryText,
            strokeWidth = 2.dp,
            trackColor = PhoebeUi.primaryText.copy(alpha = 0.22f),
        )
    } else {
        MorphingPlayPauseIcon(
            isPlaying = isPlaying,
            tint = if (enabled) PhoebeUi.primaryText else PhoebeUi.mutedText.copy(alpha = 0.55f),
            modifier = Modifier.size(iconSize),
        )
    }
}

@Composable
private fun MorphingPlayPauseIcon(
    isPlaying: Boolean,
    tint: Color,
    modifier: Modifier = Modifier,
) {
    val motionEnabled = LocalContinuousMotionEnabled.current
    val targetMorph = if (isPlaying) 1f else 0f
    val morphState = animateFloatAsState(
        targetValue = targetMorph,
        animationSpec = tween(durationMillis = 260, easing = FastOutSlowInEasing),
        label = "play-pause-morph",
    )
    val morph = if (motionEnabled) morphState.value else targetMorph

    Canvas(modifier) {
        val s = size.minDimension
        fun x(value: Float) = s * value
        fun y(value: Float) = s * value
        fun m(start: Float, end: Float) = start + (end - start) * morph

        fun morphPath(play: List<Offset>, pause: List<Offset>) {
            val path = Path().apply {
                val first = play.first()
                moveTo(x(m(first.x, pause.first().x)), y(m(first.y, pause.first().y)))
                for (index in 1 until play.size) {
                    val p = play[index]
                    val target = pause[index]
                    lineTo(x(m(p.x, target.x)), y(m(p.y, target.y)))
                }
                close()
            }
            drawPath(path, tint)
        }

        morphPath(
            play = PlayButtonLeftPlayShape,
            pause = PlayButtonLeftPauseShape,
        )
        morphPath(
            play = PlayButtonRightPlayShape,
            pause = PlayButtonRightPauseShape,
        )
    }
}

private val PlayButtonLeftPlayShape = listOf(
    Offset(0.34f, 0.22f),
    Offset(0.55f, 0.36f),
    Offset(0.55f, 0.64f),
    Offset(0.34f, 0.78f),
)

private val PlayButtonLeftPauseShape = listOf(
    Offset(0.32f, 0.22f),
    Offset(0.44f, 0.22f),
    Offset(0.44f, 0.78f),
    Offset(0.32f, 0.78f),
)

private val PlayButtonRightPlayShape = listOf(
    Offset(0.55f, 0.36f),
    Offset(0.76f, 0.50f),
    Offset(0.76f, 0.50f),
    Offset(0.55f, 0.64f),
)

private val PlayButtonRightPauseShape = listOf(
    Offset(0.56f, 0.22f),
    Offset(0.68f, 0.22f),
    Offset(0.68f, 0.78f),
    Offset(0.56f, 0.78f),
)

/** Stable per-track seed so wave shape differs across library even when ids are opaque or similar. */
internal fun trackWaveformSeed(track: Track): String =
    "${track.id}\u0000${track.title}\u0000${track.artist}\u0000${track.album}\u0000${track.durationMs}"

@Composable
internal fun rememberTimelineBufferedPositionMs(
    track: Track?,
    positionMs: Long,
    bufferedPositionMs: Long,
    isPlaying: Boolean,
    isBuffering: Boolean,
): Long {
    val remoteDurationMs = track
        ?.takeUnless { it.isLocalMediaPlayback() }
        ?.durationMs
        ?.takeIf { it > 0L }
    val latestPositionMs by rememberUpdatedState(positionMs)
    val latestBufferedPositionMs by rememberUpdatedState(bufferedPositionMs)
    var estimatedRemoteBufferedPositionMs by remember(track?.id) {
        mutableStateOf(max(positionMs, bufferedPositionMs))
    }

    LaunchedEffect(track?.id) {
        estimatedRemoteBufferedPositionMs = max(positionMs, bufferedPositionMs)
    }
    LaunchedEffect(remoteDurationMs, bufferedPositionMs, positionMs) {
        val duration = remoteDurationMs
        if (duration == null) {
            estimatedRemoteBufferedPositionMs = bufferedPositionMs
            return@LaunchedEffect
        }
        estimatedRemoteBufferedPositionMs = max(
            estimatedRemoteBufferedPositionMs,
            max(positionMs, bufferedPositionMs),
        ).coerceAtMost(duration)
    }
    LaunchedEffect(track?.id, remoteDurationMs, isPlaying, isBuffering) {
        val duration = remoteDurationMs ?: return@LaunchedEffect
        if (!isPlaying && !isBuffering) return@LaunchedEffect
        while (estimatedRemoteBufferedPositionMs < duration) {
            delay(TimelineBufferFallbackTickMs)
            val platformFloor = max(latestPositionMs, latestBufferedPositionMs)
            estimatedRemoteBufferedPositionMs = max(estimatedRemoteBufferedPositionMs, platformFloor)
                .plus(TimelineBufferFallbackAdvanceMs)
                .coerceAtMost(duration)
        }
    }
    return remember(remoteDurationMs, bufferedPositionMs, estimatedRemoteBufferedPositionMs) {
        remoteDurationMs?.let { duration ->
            max(bufferedPositionMs, estimatedRemoteBufferedPositionMs).coerceIn(0L, duration)
        } ?: bufferedPositionMs
    }
}

internal fun waveBarHeight(seed: String, index: Int): Float {
    var h = 0L
    for (c in seed) {
        h = h * 31L + c.code
    }
    var x = h xor (index.toLong() * 0x9e3779b9L)
    x = (x * 0x85ebca6bL) xor (x ushr 13)
    x = (x * 0xc2b2ae35L) xor (x ushr 16)
    var t = x xor (index.toLong() * 0x27d4eb2fL)
    t = t xor (t ushr 4)
    t *= 0xcc9e2d51L
    t = t xor (t ushr 11)
    val u = ((t ushr 8) and 0xffffL).toFloat() / 65536f
    val w = kotlin.math.sin(index * 1.17 + h * 2.1e-5 + t * 1.5e-4).toFloat()
    val w2 = kotlin.math.cos(index * 0.53 + (t and 0xffL).toDouble() * 0.11).toFloat()
    return (0.13f + 0.54f * u + 0.19f * w + 0.15f * w2).coerceIn(0.12f, 1f)
}

@Composable
internal fun WaveformDurationBar(
    seed: String,
    durationMs: Long,
    progress: Float?,
    bufferedProgress: Float?,
    contentDescription: String,
    modifier: Modifier = Modifier,
    isScrubbing: Boolean = false,
    maxBarSlots: Int = 140,
) {
    val p = progress?.coerceIn(0f, 1f)
    val bp = bufferedProgress?.coerceIn(0f, 1f)
    val playedColor = PhoebeUi.accentLight
    val bufferedColor = PhoebeUi.primaryText.copy(alpha = 0.34f)
    val unplayedBase = PhoebeUi.waveformUnplayed
    val playheadColor = PhoebeUi.waveformPlayhead
    val waveformAmplitudes = remember(seed, durationMs, maxBarSlots) {
        FloatArray(maxBarSlots.coerceAtLeast(20)) { index ->
            if (durationMs > 0L) waveBarHeight(seed, index) else 0.12f
        }
    }
    Canvas(
        modifier.semantics {
            this.contentDescription = contentDescription
        },
    ) {
        if (size.width <= 0f || size.height <= 0f) return@Canvas
        val barSlots = (size.width / (2.2f * density)).toInt().coerceIn(20, maxBarSlots.coerceAtLeast(20))
        val slotW = size.width / barSlots
        val barW = (slotW * 0.62f).coerceAtLeast(1.2f)
        val played = playedColor
        val queued = unplayedBase
        for (i in 0 until barSlots) {
            val frac = (i + 0.5f) / barSlots
            val amp = waveformAmplitudes[i]
            val barH = size.height * amp
            val x = i * slotW + (slotW - barW) / 2f
            val color = when {
                durationMs <= 0L -> unplayedBase.copy(alpha = (unplayedBase.alpha * 0.75f).coerceIn(0.08f, 0.5f))
                p == null -> {
                    val a = (0.12f + 0.52f * amp).coerceIn(0.12f, 0.55f)
                    unplayedBase.copy(alpha = a)
                }
                frac <= p -> played
                bp != null && frac <= bp -> bufferedColor
                else -> queued
            }
            drawRoundRect(
                color = color,
                topLeft = Offset(x, size.height - barH),
                size = Size(barW, barH),
                cornerRadius = CornerRadius(barW * 0.45f, barW * 0.45f),
            )
        }
        if (durationMs > 0L && p != null && p in 0.001f..0.999f) {
            val cx = size.width * p
            val playheadWidth = if (isScrubbing) 2.dp.toPx() else 1.dp.toPx()
            drawLine(
                color = playheadColor,
                start = Offset(cx, 0f),
                end = Offset(cx, size.height),
                strokeWidth = playheadWidth,
            )
            if (isScrubbing) {
                drawCircle(
                    color = playheadColor,
                    radius = 5.dp.toPx(),
                    center = Offset(cx, size.height / 2f),
                )
            }
        }
    }
}

@Composable
internal fun ProgressLine(
    positionMs: Long,
    bufferedPositionMs: Long,
    durationMs: Long,
    waveformSeed: String,
    modifier: Modifier,
    onSeek: ((Long) -> Unit)? = null,
    barHeight: Dp = 28.dp,
    labelFontSize: TextUnit = 12.sp,
    labelSpacing: Dp = 6.dp,
    maxBarSlots: Int = 140,
) {
    val safeDuration = max(durationMs, 1L)
    var scrubPositionMs by remember { mutableStateOf<Long?>(null) }
    val isScrubbing = scrubPositionMs != null
    val displayPositionMs = scrubPositionMs ?: positionMs
    val progressFrac = (displayPositionMs.toFloat() / safeDuration).coerceIn(0f, 1f)
    val bufferedFrac = (bufferedPositionMs.toFloat() / safeDuration).coerceIn(progressFrac, 1f)

    LaunchedEffect(waveformSeed, durationMs) {
        scrubPositionMs = null
    }

    val seekModifier = if (onSeek != null && durationMs > 0L) {
        Modifier.pointerInput(durationMs, onSeek) {
            fun offsetToMs(x: Float): Long {
                val frac = (x / size.width).coerceIn(0f, 1f)
                return (durationMs * frac).toLong()
            }
            awaitEachGesture {
                val down = awaitFirstDown()
                down.consume()
                var scrubMs = offsetToMs(down.position.x)
                scrubPositionMs = scrubMs
                val pointerId = down.id
                while (true) {
                    val event = awaitPointerEvent()
                    val change = event.changes.firstOrNull { it.id == pointerId } ?: break
                    if (!change.pressed) {
                        onSeek(scrubMs)
                        scrubPositionMs = null
                        break
                    }
                    scrubMs = offsetToMs(change.position.x)
                    scrubPositionMs = scrubMs
                    change.consume()
                }
            }
        }
    } else {
        Modifier
    }
    Column(modifier, verticalArrangement = Arrangement.spacedBy(labelSpacing)) {
        WaveformDurationBar(
            seed = waveformSeed,
            durationMs = durationMs,
            progress = if (durationMs > 0L) progressFrac else null,
            bufferedProgress = if (durationMs > 0L) bufferedFrac else null,
            isScrubbing = isScrubbing,
            contentDescription = if (durationMs > 0L) {
                "Playback progress, ${formatDuration(displayPositionMs)} of ${formatDuration(durationMs)}"
            } else {
                "Playback progress, no duration"
            },
            modifier = seekModifier
                .fillMaxWidth()
                .height(barHeight),
            maxBarSlots = maxBarSlots,
        )
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(
                formatDuration(displayPositionMs),
                color = if (isScrubbing) PhoebeUi.primaryText else PhoebeUi.mutedText,
                fontSize = labelFontSize,
            )
            Text(formatDuration(durationMs), color = PhoebeUi.mutedText, fontSize = labelFontSize)
        }
    }
}

@Composable
internal fun VolumeSlider(volume: Float, onVolume: (Float) -> Unit, modifier: Modifier) {
    Slider(
        value = volume,
        onValueChange = onVolume,
        modifier = modifier.semantics { contentDescription = "Volume" },
        colors = SliderDefaults.colors(
            thumbColor = PhoebeUi.accentLight,
            activeTrackColor = PhoebeUi.accentLight,
            inactiveTrackColor = PhoebeUi.progressTrack,
        ),
    )
}
