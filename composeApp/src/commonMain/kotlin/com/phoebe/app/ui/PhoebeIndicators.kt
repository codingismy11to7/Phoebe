package com.phoebe.app.ui

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.ContentTransform
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
import androidx.compose.animation.expandHorizontally
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkHorizontally
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
internal fun DragGhost() {
    val controller = LocalDragDrop.current ?: return
    val track = controller.draggedTrack ?: return
    val pointer = controller.pointer ?: return
    val density = LocalDensity.current
    val hoveringTitle = controller.hoveringPlaylistTitle
    val onTarget = hoveringTitle != null

    Box(
        modifier = Modifier
            .offset {
                // Anchor the ghost just below-and-right of the actual cursor so it doesn't
                // hide the underlying drop target. Constants are in px.
                val px = with(density) { 14.dp.toPx() }
                val py = with(density) { 10.dp.toPx() }
                IntOffset(
                    x = (pointer.x + px).roundToInt(),
                    y = (pointer.y + py).roundToInt(),
                )
            }
            .zIndex(1000f)
            .shadow(elevation = 18.dp, shape = RoundedCornerShape(12.dp), clip = false)
            .clip(RoundedCornerShape(12.dp))
            .background(
                if (onTarget) PhoebeUi.accentLight.copy(alpha = 0.96f) else PhoebeUi.accent.copy(alpha = 0.92f),
            )
            .border(
                BorderStroke(1.dp, Color.White.copy(alpha = if (onTarget) 0.45f else 0.18f)),
                RoundedCornerShape(12.dp),
            )
            .padding(horizontal = 14.dp, vertical = 9.dp),
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(
                if (onTarget) "Add to $hoveringTitle" else "Moving",
                color = if (onTarget) Color.White else PhoebeUi.primaryText.copy(alpha = 0.85f),
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 0.08.em,
            )
            Text(
                "♪  ${track.title}",
                color = PhoebeUi.primaryText,
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.widthIn(max = 260.dp),
            )
        }
    }
}

/**
 * Three-bar animated equaliser used as a "this track is currently playing" indicator
 * inside library track rows. The bars pulse while [isPlaying] is true and freeze at a low
 * height when playback is paused.
 */
@Composable
internal fun NowPlayingIndicator(
    isPlaying: Boolean,
    isBuffering: Boolean = false,
    modifier: Modifier = Modifier,
    barColor: Color = PhoebeUi.accentLight,
) {
    when {
        isBuffering -> CircularProgressIndicator(
            modifier = modifier,
            color = barColor,
            strokeWidth = 2.dp,
            trackColor = barColor.copy(alpha = 0.22f),
        )
        isPlaying -> AnimatedNowPlayingIndicator(modifier = modifier, barColor = barColor)
        else -> NowPlayingIndicatorBars(
            heights = listOf(0.3f, 0.3f, 0.3f),
            modifier = modifier,
            barColor = barColor,
        )
    }
}

@Composable
internal fun AnimatedNowPlayingIndicator(
    modifier: Modifier = Modifier,
    barColor: Color = PhoebeUi.accentLight,
) {
    val transition = rememberInfiniteTransition(label = "now-playing")
    val bar1 by transition.animateFloat(
        initialValue = 0.25f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 520, easing = androidx.compose.animation.core.FastOutSlowInEasing),
            repeatMode = androidx.compose.animation.core.RepeatMode.Reverse,
        ),
        label = "bar1",
    )
    val bar2 by transition.animateFloat(
        initialValue = 0.6f,
        targetValue = 0.35f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 600, easing = androidx.compose.animation.core.FastOutSlowInEasing),
            repeatMode = androidx.compose.animation.core.RepeatMode.Reverse,
        ),
        label = "bar2",
    )
    val bar3 by transition.animateFloat(
        initialValue = 0.4f,
        targetValue = 0.85f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 460, easing = androidx.compose.animation.core.FastOutSlowInEasing),
            repeatMode = androidx.compose.animation.core.RepeatMode.Reverse,
        ),
        label = "bar3",
    )
    NowPlayingIndicatorBars(
        heights = listOf(bar1, bar2, bar3),
        modifier = modifier,
        barColor = barColor,
    )
}

@Composable
internal fun NowPlayingIndicatorBars(
    heights: List<Float>,
    modifier: Modifier = Modifier,
    barColor: Color = PhoebeUi.accentLight,
) {
    Canvas(modifier) {
        val barWidth = size.width / 7f
        val gap = barWidth
        val totalWidth = barWidth * 3 + gap * 2
        val startX = (size.width - totalWidth) / 2f
        heights.forEachIndexed { i, h ->
            val barH = (size.height * h).coerceAtLeast(barWidth)
            val x = startX + i * (barWidth + gap)
            val y = size.height - barH
            drawRoundRect(
                color = barColor,
                topLeft = Offset(x, y),
                size = androidx.compose.ui.geometry.Size(barWidth, barH),
                cornerRadius = CornerRadius(barWidth / 2f, barWidth / 2f),
            )
        }
    }
}

@Composable
internal fun CatalogLoadingStrip(modifier: Modifier = Modifier) {
    val hasContent = LocalCatalogHasContent.current
    val syncState = LocalCatalogSyncState.current
    val message = syncState.message ?: if (hasContent) "Syncing…" else "Loading your library…"
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        LinearProgressIndicator(
            modifier = Modifier
                .fillMaxWidth()
                .height(3.dp)
                .clip(RoundedCornerShape(999.dp)),
            color = PhoebeUi.accentLight,
            trackColor = Color.White.copy(alpha = 0.08f),
        )
        Text(
            message,
            color = PhoebeUi.mutedText,
            fontSize = 11.sp,
            fontWeight = FontWeight.SemiBold,
            letterSpacing = 0.06.em,
        )
    }
}

@Composable
internal fun CatalogMenuSyncIndicator(modifier: Modifier = Modifier) {
    val syncState = LocalCatalogSyncState.current
    if (!syncState.isActive) return
    val message = syncState.message ?: "Syncing library…"
    val detail = when {
        syncState.loadedTracks > 0 -> "${syncState.loadedTracks} songs"
        syncState.loadedAlbums > 0 -> "${syncState.loadedAlbums} albums"
        else -> null
    }
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        CircularProgressIndicator(
            modifier = Modifier
                .size(16.dp)
                .semantics { contentDescription = "Library sync in progress" },
            color = PhoebeUi.accentLight,
            strokeWidth = 2.dp,
        )
        Column {
            Text(
                message,
                color = PhoebeUi.secondaryText,
                fontSize = 12.sp,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (detail != null) {
                Text(
                    detail,
                    color = PhoebeUi.mutedText,
                    fontSize = 10.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

private val countTransitionSpec: AnimatedContentTransitionScope<Int>.() -> ContentTransform = {
    (slideInVertically(animationSpec = tween(200)) { it / 3 } + fadeIn(tween(200))) togetherWith
        (slideOutVertically(animationSpec = tween(160)) { -it / 3 } + fadeOut(tween(160)))
}

@Composable
internal fun PlaylistTrackSummaryLine(
    totalCount: Int,
    visibleCount: Int,
    searchQuery: String,
    modifier: Modifier = Modifier,
) {
    val searchActive = searchQuery.isNotBlank()
    Row(modifier, verticalAlignment = Alignment.CenterVertically) {
        AnimatedContent(
            targetState = totalCount,
            transitionSpec = countTransitionSpec,
            label = "playlist-total-count",
        ) { count ->
            Text(
                "$count ${if (count == 1) "song" else "songs"}",
                color = PhoebeUi.secondaryText,
                fontSize = 14.sp,
            )
        }
        AnimatedVisibility(
            visible = searchActive,
            enter = fadeIn(tween(180)) + expandHorizontally(tween(200)),
            exit = fadeOut(tween(140)) + shrinkHorizontally(tween(160)),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(" · ", color = PhoebeUi.mutedText, fontSize = 14.sp)
                AnimatedContent(
                    targetState = visibleCount,
                    transitionSpec = countTransitionSpec,
                    label = "playlist-filter-count",
                ) { count ->
                    Text(
                        "$count ${if (count == 1) "result" else "results"}",
                        color = PhoebeUi.secondaryText,
                        fontSize = 14.sp,
                    )
                }
            }
        }
    }
}
