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
import com.phoebe.app.domain.JellyfinLibraryPageKind
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
import com.phoebe.app.domain.isLikedSongsPlaylist
import com.phoebe.app.domain.isRemoteLibraryTrack
import com.phoebe.app.domain.supportsPlexPlaylists
import com.phoebe.app.platform.createPlatformHttpClient
import com.phoebe.app.platform.currentTimeMs
import com.phoebe.app.platform.prefersReducedArtworkEffects
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import com.phoebe.app.sources.rememberPickLocalFolder
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.get
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import kotlinx.coroutines.yield
import kotlin.math.max

@Composable
internal fun LibraryTopBar(searchQuery: String, onSearchQuery: (String) -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 36.dp, vertical = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center,
    ) {
        SearchPill(searchQuery, onSearchQuery, Modifier.width(380.dp))
    }
}

@Composable
internal fun MainFeature(track: Track?, modifier: Modifier) {
    Column(modifier.padding(36.dp), verticalArrangement = Arrangement.spacedBy(26.dp)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                GlassIcon(PhoebeIcon.Back, "Back")
                GlassIcon(PhoebeIcon.Forward, "Forward")
            }
            Spacer(Modifier.weight(1f))
            GlassIcon(PhoebeIcon.Bell, "Notifications")
        }

        if (track == null) {
            HomeNothingPlayingHero()
        } else {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(40.dp),
            ) {
                FlippableSongArtwork(track = track, modifier = Modifier.size(292.dp))
                Column(Modifier.widthIn(max = 280.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                    SectionLabel("Now Playing", PhoebeUi.accentLight)
                    Text(track.title, color = PhoebeUi.primaryText, fontSize = 34.sp, lineHeight = 39.sp, fontWeight = FontWeight.Black)
                    Text(track.artist.uppercase(), color = PhoebeUi.secondaryText, fontSize = 20.sp, letterSpacing = 0.05.em)
                    Row(horizontalArrangement = Arrangement.spacedBy(22.dp), verticalAlignment = Alignment.CenterVertically) {
                        PhoebeIconView(PhoebeIcon.Heart, tint = PhoebeUi.accentLight, modifier = Modifier.size(30.dp), filled = true)
                        PhoebeIconView(PhoebeIcon.Queue, tint = PhoebeUi.secondaryText, modifier = Modifier.size(24.dp))
                        PhoebeIconView(PhoebeIcon.More, tint = PhoebeUi.secondaryText, modifier = Modifier.size(24.dp))
                    }
                }
            }

            Column(Modifier.widthIn(max = 380.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                SectionLabel("About The Album", PhoebeUi.mutedText)
                Text(
                    buildString {
                        if (track.album.isNotBlank()) {
                            append("Notes for ")
                            append(track.album)
                            append(" will appear here when your library provides them.")
                        } else {
                            append("Album notes from your library appear here when available.")
                        }
                    },
                    color = PhoebeUi.secondaryText,
                    fontSize = 15.sp,
                    lineHeight = 23.sp,
                )
                Row(horizontalArrangement = Arrangement.spacedBy(24.dp), verticalAlignment = Alignment.CenterVertically) {
                    if (track.durationMs > 0L) {
                        WaveformDurationBar(
                            seed = trackWaveformSeed(track),
                            durationMs = track.durationMs,
                            progress = null,
                            bufferedProgress = null,
                            contentDescription = "Track length ${formatDuration(track.durationMs)}",
                            modifier = Modifier.width(132.dp).height(22.dp),
                        )
                        Text(formatDuration(track.durationMs), color = PhoebeUi.secondaryText, fontSize = 14.sp)
                    }
                }
            }
        }
    }
}

@Composable
internal fun HomeNothingPlayingHero() {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(40.dp),
    ) {
        EmptyNowPlayingArtworkSlot(Modifier.size(292.dp), glyphSp = 52.sp)
        Column(Modifier.widthIn(max = 360.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            SectionLabel("Now Playing", PhoebeUi.accentLight)
            Text("Nothing playing", color = PhoebeUi.primaryText, fontSize = 34.sp, lineHeight = 39.sp, fontWeight = FontWeight.Black)
            Text(
                "When you start a track, it appears here. Use search or your library to pick something.",
                color = PhoebeUi.secondaryText,
                fontSize = 15.sp,
                lineHeight = 23.sp,
            )
        }
    }
    Column(Modifier.widthIn(max = 380.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        SectionLabel("Listening", PhoebeUi.mutedText)
        Text(
            "The queue and transport below stay ready. Nothing is queued until you play music.",
            color = PhoebeUi.mutedText,
            fontSize = 14.sp,
            lineHeight = 22.sp,
        )
    }
}

@Composable
internal fun EmptyNowPlayingArtworkSlot(
    modifier: Modifier = Modifier,
    glyphSp: TextUnit = 52.sp,
    shadowElevation: Dp = 18.dp,
    shadowAlpha: Float = 0.28f,
) {
    val shape = RoundedCornerShape(14.dp)
    val shadowColor = Color.Black.copy(alpha = shadowAlpha)
    val decorationModifier = if (shadowElevation > 0.dp && !prefersReducedArtworkEffects()) {
        modifier.shadow(
            shadowElevation,
            shape,
            ambientColor = shadowColor,
            spotColor = shadowColor,
        )
    } else {
        modifier
    }
    Box(
        decorationModifier
            .clip(shape)
            .background(PhoebeUi.glass)
            .border(BorderStroke(1.dp, PhoebeUi.border), shape),
        contentAlignment = Alignment.Center,
    ) {
        PhoebeIconView(PhoebeIcon.Music, tint = PhoebeUi.mutedText.copy(alpha = 0.42f), modifier = Modifier.size(glyphSp.value.dp))
    }
}

@Composable
internal fun DesktopContent(
    catalog: CatalogSnapshot,
    catalogRefreshing: Boolean,
    jellyfinPagination: Boolean = false,
    section: BrowseSection,
    selectedPlaylistId: String?,
    searchQuery: String,
    libraryFilter: LibraryFilterTab,
    libraryUi: LibraryUiPreferences,
    modifier: Modifier,
    onSearchQuery: (String) -> Unit,
    onLibraryFilter: (LibraryFilterTab) -> Unit,
    onPlaylist: (Playlist) -> Unit,
    onArtist: (Artist) -> Unit,
    onAlbum: (Album) -> Unit,
    onPlayTracks: (List<Track>, Int) -> Unit,
    onAddToUpNext: (Track) -> Unit,
    onDownload: (Track) -> Unit,
    onLibrarySortBy: (LibrarySortBy) -> Unit,
    onLibraryAscending: (Boolean) -> Unit,
    onLibraryColumns: (LibraryColumnVisibility) -> Unit,
    edgePadding: Dp = 36.dp,
    headlineFontSize: TextUnit = 30.sp,
    headlineLineHeight: TextUnit = 35.sp,
    searchPillModifier: Modifier = Modifier.width(270.dp),
    onDownloadPlaylist: (Playlist) -> Unit = {},
    onJellyfinPage: (JellyfinLibraryPageKind, Int) -> Unit = { _, _ -> },
) {
    val selectedPlaylist = catalog.playlists.firstOrNull { it.id == selectedPlaylistId }
    val playlistTracks = selectedPlaylistId?.let { catalog.tracksByParent[it].orEmpty() }.orEmpty()
    val favoriteActions = LocalFavoriteActions.current

    Column(
        modifier.padding(
            start = edgePadding,
            end = edgePadding,
            top = edgePadding,
            bottom = 24.dp,
        ),
        verticalArrangement = Arrangement.spacedBy(22.dp),
    ) {
        BoxWithConstraints(Modifier.fillMaxWidth()) {
            val sectionLabel = when {
                selectedPlaylist != null -> "Playlist"
                section == BrowseSection.Search -> "Search"
                section == BrowseSection.Library -> "Your Library"
                section == BrowseSection.Lyrics -> "Lyrics"
                section == BrowseSection.Playlists -> "Playlists"
                section == BrowseSection.Settings -> "Settings"
                else -> "Home"
            }
            val headline = selectedPlaylist?.title ?: when (section) {
                BrowseSection.Search -> "Find your sound"
                BrowseSection.Library -> "Albums, artists, and songs"
                BrowseSection.Lyrics -> "Follow along"
                BrowseSection.Playlists -> "Your playlists"
                BrowseSection.Settings -> "Customize your listening experience"
                BrowseSection.Home -> "Now playing"
            }
            val searchPlaceholder = when {
                selectedPlaylist != null -> "Search songs and artists"
                section == BrowseSection.Playlists -> "Search playlists"
                else -> "Search songs, artists, albums"
            }
            val titleBlock: @Composable () -> Unit = {
                SectionLabel(sectionLabel, PhoebeUi.accentLight)
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        headline,
                        color = PhoebeUi.primaryText,
                        fontSize = headlineFontSize,
                        lineHeight = headlineLineHeight,
                        fontWeight = FontWeight.Black,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false),
                    )
                    selectedPlaylist?.let { playlist ->
                        LikeButton(
                            liked = favoriteActions.isFavorite(playlist),
                            enabled = true,
                            onClick = { favoriteActions.onTogglePlaylist(playlist) },
                        )
                    }
                }
            }
            if (maxWidth < 640.dp) {
                Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                    Column(Modifier.fillMaxWidth()) { titleBlock() }
                    SearchPill(searchQuery, onSearchQuery, searchPillModifier, searchPlaceholder)
                }
            } else {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) { titleBlock() }
                    SearchPill(searchQuery, onSearchQuery, searchPillModifier, searchPlaceholder)
                }
            }
        }

        when {
            selectedPlaylist != null -> {
                val playlistActions = LocalPlaylistActions.current
                var playlistSortBy by remember(selectedPlaylist.id) { mutableStateOf(LibrarySortBy.PlaylistOrder) }
                var playlistAscending by remember(selectedPlaylist.id) { mutableStateOf(true) }
                val sortedPlaylistTracks = remember(playlistTracks, playlistSortBy, playlistAscending) {
                    sortTracksForLibrary(playlistTracks, playlistSortBy, playlistAscending)
                }
                val filteredPlaylistTracks = remember(sortedPlaylistTracks, searchQuery) {
                    filterTracksByQuery(sortedPlaylistTracks, searchQuery)
                }
                val playFilteredPlaylistTracks: (List<Track>, Int) -> Unit = { visible, visibleIndex ->
                    val sourceTracks = if (
                        playlistSortBy == LibrarySortBy.PlaylistOrder &&
                        playlistAscending &&
                        searchQuery.isBlank()
                    ) {
                        visible
                    } else {
                        sortedPlaylistTracks
                    }
                    val (queueTracks, queueIndex) = playbackQueueForVisibleTrack(
                        sourceTracks,
                        visible,
                        visibleIndex,
                    )
                    onPlayTracks(queueTracks, queueIndex)
                }
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    PlaylistTrackSummaryLine(
                        totalCount = sortedPlaylistTracks.size,
                        visibleCount = filteredPlaylistTracks.size,
                        searchQuery = searchQuery,
                    )
                    DetailSectionToolbar(
                        sortBy = playlistSortBy,
                        sortKeys = listOf(
                            LibrarySortBy.PlaylistOrder,
                            LibrarySortBy.Name,
                            LibrarySortBy.Album,
                            LibrarySortBy.Year,
                        ),
                        sortLabel = { key ->
                            when (key) {
                                LibrarySortBy.PlaylistOrder -> "Playlist order"
                                LibrarySortBy.Album -> "Album name"
                                LibrarySortBy.Year -> "Release date"
                                else -> "Song name"
                            }
                        },
                        onSortBy = { playlistSortBy = it },
                        ascending = playlistAscending,
                        onAscending = { playlistAscending = it },
                        columns = libraryUi.columns,
                        onColumns = onLibraryColumns,
                        actions = {
                            DownloadActionButton("Download Playlist", sortedPlaylistTracks) { onDownloadPlaylist(selectedPlaylist) }
                            PlaylistExportMenu(playlist = selectedPlaylist)
                        },
                    )
                    TrackList(
                        tracks = filteredPlaylistTracks,
                        empty = if (searchQuery.isNotBlank()) {
                            "No tracks / artists in ${selectedPlaylist.title} match \"$searchQuery\"."
                        } else {
                            "No tracks loaded for ${selectedPlaylist.title}."
                        },
                        catalogRefreshing = catalogRefreshing,
                        showLoadingWhenEmpty = searchQuery.isBlank(),
                        onPlayTracks = playFilteredPlaylistTracks,
                        onAddToUpNext = onAddToUpNext,
                        onDownload = onDownload,
                        libraryColumns = libraryUi.columns,
                        onMoveTrack = if (
                            playlistSortBy == LibrarySortBy.PlaylistOrder &&
                            playlistAscending &&
                            searchQuery.isBlank()
                        ) {
                            { from, to -> playlistActions.onMovePlaylistTrack(selectedPlaylist, from, to) }
                        } else {
                            null
                        },
                    )
                }
            }
            section == BrowseSection.Search -> {
                val query = searchQuery.trim()
                val allTracks = remember(catalog.tracksByParent) {
                    catalog.tracksByParent.values.asSequence().flatten().distinctBy { it.id }.toList()
                }
                val results = if (query.isBlank()) {
                    allTracks.take(8)
                } else {
                    allTracks.filter {
                        it.title.contains(query, ignoreCase = true) ||
                            it.artist.contains(query, ignoreCase = true) ||
                            it.album.contains(query, ignoreCase = true)
                    }
                }
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    DetailSectionToolbar(
                        sortBy = null,
                        sortKeys = emptyList(),
                        sortLabel = { "" },
                        onSortBy = null,
                        ascending = null,
                        onAscending = null,
                        columns = libraryUi.columns,
                        onColumns = onLibraryColumns,
                    )
                    TrackList(
                        results,
                        if (query.isBlank()) "Start typing to search songs, artists, and albums." else "No matches for \"$query\".",
                        catalogRefreshing,
                        onPlayTracks,
                        onAddToUpNext,
                        onDownload,
                        libraryColumns = libraryUi.columns,
                    )
                }
            }
            section == BrowseSection.Library -> LibraryPanel(
                catalog,
                catalogRefreshing,
                jellyfinPagination,
                libraryFilter,
                libraryUi,
                onLibraryFilter,
                onLibrarySortBy,
                onLibraryAscending,
                onLibraryColumns,
                onPlaylist,
                onArtist,
                onAlbum,
                onPlayTracks,
                onAddToUpNext,
                onDownload,
                onJellyfinPage,
            )
            section == BrowseSection.Playlists -> {
                val playlistActions = LocalPlaylistActions.current
                val catalogSyncInProgress = LocalCatalogSyncInProgress.current
                val sourcePlaylists = playlistActions.playlists
                var visiblePlaylists by remember { mutableStateOf<List<Playlist>?>(null) }
                LaunchedEffect(sourcePlaylists, searchQuery) {
                    visiblePlaylists = withContext(Dispatchers.Default) {
                        filterPlaylistsByQuery(sourcePlaylists, searchQuery)
                    }
                }
                val preparedVisiblePlaylists = visiblePlaylists
                    ?: if (searchQuery.isBlank()) sourcePlaylists else emptyList()
                val preparingPlaylists = visiblePlaylists == null &&
                    preparedVisiblePlaylists.isEmpty() &&
                    sourcePlaylists.isNotEmpty()
                val showPlaylistSyncProgress = catalogSyncInProgress && searchQuery.isBlank()
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    if (!playlistActions.playlistsEnabled) {
                        Text(
                            "Sign in to your provider, or add a local music folder to use playlists.",
                            color = PhoebeUi.mutedText,
                            fontSize = 14.sp,
                        )
                    } else {
                        if (showPlaylistSyncProgress) {
                            CatalogLoadingStrip()
                        }
                        if (preparingPlaylists) {
                            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                LibraryLoadingStrip()
                                Text(
                                    "Loading playlists...",
                                    color = PhoebeUi.mutedText,
                                    fontSize = 14.sp,
                                )
                            }
                        } else if (preparedVisiblePlaylists.isEmpty()) {
                            if (!showPlaylistSyncProgress) {
                                Text(
                                    if (searchQuery.isNotBlank()) {
                                        "No playlists match \"$searchQuery\"."
                                    } else {
                                        "No playlists yet. Create one from the sidebar."
                                    },
                                    color = PhoebeUi.mutedText,
                                    fontSize = 14.sp,
                                )
                            }
                        } else {
                            LazyColumn(
                                modifier = Modifier.fillMaxSize(),
                                verticalArrangement = Arrangement.spacedBy(6.dp),
                            ) {
                                items(preparedVisiblePlaylists, key = { it.id }, contentType = { "playlist" }) { playlist ->
                                    val liked = playlist.isLikedSongsPlaylist()
                                    Box(Modifier.draggablePlaylist(playlist).playlistDropTarget(playlist)) {
                                        PlaylistRow(
                                            icon = if (liked) PhoebeIcon.Heart else null,
                                            title = playlist.title,
                                            subtitle = "${playlist.trackCount} songs",
                                            thumbUrl = playlist.thumbUrl,
                                            accent = liked,
                                            onClick = { onPlaylist(playlist) },
                                            onLongClick = { playlistActions.onShufflePlaylist(playlist) },
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
            else -> {
                val firstTracks = catalog.tracksByParent.values.firstOrNull().orEmpty()
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    DetailSectionToolbar(
                        sortBy = null,
                        sortKeys = emptyList(),
                        sortLabel = { "" },
                        onSortBy = null,
                        ascending = null,
                        onAscending = null,
                        columns = libraryUi.columns,
                        onColumns = onLibraryColumns,
                    )
                    TrackList(
                        firstTracks,
                        "Your library is empty.",
                        catalogRefreshing,
                        onPlayTracks,
                        onAddToUpNext,
                        onDownload,
                        libraryColumns = libraryUi.columns,
                    )
                }
            }
        }
    }
}
