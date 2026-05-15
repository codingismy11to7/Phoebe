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
import com.phoebe.app.domain.canTogglePlexLike
import com.phoebe.app.player.CastState
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
internal fun DesktopTransport(
    track: Track?,
    isPlaying: Boolean,
    isBuffering: Boolean = false,
    positionMs: Long,
    shuffle: Boolean,
    repeat: RepeatMode,
    volume: Float,
    castState: CastState = CastState(),
    compact: Boolean,
    lyricsVisible: Boolean = false,
    upNextVisible: Boolean,
    upNextToggleEnabled: Boolean,
    onToggle: () -> Unit,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
    onShuffle: () -> Unit,
    onRepeat: () -> Unit,
    onVolume: (Float) -> Unit,
    onSeek: (Long) -> Unit,
    onLyrics: () -> Unit,
    onToggleUpNext: () -> Unit,
    onCast: () -> Unit,
) {
    val hasTrack = track != null
    val likeActions = LocalLikeActions.current
    val trackNavigationActions = LocalTrackNavigationActions.current
    val canLike = track != null && likeActions.likesEnabled && track.canTogglePlexLike()
    val liked = track != null && likeActions.isLiked(track)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(124.dp)
            .background(PhoebeUi.panel)
            .border(BorderStroke(1.dp, PhoebeUi.border))
            .padding(horizontal = 24.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (track != null) {
            TrackArtworkImage(
                track,
                Modifier
                    .size(56.dp)
                    .clickable { trackNavigationActions.onOpenAlbumForTrack(track) },
            )
        } else {
            EmptyNowPlayingArtworkSlot(Modifier.size(56.dp), glyphSp = 20.sp)
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.width(150.dp)) {
            Text(
                track?.title ?: "Nothing playing",
                color = PhoebeUi.primaryText,
                fontWeight = FontWeight.Bold,
                fontSize = 14.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                track?.artist?.takeIf { it.isNotBlank() } ?: "Pick a track to begin",
                color = if (hasTrack) PhoebeUi.secondaryText else PhoebeUi.mutedText,
                fontSize = 12.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.clickable(
                    enabled = track != null && track.artist.isNotBlank(),
                ) {
                    track?.let { trackNavigationActions.onOpenArtistForTrack(it) }
                },
            )
        }
        LikeButton(
            liked = liked,
            enabled = canLike,
            onClick = { track?.let(likeActions.onToggleLiked) },
            modifier = Modifier.size(34.dp),
        )
        Spacer(Modifier.weight(1f))
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(24.dp), verticalAlignment = Alignment.CenterVertically, modifier = Modifier.height(50.dp)) {
                ShuffleIcon(active = shuffle, onClick = onShuffle)
                TransportIcon(PhoebeIcon.Previous, "Previous Track", onPrevious)
                PlayButton(isPlaying, isBuffering, 48.dp, onToggle, enabled = hasTrack)
                TransportIcon(PhoebeIcon.Next, "Next Track", onNext)
                RepeatIcon(mode = repeat, onClick = onRepeat)
            }
            ProgressLine(
                positionMs,
                track?.durationMs ?: 0L,
                waveformSeed = track?.let(::trackWaveformSeed) ?: "",
                Modifier.width(if (compact) 320.dp else 460.dp),
                onSeek = if (hasTrack) onSeek else null,
            )
        }
        Spacer(Modifier.weight(1f))
        Row(
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.height(40.dp),
        ) {
            Box(Modifier.height(40.dp), contentAlignment = Alignment.Center) {
                PhoebeIconView(PhoebeIcon.Volume, tint = PhoebeUi.secondaryText, modifier = Modifier.size(20.dp))
            }
            Box(Modifier.height(40.dp), contentAlignment = Alignment.Center) {
                VolumeSlider(volume, onVolume, Modifier.width(if (compact) 84.dp else 112.dp))
            }
            TransportIcon(
                PhoebeIcon.Lyrics,
                if (lyricsVisible) "Hide Lyrics" else "Show Lyrics",
                onLyrics,
                active = lyricsVisible,
            )
            UpNextToggleIcon(
                visible = upNextVisible,
                enabled = upNextToggleEnabled,
                onClick = onToggleUpNext,
            )
            CastIcon(
                active = castState.isConnected,
                loading = castState.isBuffering,
                enabled = castState.isAvailable || castState.isConnected,
                onClick = onCast,
            )
        }
    }
}

@Composable
internal fun ShuffleIcon(active: Boolean, onClick: () -> Unit) {
    Box(
        Modifier
            .size(44.dp)
            .clip(CircleShape)
            .background(if (active) PhoebeUi.accent.copy(alpha = 0.16f) else Color.Transparent)
            .clickable(onClick = onClick)
            .semantics { contentDescription = if (active) "Shuffle on" else "Shuffle off" },
        contentAlignment = Alignment.Center,
    ) {
        val tint = if (active) PhoebeUi.accentLight else PhoebeUi.primaryText
        Canvas(Modifier.size(20.dp)) {
            val s = size.minDimension
            val arrowHeadLen = s * 0.18f
            val stroke = androidx.compose.ui.graphics.drawscope.Stroke(width = s * 0.085f, cap = StrokeCap.Round)
            val p1Start = Offset(s * 0.10f, s * 0.22f)
            val p1End = Offset(s * 0.85f, s * 0.78f)
            val p2Start = Offset(s * 0.10f, s * 0.78f)
            val p2End = Offset(s * 0.85f, s * 0.22f)
            drawLine(tint, p1Start, p1End, strokeWidth = stroke.width, cap = StrokeCap.Round)
            drawLine(tint, p2Start, p2End, strokeWidth = stroke.width, cap = StrokeCap.Round)
            // Arrowheads
            drawLine(tint, p1End, p1End + Offset(-arrowHeadLen, 0f), strokeWidth = stroke.width, cap = StrokeCap.Round)
            drawLine(tint, p1End, p1End + Offset(0f, -arrowHeadLen), strokeWidth = stroke.width, cap = StrokeCap.Round)
            drawLine(tint, p2End, p2End + Offset(-arrowHeadLen, 0f), strokeWidth = stroke.width, cap = StrokeCap.Round)
            drawLine(tint, p2End, p2End + Offset(0f, arrowHeadLen), strokeWidth = stroke.width, cap = StrokeCap.Round)
        }
    }
}

@Composable
internal fun RepeatIcon(mode: RepeatMode, onClick: () -> Unit) {
    val active = mode != RepeatMode.Off
    val label = when (mode) {
        RepeatMode.Off -> "Repeat off"
        RepeatMode.One -> "Repeat one"
        RepeatMode.All -> "Repeat all"
    }
    Box(
        Modifier
            .size(44.dp)
            .clip(CircleShape)
            .background(if (active) PhoebeUi.accent.copy(alpha = 0.16f) else Color.Transparent)
            .clickable(onClick = onClick)
            .semantics { contentDescription = label },
        contentAlignment = Alignment.Center,
    ) {
        when (mode) {
            RepeatMode.Off -> PhoebeIconView(PhoebeIcon.Repeat, tint = PhoebeUi.primaryText, modifier = Modifier.size(20.dp))
            RepeatMode.One -> Box(
                Modifier
                    .clip(RoundedCornerShape(999.dp))
                    .background(PhoebeUi.accentLight)
                    .padding(horizontal = 8.dp, vertical = 2.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    "1",
                    color = PhoebeUi.canvasBackground,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Black,
                )
            }
            RepeatMode.All -> Box(
                Modifier
                    .clip(RoundedCornerShape(999.dp))
                    .background(PhoebeUi.accentLight)
                    .padding(horizontal = 8.dp, vertical = 2.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    "All",
                    color = PhoebeUi.canvasBackground,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Black,
                    letterSpacing = 0.02.em,
                )
            }
        }
    }
}

@Composable
internal fun UpNextToggleIcon(visible: Boolean, enabled: Boolean, onClick: () -> Unit) {
    val tint = when {
        !enabled -> PhoebeUi.mutedText.copy(alpha = 0.35f)
        visible -> PhoebeUi.accentLight
        else -> PhoebeUi.secondaryText
    }
    Box(
        Modifier
            .size(40.dp)
            .clip(CircleShape)
            .background(if (visible) PhoebeUi.accent.copy(alpha = 0.16f) else Color.Transparent)
            .let { if (enabled) it.clickable(onClick = onClick) else it }
            .semantics { contentDescription = if (visible) "Hide Up Next" else "Show Up Next" },
        contentAlignment = Alignment.Center,
    ) {
        Canvas(Modifier.size(20.dp)) {
            val w = size.width
            val h = size.height
            val stroke = h * 0.10f
            val barHeight = stroke
            val y1 = h * 0.28f
            val y2 = h * 0.50f
            val y3 = h * 0.72f
            val barColor = tint
            drawRect(color = barColor, topLeft = Offset(0f, y1), size = androidx.compose.ui.geometry.Size(w, barHeight))
            drawRect(color = barColor, topLeft = Offset(0f, y2), size = androidx.compose.ui.geometry.Size(w * 0.78f, barHeight))
            drawRect(color = barColor, topLeft = Offset(0f, y3), size = androidx.compose.ui.geometry.Size(w * 0.55f, barHeight))
        }
    }
}

@Composable
internal fun CastIcon(active: Boolean, loading: Boolean, enabled: Boolean, onClick: () -> Unit) {
    val strokeColor = when {
        loading -> PhoebeUi.accentLight
        active -> PhoebeUi.accentLight
        enabled -> PhoebeUi.secondaryText
        else -> PhoebeUi.mutedText.copy(alpha = 0.45f)
    }
    Box(
        Modifier
            .size(40.dp)
            .clip(CircleShape)
            .background(if (active || loading) PhoebeUi.accent.copy(alpha = 0.14f) else Color.Transparent)
            .clickable(enabled = enabled && !loading, onClick = onClick)
            .semantics {
                contentDescription = when {
                    loading -> "Connecting to Chromecast"
                    active -> "Casting"
                    enabled -> "Cast"
                    else -> "Cast unavailable"
                }
            },
        contentAlignment = Alignment.Center,
    ) {
        if (loading) {
            CircularProgressIndicator(
                modifier = Modifier.size(18.dp),
                color = PhoebeUi.accentLight,
                strokeWidth = 2.dp,
            )
            return@Box
        }
        // Match the Up Next toggle's 20.dp canvas. Inside that canvas, draw the cast
        // glyph in a 14.dp-tall band that is vertically centred — this keeps the icon's
        // optical centre on the same baseline as the music note, slider, and up-next bars.
        Canvas(Modifier.size(20.dp)) {
            val w = size.width
            val h = size.height
            val rectH = h * 0.70f
            val rectTop = (h - rectH) / 2f
            val rectBottom = rectTop + rectH
            val stroke = h * 0.10f
            val cornerRadius = h * 0.12f
            drawRoundRect(
                color = strokeColor,
                topLeft = Offset(0f, rectTop),
                size = androidx.compose.ui.geometry.Size(w, rectH),
                cornerRadius = CornerRadius(cornerRadius, cornerRadius),
                style = androidx.compose.ui.graphics.drawscope.Stroke(width = stroke),
            )
            // Wifi-style arcs anchored at the bottom-left interior of the screen.
            val arcOrigin = Offset(stroke * 1.5f, rectBottom - stroke * 1.5f)
            drawCircle(color = strokeColor, radius = stroke * 0.9f, center = arcOrigin)
            val midRadius = rectH * 0.28f
            drawArc(
                color = strokeColor,
                startAngle = -90f,
                sweepAngle = 90f,
                useCenter = false,
                topLeft = Offset(arcOrigin.x - midRadius, arcOrigin.y - midRadius),
                size = androidx.compose.ui.geometry.Size(midRadius * 2, midRadius * 2),
                style = androidx.compose.ui.graphics.drawscope.Stroke(width = stroke, cap = StrokeCap.Round),
            )
            val outRadius = rectH * 0.5f
            drawArc(
                color = strokeColor,
                startAngle = -90f,
                sweepAngle = 90f,
                useCenter = false,
                topLeft = Offset(arcOrigin.x - outRadius, arcOrigin.y - outRadius),
                size = androidx.compose.ui.geometry.Size(outRadius * 2, outRadius * 2),
                style = androidx.compose.ui.graphics.drawscope.Stroke(width = stroke, cap = StrokeCap.Round),
            )
        }
    }
}
