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
import androidx.compose.foundation.lazy.rememberLazyListState
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
import com.phoebe.app.domain.DownloadState
import com.phoebe.app.domain.LocalFolderMediaSourceConfig
import com.phoebe.app.domain.MediaSourcesState
import com.phoebe.app.domain.MusicLibrary
import com.phoebe.app.domain.PlexServer
import com.phoebe.app.domain.PlexSession
import com.phoebe.app.domain.Playlist
import com.phoebe.app.domain.RepeatMode
import com.phoebe.app.domain.Track
import com.phoebe.app.domain.canAddToLocalPlaylist
import com.phoebe.app.domain.canAddToPlexPlaylist
import com.phoebe.app.domain.canTogglePlexLike
import com.phoebe.app.domain.isLocalMediaPlayback
import com.phoebe.app.domain.isLocalPlaylist
import com.phoebe.app.domain.isLikedSongsPlaylist
import com.phoebe.app.domain.isRemoteLibraryTrack
import com.phoebe.app.domain.isRemoteProviderPlaylist
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

@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun TrackList(
    tracks: List<Track>,
    empty: String,
    catalogRefreshing: Boolean,
    onPlayTracks: (List<Track>, Int) -> Unit,
    onAddToUpNext: (Track) -> Unit,
    onDownload: (Track) -> Unit,
    libraryColumns: LibraryColumnVisibility = FullTrackMetadataColumns,
    showLoadingWhenEmpty: Boolean = true,
    onMoveTrack: ((Int, Int) -> Unit)? = null,
) {
    if (tracks.isEmpty()) {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            if (catalogRefreshing && showLoadingWhenEmpty) {
                CatalogLoadingStrip()
            }
            Text(empty, color = PhoebeUi.mutedText, fontSize = 15.sp)
        }
        return
    }
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val useTable = maxWidth >= 640.dp
        val listState = rememberLazyListState()
        val reorderEnabled = onMoveTrack != null && tracks.size > 1
        val reorderState = rememberPlaylistTrackReorderState(
            tracks = tracks,
            enabled = reorderEnabled,
            listState = listState,
            rowStep = if (useTable) 56.dp else 72.dp,
            onMove = { from, to -> onMoveTrack?.invoke(from, to) },
        )
        val displayTracks = if (reorderEnabled || reorderState.isDragging) reorderState.tracks else tracks
        LazyColumn(
            state = listState,
            modifier = if (reorderEnabled) reorderState.listModifier() else Modifier,
            verticalArrangement = Arrangement.spacedBy(if (useTable) 2.dp else 10.dp),
        ) {
            if (catalogRefreshing) {
                item(contentType = "loading") { CatalogLoadingStrip(Modifier.padding(bottom = 4.dp)) }
            }
            if (useTable) {
                item(contentType = "track-header") {
                    SongsTableHeader(
                        columns = libraryColumns,
                        showLeadingHandle = reorderEnabled || LocalPlaylistDragEnabled.current,
                    )
                }
                itemsIndexed(displayTracks, key = { _, track -> track.id }, contentType = { _, _ -> "track" }) { index, track ->
                    SongRow(
                        track = track,
                        selected = false,
                        columns = libraryColumns,
                        onSelect = { onPlayTracks(displayTracks, index) },
                        onPlay = { onPlayTracks(displayTracks, index) },
                        onAddToUpNext = { onAddToUpNext(track) },
                        onDownload = { onDownload(track) },
                        modifier = if (reorderEnabled) reorderState.itemModifier(track) else Modifier.animateItem(),
                        leadingHandle = if (reorderEnabled) {
                            { PlaylistTrackReorderHandle(reorderState, track, index) }
                        } else {
                            null
                        },
                    )
                }
            } else {
                itemsIndexed(displayTracks, key = { _, track -> track.id }, contentType = { _, _ -> "track" }) { index, track ->
                    ContentTrackRow(
                        track = track,
                        libraryColumns = libraryColumns,
                        onPlay = { onPlayTracks(displayTracks, index) },
                        onAddToUpNext = { onAddToUpNext(track) },
                        onDownload = { onDownload(track) },
                        modifier = if (reorderEnabled) reorderState.itemModifier(track) else Modifier.animateItem(),
                        leadingHandle = if (reorderEnabled) {
                            { PlaylistTrackReorderHandle(reorderState, track, index) }
                        } else {
                            null
                        },
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun ContentTrackRow(
    track: Track,
    libraryColumns: LibraryColumnVisibility = FullTrackMetadataColumns,
    onPlay: () -> Unit,
    onAddToUpNext: () -> Unit,
    onDownload: () -> Unit,
    modifier: Modifier = Modifier,
    compactLayout: Boolean = false,
    isNowPlaying: Boolean = false,
    nowPlayingIsPlaying: Boolean = false,
    nowPlayingIsBuffering: Boolean = false,
    playCount: Long? = null,
    sharedKey: String? = null,
    leadingHandle: (@Composable () -> Unit)? = null,
) {
    var menuExpanded by remember { mutableStateOf(false) }
    val cols = libraryColumns
    val likeActions = LocalLikeActions.current
    val ratingActions = LocalRatingActions.current
    val downloads = LocalDownloadStatus.current
    val canLike = likeActions.likesEnabled && track.canTogglePlexLike()
    val liked = likeActions.isLiked(track)
    val downloaded = downloads.isComplete(track)
    val rating = ratingActions.ratingFor(track)
    val techParts = remember(track.id, cols) {
        buildList {
            if (cols.audioCodec) {
                track.audioCodec?.takeIf { it.isNotBlank() }?.let(::add)
            }
            if (cols.bitrate && track.bitrateKbps != null && track.bitrateKbps > 0) add("${track.bitrateKbps} kbps")
            if (cols.sampleRate) {
                val rate = displaySampleRateLabel(track)
                if (rate != "—") add(rate)
            }
            if (cols.fileType) {
                val ext = displayFileTypeLabel(track)
                if (ext != "—") add(ext.trimStart('.').uppercase())
            }
        }
    }
    val playlistDragEnabled = LocalPlaylistDragEnabled.current
    val showPlaylistDragHandle = playlistDragEnabled && leadingHandle == null
    Box(if (showPlaylistDragHandle) modifier.draggableSong(track) else modifier) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .playTrackTarget(track)
                .clip(RoundedCornerShape(10.dp))
                .combinedClickable(onClick = onPlay, onLongClick = { menuExpanded = true })
                .background(
                    if (isNowPlaying) PhoebeUi.accent.copy(alpha = 0.12f) else Color.White.copy(alpha = 0.045f),
                )
                .padding(horizontal = 10.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            if (leadingHandle != null) {
                leadingHandle()
            } else if (showPlaylistDragHandle) {
                Box(
                    Modifier
                        .draggableSong(track, immediate = true)
                        .padding(horizontal = 2.dp, vertical = 6.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    PhoebeIconView(PhoebeIcon.Drag, tint = PhoebeUi.mutedText, modifier = Modifier.size(15.dp))
                }
            }
            Box(Modifier.size(50.dp), contentAlignment = Alignment.Center) {
                TrackArtworkImage(
                    track,
                    Modifier.fillMaxSize().sharedArtworkTransition(sharedKey),
                    elevated = !compactLayout,
                    maxDecodeDimension = ThumbnailArtworkMaxDecodeDimension,
                )
                if (isNowPlaying) {
                    Box(
                        Modifier
                            .fillMaxSize()
                            .clip(RoundedCornerShape(8.dp))
                            .background(Color.Black.copy(alpha = 0.45f)),
                        contentAlignment = Alignment.Center,
                    ) {
                        NowPlayingIndicator(
                            isPlaying = nowPlayingIsPlaying,
                            isBuffering = nowPlayingIsBuffering,
                            modifier = Modifier.size(20.dp),
                        )
                    }
                }
            }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                AutoScrollingText(
                    track.title,
                    color = if (isNowPlaying) PhoebeUi.accentLight else PhoebeUi.primaryText,
                    fontWeight = FontWeight.Bold,
                    fontSize = 14.sp,
                    lineHeight = 17.sp,
                    modifier = Modifier.sharedBoundsTransition(sharedKey?.let { "$it:title" }),
                )
                AutoScrollingText(track.artist, color = PhoebeUi.secondaryText, fontSize = 12.sp, lineHeight = 15.sp)
                if (track.album.isNotBlank()) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        AutoScrollingText(
                            track.album,
                            color = PhoebeUi.mutedText,
                            fontSize = 11.sp,
                            lineHeight = 14.sp,
                            modifier = Modifier.weight(1f, fill = false),
                        )
                        TrackStateBadges(
                            liked = !cols.favorite && canLike && liked,
                            downloaded = downloaded,
                            iconSize = 10.dp,
                        )
                    }
                }
                if (cols.year) {
                    AutoScrollingText(track.year?.toString() ?: "Year —", color = PhoebeUi.mutedText, fontSize = 11.sp)
                }
                if (cols.genre) {
                    AutoScrollingText(track.genre?.takeIf { it.isNotBlank() } ?: "Genre —", color = PhoebeUi.mutedText, fontSize = 11.sp)
                }
                if (cols.filepath) {
                    track.filepath?.takeIf { it.isNotBlank() }?.let { filepath ->
                        Text(filepath, color = PhoebeUi.mutedText, fontSize = 11.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    }
                }
                if (techParts.isNotEmpty()) {
                    AutoScrollingText(techParts.joinToString(" · "), color = PhoebeUi.mutedText, fontSize = 11.sp)
                }
            }
            if (playCount != null) {
                Text(
                    formatPlayCount(playCount),
                    color = PhoebeUi.secondaryText,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    textAlign = TextAlign.End,
                    modifier = Modifier.widthIn(min = 58.dp, max = 82.dp),
                )
            }
            if (cols.duration) {
                if (compactLayout) {
                    Text(
                        formatDuration(track.durationMs),
                        color = PhoebeUi.mutedText,
                        fontSize = 11.sp,
                    )
                } else {
                    WaveformDurationBar(
                        seed = trackWaveformSeed(track),
                        durationMs = track.durationMs,
                        progress = null,
                        bufferedProgress = null,
                        contentDescription = "Duration ${formatDuration(track.durationMs)}",
                        modifier = Modifier.width(64.dp).height(16.dp),
                    )
                }
            } else {
                Spacer(Modifier.width(8.dp))
            }
            if (cols.rating && ratingActions.ratingsEnabled && track.isRemoteLibraryTrack()) {
                RatingStars(
                    rating = rating,
                    enabled = true,
                    onRating = { ratingActions.onRateTrack(track, it) },
                    starSize = 11.dp,
                )
            }
            if (cols.favorite) {
                LikeButton(
                    liked = liked,
                    enabled = canLike,
                    onClick = { likeActions.onToggleLiked(track) },
                )
            }
            TrackDownloadIndicator(
                track = track,
                onDownload = null,
                showIdle = false,
                showComplete = false,
                showFailed = false,
                touchTargetSize = 40.dp,
            )
            Box(
                Modifier.size(40.dp).clip(CircleShape).clickable { menuExpanded = true },
                contentAlignment = Alignment.Center,
            ) {
                Text("···", color = PhoebeUi.secondaryText, fontSize = 17.sp)
            }
        }
        TrackActionMenu(
            expanded = menuExpanded,
            onDismiss = { menuExpanded = false },
            onAddToUpNext = onAddToUpNext,
            onDownload = onDownload,
            track = track,
        )
    }
}

@Composable
internal fun TrackDownloadIndicator(
    track: Track,
    modifier: Modifier = Modifier,
    onDownload: (() -> Unit)? = null,
    showIdle: Boolean = true,
    touchTargetSize: Dp = 28.dp,
    showComplete: Boolean = true,
    showFailed: Boolean = true,
) {
    val downloads = LocalDownloadStatus.current
    val downloadActions = LocalDownloadActions.current
    val item = downloads.itemFor(track)
    var confirmDelete by remember(track.id) { mutableStateOf(false) }
    var confirmCancel by remember(track.id) { mutableStateOf(false) }
    val isActive = downloads.isActive(track)
    val isComplete = downloads.isComplete(track)
    val isFailed = downloads.isFailed(track)
    val showCompleteState = showComplete && isComplete
    val showFailedState = showFailed && isFailed
    val hasVisibleState = isActive || showCompleteState || showFailedState
    val showIdleAction = showIdle && onDownload != null
    if (!hasVisibleState && !showIdleAction) return
    val clickModifier = if (onDownload != null && (showIdleAction || hasVisibleState)) {
        Modifier
            .clip(CircleShape)
            .clickable {
                if (isActive) {
                    confirmCancel = true
                } else if (isComplete) {
                    confirmDelete = true
                } else {
                    onDownload()
                }
            }
    } else {
        Modifier
    }
    Box(modifier.size(touchTargetSize).then(clickModifier), contentAlignment = Alignment.Center) {
        when {
            isActive && item?.state == DownloadState.Queued -> CircularProgressIndicator(
                color = PhoebeUi.accentLight,
                strokeWidth = 2.dp,
                modifier = Modifier.size(16.dp),
            )
            isActive -> CircularProgressIndicator(
                progress = { item?.progress?.coerceIn(0f, 1f) ?: 0f },
                color = PhoebeUi.accentLight,
                strokeWidth = 2.dp,
                modifier = Modifier.size(16.dp),
            )
            showCompleteState -> PhoebeIconView(
                PhoebeIcon.Check,
                tint = PhoebeUi.accentLight,
                modifier = Modifier.size(15.dp),
            )
            showFailedState -> PhoebeIconView(
                PhoebeIcon.Close,
                tint = PhoebeUi.accentLight,
                modifier = Modifier.size(14.dp),
            )
            showIdleAction -> PhoebeIconView(
                PhoebeIcon.Download,
                tint = PhoebeUi.mutedText.copy(alpha = 0.42f),
                modifier = Modifier.size(14.dp),
            )
        }
    }
    if (confirmDelete) {
        ConfirmDeleteDownloadsDialog(
            title = "Delete Download?",
            body = "Remove the downloaded file for \"${track.title}\" from this device?",
            confirmLabel = "Delete",
            onDismiss = { confirmDelete = false },
            onConfirm = {
                downloadActions.onDeleteDownloadedTracks(listOf(track))
                confirmDelete = false
            },
        )
    }
    if (confirmCancel) {
        ConfirmDeleteDownloadsDialog(
            title = "Cancel Download?",
            body = "Stop the current download and remove anything already downloaded for \"${track.title}\" from this device?",
            confirmLabel = "Cancel Download",
            onDismiss = { confirmCancel = false },
            onConfirm = {
                downloadActions.onCancelDownloadedTracks(listOf(track))
                confirmCancel = false
            },
        )
    }
}

private fun formatPlayCount(playCount: Long): String {
    val playWord = if (playCount == 1L) "play" else "plays"
    return "$playCount $playWord"
}

@Composable
internal fun TrackActionMenu(
    expanded: Boolean,
    onDismiss: () -> Unit,
    onAddToUpNext: (() -> Unit)?,
    onDownload: (() -> Unit)?,
    track: Track? = null,
) {
    val actions = LocalPlaylistActions.current
    val likeActions = LocalLikeActions.current
    val ratingActions = LocalRatingActions.current
    val metadataEditorActions = LocalMetadataEditorActions.current
    val navigationActions = LocalTrackNavigationActions.current
    val downloads = LocalDownloadStatus.current
    val downloadActions = LocalDownloadActions.current
    val downloadItem = track?.let { downloads.itemFor(it) }
    val downloadActive = track?.let { downloads.isActive(it) } == true
    val downloadComplete = track?.let { downloads.isComplete(it) } == true
    val downloadFailed = track?.let { downloads.isFailed(it) } == true
    val downloadProgress = downloadItem?.progress?.coerceIn(0f, 1f) ?: 0f
    var confirmDeleteDownload by remember(track?.id) { mutableStateOf(false) }
    var confirmCancelDownload by remember(track?.id) { mutableStateOf(false) }
    DropdownMenu(expanded = expanded, onDismissRequest = onDismiss) {
        if (track != null) {
            DropdownMenuItem(
                text = { Text("Edit Metadata") },
                onClick = {
                    metadataEditorActions.onRequestEdit(track)
                    onDismiss()
                },
            )
        }
        if (onAddToUpNext != null) {
            DropdownMenuItem(
                text = { Text("Add to Up Next") },
                onClick = {
                    onAddToUpNext()
                    onDismiss()
                },
            )
        }
        if (track != null) {
            DropdownMenuItem(
                text = { Text("Go to Song Detail") },
                onClick = {
                    navigationActions.onOpenSongDetail(track)
                    onDismiss()
                },
            )
            DropdownMenuItem(
                text = { Text("Go to Artist") },
                onClick = {
                    navigationActions.onOpenArtistForTrack(track)
                    onDismiss()
                },
            )
            DropdownMenuItem(
                text = { Text("Go to Album") },
                onClick = {
                    navigationActions.onOpenAlbumForTrack(track)
                    onDismiss()
                },
            )
            if (likeActions.likesEnabled && track.canTogglePlexLike()) {
                val liked = likeActions.isLiked(track)
                DropdownMenuItem(
                    text = { Text(if (liked) "Unlike Song" else "Like Song") },
                    onClick = {
                        likeActions.onToggleLiked(track)
                        onDismiss()
                    },
                )
            }
            if (ratingActions.ratingsEnabled && track.isRemoteLibraryTrack()) {
                DropdownMenuItem(
                    text = {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                        ) {
                            Text("Rate")
                            RatingStars(
                                rating = ratingActions.ratingFor(track),
                                enabled = true,
                                onRating = {
                                    ratingActions.onRateTrack(track, it)
                                    onDismiss()
                                },
                                starSize = 16.dp,
                                showClear = true,
                            )
                        }
                    },
                    onClick = {},
                )
            }
            AddToPlaylistMenuItems(
                track = track,
                actions = actions,
                onAfter = onDismiss,
            )
        }
        if (onDownload != null && track != null) {
            DropdownMenuItem(
                text = {
                    if (downloadActive) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                        ) {
                            CircularProgressIndicator(
                                progress = { downloadProgress },
                                modifier = Modifier.size(16.dp),
                                color = PhoebeUi.accentLight,
                                strokeWidth = 2.dp,
                            )
                            Text(
                                if (downloadItem?.state?.name == "Queued") "Queued" else "Downloading",
                            )
                            Text(downloadPercentLabel(downloadProgress), color = PhoebeUi.mutedText)
                        }
                    } else {
                        Text(
                            when {
                                downloadComplete -> "Delete Download"
                                downloadFailed -> "Retry Download"
                                else -> "Download Song"
                            },
                        )
                    }
                },
                onClick = {
                    when {
                        downloadActive -> confirmCancelDownload = true
                        downloadComplete -> confirmDeleteDownload = true
                        else -> {
                            onDownload()
                            onDismiss()
                        }
                    }
                },
            )
        }
    }
    if (confirmCancelDownload && track != null) {
        ConfirmDeleteDownloadsDialog(
            title = "Cancel Download?",
            body = "Stop the current download and remove anything already downloaded for \"${track.title}\" from this device?",
            confirmLabel = "Cancel Download",
            onDismiss = {
                confirmCancelDownload = false
                onDismiss()
            },
            onConfirm = {
                downloadActions.onCancelDownloadedTracks(listOf(track))
                confirmCancelDownload = false
                onDismiss()
            },
        )
    }
    if (confirmDeleteDownload && track != null) {
        ConfirmDeleteDownloadsDialog(
            title = "Delete Download?",
            body = "Remove the downloaded file for \"${track.title}\" from this device?",
            confirmLabel = "Delete",
            onDismiss = {
                confirmDeleteDownload = false
                onDismiss()
            },
            onConfirm = {
                downloadActions.onDeleteDownloadedTracks(listOf(track))
                confirmDeleteDownload = false
                onDismiss()
            },
        )
    }
}

@Composable
internal fun ConfirmDeleteDownloadsDialog(
    title: String,
    body: String,
    confirmLabel: String,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit,
) {
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Column(
            Modifier
                .padding(horizontal = 24.dp, vertical = 24.dp)
                .widthIn(min = 300.dp, max = 420.dp)
                .shadow(elevation = 28.dp, shape = RoundedCornerShape(18.dp), clip = false)
                .clip(RoundedCornerShape(18.dp))
                .background(PhoebeUi.modalSurface)
                .border(BorderStroke(1.dp, PhoebeUi.border), RoundedCornerShape(18.dp))
                .padding(horizontal = 22.dp, vertical = 20.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Text(title, color = PhoebeUi.primaryText, fontSize = 18.sp, fontWeight = FontWeight.Bold)
            Text(body, color = PhoebeUi.secondaryText, fontSize = 13.sp, lineHeight = 18.sp)
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                TextButton(onClick = onDismiss) {
                    Text("Cancel", color = PhoebeUi.secondaryText)
                }
                TextButton(onClick = onConfirm) {
                    Text(confirmLabel, color = PhoebeUi.accentLight, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

@Composable
internal fun LikeButton(
    liked: Boolean,
    enabled: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier
            .size(34.dp)
            .clip(CircleShape)
            .clickable(enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        PhoebeIconView(
            PhoebeIcon.Heart,
            tint = when {
                liked -> PhoebeUi.accentLight
                enabled -> PhoebeUi.secondaryText
                else -> PhoebeUi.mutedText.copy(alpha = 0.35f)
            },
            modifier = Modifier.size(17.dp),
            filled = liked,
        )
    }
}

@Composable
internal fun TrackStateBadges(
    liked: Boolean,
    downloaded: Boolean,
    iconSize: Dp,
    modifier: Modifier = Modifier,
) {
    if (!liked && !downloaded) return
    Row(
        modifier,
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(3.dp),
    ) {
        if (liked) {
            PhoebeIconView(
                PhoebeIcon.Heart,
                tint = PhoebeUi.accentLight,
                modifier = Modifier.size(iconSize),
                filled = true,
            )
        }
        if (downloaded) {
            PhoebeIconView(
                PhoebeIcon.Check,
                tint = PhoebeUi.mutedText,
                modifier = Modifier.size(iconSize),
            )
        }
    }
}

/**
 * Reusable group of [DropdownMenuItem]s for playlists: "New playlist…" plus existing playlists.
 * Local tracks target local playlists; Plex tracks target Plex playlists.
 */
@Composable
internal fun AddToPlaylistMenuItems(
    track: Track,
    actions: PlaylistActions = LocalPlaylistActions.current,
    onAfter: () -> Unit = {},
) {
    if (!actions.playlistsEnabled) return
    val isLocal = track.canAddToLocalPlaylist()
    val isPlex = track.canAddToPlexPlaylist()
    if (!isLocal && !isPlex) return
    val eligiblePlaylists = actions.playlists.filter { playlist ->
        when {
            playlist.isLocalPlaylist() -> isLocal
            playlist.isRemoteProviderPlaylist() -> isPlex
            else -> false
        }
    }
    var submenuExpanded by remember { mutableStateOf(false) }
    DropdownMenuItem(
        text = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Add to Playlist", modifier = Modifier.weight(1f))
                PhoebeIconView(PhoebeIcon.Forward, tint = PhoebeUi.mutedText, modifier = Modifier.size(14.dp))
            }
        },
        onClick = { submenuExpanded = true },
    )
    DropdownMenu(expanded = submenuExpanded, onDismissRequest = { submenuExpanded = false }) {
        DropdownMenuItem(
            text = { Text("New playlist…", color = PhoebeUi.accentLight, fontWeight = FontWeight.SemiBold) },
            onClick = {
                submenuExpanded = false
                actions.onRequestCreatePlaylist(listOf(track))
                onAfter()
            },
        )
        if (eligiblePlaylists.isNotEmpty()) {
            eligiblePlaylists.forEach { playlist ->
                DropdownMenuItem(
                    text = {
                        Column {
                            Text(playlist.title, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text(
                                "${playlist.trackCount} songs",
                                color = PhoebeUi.mutedText,
                                fontSize = 11.sp,
                            )
                        }
                    },
                    onClick = {
                        submenuExpanded = false
                        actions.onAddTrackToPlaylist(playlist, track)
                        onAfter()
                    },
                )
            }
        } else {
            DropdownMenuItem(
                text = { Text("No playlists yet", color = PhoebeUi.mutedText) },
                onClick = { submenuExpanded = false },
                enabled = false,
            )
        }
    }
}

/**
 * Minimal modal to capture the title for a new playlist. Driven from anywhere that
 * pushes onto [PlaylistActions.onRequestCreatePlaylist]; we collect the title and call
 * back into [AppState.createPlaylist] with the original seed tracks.
 */
@Composable
internal fun CreatePlaylistDialog(
    initialTracks: List<Track>,
    onDismiss: () -> Unit,
    onConfirm: (title: String) -> Unit,
) {
    var title by remember { mutableStateOf(defaultPlaylistName(initialTracks)) }
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Box(
            modifier = Modifier
                .padding(horizontal = 24.dp, vertical = 24.dp)
                .widthIn(min = 320.dp, max = 440.dp)
                .shadow(elevation = 30.dp, shape = RoundedCornerShape(18.dp), clip = false)
                .clip(RoundedCornerShape(18.dp))
                .background(PhoebeUi.modalSurface)
                .border(BorderStroke(1.dp, PhoebeUi.accentLight.copy(alpha = 0.18f)), RoundedCornerShape(18.dp))
                .padding(horizontal = 22.dp, vertical = 22.dp),
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                Text(
                    "New Playlist",
                    color = PhoebeUi.primaryText,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                )
                when {
                    initialTracks.size == 1 -> Text(
                        "Adding \"${initialTracks.first().title}\" to a new playlist.",
                        color = PhoebeUi.secondaryText,
                        fontSize = 13.sp,
                    )
                    initialTracks.size > 1 -> Text(
                        "Adding ${initialTracks.size} songs to a new playlist.",
                        color = PhoebeUi.secondaryText,
                        fontSize = 13.sp,
                    )
                    else -> Text(
                        "Pick a name for your new playlist.",
                        color = PhoebeUi.secondaryText,
                        fontSize = 13.sp,
                    )
                }
                PillTextField(
                    value = title,
                    onValueChange = { title = it },
                    modifier = Modifier.fillMaxWidth(),
                    placeholder = "Playlist name",
                    contentDescription = "Playlist name",
                    leadingIcon = PhoebeIcon.Plus,
                    showClearButton = true,
                    clearButtonContentDescription = "Clear playlist name",
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End),
                ) {
                    TextButton(onClick = onDismiss) {
                        Text("Cancel", color = PhoebeUi.secondaryText)
                    }
                    TextButton(
                        onClick = { if (title.isNotBlank()) onConfirm(title.trim()) },
                        enabled = title.isNotBlank(),
                    ) {
                        Text(
                            "Create",
                            color = if (title.isNotBlank()) PhoebeUi.accentLight else PhoebeUi.mutedText,
                            fontWeight = FontWeight.SemiBold,
                        )
                    }
                }
            }
        }
    }
}

internal fun defaultPlaylistName(initialTracks: List<Track>): String =
    when {
        initialTracks.isEmpty() -> "New Playlist"
        initialTracks.size == 1 -> initialTracks.first().title.take(40)
        else -> "New Playlist"
    }

/**
 * Maps a tap from a filtered/visible track list back into the unfiltered source queue.
 * This keeps Up Next aligned with the list the user would have seen after clearing the filter.
 */
internal fun playbackQueueForVisibleTrack(
    sourceTracks: List<Track>,
    visibleTracks: List<Track>,
    visibleIndex: Int,
): Pair<List<Track>, Int> {
    val visibleTrack = visibleTracks.getOrNull(visibleIndex) ?: return visibleTracks to visibleIndex
    val sourceIndex = sourceTracks.indexOfFirst { it.id == visibleTrack.id }
    return if (sourceIndex >= 0) sourceTracks to sourceIndex else visibleTracks to visibleIndex
}

/** Filter a list of tracks by a free-form search query against title/artist/album. */
internal fun filterTracksByQuery(tracks: List<Track>, query: String): List<Track> {
    val trimmed = query.trim()
    if (trimmed.isBlank()) return tracks
    return tracks.filter {
        it.title.contains(trimmed, ignoreCase = true) ||
            it.artist.contains(trimmed, ignoreCase = true) ||
            it.album.contains(trimmed, ignoreCase = true)
    }
}

/** Filter albums by query against album title or artist. */
internal fun filterAlbumsByQuery(albums: List<Album>, query: String): List<Album> {
    val trimmed = query.trim()
    if (trimmed.isBlank()) return albums
    return albums.filter {
        it.title.contains(trimmed, ignoreCase = true) ||
            it.artist.contains(trimmed, ignoreCase = true)
    }
}

/** Filter artists by query against their title. */
internal fun filterArtistsByQuery(artists: List<Artist>, query: String): List<Artist> {
    val trimmed = query.trim()
    if (trimmed.isBlank()) return artists
    return artists.filter { it.title.contains(trimmed, ignoreCase = true) }
}

/** Filter playlists by query against playlist title. */
internal fun filterPlaylistsByQuery(playlists: List<Playlist>, query: String): List<Playlist> {
    val trimmed = query.trim()
    val filtered = if (trimmed.isBlank()) {
        playlists
    } else {
        playlists.filter { it.title.contains(trimmed, ignoreCase = true) }
    }
    return filtered.sortedWith(compareByDescending<Playlist> { it.isLikedSongsPlaylist() })
}

internal fun artistAlbumCountSubtitle(artist: Artist): String {
    val w = if (artist.albumCount == 1) "album" else "albums"
    return "${artist.albumCount} $w"
}
