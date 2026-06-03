package com.phoebe.app.ui

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.SharedTransitionScope
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
import androidx.compose.runtime.mutableIntStateOf
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
import com.phoebe.app.domain.CatalogSyncState
import com.phoebe.app.domain.LibraryColumnVisibility
import com.phoebe.app.domain.LibrarySortBy
import com.phoebe.app.domain.LibraryUiPreferences
import com.phoebe.app.domain.CatalogSnapshot
import com.phoebe.app.domain.DownloadItem
import com.phoebe.app.domain.DownloadState
import com.phoebe.app.domain.DownloadStatusEvent
import com.phoebe.app.domain.LocalFolderMediaSourceConfig
import com.phoebe.app.domain.MediaProviderType
import com.phoebe.app.domain.MediaSourcesState
import com.phoebe.app.domain.MostPlayedEntry
import com.phoebe.app.domain.MusicLibrary
import com.phoebe.app.domain.RecentlyPlayedEntry
import com.phoebe.app.domain.PlexServer
import com.phoebe.app.domain.PlexSession
import com.phoebe.app.domain.Playlist
import com.phoebe.app.domain.RepeatMode
import com.phoebe.app.domain.Track
import com.phoebe.app.domain.catalogPrefix
import com.phoebe.app.domain.isLocalMediaPlayback
import com.phoebe.app.domain.isRemoteLibraryTrack
import com.phoebe.app.domain.supportsPlexPlaylists
import com.phoebe.app.playlists.PlaylistExportFormat
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

internal val LocalCatalogHasContent = compositionLocalOf { false }

internal val LocalCatalogSyncState = compositionLocalOf { CatalogSyncState() }

/** True while a foreground or background catalog sync is still running (metadata, tracks, playlists, artwork). */
internal val LocalCatalogSyncInProgress = compositionLocalOf { false }

/**
 * When false, tiles keep gradient placeholders (no new decode jobs).
 * Used briefly after sync so layout can settle before artwork loads.
 */
internal val LocalArtworkLoadingEnabled = compositionLocalOf { true }

/** False while the home track index and played rows are still being derived after sync. */
internal val LocalHomeTrackSectionsReady = compositionLocalOf { true }

/** True when SQL-ranked most-played rows exist but home has not resolved the full panel yet. */
internal val LocalMostPlayedResolving = compositionLocalOf { false }

internal data class MobileChromePadding(
    val top: Dp = 0.dp,
    val bottom: Dp = 0.dp,
)

internal val LocalMobileChromePadding = compositionLocalOf { MobileChromePadding() }

internal val LocalTracksLoading = compositionLocalOf { emptySet<String>() }

internal data class DownloadStatusSummary(
    val total: Int,
    val complete: Int,
    val active: Int,
    val activeProgress: Float,
    val failed: Int,
    val unavailable: Int,
)

internal class DownloadStatusSnapshot(
    itemsByTrackId: Map<String, DownloadItem> = emptyMap(),
    hasActiveDownloadJobs: Boolean = false,
) {
    private data class DownloadUiItem(
        val state: DownloadState,
        val progress: Float,
        val localUri: String?,
        val updatedAtMs: Long,
    )

    private val itemStateByTrackId = linkedMapOf<String, DownloadUiItem>().apply {
        putAll(itemsByTrackId.mapValues { (_, item) -> item.toDownloadUiItem() })
    }
    private val progressStateByTrackId = linkedMapOf<String, Float>().apply {
        itemsByTrackId.forEach { (trackId, item) ->
            if (item.state == DownloadState.Downloading) {
                put(trackId, item.progress.coerceIn(0f, 1f))
            }
        }
    }
    private var stateVersion by mutableIntStateOf(0)
    var hasActiveDownloadJobs by mutableStateOf(hasActiveDownloadJobs)
        private set

    fun replaceItems(items: List<DownloadItem>) {
        var changed = false
        val nextIds = items.mapTo(mutableSetOf()) { it.trackId }
        val itemIterator = itemStateByTrackId.keys.iterator()
        while (itemIterator.hasNext()) {
            if (itemIterator.next() !in nextIds) {
                itemIterator.remove()
                changed = true
            }
        }
        val progressIterator = progressStateByTrackId.keys.iterator()
        while (progressIterator.hasNext()) {
            if (progressIterator.next() !in nextIds) {
                progressIterator.remove()
                changed = true
            }
        }
        items.forEach { item ->
            changed = updateDownloadItem(item) || changed
        }
        if (changed) bumpStateVersion()
    }

    private fun updateDownloadItem(item: DownloadItem): Boolean {
        val existing = itemStateByTrackId[item.trackId]
        if (existing != null && shouldKeepExistingDownloadItem(existing, item)) return false
        var changed = false
        if (item.state == DownloadState.Downloading) {
            val progress = item.progress.coerceIn(0f, 1f)
            if (progressStateByTrackId[item.trackId] != progress) {
                progressStateByTrackId[item.trackId] = progress
                changed = true
            }
        } else {
            if (progressStateByTrackId.remove(item.trackId) != null) {
                changed = true
            }
        }
        val coarseItem = item.toDownloadUiItem()
        if (itemStateByTrackId[item.trackId] != coarseItem) {
            itemStateByTrackId[item.trackId] = coarseItem
            changed = true
        }
        return changed
    }

    private fun shouldKeepExistingDownloadItem(existing: DownloadUiItem, incoming: DownloadItem): Boolean {
        val existingComplete = existing.state == DownloadState.Complete || !existing.localUri.isNullOrBlank()
        val incomingIncomplete = incoming.state != DownloadState.Complete && incoming.localUri.isNullOrBlank()
        if (existingComplete && incomingIncomplete) return true
        return existing.updatedAtMs > 0L &&
            incoming.updatedAtMs > 0L &&
            incoming.updatedAtMs < existing.updatedAtMs
    }

    fun apply(event: DownloadStatusEvent) {
        var changed = false
        event.removedTrackIds.forEach { trackId ->
            if (itemStateByTrackId.remove(trackId) != null) changed = true
            if (progressStateByTrackId.remove(trackId) != null) changed = true
        }
        event.items.forEach { item ->
            changed = updateDownloadItem(item) || changed
        }
        if (changed) bumpStateVersion()
    }

    fun setActiveDownloadJobs(active: Boolean) {
        hasActiveDownloadJobs = active
    }

    fun itemFor(track: Track): DownloadItem? {
        observeDownloadState()
        val item = itemStateByTrackId[track.id] ?: return null
        val progress = if (item.state == DownloadState.Downloading) {
            progressStateByTrackId[track.id] ?: item.progress
        } else {
            item.progress
        }
        return DownloadItem(
            trackId = track.id,
            title = track.title,
            artist = track.artist,
            state = item.state,
            progress = progress,
            localUri = item.localUri,
            downloadUrl = track.downloadUrl,
            updatedAtMs = item.updatedAtMs,
        )
    }

    fun isComplete(track: Track): Boolean {
        observeDownloadState()
        val item = itemStateByTrackId[track.id]
        return isComplete(track, item)
    }

    val count: Int
        get() {
            observeDownloadState()
            return itemStateByTrackId.size
        }

    fun isActive(track: Track): Boolean {
        observeDownloadState()
        val item = itemStateByTrackId[track.id]
        return isActive(item, isComplete(track, item))
    }

    fun isFailed(track: Track): Boolean {
        observeDownloadState()
        val item = itemStateByTrackId[track.id]
        return isFailed(track, item, isComplete(track, item))
    }

    fun summarize(tracks: List<Track>): DownloadStatusSummary {
        observeDownloadState()
        var complete = 0
        var active = 0
        var activeProgress = 0f
        var failed = 0
        var unavailable = 0
        tracks.forEach { track ->
            val item = itemStateByTrackId[track.id]
            val isComplete = isComplete(track, item)
            if (isComplete) {
                complete++
            } else {
                if (isActive(item, isComplete)) {
                    active++
                    activeProgress += item?.progress?.coerceIn(0f, 1f) ?: 0f
                }
                if (isFailed(track, item, isComplete)) {
                    failed++
                }
                if (track.downloadUrl.isBlank()) {
                    unavailable++
                }
            }
        }
        return DownloadStatusSummary(
            total = tracks.size,
            complete = complete,
            active = active,
            activeProgress = activeProgress,
            failed = failed,
            unavailable = unavailable,
        )
    }

    private fun observeDownloadState() {
        stateVersion
    }

    private fun bumpStateVersion() {
        stateVersion++
    }

    private fun isComplete(track: Track, item: DownloadUiItem?): Boolean =
        item?.state == DownloadState.Complete ||
            !item?.localUri.isNullOrBlank() ||
            (!track.localUri.isNullOrBlank() && (track.downloadUrl.isNotBlank() || track.isRemoteLibraryTrack()))

    private fun isActive(item: DownloadUiItem?, complete: Boolean): Boolean {
        if (complete || item == null) return false
        val activeState = item.state == DownloadState.Queued || item.state == DownloadState.Downloading
        return activeState && (hasActiveDownloadJobs || item.updatedAtMs > 0L)
    }

    private fun isFailed(track: Track, item: DownloadUiItem?, complete: Boolean): Boolean =
        track.downloadUrl.isNotBlank() && !complete && item?.state == DownloadState.Failed

    private fun DownloadItem.toDownloadUiItem(): DownloadUiItem {
        val coarseProgress = if (state == DownloadState.Downloading) {
            val normalized = progress.coerceIn(0f, 1f)
            val coarseProgress = (normalized * 20f).roundToInt() / 20f
            if (normalized > 0f) max(0.05f, coarseProgress) else 0f
        } else {
            progress.coerceIn(0f, 1f)
        }
        return DownloadUiItem(
            state = state,
            progress = coarseProgress,
            localUri = localUri,
            updatedAtMs = updatedAtMs,
        )
    }
}

internal val LocalDownloadStatus = compositionLocalOf { DownloadStatusSnapshot() }

internal data class DownloadActions(
    val onDeleteDownloadedTracks: (List<Track>) -> Unit = {},
    val onCancelDownloadedTracks: (List<Track>) -> Unit = {},
)

internal val LocalDownloadActions = compositionLocalOf { DownloadActions() }

/** Current playback state, exposed implicitly so any track row can show a "now playing" badge. */
internal data class NowPlayingIndicatorState(
    val trackId: String? = null,
    val isPlaying: Boolean = false,
    val isBuffering: Boolean = false,
)

internal val LocalNowPlaying = compositionLocalOf { NowPlayingIndicatorState() }

internal class MobileNowPlayingArtworkTransitionState {
    var miniArtworkBounds by mutableStateOf<Rect?>(null)
    var fullArtworkBounds by mutableStateOf<Rect?>(null)
    var activeTrack by mutableStateOf<Track?>(null)
    var progress by mutableFloatStateOf(0f)
}

internal val LocalMobileNowPlayingArtworkTransition =
    compositionLocalOf<MobileNowPlayingArtworkTransitionState?> { null }

/**
 * Snapshot of "last played" timestamps (Unix millis) keyed by artist title, album title,
 * and track id. Threaded through the composition so any row in the library / detail UI
 * can ask "when was this last heard?" without taking a hard dependency on AppState.
 */
internal data class PlayHistorySnapshot(
    val byArtist: Map<String, Long> = emptyMap(),
    val byAlbum: Map<String, Long> = emptyMap(),
    val byTrack: Map<String, Long> = emptyMap(),
    val playCountByTrack: Map<String, Long> = emptyMap(),
    val playEventsByTrack: Map<String, List<Long>> = emptyMap(),
    val topMostPlayed: List<MostPlayedEntry> = emptyList(),
    val topRecentlyPlayed: List<RecentlyPlayedEntry> = emptyList(),
)

internal val LocalPlayHistory = compositionLocalOf { PlayHistorySnapshot() }

/**
 * Current wall-clock reference used to render relative "last played" strings.
 * Updated periodically by [PhoebeRoot] so "Just now" eventually slides to "Today",
 * "Today" slides to "Yesterday", etc. without requiring a recomposition trigger
 * from elsewhere.
 */
internal val LocalNowMs = compositionLocalOf { 0L }

/**
 * Playlist mutation surface exposed via [CompositionLocal] so overflow menus / song rows /
 * sidebar drop targets can add to Plex playlists without threading callbacks everywhere.
 *
 * [playlistsEnabled] is true only when Plex is signed in with a server and music library selected.
 * [onAddTrackToPlaylist] and [onCreatePlaylist] both no-op by default.
 */
internal data class PlaylistActions(
    val playlists: List<Playlist> = emptyList(),
    /** Plex session or enabled local folders — required for any playlist UI or mutations. */
    val playlistsEnabled: Boolean = false,
    val onAddTrackToPlaylist: (Playlist, Track) -> Unit = { _, _ -> },
    val onMovePlaylistTrack: (Playlist, Int, Int) -> Unit = { _, _, _ -> },
    val onCopyPlaylistToPlaylist: (source: Playlist, target: Playlist) -> Unit = { _, _ -> },
    val onCreatePlaylist: (title: String, initialTracks: List<Track>) -> Unit = { _, _ -> },
    val onRequestCreatePlaylist: (initialTracks: List<Track>) -> Unit = {},
    val onOpenLikedSongs: () -> Unit = {},
    val onExportLocalPlaylist: (Playlist, PlaylistExportFormat) -> Unit = { _, _ -> },
    val onShufflePlaylist: (Playlist) -> Unit = {},
)

internal val LocalPlaylistActions = compositionLocalOf { PlaylistActions() }

internal data class LikeActions(
    val likedTrackIds: Set<String> = emptySet(),
    val likesEnabled: Boolean = false,
    val onToggleLiked: (Track) -> Unit = {},
) {
    fun isLiked(track: Track): Boolean =
        equivalentTrackIds(track.id).any { it in likedTrackIds }
}

internal val LocalLikeActions = compositionLocalOf { LikeActions() }

internal data class FavoriteActions(
    val catalog: CatalogSnapshot = CatalogSnapshot(),
    val onToggleArtist: (Artist) -> Unit = {},
    val onToggleAlbum: (Album) -> Unit = {},
    val onTogglePlaylist: (Playlist) -> Unit = {},
) {
    fun isFavorite(artist: Artist): Boolean =
        catalog.artists.firstOrNull { it.id == artist.id }?.favorite ?: artist.favorite

    fun isFavorite(album: Album): Boolean =
        catalog.albums.firstOrNull { it.id == album.id }?.favorite ?: album.favorite

    fun isFavorite(playlist: Playlist): Boolean =
        catalog.playlists.firstOrNull { it.id == playlist.id }?.favorite ?: playlist.favorite
}

internal val LocalFavoriteActions = compositionLocalOf { FavoriteActions() }

internal data class RatingActions(
    val ratingsEnabled: Boolean = false,
    val catalog: CatalogSnapshot = CatalogSnapshot(),
    val trackRatingsById: Map<String, Float?> = emptyMap(),
    val onRateTrack: (Track, Float?) -> Unit = { _, _ -> },
    val onRateArtist: (Artist, Float?) -> Unit = { _, _ -> },
    val onRateAlbum: (Album, Float?) -> Unit = { _, _ -> },
    val onRatePlaylist: (Playlist, Float?) -> Unit = { _, _ -> },
) {
    fun ratingFor(track: Track): Float? =
        equivalentTrackIds(track.id)
            .firstNotNullOfOrNull { trackRatingsById[it] }
            ?: track.rating

    fun ratingFor(artist: Artist): Float? =
        catalog.artists.firstOrNull { it.id == artist.id }?.rating ?: artist.rating

    fun ratingFor(album: Album): Float? =
        catalog.albums.firstOrNull { it.id == album.id }?.rating ?: album.rating

    fun ratingFor(playlist: Playlist): Float? =
        catalog.playlists.firstOrNull { it.id == playlist.id }?.rating ?: playlist.rating
}

internal val LocalRatingActions = compositionLocalOf { RatingActions() }

internal data class TrackNavigationActions(
    val onOpenArtistForTrack: (Track) -> Boolean = { false },
    val onOpenAlbumForTrack: (Track) -> Boolean = { false },
    val onOpenSongDetail: (Track) -> Unit = {},
)

internal val LocalTrackNavigationActions = compositionLocalOf { TrackNavigationActions() }

private fun equivalentTrackIds(id: String): Set<String> {
    if (id.isBlank()) return emptySet()
    for (provider in MediaProviderType.entries) {
        val prefix = "${provider.catalogPrefix}:"
        if (id.startsWith(prefix)) {
            val bare = id.removePrefix(prefix)
            return setOf(id, bare)
        }
    }
    return buildSet {
        add(id)
        for (provider in MediaProviderType.entries) {
            add("${provider.catalogPrefix}:$id")
        }
    }
}

internal fun buildTrackRatingIndex(catalog: CatalogSnapshot): Map<String, Float?> {
    val ratings = hashMapOf<String, Float?>()
    catalog.tracksByParent.values.forEach { tracks ->
        tracks.forEach { track ->
            equivalentTrackIds(track.id).forEach { id -> ratings[id] = track.rating }
        }
    }
    return ratings
}

/**
 * Drag state for "drag a song row onto a sidebar playlist row to add it". Song rows update
 * this when the user picks one up; playlist rows register their on-screen bounds via
 * [DragDropController.register] and read [DragDropController.draggedTrack] to draw a
 * highlight when the pointer is hovering above them.
 */
internal class DragDropController {
    var draggedPlaylist by mutableStateOf<Playlist?>(null)
        private set
    var draggedTrack by mutableStateOf<Track?>(null)
        private set
    var pointer by mutableStateOf<Offset?>(null)
        private set
    internal val targets = mutableStateMapOf<String, DropTarget>()

    /** Title of the playlist row currently hovered by the drag pointer, or null. */
    val hoveringPlaylistTitle: String?
        get() = currentHover()?.title

    fun start(track: Track, initialPointer: Offset) {
        draggedPlaylist = null
        draggedTrack = track
        pointer = initialPointer
    }

    fun start(playlist: Playlist, initialPointer: Offset) {
        draggedTrack = null
        draggedPlaylist = playlist
        pointer = initialPointer
    }

    fun update(pointer: Offset) {
        this.pointer = pointer
    }

    /** Drops the dragged track, returning the playlist it landed on (if any). */
    fun end(): Playlist? {
        val hit = currentHover()
        draggedPlaylist = null
        draggedTrack = null
        pointer = null
        return hit
    }

    fun cancel() {
        draggedPlaylist = null
        draggedTrack = null
        pointer = null
    }

    fun register(playlist: Playlist, bounds: Rect) {
        targets[playlist.id] = DropTarget(playlist, bounds)
    }

    fun unregister(playlistId: String) {
        targets.remove(playlistId)
    }

    /** True when the active drag pointer is currently hovering over [playlistId]. */
    fun isHovering(playlistId: String): Boolean {
        if (draggedTrack == null && draggedPlaylist == null) return false
        val pt = pointer ?: return false
        return targets[playlistId]?.bounds?.contains(pt) == true
    }

    internal fun currentHover(): Playlist? {
        val pt = pointer ?: return null
        return targets.values.firstOrNull { it.bounds.contains(pt) }?.playlist
    }

    internal data class DropTarget(val playlist: Playlist, val bounds: Rect)
}

internal val LocalDragDrop = compositionLocalOf<DragDropController?> { null }

/** When false, song rows skip playlist drag-and-drop (mobile compact layout). */
internal val LocalPlaylistDragEnabled = compositionLocalOf { true }

internal val LocalSharedElementTransitionsEnabled = compositionLocalOf { true }

internal val LocalContinuousMotionEnabled = compositionLocalOf { true }

@OptIn(ExperimentalSharedTransitionApi::class)
internal val LocalSharedTransitionScope = compositionLocalOf<SharedTransitionScope?> { null }

internal val LocalAnimatedVisibilityScope = compositionLocalOf<AnimatedVisibilityScope?> { null }

/** Windows desktop: app content draws under the native caption; insets omit the top safe area. */
internal val LocalDesktopMergesTitleBar = compositionLocalOf { false }
