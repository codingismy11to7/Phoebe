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

/** Current playback state, exposed implicitly so any track row can show a "now playing" badge. */
internal data class NowPlayingIndicatorState(
    val trackId: String? = null,
    val isPlaying: Boolean = false,
    val isBuffering: Boolean = false,
)

internal val LocalNowPlaying = compositionLocalOf { NowPlayingIndicatorState() }

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
    val onCopyPlaylistToPlaylist: (source: Playlist, target: Playlist) -> Unit = { _, _ -> },
    val onCreatePlaylist: (title: String, initialTracks: List<Track>) -> Unit = { _, _ -> },
    val onRequestCreatePlaylist: (initialTracks: List<Track>) -> Unit = {},
    val onOpenLikedSongs: () -> Unit = {},
    val onExportLocalPlaylist: (Playlist, PlaylistExportFormat) -> Unit = { _, _ -> },
)

internal val LocalPlaylistActions = compositionLocalOf { PlaylistActions() }

internal data class LikeActions(
    val likedTrackIds: Set<String> = emptySet(),
    val likesEnabled: Boolean = false,
    val onToggleLiked: (Track) -> Unit = {},
) {
    fun isLiked(track: Track): Boolean = track.id in likedTrackIds
}

internal val LocalLikeActions = compositionLocalOf { LikeActions() }

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

@OptIn(ExperimentalSharedTransitionApi::class)
internal val LocalSharedTransitionScope = compositionLocalOf<SharedTransitionScope?> { null }

internal val LocalAnimatedVisibilityScope = compositionLocalOf<AnimatedVisibilityScope?> { null }
