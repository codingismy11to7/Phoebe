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
import androidx.compose.runtime.mutableStateMapOf
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
import com.phoebe.app.domain.isPlexLibraryTrack
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

@Composable
internal fun QueuePanel(
    upNext: List<Track>,
    currentTrack: Track?,
    repeat: RepeatMode,
    modifier: Modifier,
    onPlayQueue: (Int) -> Unit,
    onClearQueue: () -> Unit,
    onMoveUpNext: (Int, Int) -> Unit,
    onRemoveUpNext: (Int) -> Unit,
) {
    Column(modifier.padding(top = 132.dp, end = 36.dp, bottom = 24.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            SectionLabel("Up Next", PhoebeUi.primaryText)
            if (repeat != RepeatMode.Off) {
                Spacer(Modifier.width(8.dp))
                RepeatBadge(mode = repeat)
            }
            Spacer(Modifier.weight(1f))
            if (upNext.isNotEmpty()) {
                Text(
                    "Clear",
                    color = PhoebeUi.mutedText,
                    fontSize = 12.sp,
                    modifier = Modifier
                        .clip(RoundedCornerShape(6.dp))
                        .clickable(onClick = onClearQueue)
                        .padding(horizontal = 8.dp, vertical = 5.dp),
                )
            }
        }
        if (currentTrack == null && upNext.isEmpty()) {
            Text("Pick a song to start a queue.", color = PhoebeUi.mutedText, fontSize = 13.sp, lineHeight = 18.sp)
        } else {
            UpNextList(
                currentTrack = currentTrack,
                upNext = upNext,
                repeat = repeat,
                onPlayQueue = onPlayQueue,
                onMoveUpNext = onMoveUpNext,
                onRemoveUpNext = onRemoveUpNext,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

@Composable
internal fun RepeatBadge(mode: RepeatMode) {
    val (label, description) = when (mode) {
        RepeatMode.One -> "1" to "Repeating current track"
        RepeatMode.All -> "All" to "Repeating queue"
        RepeatMode.Off -> return
    }
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(999.dp))
            .background(PhoebeUi.accent.copy(alpha = 0.18f))
            .padding(horizontal = 8.dp, vertical = 3.dp)
            .semantics { contentDescription = description },
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        PhoebeIconView(PhoebeIcon.Repeat, tint = PhoebeUi.accentLight, modifier = Modifier.size(10.dp))
        Text(label, color = PhoebeUi.accentLight, fontSize = 10.sp, fontWeight = FontWeight.SemiBold, letterSpacing = 0.04.em)
    }
}

/**
 * Vertical Up Next list with a non-draggable "currently playing" header row followed
 * by reorderable upcoming tracks. Used on both desktop and mobile expanded panels.
 */
@Composable
internal fun UpNextList(
    currentTrack: Track?,
    upNext: List<Track>,
    repeat: RepeatMode = RepeatMode.Off,
    onPlayQueue: (Int) -> Unit,
    onMoveUpNext: (Int, Int) -> Unit,
    onRemoveUpNext: (Int) -> Unit,
    modifier: Modifier = Modifier,
    thumbnail: Dp = 44.dp,
    rowHeight: Dp = 60.dp,
) {
    val density = LocalDensity.current
    val rowHeightPx = with(density) { rowHeight.toPx() }
    var draggingTrackId by remember { mutableStateOf<String?>(null) }
    var dragOffsetPx by remember { mutableStateOf(0f) }

    LazyColumn(modifier = modifier, verticalArrangement = Arrangement.spacedBy(2.dp)) {
        if (currentTrack != null) {
            item(key = "now-playing-${currentTrack.id}", contentType = "now-playing") {
                UpNextRow(
                    track = currentTrack,
                    active = true,
                    repeatBadge = if (repeat == RepeatMode.One) "1" else null,
                    thumbnail = thumbnail,
                    rowHeight = rowHeight,
                    dragHandle = null,
                    onClick = { /* no-op, already playing */ },
                )
            }
        }
        itemsIndexed(upNext, key = { _, t -> t.id }, contentType = { _, _ -> "up-next" }) { index, track ->
            val isDragging = draggingTrackId == track.id
            Box(
                Modifier
                    .fillMaxWidth()
                    .offset { IntOffset(0, if (isDragging) dragOffsetPx.roundToInt() else 0) }
                    .zIndex(if (isDragging) 1f else 0f),
            ) {
                UpNextRow(
                    track = track,
                    active = false,
                    thumbnail = thumbnail,
                    rowHeight = rowHeight,
                    backgroundAlpha = if (isDragging) 0.22f else 0f,
                    dragHandle = {
                        Box(
                            Modifier
                                .size(36.dp)
                                .pointerInput(Unit) {
                                    detectDragGestures(
                                        onDragStart = {
                                            draggingTrackId = track.id
                                            dragOffsetPx = 0f
                                        },
                                        onDragEnd = {
                                            draggingTrackId = null
                                            dragOffsetPx = 0f
                                        },
                                        onDragCancel = {
                                            draggingTrackId = null
                                            dragOffsetPx = 0f
                                        },
                                        onDrag = { _, drag ->
                                            dragOffsetPx += drag.y
                                            val currentId = draggingTrackId
                                                ?: return@detectDragGestures
                                            val curIndex = upNext.indexOfFirst { it.id == currentId }
                                            if (curIndex < 0) return@detectDragGestures
                                            val shift = (dragOffsetPx / rowHeightPx).roundToInt()
                                            if (shift != 0) {
                                                val target = (curIndex + shift)
                                                    .coerceIn(0, upNext.lastIndex)
                                                if (target != curIndex) {
                                                    onMoveUpNext(curIndex, target)
                                                    dragOffsetPx -= shift * rowHeightPx
                                                }
                                            }
                                        },
                                    )
                                }
                                .semantics { contentDescription = "Reorder ${track.title}" },
                            contentAlignment = Alignment.Center,
                        ) {
                            PhoebeIconView(PhoebeIcon.Drag, tint = PhoebeUi.mutedText, modifier = Modifier.size(18.dp))
                        }
                    },
                    onClick = { onPlayQueue(index) },
                    onLongPress = { onRemoveUpNext(index) },
                )
            }
        }
        if (repeat == RepeatMode.All && (currentTrack != null || upNext.isNotEmpty())) {
            item(contentType = "repeat-all-divider") {
                RepeatAllDivider()
            }
        }
    }
}

@Composable
internal fun RepeatAllDivider() {
    Row(
        Modifier.fillMaxWidth().padding(vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Box(Modifier.weight(1f).height(1.dp).background(PhoebeUi.accent.copy(alpha = 0.32f)))
        Text(
            "Loops",
            color = PhoebeUi.accentLight,
            fontSize = 10.sp,
            fontWeight = FontWeight.SemiBold,
            letterSpacing = 0.10.em,
        )
        Box(Modifier.weight(1f).height(1.dp).background(PhoebeUi.accent.copy(alpha = 0.32f)))
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun UpNextRow(
    track: Track,
    active: Boolean,
    thumbnail: Dp,
    rowHeight: Dp,
    backgroundAlpha: Float = 0f,
    repeatBadge: String? = null,
    dragHandle: (@Composable () -> Unit)?,
    onClick: () -> Unit,
    onLongPress: (() -> Unit)? = null,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(rowHeight)
            .clip(RoundedCornerShape(10.dp))
            .background(
                if (active) PhoebeUi.accent.copy(alpha = 0.10f)
                else if (backgroundAlpha > 0f) Color.Black.copy(alpha = backgroundAlpha)
                else Color.Transparent,
            )
            .combinedClickable(
                onClick = onClick,
                onLongClick = onLongPress,
            )
            .padding(horizontal = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Box(Modifier.size(thumbnail), contentAlignment = Alignment.Center) {
            ArtworkImage(track.album, track.thumbUrl, Modifier.fillMaxSize(), radius = 6.dp)
            if (active) {
                Box(
                    Modifier
                        .fillMaxSize()
                        .clip(RoundedCornerShape(6.dp))
                        .background(Color.Black.copy(alpha = 0.35f)),
                    contentAlignment = Alignment.Center,
                ) {
                    PhoebeIconView(PhoebeIcon.ActiveDot, tint = PhoebeUi.accentLight, modifier = Modifier.size(14.dp), filled = true)
                }
            }
        }
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(
                    track.title,
                    color = if (active) PhoebeUi.accentLight else PhoebeUi.primaryText,
                    fontSize = 14.sp,
                    fontWeight = if (active) FontWeight.Bold else FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false),
                )
                if (repeatBadge != null) {
                    Box(
                        Modifier
                            .clip(RoundedCornerShape(999.dp))
                            .background(PhoebeUi.accent.copy(alpha = 0.22f))
                            .padding(horizontal = 6.dp, vertical = 2.dp),
                    ) {
                        Text(
                            repeatBadge,
                            color = PhoebeUi.accentLight,
                            fontSize = 9.sp,
                            fontWeight = FontWeight.SemiBold,
                            letterSpacing = 0.04.em,
                        )
                    }
                }
            }
            Text(
                track.artist,
                color = PhoebeUi.secondaryText,
                fontSize = 12.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Text(
            formatDuration(track.durationMs),
            color = PhoebeUi.mutedText,
            fontSize = 12.sp,
        )
        if (dragHandle != null) {
            dragHandle()
        } else {
            Spacer(Modifier.width(36.dp))
        }
    }
}

