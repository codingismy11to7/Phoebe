package com.phoebe.app.ui

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.SharedTransitionLayout
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.MutableTransitionState
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
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBars
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
import androidx.compose.runtime.key
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.produceState
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
import androidx.compose.ui.graphics.graphicsLayer
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
import com.phoebe.app.domain.ShellPlaybackState
import com.phoebe.app.data.catalogAlbumsForArtist
import com.phoebe.app.data.catalogTracksForArtist
import com.phoebe.app.domain.Album
import com.phoebe.app.domain.AppScreen
import com.phoebe.app.domain.Artist
import com.phoebe.app.domain.CollectionEntry
import com.phoebe.app.domain.CollectionTarget
import com.phoebe.app.domain.LibraryColumnVisibility
import com.phoebe.app.domain.LibrarySortBy
import com.phoebe.app.domain.LibraryUiPreferences
import com.phoebe.app.domain.LyricsLoadState
import com.phoebe.app.domain.CatalogSnapshot
import com.phoebe.app.domain.LocalFolderMediaSourceConfig
import com.phoebe.app.domain.MediaSourcesState
import com.phoebe.app.domain.MusicLibrary
import com.phoebe.app.domain.PlexServer
import com.phoebe.app.domain.PlexSession
import com.phoebe.app.domain.Playlist
import com.phoebe.app.domain.PlayHistoryKind
import com.phoebe.app.domain.RecentSearchItem
import com.phoebe.app.domain.RepeatMode
import com.phoebe.app.domain.RecentlyAddedKind
import com.phoebe.app.domain.supportedCollectionEntries
import com.phoebe.app.domain.supportsCollectionEntry
import com.phoebe.app.domain.Track
import com.phoebe.app.domain.belongsToProvider
import com.phoebe.app.domain.canAddToLocalPlaylist
import com.phoebe.app.domain.canAddToPlexPlaylist
import com.phoebe.app.domain.isEmbyFamily
import com.phoebe.app.domain.isLocalMediaPlayback
import com.phoebe.app.domain.isJellyfin
import com.phoebe.app.domain.isLocalPlaylist
import com.phoebe.app.domain.isLikedSongsPlaylist
import com.phoebe.app.domain.isRemoteProviderPlaylist
import com.phoebe.app.domain.isRemoteLibraryTrack
import com.phoebe.app.domain.supportsPlexPlaylists
import com.phoebe.app.domain.supportsPlexRatings
import com.phoebe.app.domain.supportsRemotePlaylists
import com.phoebe.app.domain.supportsRemoteRatings
import com.phoebe.app.domain.telemetryName
import com.phoebe.app.player.CastState
import com.phoebe.app.platform.createPlatformHttpClient
import com.phoebe.app.platform.currentTimeMs
import com.phoebe.app.platform.isDesktopPlatform
import com.phoebe.app.platform.prefersReducedArtworkEffects
import com.phoebe.app.platform.supportsPredictiveBack
import com.phoebe.app.telemetry.Telemetry
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import com.phoebe.app.sources.rememberPickLocalFolder
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.get
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.yield
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.random.Random

private data class PendingMobilePlaybackPreview(
    val tracks: List<Track>,
    val index: Int,
) {
    val currentTrack: Track?
        get() = tracks.getOrNull(index)

    val previousTrack: Track?
        get() = tracks.getOrNull(index - 1)

    val upNext: List<Track>
        get() = if (index + 1 <= tracks.lastIndex) tracks.drop(index + 1) else emptyList()
}

@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
fun PhoebeRoot(
    state: AppState,
    useLightAppearance: Boolean,
    onUseLightAppearanceChange: (Boolean) -> Unit,
    appearanceTintId: String,
    onAppearanceTintChange: (String) -> Unit,
    homeScreenLayoutMode: HomeScreenLayoutMode = HomeScreenLayoutMode.Default,
    onHomeScreenLayoutModeChange: (HomeScreenLayoutMode) -> Unit = {},
) {
    PhoebeRootStateHolder(
        state = state,
        useLightAppearance = useLightAppearance,
        onUseLightAppearanceChange = onUseLightAppearanceChange,
        appearanceTintId = appearanceTintId,
        onAppearanceTintChange = onAppearanceTintChange,
        homeScreenLayoutMode = homeScreenLayoutMode,
        onHomeScreenLayoutModeChange = onHomeScreenLayoutModeChange,
    )
}

@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
private fun PhoebeRootStateHolder(
    state: AppState,
    useLightAppearance: Boolean,
    onUseLightAppearanceChange: (Boolean) -> Unit,
    appearanceTintId: String,
    onAppearanceTintChange: (String) -> Unit,
    homeScreenLayoutMode: HomeScreenLayoutMode,
    onHomeScreenLayoutModeChange: (HomeScreenLayoutMode) -> Unit,
) {
    val navigator = rememberPhoebeNavigator(state.initialNavigationRequest().toPhoebeRoute())
    val catalog by state.catalog.collectAsState()
    val catalogWorkActive by state.catalogRefreshing.collectAsState()
    val catalogSyncState by state.catalogSyncState.collectAsState()
    val tracksLoading by state.tracksLoading.collectAsState()
    val session by state.session.collectAsState()
    val supportedCollectionEntries = remember(session) { session.supportedCollectionEntries().toSet() }
    val mediaSources by state.mediaSources.collectAsState()
    val shellPlayback by state.shellPlayback.collectAsState()
    val playerQueue by state.playerQueue.collectAsState()
    val musicAssistantRemotePlayback by state.musicAssistantRemotePlayback.collectAsState()
    val cast by state.cast.collectAsState()
    val busy by state.busy.collectAsState()
    val authInProgress by state.authInProgress.collectAsState()
    val serversLoading by state.serversLoading.collectAsState()
    val librariesLoading by state.librariesLoading.collectAsState()
    val message by state.message.collectAsState()
    val playbackSnackbar by state.playbackSnackbar.collectAsState()
    val decadeMixNotice by state.decadeMixNotice.collectAsState()
    val radioStations by state.radioStations.collectAsState()
    val radioStartingIds by state.radioStartingIds.collectAsState()
    val artistRadioAvailability by state.artistRadioAvailability.collectAsState()
    val downloadDirectory by state.downloadDirectory.collectAsState()
    val pin by state.pin.collectAsState()
    val servers by state.servers.collectAsState()
    val jellyfinServers by state.jellyfinServers.collectAsState()
    val jellyfinDiscoveryLoading by state.jellyfinDiscoveryLoading.collectAsState()
    val jellyfinQuickConnect by state.jellyfinQuickConnect.collectAsState()
    val libraries by state.libraries.collectAsState()
    val libraryUi by state.libraryUi.collectAsState()
    val appSettings by state.appSettings.collectAsState()
    val listenBrainzFeedbackTarget by state.listenBrainzFeedbackTarget.collectAsState()
    val equalizerProfile by state.equalizerProfile.collectAsState()
    val equalizerRemoteUnavailable by state.equalizerRemoteUnavailable.collectAsState()
    val lastPlayedByArtist by state.lastPlayedByArtist.collectAsState()
    val lastPlayedByAlbum by state.lastPlayedByAlbum.collectAsState()
    val lastPlayedByTrack by state.lastPlayedByTrack.collectAsState()
    val playCountsByTrack by state.playCountsByTrack.collectAsState()
    val playEventsByTrack by state.playEventsByTrack.collectAsState()
    val topMostPlayed by state.topMostPlayed.collectAsState()
    val topRecentlyPlayed by state.topRecentlyPlayed.collectAsState()
    val upNext = playerQueue.upNext
    val currentTrack = shellPlayback.currentTrack
    val currentIndex = playerQueue.currentIndex.takeIf { it >= 0 } ?: 0
    LaunchedEffect(state, navigator) {
        state.navigationRequests.collect { request ->
            navigator.handle(request)
        }
    }
    val currentRoute = navigator.currentRoute
    val routeResolution = remember(currentRoute, catalog, currentTrack) {
        resolvePhoebeRoute(currentRoute, catalog, currentTrack)
    }
    val missingRoute = routeResolution as? PhoebeRouteResolution.Missing
    val screen = (routeResolution as? PhoebeRouteResolution.Resolved)?.screen ?: AppScreen.Home
    val browseSection = navigator.routes.filterIsInstance<PhoebeRoute.Browse>().lastOrNull()?.section ?: BrowseSection.Home
    var selectedPlaylistId by remember { mutableStateOf<String?>(null) }
    val collapseMobilePlayer: () -> Unit = {
        if (!navigator.pop()) {
            navigator.replaceRoot(PhoebeRoute.Browse(BrowseSection.Home))
        }
    }
    val exitPlaylistDetail: () -> Unit = {
        selectedPlaylistId = null
        navigator.pop()
    }
    val canHandleBrowseBack = screen == AppScreen.Home &&
        (selectedPlaylistId != null || browseSection != BrowseSection.Home)
    PlatformBackHandler(
        enabled = canHandleBrowseBack,
        onBack = {
            when {
                screen == AppScreen.Home && selectedPlaylistId != null -> {
                    selectedPlaylistId = null
                }
                screen == AppScreen.Home && browseSection != BrowseSection.Home -> {
                    navigator.openBrowse(BrowseSection.Home)
                }
            }
        },
    )
    var searchQuery by remember { mutableStateOf("") }
    val searchScopeKey = when (val currentScreen = screen) {
        is AppScreen.ArtistDetail -> "artist:${currentScreen.artist.id}"
        is AppScreen.AlbumDetail -> "album:${currentScreen.album.id}"
        is AppScreen.SongDetail -> "song:${currentScreen.track.id}"
        is AppScreen.Lyrics -> "lyrics:${currentScreen.track?.id.orEmpty()}"
        is AppScreen.Collections -> "collections:${currentScreen.entry}"
        is AppScreen.CollectionItems -> "collection-items:${currentScreen.entry}:${currentScreen.value}"
        is AppScreen.RecentlyAdded -> "recently-added:${currentScreen.kind}"
        is AppScreen.PlayHistory -> "play-history:${currentScreen.kind}"
        AppScreen.FavoritePlaylists -> "favorite-playlists"
        AppScreen.FavoriteArtists -> "favorite-artists"
        AppScreen.FavoriteAlbums -> "favorite-albums"
        is AppScreen.PlaylistDetail -> "playlist:${currentScreen.playlist.id}"
        else -> "browse:$browseSection:${selectedPlaylistId.orEmpty()}"
    }
    LaunchedEffect(searchScopeKey) {
        searchQuery = ""
    }
    val recentSearchItems by state.recentSearchItems.collectAsState()
    var libraryFilter by remember { mutableStateOf(LibraryFilterTab.Artists) }

    LaunchedEffect(currentRoute) {
        Telemetry.trackScreen(currentRoute.telemetryName)
    }
    LaunchedEffect(currentRoute, screen) {
        when (screen) {
            is AppScreen.ArtistDetail -> state.preloadArtistDetail(screen.artist)
            is AppScreen.AlbumDetail -> state.preloadAlbumDetail(screen.album)
            is AppScreen.PlaylistDetail -> state.preloadPlaylistDetail(screen.playlist)
            is AppScreen.Collections -> state.preloadCollections(screen.entry)
            is AppScreen.CollectionItems -> state.preloadCollectionItems(screen.entry, screen.value)
            else -> Unit
        }
    }
    var lyricsRefreshNonce by remember { mutableStateOf(0) }
    var lyricsRefreshTrackId by remember { mutableStateOf<String?>(null) }
    val lyricsTrack = when (val currentScreen = screen) {
        is AppScreen.Lyrics -> currentScreen.track ?: currentTrack
        AppScreen.Home -> if (browseSection == BrowseSection.Lyrics) currentTrack else null
        else -> null
    }
    val lyricsState by produceState<LyricsLoadState>(
        initialValue = if (lyricsTrack == null) LyricsLoadState.Idle else LyricsLoadState.Loading,
        lyricsTrack?.id,
        lyricsRefreshNonce,
    ) {
        val target = lyricsTrack
        if (target == null) {
            value = LyricsLoadState.Idle
        } else {
            value = LyricsLoadState.Loading
            value = state.loadLyrics(
                target,
                forceRefresh = lyricsRefreshNonce > 0 && lyricsRefreshTrackId == target.id,
            )
        }
    }
    val retryLyrics = {
        lyricsRefreshTrackId = lyricsTrack?.id
        lyricsRefreshNonce++
        Unit
    }
    val catalogHasContent = catalog.artists.isNotEmpty() ||
        catalog.albums.isNotEmpty() ||
        catalog.playlists.isNotEmpty()
    val activeCatalogSurfaceHasContent = remember(catalog, screen, browseSection, selectedPlaylistId, libraryFilter) {
        catalogHasContentForSurface(
            catalog = catalog,
            screen = screen,
            browseSection = browseSection,
            selectedPlaylistId = selectedPlaylistId,
            libraryFilter = libraryFilter,
        )
    }
    val catalogSyncInProgress = catalogWorkActive || catalogSyncState.isActive
    val catalogRefreshing = catalogSyncState.showGlobalProgress ||
        (catalogSyncInProgress && !activeCatalogSurfaceHasContent)
    val trackHeavySectionsEnabled by produceState(false, catalogSyncInProgress) {
        if (catalogSyncInProgress) {
            value = false
        } else {
            delay(120L)
            value = true
        }
    }
    val artworkLoadingEnabled by produceState(!catalogSyncInProgress, catalogSyncInProgress) {
        if (catalogSyncInProgress) {
            value = false
        } else {
            delay(250L)
            value = true
        }
    }
    LaunchedEffect(catalogSyncInProgress) {
        if (catalogSyncInProgress) {
            RemoteArtworkCache.configurePacingEnabled(true)
        } else {
            delay(2_000L)
            RemoteArtworkCache.configurePacingEnabled(false)
        }
    }

    val nowPlaying = remember(currentTrack?.id, shellPlayback.isPlaying, shellPlayback.isBuffering) {
        NowPlayingIndicatorState(
            trackId = currentTrack?.id,
            isPlaying = shellPlayback.isPlaying,
            isBuffering = shellPlayback.isBuffering,
        )
    }
    val downloadStatus = remember { DownloadStatusSnapshot() }
    LaunchedEffect(state, downloadStatus) {
        launch {
            state.downloads.collect { downloads ->
                downloadStatus.replaceItems(downloads)
            }
        }
        launch {
            state.downloadEvents.collect { event ->
                downloadStatus.apply(event)
            }
        }
        launch {
            state.activeDownloadJobCount.collect { activeDownloadJobCount ->
                val active = activeDownloadJobCount > 0
                downloadStatus.setActiveDownloadJobs(active)
                RemoteArtworkCache.configureDownloadMemoryMode(active)
            }
        }
    }
    val playHistory = remember(
        lastPlayedByArtist,
        lastPlayedByAlbum,
        lastPlayedByTrack,
        playCountsByTrack,
        playEventsByTrack,
        topMostPlayed,
        topRecentlyPlayed,
    ) {
        PlayHistorySnapshot(
            byArtist = lastPlayedByArtist,
            byAlbum = lastPlayedByAlbum,
            byTrack = lastPlayedByTrack,
            playCountByTrack = playCountsByTrack,
            playEventsByTrack = playEventsByTrack,
            topMostPlayed = topMostPlayed,
            topRecentlyPlayed = topRecentlyPlayed,
        )
    }
    var randomArtistSeed by remember { mutableStateOf(Random.nextInt()) }
    var randomAlbumSeed by remember { mutableStateOf(Random.nextInt()) }
    // Re-tick "now" every minute so relative timestamps in the library refresh
    // without requiring an unrelated recomposition. We also re-read the clock
    // immediately whenever the play history changes — without that nudge, a
    // brand-new play whose timestamp is newer than our cached `nowMs` would
    // briefly render as "Just now"… but only after the next 60s tick caught up.
    var nowMs by remember { mutableStateOf(currentTimeMs()) }
    LaunchedEffect(Unit) {
        while (true) {
            delay(60_000L)
            nowMs = currentTimeMs()
        }
    }
    LaunchedEffect(lastPlayedByTrack) {
        nowMs = currentTimeMs()
    }
    val homeTrackIndexCache = remember { HomeCatalogIndexCache() }
    val catalogHomeMetadataKey = if (catalogSyncInProgress) {
        catalog.homeMetadataRevisionKey()
    } else {
        catalog.homeMetadataKey()
    }
    val catalogTrackIndexKey = if (trackHeavySectionsEnabled) catalog.trackBatchRevisionKey() else 0L
    val mostPlayedPrefetchIds = remember(topMostPlayed, topRecentlyPlayed) {
        (topMostPlayed.take(30).map { it.trackId } + topRecentlyPlayed.take(15).map { it.trackId }).distinct()
    }
    val resolvedTracksById by produceState(emptyMap<String, Track>(), mostPlayedPrefetchIds, catalogTrackIndexKey) {
        if (mostPlayedPrefetchIds.isEmpty()) {
            value = emptyMap()
            return@produceState
        }
        value = state.resolveTracksByIds(mostPlayedPrefetchIds)
    }
    val resolvedTracksKey = resolvedTracksById.keys.fold(0L) { acc, id -> acc * 31L + id.hashCode() }
    val playHistoryDerivationKey = playHistory.derivationKey()
    val homeUiState by produceState(
        initialValue = HomeUiState(),
        catalogTrackIndexKey,
        catalogHomeMetadataKey,
        playHistoryDerivationKey,
        randomArtistSeed,
        randomAlbumSeed,
        nowMs,
        trackHeavySectionsEnabled,
        resolvedTracksKey,
    ) {
        delay(if (catalogSyncInProgress) 250L else if (trackHeavySectionsEnabled) 80L else 50L)
        value = withContext(Dispatchers.Default) {
            deriveHomeUiState(
                catalog = catalog,
                playHistory = playHistory,
                randomArtistSeed = randomArtistSeed,
                randomAlbumSeed = randomAlbumSeed,
                nowMs = nowMs,
                trackIndexCache = homeTrackIndexCache,
                includeTrackDerivedSections = trackHeavySectionsEnabled,
                resolvedTracksById = resolvedTracksById,
            )
        }
    }
    val playedPanelMaxRows = 3
    val mostPlayedResolving = playHistory.mostPlayedPendingResolution(
        resolvedCount = homeUiState.mostPlayedTracks.size,
        limit = playedPanelMaxRows,
    )
    val playTracks: (List<Track>, Int) -> Unit = { tracks, index ->
        state.playTracks(
            tracks = tracks,
            index = index,
            collectionMixSeed = navigator.routes.collectionMixSeed(),
        )
    }
    val mobilePlaybackScope = rememberCoroutineScope()
    var pendingMobilePlaybackJob by remember { mutableStateOf<Job?>(null) }
    var pendingMobilePlaybackPreview by remember { mutableStateOf<PendingMobilePlaybackPreview?>(null) }
    LaunchedEffect(currentTrack?.id, pendingMobilePlaybackPreview?.currentTrack?.id) {
        val preview = pendingMobilePlaybackPreview ?: return@LaunchedEffect
        if (preview.currentTrack?.id == currentTrack?.id) {
            pendingMobilePlaybackPreview = null
        }
    }
    val playTracksFromMobile: (List<Track>, Int) -> Unit = playTracksFromMobile@{ tracks, index ->
        if (tracks.isEmpty()) return@playTracksFromMobile
        val collectionMixSeed = navigator.routes.collectionMixSeed()
        val previewIndex = index.coerceIn(0, tracks.lastIndex)
        pendingMobilePlaybackPreview = PendingMobilePlaybackPreview(tracks, previewIndex)
        navigator.openPlayer()
        pendingMobilePlaybackJob?.cancel()
        state.playTracks(
            tracks = tracks,
            index = index,
            collectionMixSeed = collectionMixSeed,
        )
        pendingMobilePlaybackJob = mobilePlaybackScope.launch {
            delay(1_500L)
            if (pendingMobilePlaybackPreview?.currentTrack?.id == tracks.getOrNull(previewIndex)?.id) {
                pendingMobilePlaybackPreview = null
            }
        }
    }
    val mobilePlayerTrack = pendingMobilePlaybackPreview?.currentTrack ?: currentTrack
    val mobilePlayerUpNext = pendingMobilePlaybackPreview?.upNext ?: upNext
    val mobilePlayerPreviousTrack = pendingMobilePlaybackPreview?.previousTrack
        ?: playerQueue.queue.getOrNull(currentIndex - 1)
    val mobilePlayerCurrentIndex = pendingMobilePlaybackPreview?.index ?: currentIndex
    val pendingMobilePlaybackTrackId = pendingMobilePlaybackPreview?.currentTrack?.id
    val mobilePlaybackStarting = pendingMobilePlaybackTrackId != null &&
        pendingMobilePlaybackTrackId != currentTrack?.id
    val personalMixCatalog = rememberUpdatedState(catalog)
    val personalMixHomeUiState = rememberUpdatedState(homeUiState)
    val personalMixPreferences = rememberUpdatedState(libraryUi.personalMix)
    val personalMixPlayHistory = rememberUpdatedState(playHistory)
    var recentPersonalMixKeys by remember { mutableStateOf(emptySet<String>()) }
    val personalMixScope = rememberCoroutineScope()
    val playPersonalMix = remember(state, personalMixScope) {
        {
            personalMixScope.launch {
                val preferences = personalMixPreferences.value.normalized()
                state.ensurePersonalMixTracks(preferences.limit)
                val tracks = personalMix(
                    catalog = personalMixCatalog.value,
                    state = personalMixHomeUiState.value,
                    preferences = preferences,
                    playHistory = personalMixPlayHistory.value,
                    recentMixTrackKeys = recentPersonalMixKeys,
                )
                if (tracks.isEmpty()) return@launch
                recentPersonalMixKeys = (recentPersonalMixKeys + tracks.map { it.personalMixIdentityKey() })
                    .let { keys -> if (keys.size > 100) keys.drop(keys.size - 100).toSet() else keys.toSet() }
                playTracksFromMobile(tracks, 0)
            }
            Unit
        }
    }
    LaunchedEffect(screen, browseSection, catalog.albums, catalog.tracksByParent.keys, session?.selectedServer, nowMs, trackHeavySectionsEnabled) {
        if (!trackHeavySectionsEnabled) return@LaunchedEffect
        if (screen == AppScreen.Home && browseSection == BrowseSection.Home) {
            delay(1_500L)
            state.warmRecentAlbumTracks(cutoffMs = nowMs - RecentlyAddedWindowMs, maxAlbums = 10)
        }
    }
    LaunchedEffect(screen, browseSection, topMostPlayed, topRecentlyPlayed, session?.selectedServer, trackHeavySectionsEnabled) {
        if (!trackHeavySectionsEnabled) return@LaunchedEffect
        if (screen == AppScreen.Home && browseSection == BrowseSection.Home &&
            (topMostPlayed.isNotEmpty() || topRecentlyPlayed.isNotEmpty())
        ) {
            state.warmTracksForMostPlayed()
        }
    }
    LaunchedEffect(screen, topMostPlayed, topRecentlyPlayed, session?.selectedServer) {
        if (screen is AppScreen.PlayHistory &&
            (topMostPlayed.isNotEmpty() || topRecentlyPlayed.isNotEmpty())
        ) {
            state.warmTracksForMostPlayed(maxTracks = 50)
        }
    }
    LaunchedEffect(session?.selectedServer?.id, session?.selectedLibrary?.key) {
        state.refreshRadioStations()
    }
    val openRecentSongs: () -> Unit = {
        selectedPlaylistId = null
        navigator.openBrowse(BrowseSection.Home)
        navigator.open(PhoebeRoute.RecentlyAdded(RecentlyAddedKind.Songs))
    }
    val openRecentArtists: () -> Unit = {
        selectedPlaylistId = null
        navigator.openBrowse(BrowseSection.Home)
        navigator.open(PhoebeRoute.RecentlyAdded(RecentlyAddedKind.Artists))
    }
    val openRecentAlbums: () -> Unit = {
        selectedPlaylistId = null
        navigator.openBrowse(BrowseSection.Home)
        navigator.open(PhoebeRoute.RecentlyAdded(RecentlyAddedKind.Albums))
    }
    val openRecentlyPlayed: () -> Unit = {
        selectedPlaylistId = null
        navigator.openBrowse(BrowseSection.Home)
        navigator.open(PhoebeRoute.PlayHistory(PlayHistoryKind.RecentlyPlayed))
    }
    val openMostPlayed: () -> Unit = {
        selectedPlaylistId = null
        navigator.openBrowse(BrowseSection.Home)
        navigator.open(PhoebeRoute.PlayHistory(PlayHistoryKind.MostPlayed))
    }
    val openFavoritePlaylists: () -> Unit = {
        selectedPlaylistId = null
        navigator.openBrowse(BrowseSection.Home)
        navigator.open(PhoebeRoute.FavoritePlaylists)
    }
    val openFavoriteArtists: () -> Unit = {
        selectedPlaylistId = null
        navigator.openBrowse(BrowseSection.Home)
        navigator.open(PhoebeRoute.FavoriteArtists)
    }
    val openFavoriteAlbums: () -> Unit = {
        selectedPlaylistId = null
        navigator.openBrowse(BrowseSection.Home)
        navigator.open(PhoebeRoute.FavoriteAlbums)
    }
    val openCollections: (CollectionEntry) -> Unit = { entry ->
        if (session.supportsCollectionEntry(entry)) {
            selectedPlaylistId = null
            navigator.openBrowse(BrowseSection.Home)
            libraryFilter = when (entry.target) {
                CollectionTarget.Artists -> LibraryFilterTab.Artists
                CollectionTarget.Albums -> LibraryFilterTab.Albums
            }
            navigator.open(PhoebeRoute.Collections(entry))
        }
    }
    val openCollectionValue: (CollectionEntry, String) -> Unit = { entry, value ->
        if (session.supportsCollectionEntry(entry)) {
            selectedPlaylistId = null
            navigator.open(PhoebeRoute.CollectionItems(entry, value))
        }
    }
    fun prependRecentSearch(item: RecentSearchItem) {
        state.prependRecentSearch(item)
    }
    val searchHistory = remember(recentSearchItems) {
        SearchHistoryState(
            recentItems = recentSearchItems,
            recordArtist = { artist -> prependRecentSearch(RecentSearchItem.ArtistHit(artist)) },
            recordAlbum = { album -> prependRecentSearch(RecentSearchItem.AlbumHit(album)) },
            recordTrack = { track -> prependRecentSearch(RecentSearchItem.TrackHit(track)) },
            removeItem = { item -> state.removeRecentSearch(item) },
            clearItems = { state.clearRecentSearches() },
        )
    }
    var createPlaylistFor by remember { mutableStateOf<List<Track>?>(null) }
    var metadataEditorTrack by remember { mutableStateOf<Track?>(null) }
    val catalogActionsKey = catalogHomeMetadataKey to catalog.playlists.size
    val sessionKey = session?.selectedServer?.id to session?.selectedLibrary?.key
    val playlistActions = remember(catalogActionsKey, sessionKey, mediaSources.localFolders) {
        val plexReady = session.supportsRemotePlaylists()
        val localReady = mediaSources.localFolders.any { it.enabled }
        val providerType = session?.providerType
        val list = catalog.playlists.filter { playlist ->
            playlist.isLocalPlaylist() ||
                playlist.isLikedSongsPlaylist() ||
                (plexReady && providerType != null && playlist.isRemoteProviderPlaylist() && playlist.belongsToProvider(providerType))
        }
        PlaylistActions(
            playlists = list,
            playlistsEnabled = plexReady || localReady,
            onAddTrackToPlaylist = { playlist, track -> state.addToPlaylist(playlist, track) },
            onMovePlaylistTrack = { playlist, from, to -> state.movePlaylistTrack(playlist, from, to) },
            onCopyPlaylistToPlaylist = { source, target -> state.copyPlaylistIntoPlaylist(source, target) },
            onCreatePlaylist = { title, initialTracks -> state.createPlaylist(title, initialTracks) },
            onRequestCreatePlaylist = { initialTracks ->
                val canCreate = when {
                    initialTracks.any { it.canAddToPlexPlaylist() } -> plexReady
                    initialTracks.any { it.canAddToLocalPlaylist() } -> localReady
                    else -> plexReady || localReady
                }
                if (canCreate) {
                    createPlaylistFor = initialTracks
                }
            },
            onOpenLikedSongs = { state.openLikedSongsPlaylist() },
            onExportLocalPlaylist = { playlist, format -> state.exportLocalPlaylist(playlist, format) },
            onShufflePlaylist = { playlist -> state.playPlaylistShuffled(playlist) },
        )
    }
    val likedTracksKey = if (trackHeavySectionsEnabled) catalog.trackIndexKey() else -1L
    val likeActions = remember(catalogActionsKey, likedTracksKey, sessionKey) {
        val likedPlaylist = catalog.playlists.firstOrNull { it.isLikedSongsPlaylist() }
        LikeActions(
            likedTrackIds = likedPlaylist?.let { playlist ->
                catalog.tracksByParent[playlist.id].orEmpty().map { it.id }.toSet()
            }.orEmpty(),
            likesEnabled = session.supportsRemotePlaylists(),
            onToggleLiked = { track -> state.toggleLikedTrack(track) },
        )
    }
    val trackRatingIndex = remember(catalogTrackIndexKey) {
        if (trackHeavySectionsEnabled) buildTrackRatingIndex(catalog) else emptyMap()
    }
    val ratingActions = remember(catalogHomeMetadataKey, catalogTrackIndexKey, sessionKey) {
        RatingActions(
            ratingsEnabled = session.supportsRemoteRatings(),
            catalog = catalog,
            trackRatingsById = trackRatingIndex,
            onRateTrack = { track, rating -> state.rateTrack(track, rating) },
            onRateArtist = { artist, rating -> state.rateArtist(artist, rating) },
            onRateAlbum = { album, rating -> state.rateAlbum(album, rating) },
            onRatePlaylist = { playlist, rating -> state.ratePlaylist(playlist, rating) },
        )
    }
    val favoriteActions = remember(catalogHomeMetadataKey, state) {
        FavoriteActions(
            catalog = catalog,
            onToggleArtist = { artist -> state.toggleFavoriteArtist(artist) },
            onToggleAlbum = { album -> state.toggleFavoriteAlbum(album) },
            onTogglePlaylist = { playlist -> state.toggleFavoritePlaylist(playlist) },
        )
    }
    val trackNavigationActions = remember(catalog, state) {
        TrackNavigationActions(
            onOpenArtistForTrack = { track ->
                resolveArtistForTrack(catalog, track)?.let { artist ->
                    navigator.open(artist.route())
                    true
                } ?: false
            },
            onOpenAlbumForTrack = { track ->
                resolveAlbumForTrack(catalog, track)?.let { album ->
                    navigator.open(album.route())
                    true
                } ?: false
            },
            onOpenSongDetail = { track ->
                navigator.open(track.route())
            },
        )
    }
    val metadataEditorActions = remember {
        MetadataEditorActions(onRequestEdit = { track -> metadataEditorTrack = track })
    }
    val downloadActions = remember(state) {
        DownloadActions(
            onDeleteDownloadedTracks = { tracks -> state.deleteDownloads(tracks) },
            onCancelDownloadedTracks = { tracks -> state.cancelDownloads(tracks) },
        )
    }
    val dragDrop = remember { DragDropController() }

    CompositionLocalProvider(
        LocalCatalogHasContent provides catalogHasContent,
        LocalCatalogSyncState provides catalogSyncState,
        LocalCatalogSyncInProgress provides catalogSyncInProgress,
        LocalArtworkLoadingEnabled provides artworkLoadingEnabled,
        LocalHomeTrackSectionsReady provides trackHeavySectionsEnabled,
        LocalMostPlayedResolving provides mostPlayedResolving,
        LocalSharedElementTransitionsEnabled provides trackHeavySectionsEnabled,
        LocalTracksLoading provides tracksLoading,
        LocalDownloadStatus provides downloadStatus,
        LocalDownloadActions provides downloadActions,
        LocalNowPlaying provides nowPlaying,
        LocalPlayHistory provides playHistory,
        LocalNowMs provides nowMs,
        LocalPlaylistActions provides playlistActions,
        LocalLikeActions provides likeActions,
        LocalFavoriteActions provides favoriteActions,
        LocalRatingActions provides ratingActions,
        LocalTrackNavigationActions provides trackNavigationActions,
        LocalMetadataEditorActions provides metadataEditorActions,
        LocalDragDrop provides dragDrop,
        LocalSearchHistory provides searchHistory,
    ) {
    createPlaylistFor?.let { seedTracks ->
        CreatePlaylistDialog(
            initialTracks = seedTracks,
            onDismiss = { createPlaylistFor = null },
            onConfirm = { title ->
                state.createPlaylist(title, seedTracks)
                createPlaylistFor = null
            },
        )
    }
    // Wrap everything in a single Box so the drag-ghost overlay actually sits ON TOP of the
    // app rather than under it (CompositionLocalProvider isn't a layout, so emitting siblings
    // here results in painter order = source order, with the last one rendered last/highest).
    Box(modifier = Modifier.fillMaxSize()) {
        BoxWithConstraints(Modifier.fillMaxSize()) {
            val compact = maxWidth < 1200.dp
            val wideDesktop = maxWidth >= 1280.dp
            CompositionLocalProvider(LocalPlaylistDragEnabled provides !compact) {
            val mergesTitleBar = LocalDesktopMergesTitleBar.current
            val desktopCompactTopPadding = if (compact && isDesktopPlatform()) 22.dp else 0.dp
            val shellModifier = if (compact) {
                Modifier
                    .fillMaxSize()
                    .background(PhoebeUi.shellTop)
                    .statusBarsPadding()
                    .padding(top = desktopCompactTopPadding)
            } else {
                val shellInsets = if (mergesTitleBar) {
                    WindowInsets.safeDrawing.only(
                        WindowInsetsSides.Start + WindowInsetsSides.End + WindowInsetsSides.Bottom,
                    )
                } else {
                    WindowInsets.safeDrawing
                }
                Modifier
                    .fillMaxSize()
                    .background(
                        Brush.radialGradient(
                            colors = listOf(PhoebeUi.shellRadialTint, PhoebeUi.canvasBackground),
                            center = Offset(420f, 40f),
                            radius = 960f,
                        ),
                    )
                    .windowInsetsPadding(shellInsets)
            }
            Box(modifier = shellModifier) {
            if (compact) {
                SharedTransitionLayout(Modifier.fillMaxSize()) {
                val sharedTransitionScope = this
                val mobileArtworkTransition = remember { MobileNowPlayingArtworkTransitionState() }
                val mobileChromeVisible = canBrowseMainSections(session, mediaSources) &&
                    screen != AppScreen.SignIn &&
                    screen != AppScreen.ServerPicker &&
                    screen != AppScreen.LibraryPicker
                val mobileChromePadding = if (mobileChromeVisible) {
                    val mobileDensity = LocalDensity.current
                    val navigationBottomPadding = with(mobileDensity) {
                        WindowInsets.navigationBars.getBottom(this).toDp()
                    }
                    MobileChromePadding(
                        bottom = MobileBottomNavChromeHeight +
                            navigationBottomPadding +
                            MobileChromeScrollGap +
                            if (currentTrack != null) MobileMiniPlayerChromeHeight else 0.dp,
                    )
                } else {
                    MobileChromePadding()
                }
                CompositionLocalProvider(
                    LocalSharedTransitionScope provides sharedTransitionScope,
                    LocalSharedElementTransitionsEnabled provides trackHeavySectionsEnabled,
                    LocalMobileChromePadding provides mobileChromePadding,
                    LocalMobileNowPlayingArtworkTransition provides mobileArtworkTransition,
                ) {
                val mobileRoutes = navigator.routes
                val mobilePlayerAsSheet = mobileRoutes.lastOrNull() == PhoebeRoute.Player && mobileRoutes.size > 1
                val mobileContentRoutes = if (mobilePlayerAsSheet) mobileRoutes.dropLast(1) else mobileRoutes
                LaunchedEffect(mobilePlayerAsSheet, currentTrack?.id) {
                    if (!mobilePlayerAsSheet) {
                        mobileArtworkTransition.activeTrack = null
                        mobileArtworkTransition.fullArtworkBounds = null
                        mobileArtworkTransition.fullArtworkTrackId = null
                        mobileArtworkTransition.progress = 0f
                    }
                }
                PhoebeNavDisplay(
                    backStack = mobileContentRoutes,
                    modifier = Modifier.fillMaxSize(),
                    animateTransitions = supportsPredictiveBack(),
                    opaqueSceneBackgrounds = true,
                    onBack = {
                        when (navigator.currentRoute) {
                            is PhoebeRoute.PlaylistDetail -> exitPlaylistDetail()
                            else -> navigator.pop()
                        }
                    },
                ) { targetRoute ->
                val targetResolution = resolvePhoebeRoute(targetRoute, catalog, currentTrack)
                val targetMissingRoute = targetResolution as? PhoebeRouteResolution.Missing
                val scr = (targetResolution as? PhoebeRouteResolution.Resolved)?.screen ?: AppScreen.Home
                if (targetMissingRoute != null) {
                    MissingRouteFallback(
                        title = targetMissingRoute.title,
                        message = targetMissingRoute.message,
                        onBack = { navigator.pop() },
                        modifier = Modifier.fillMaxSize(),
                    )
                } else {
                when (scr) {
                    is AppScreen.ServerPicker -> PlexServerPickerPanel(
                        servers = servers,
                        busy = busy,
                        serversLoading = serversLoading,
                        onSelectServer = state::selectServer,
                        onCancel = state::signOut,
                        onRetry = state::loadServers,
                        modifier = Modifier.fillMaxSize(),
                    )
                    is AppScreen.LibraryPicker -> PlexLibraryPickerPanel(
                        libraries = libraries,
                        serverName = session?.selectedServer?.name,
                        providerType = session?.providerType ?: com.phoebe.app.domain.MediaProviderType.Plex,
                        busy = busy,
                        librariesLoading = librariesLoading,
                        isJellyfin = session.isEmbyFamily(),
                        onSelectLibrary = { library, mode -> state.selectLibrary(library, mode) },
                        onBack = state::returnToServerPicker,
                        onCancel = state::signOut,
                        modifier = Modifier.fillMaxSize(),
                    )
                    is AppScreen.SignIn -> MobileSignInWelcomeScreen(
                        message = message,
                        pinCode = pin?.code,
                        jellyfinServers = jellyfinServers,
                        jellyfinDiscoveryLoading = jellyfinDiscoveryLoading,
                        jellyfinQuickConnect = jellyfinQuickConnect,
                        authInProgress = authInProgress,
                        onStartSignIn = state::startPlexSignIn,
                        onFinishSignIn = state::finishPlexSignIn,
                        onSignInJellyfin = state::signInJellyfin,
                        onSignInProvider = state::signInProvider,
                        onDiscoverJellyfinServers = state::discoverJellyfinServers,
                        onStartJellyfinQuickConnect = state::startJellyfinQuickConnect,
                        onFinishJellyfinQuickConnect = state::finishJellyfinQuickConnect,
                        onAddLocalFolder = state::addLocalFolderFromUri,
                        modifier = Modifier.fillMaxSize(),
                    )
                    is AppScreen.ArtistDetail -> ArtistDetailPanel(
                        artist = scr.artist,
                        catalog = catalog,
                        libraryUi = libraryUi,
                        catalogRefreshing = catalogRefreshing,
                        modifier = Modifier.fillMaxSize(),
                        searchQuery = searchQuery,
                        onBack = { navigator.pop() },
                        onAlbum = { navigator.open(it.route()) },
                        onPlayTracks = playTracksFromMobile,
                        onAddToUpNext = state::addToUpNext,
                        onDownload = state::download,
                        onDownloadArtist = state::download,
                        artistRadioAvailability = artistRadioAvailability[scr.artist.id],
                        artistRadioStarting = scr.artist.id in radioStartingIds,
                        onProbeArtistRadio = state::probeArtistRadio,
                        onPlayArtistRadio = state::playArtistRadio,
                        onArtist = { navigator.open(it.route()) },
                        onLibraryColumns = state::setLibraryColumns,
                    )
                    is AppScreen.AlbumDetail -> AlbumDetailPanel(
                        album = scr.album,
                        catalog = catalog,
                        libraryUi = libraryUi,
                        catalogRefreshing = catalogRefreshing,
                        modifier = Modifier.fillMaxSize(),
                        searchQuery = searchQuery,
                        onBack = { navigator.pop() },
                        onPlayTracks = playTracksFromMobile,
                        onAddToUpNext = state::addToUpNext,
                        onDownload = state::download,
                        onDownloadAlbum = state::download,
                        onArtist = { navigator.open(it.route()) },
                        onLibraryColumns = state::setLibraryColumns,
                    )
                    is AppScreen.SongDetail -> SongDetailPanel(
                        track = scr.track,
                        modifier = Modifier.fillMaxSize(),
                        onBack = { navigator.pop() },
                        onPlay = { playTracksFromMobile(listOf(scr.track), 0) },
                        onAddToUpNext = state::addToUpNext,
                        onDownload = state::download,
                        onOpenLyrics = { navigator.open(PhoebeRoute.Lyrics(it.id)) },
                    )
                    is AppScreen.Lyrics -> LyricsScreenHost(
                        appState = state,
                        track = lyricsTrack,
                        currentTrackId = currentTrack?.id,
                        lyricsState = lyricsState,
                        onBack = { navigator.pop() },
                        onRetry = retryLyrics,
                        modifier = Modifier.fillMaxSize(),
                    )
                    is AppScreen.RecentlyAdded -> RecentlyAddedScreen(
                        kind = scr.kind,
                        catalog = catalog,
                        nowMs = nowMs,
                        modifier = Modifier.fillMaxSize(),
                        onBack = { navigator.pop() },
                        onArtist = { navigator.open(it.route()) },
                        onAlbum = { navigator.open(it.route()) },
                        onPlayTracks = playTracksFromMobile,
                        onAddToUpNext = state::addToUpNext,
                        onDownload = state::download,
                    )
                    is AppScreen.Collections -> CollectionsScreen(
                        entry = scr.entry,
                        catalog = catalog,
                        modifier = Modifier.fillMaxSize(),
                        supportedCollectionEntries = supportedCollectionEntries,
                        onBack = { navigator.pop() },
                        onCollectionValue = openCollectionValue,
                    )
                    is AppScreen.CollectionItems -> CollectionItemsScreen(
                        entry = scr.entry,
                        value = scr.value,
                        catalog = catalog,
                        modifier = Modifier.fillMaxSize(),
                        supportedCollectionEntries = supportedCollectionEntries,
                        onBack = { navigator.pop() },
                        onArtist = { navigator.open(it.route()) },
                        onAlbum = { navigator.open(it.route()) },
                    )
                    is AppScreen.PlayHistory -> PlayHistoryScreen(
                        kind = scr.kind,
                        catalog = catalog,
                        playHistory = playHistory,
                        resolvedTracksById = resolvedTracksById,
                        modifier = Modifier.fillMaxSize(),
                        onBack = { navigator.pop() },
                        onPlayTracks = playTracksFromMobile,
                        onAddToUpNext = state::addToUpNext,
                        onDownload = state::download,
                    )
                    AppScreen.FavoritePlaylists -> FavoritePlaylistsMobileView(
                        searchQuery = searchQuery,
                        onSearchQuery = { searchQuery = it },
                        modifier = Modifier.fillMaxSize(),
                        onBack = { navigator.pop() },
                        onPlaylist = { playlist ->
                            navigator.open(playlist.route())
                        },
                    )
                    AppScreen.FavoriteArtists -> FavoriteArtistsMobileView(
                        catalog = catalog,
                        libraryUi = libraryUi,
                        modifier = Modifier.fillMaxSize(),
                        onBack = { navigator.pop() },
                        onLibrarySortBy = state::setLibrarySortBy,
                        onLibraryAscending = state::setLibrarySortAscending,
                        onLibraryColumns = state::setLibraryColumns,
                        onArtist = { navigator.open(it.route()) },
                    )
                    AppScreen.FavoriteAlbums -> FavoriteAlbumsMobileView(
                        catalog = catalog,
                        libraryUi = libraryUi,
                        modifier = Modifier.fillMaxSize(),
                        onBack = { navigator.pop() },
                        onLibrarySortBy = state::setLibrarySortBy,
                        onLibraryAscending = state::setLibrarySortAscending,
                        onLibraryColumns = state::setLibraryColumns,
                        onAlbum = { navigator.open(it.route()) },
                    )
                    is AppScreen.PlaylistDetail -> PlaylistDetailPanel(
                        playlist = scr.playlist,
                        catalog = catalog,
                        catalogRefreshing = catalogRefreshing,
                        libraryUi = libraryUi,
                        modifier = Modifier.fillMaxSize(),
                        searchQuery = searchQuery,
                        onSearchQuery = { searchQuery = it },
                        onBack = exitPlaylistDetail,
                        onPlayTracks = playTracksFromMobile,
                        onAddToUpNext = state::addToUpNext,
                        onDownload = state::download,
                        onDownloadPlaylist = state::download,
                        onCancelDownloadPlaylist = state::cancelDownloads,
                        onDeleteDownloadPlaylist = state::deleteDownloads,
                        onMovePlaylistTrack = state::movePlaylistTrack,
                        onLibraryColumns = state::setLibraryColumns,
                    )
                    AppScreen.Player -> MobilePlayerHost(
                        appState = state,
                        track = mobilePlayerTrack,
                        upNext = mobilePlayerUpNext,
                        previousTrack = mobilePlayerPreviousTrack,
                        currentIndex = mobilePlayerCurrentIndex,
                        playbackStarting = mobilePlaybackStarting,
                        castState = cast,
                        remotePlaybackTarget = musicAssistantRemotePlayback?.target,
                        onToggle = state::togglePlayPause,
                        onPrevious = state::previous,
                        onNext = state::next,
                        onSkipQueueBy = state::skipQueueBy,
                        onShuffle = state::toggleShuffle,
                        onRepeat = state::cycleRepeat,
                        onSeek = state::seekTo,
                        onPlayQueue = state::playUpNext,
                        onMoveUpNext = state::moveUpNext,
                        onRemoveUpNext = state::removeUpNext,
                        onOpenSongDetail = { navigator.open(it.route()) },
                        onCast = state::showCastPicker,
                        onLyrics = {
                            currentTrack?.let { navigator.open(PhoebeRoute.Lyrics(it.id)) }
                        },
                        onBack = collapseMobilePlayer,
                        onSwipeDismiss = collapseMobilePlayer,
                        handleSystemBack = navigator.routes.size > 1,
                    )
                    AppScreen.Home -> {
                    val onHomeBrowse = browseSection == BrowseSection.Home && selectedPlaylistId == null
                    val catalogForMobileBrowse = if (onHomeBrowse) {
                        remember(catalog.homeMetadataKey(), catalog.trackIndexKey()) { catalog }
                    } else {
                        catalog
                    }
                    MobileBrowseShell(
                        catalog = catalogForMobileBrowse,
                        catalogRefreshing = catalogRefreshing,
                        session = session,
                        section = browseSection,
                        selectedPlaylistId = selectedPlaylistId,
                        searchQuery = searchQuery,
                        libraryFilter = libraryFilter,
                        libraryUi = libraryUi,
                        currentTrack = currentTrack,
                        homeUiState = homeUiState,
                        isPlaying = shellPlayback.isPlaying,
                        isBuffering = shellPlayback.isBuffering,
                        onNavigate = {
                            navigator.openBrowse(it)
                            selectedPlaylistId = null
                        },
                        onSearchQuery = { newQuery ->
                            searchQuery = newQuery
                            val scopedScreen = screen
                            val scoped = scopedScreen is AppScreen.ArtistDetail ||
                                scopedScreen is AppScreen.AlbumDetail ||
                                scopedScreen is AppScreen.SongDetail ||
                                scopedScreen is AppScreen.Lyrics ||
                                scopedScreen is AppScreen.Collections ||
                                scopedScreen is AppScreen.CollectionItems ||
                                scopedScreen is AppScreen.RecentlyAdded ||
                                scopedScreen is AppScreen.PlayHistory ||
                                scopedScreen is AppScreen.FavoritePlaylists ||
                                scopedScreen is AppScreen.FavoriteArtists ||
                                scopedScreen is AppScreen.FavoriteAlbums ||
                                scopedScreen is AppScreen.PlaylistDetail ||
                                selectedPlaylistId != null ||
                                browseSection == BrowseSection.Library ||
                                browseSection == BrowseSection.Playlists ||
                                browseSection == BrowseSection.Settings
                            if (!scoped && newQuery.isNotBlank()) {
                                navigator.openBrowse(BrowseSection.Search)
                            }
                        },
                        onLibraryFilter = { libraryFilter = it },
                        onPlaylist = { playlist ->
                            navigator.open(playlist.route())
                        },
                        onArtist = { navigator.open(it.route()) },
                        onAlbum = { navigator.open(it.route()) },
                        onSong = { navigator.open(it.route()) },
                        onRecentSongs = openRecentSongs,
                        onRecentArtists = openRecentArtists,
                        onRecentAlbums = openRecentAlbums,
                        onFavoritePlaylists = openFavoritePlaylists,
                        onFavoriteArtists = openFavoriteArtists,
                        onFavoriteAlbums = openFavoriteAlbums,
                        onRecentlyPlayed = openRecentlyPlayed,
                        onMostPlayed = openMostPlayed,
                        onCollections = openCollections,
                        supportedCollectionEntries = supportedCollectionEntries,
                        onRefreshRandomArtists = { randomArtistSeed = Random.nextInt() },
                        onRefreshRandomAlbums = { randomAlbumSeed = Random.nextInt() },
                        onPrefetchHomeArtist = state::prefetchHomeArtistStats,
                        onPrefetchHomeAlbum = state::prefetchHomeAlbumStats,
                        onPlayDecadeMix = state::playDecadeMix,
                        decadeMixNotice = decadeMixNotice,
                        onClearDecadeMixNotice = state::clearDecadeMixNotice,
                        radioStations = radioStations,
                        radioStartingIds = radioStartingIds,
                        onPlayRadioStation = state::playRadioStation,
                        onPlayPersonalMix = playPersonalMix,
                        onPlayTracks = playTracksFromMobile,
                        onAddToUpNext = state::addToUpNext,
                        onDownload = state::download,
                        onOpenNowPlaying = { navigator.openPlayer() },
                        onTogglePlayPause = state::togglePlayPause,
                        onPreviousTrack = state::previous,
                        onNextTrack = state::next,
                        onSignOut = state::signOut,
                        onAddLocalFolder = state::addLocalFolderFromUri,
                        onRefreshLibrary = state::refreshCatalog,
                        onRefreshPlayHistory = state::refreshPlayHistory,
                        onJellyfinPage = state::loadJellyfinLibraryPage,
                        onLibrarySortBy = state::setLibrarySortBy,
                        onLibraryAscending = state::setLibrarySortAscending,
                        onLibraryColumns = state::setLibraryColumns,
                        onHomeSections = state::setHomeSections,
                        onPersonalMix = state::setPersonalMixPreferences,
                        onGridColumns = state::setGridColumns,
                        onExportFavoritePlaylists = state::exportFavoritePlaylists,
                        onImportFavoritePlaylists = state::importFavoritePlaylists,
                        appSettings = appSettings,
                        homeScreenLayoutMode = homeScreenLayoutMode,
                        onCrossfadeSeconds = state::setCrossfadeSeconds,
                        onScanLibraryOnLaunch = state::setScanLibraryOnLaunch,
                        onNotifyWhenDownloadFinishes = state::setNotifyWhenDownloadFinishes,
                        onPersistEqualizerSettings = state::setPersistEqualizerSettings,
                        downloadDirectory = downloadDirectory,
                        downloadCount = catalog.downloads.size,
                        defaultDownloadDirectoryLabel = state.defaultDownloadDirectoryLabel,
                        onDownloadDirectory = state::setDownloadDirectory,
                        onDeleteAllDownloads = state::deleteAllDownloads,
                        useLightAppearance = useLightAppearance,
                        onUseLightAppearanceChange = onUseLightAppearanceChange,
                        appearanceTintId = appearanceTintId,
                        onAppearanceTintChange = onAppearanceTintChange,
                        onHomeScreenLayoutModeChange = onHomeScreenLayoutModeChange,
                        listenBrainzCredentialAvailability = state.listenBrainzCredentialAvailability,
                        onConnectListenBrainz = state::connectListenBrainz,
                        onDisconnectListenBrainz = state::disconnectListenBrainz,
                        onListenBrainzSubmitNowPlaying = state::setListenBrainzSubmitNowPlaying,
                        onListenBrainzSubmitListens = state::setListenBrainzSubmitListens,
                        onListenBrainzSubmitCurrentTrackFeedback = state::setListenBrainzSubmitCurrentTrackFeedback,
                        showBottomChrome = false,
                    )
                    }
                }
                }
                }
                AnimatedVisibility(
                    visible = mobileChromeVisible,
                    enter = EnterTransition.None,
                    exit = ExitTransition.None,
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .fillMaxWidth()
                        .zIndex(3f),
                ) {
                    CompositionLocalProvider(
                        LocalAnimatedVisibilityScope provides this,
                    ) {
                        MobilePersistentPlaybackChrome(
                            section = browseSection,
                            currentTrack = currentTrack,
                            isPlaying = shellPlayback.isPlaying,
                            isBuffering = shellPlayback.isBuffering,
                            onNavigate = { section ->
                                navigator.openBrowse(section)
                                selectedPlaylistId = null
                            },
                            onOpenNowPlaying = { navigator.openPlayer() },
                            onTogglePlayPause = state::togglePlayPause,
                            onPreviousTrack = state::previous,
                            onNextTrack = state::next,
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                }
                if (mobilePlayerAsSheet) {
                    val playerSheetVisibility = remember {
                        MutableTransitionState(false)
                    }.apply {
                        targetState = true
                    }
                    AnimatedVisibility(
                        visibleState = playerSheetVisibility,
                        enter = slideInVertically(
                            animationSpec = spring(
                                stiffness = Spring.StiffnessMedium,
                                dampingRatio = Spring.DampingRatioNoBouncy,
                            ),
                            initialOffsetY = { it },
                        ),
                        exit = ExitTransition.None,
                        modifier = Modifier
                            .fillMaxSize()
                            .zIndex(4f),
                    ) {
                        CompositionLocalProvider(
                            LocalAnimatedVisibilityScope provides this,
                        ) {
                            MobilePlayerHost(
                                appState = state,
                                track = mobilePlayerTrack,
                                upNext = mobilePlayerUpNext,
                                previousTrack = mobilePlayerPreviousTrack,
                                currentIndex = mobilePlayerCurrentIndex,
                                playbackStarting = mobilePlaybackStarting,
                                castState = cast,
                                remotePlaybackTarget = musicAssistantRemotePlayback?.target,
                                onToggle = state::togglePlayPause,
                                onPrevious = state::previous,
                                onNext = state::next,
                                onSkipQueueBy = state::skipQueueBy,
                                onShuffle = state::toggleShuffle,
                                onRepeat = state::cycleRepeat,
                                onSeek = state::seekTo,
                                onPlayQueue = state::playUpNext,
                                onMoveUpNext = state::moveUpNext,
                                onRemoveUpNext = state::removeUpNext,
                                onOpenSongDetail = { navigator.open(it.route()) },
                                onCast = state::showCastPicker,
                                onLyrics = {
                                    currentTrack?.let { navigator.open(PhoebeRoute.Lyrics(it.id)) }
                                },
                                onBack = collapseMobilePlayer,
                                onSwipeDismiss = collapseMobilePlayer,
                                handleSystemBack = true,
                            )
                        }
                    }
                }
                MobileNowPlayingArtworkOverlay(
                    transitionState = mobileArtworkTransition,
                    modifier = Modifier
                        .fillMaxSize()
                        .zIndex(5f),
                )
                }
                }
            } else {
                DesktopPlayer(
                    playerFlow = state.player,
                    shellState = DesktopShellState(
                        screen = screen,
                        routes = navigator.routes,
                        catalog = catalog,
                        catalogRefreshing = catalogRefreshing,
                        session = session,
                        mediaSources = mediaSources,
                        section = browseSection,
                        selectedPlaylistId = selectedPlaylistId,
                        showQueue = wideDesktop,
                        compact = !wideDesktop,
                        busy = busy,
                    ),
                    playbackState = PlaybackUiState(
                        shellPlayback = shellPlayback,
                        track = currentTrack,
                        upNext = upNext,
                        currentIndex = currentIndex,
                        lyricsTrack = lyricsTrack,
                        lyricsState = lyricsState,
                        castState = cast,
                        remotePlaybackTarget = musicAssistantRemotePlayback?.target,
                        listenBrainzFeedbackTarget = listenBrainzFeedbackTarget,
                        equalizerProfile = equalizerProfile,
                        persistEqualizerSettings = appSettings.persistEqualizerSettings,
                        equalizerRemoteUnavailable = equalizerRemoteUnavailable,
                    ),
                    playbackActions = PlaybackActions(
                        onToggle = state::togglePlayPause,
                        onPrevious = state::previous,
                        onNext = state::next,
                        onShuffle = state::toggleShuffle,
                        onRepeat = state::cycleRepeat,
                        onVolume = state::setVolume,
                        onSeek = state::seekTo,
                        onCast = state::showCastPicker,
                        onEqualizerEnabled = state::setEqualizerEnabled,
                        onEqualizerBandCount = state::setEqualizerBandCount,
                        onEqualizerGain = state::setEqualizerGain,
                        onEqualizerReset = state::resetEqualizer,
                        onPersistEqualizerSettings = state::setPersistEqualizerSettings,
                        onListenBrainzFeedback = state::submitListenBrainzFeedback,
                        onLyrics = {
                            selectedPlaylistId = null
                            navigator.openBrowse(
                                if (browseSection == BrowseSection.Lyrics) BrowseSection.Home else BrowseSection.Lyrics,
                            )
                        },
                        onPlayQueue = state::playUpNext,
                        onClearQueue = state::clearQueue,
                        onMoveUpNext = state::moveUpNext,
                        onRemoveUpNext = state::removeUpNext,
                        onRetryLyrics = retryLyrics,
                    ),
                    browseState = BrowseUiState(
                        homeUiState = homeUiState,
                        playHistory = playHistory,
                        resolvedTracksById = resolvedTracksById,
                        searchQuery = searchQuery,
                        libraryFilter = libraryFilter,
                        libraryUi = libraryUi,
                        supportedCollectionEntries = supportedCollectionEntries,
                        decadeMixNotice = decadeMixNotice,
                        radioStations = radioStations,
                        artistRadioAvailability = artistRadioAvailability,
                        radioStartingIds = radioStartingIds,
                    ),
                    browseActions = BrowseActions(
                        onNavigate = { section ->
                            if (!canBrowseMainSections(session, mediaSources) && section.isMainBrowseSection()) return@BrowseActions
                            navigator.openBrowse(section)
                            selectedPlaylistId = null
                        },
                        onSearchQuery = { newQuery ->
                            searchQuery = newQuery
                            // Stay in any scoped context (playlist, detail, or library tab)
                            // and let that view filter its own contents by the query.
                            val scoped = screen is AppScreen.ArtistDetail ||
                                screen is AppScreen.AlbumDetail ||
                                screen is AppScreen.SongDetail ||
                                screen is AppScreen.Lyrics ||
                                screen is AppScreen.Collections ||
                                screen is AppScreen.CollectionItems ||
                                screen is AppScreen.RecentlyAdded ||
                                screen is AppScreen.PlayHistory ||
                                screen is AppScreen.FavoritePlaylists ||
                                screen is AppScreen.FavoriteArtists ||
                                screen is AppScreen.FavoriteAlbums ||
                                screen is AppScreen.PlaylistDetail ||
                                selectedPlaylistId != null ||
                                browseSection == BrowseSection.Library ||
                                browseSection == BrowseSection.Playlists ||
                                browseSection == BrowseSection.Settings
                            if (
                                !scoped &&
                                newQuery.isNotBlank() &&
                                canBrowseMainSections(session, mediaSources)
                            ) {
                                navigator.openBrowse(BrowseSection.Search)
                            }
                        },
                        onLibraryFilter = { libraryFilter = it },
                        onPlaylist = { playlist ->
                            selectedPlaylistId = playlist.id
                            navigator.open(playlist.route())
                        },
                        onArtist = { navigator.open(it.route()) },
                        onAlbum = { navigator.open(it.route()) },
                        onSong = { navigator.open(it.route()) },
                        onOpenLyrics = { navigator.open(PhoebeRoute.Lyrics(it.id)) },
                        onRecentSongs = openRecentSongs,
                        onRecentArtists = openRecentArtists,
                        onRecentAlbums = openRecentAlbums,
                        onFavoritePlaylists = openFavoritePlaylists,
                        onFavoriteArtists = openFavoriteArtists,
                        onFavoriteAlbums = openFavoriteAlbums,
                        onRecentlyPlayed = openRecentlyPlayed,
                        onMostPlayed = openMostPlayed,
                        onCollections = openCollections,
                        onCollectionValue = openCollectionValue,
                        onRefreshRandomArtists = { randomArtistSeed = Random.nextInt() },
                        onRefreshRandomAlbums = { randomAlbumSeed = Random.nextInt() },
                        onPrefetchHomeArtist = state::prefetchHomeArtistStats,
                        onPrefetchHomeAlbum = state::prefetchHomeAlbumStats,
                        onPlayDecadeMix = state::playDecadeMix,
                        onClearDecadeMixNotice = state::clearDecadeMixNotice,
                        onPlayRadioStation = state::playRadioStation,
                        onPlayPersonalMix = playPersonalMix,
                        onPopDetail = { navigator.pop() },
                        onPlayTracks = playTracks,
                        onAddToUpNext = state::addToUpNext,
                        onDownload = state::download,
                        onDownloadArtist = state::download,
                        onProbeArtistRadio = state::probeArtistRadio,
                        onPlayArtistRadio = state::playArtistRadio,
                        onDownloadAlbum = state::download,
                        onDownloadPlaylist = state::download,
                        onLibrarySortBy = state::setLibrarySortBy,
                        onLibraryAscending = state::setLibrarySortAscending,
                        onLibraryColumns = state::setLibraryColumns,
                    ),
                    authSetupState = AuthSetupState(
                        appMessage = message,
                        pinCode = pin?.code,
                        authInProgress = authInProgress,
                        serversLoading = serversLoading,
                        jellyfinServers = jellyfinServers,
                        jellyfinDiscoveryLoading = jellyfinDiscoveryLoading,
                        jellyfinQuickConnect = jellyfinQuickConnect,
                        servers = servers,
                        libraries = libraries,
                        librariesLoading = librariesLoading,
                    ),
                    authSetupActions = AuthSetupActions(
                        onStartSignIn = state::startPlexSignIn,
                        onFinishSignIn = state::finishPlexSignIn,
                        onSignInJellyfin = state::signInJellyfin,
                        onSignInProvider = state::signInProvider,
                        onDiscoverJellyfinServers = state::discoverJellyfinServers,
                        onStartJellyfinQuickConnect = state::startJellyfinQuickConnect,
                        onFinishJellyfinQuickConnect = state::finishJellyfinQuickConnect,
                        onSignOut = state::signOut,
                        onAddLocalFolder = state::addLocalFolderFromUri,
                        onRemoveLocalFolder = state::removeLocalFolder,
                        onToggleLocalFolder = state::setLocalFolderEnabled,
                        onRefreshLibrary = state::refreshCatalog,
                        onRefreshPlayHistory = state::refreshPlayHistory,
                        onJellyfinPage = state::loadJellyfinLibraryPage,
                        onSelectServer = { state.selectServer(it) },
                        onSelectLibrary = { library, mode -> state.selectLibrary(library, mode) },
                        onCancelPlexSetup = { state.signOut() },
                        onBackToServerPicker = { state.returnToServerPicker() },
                        onRetryServers = { state.loadServers() },
                    ),
                    settingsState = SettingsUiState(
                        appSettings = appSettings,
                        downloadDirectory = downloadDirectory,
                        downloadCount = catalog.downloads.size,
                        defaultDownloadDirectoryLabel = state.defaultDownloadDirectoryLabel,
                        useLightAppearance = useLightAppearance,
                        appearanceTintId = appearanceTintId,
                        homeScreenLayoutMode = homeScreenLayoutMode,
                        listenBrainzCredentialAvailability = state.listenBrainzCredentialAvailability,
                    ),
                    settingsActions = SettingsActions(
                        onHomeSections = state::setHomeSections,
                        onPersonalMix = state::setPersonalMixPreferences,
                        onGridColumns = state::setGridColumns,
                        onExportFavoritePlaylists = state::exportFavoritePlaylists,
                        onImportFavoritePlaylists = state::importFavoritePlaylists,
                        onCrossfadeSeconds = state::setCrossfadeSeconds,
                        onScanLibraryOnLaunch = state::setScanLibraryOnLaunch,
                        onNotifyWhenDownloadFinishes = state::setNotifyWhenDownloadFinishes,
                        onPersistEqualizerSettings = state::setPersistEqualizerSettings,
                        onDownloadDirectory = state::setDownloadDirectory,
                        onDeleteAllDownloads = state::deleteAllDownloads,
                        onUseLightAppearanceChange = onUseLightAppearanceChange,
                        onAppearanceTintChange = onAppearanceTintChange,
                        onHomeScreenLayoutModeChange = onHomeScreenLayoutModeChange,
                        onConnectListenBrainz = state::connectListenBrainz,
                        onDisconnectListenBrainz = state::disconnectListenBrainz,
                        onListenBrainzSubmitNowPlaying = state::setListenBrainzSubmitNowPlaying,
                        onListenBrainzSubmitListens = state::setListenBrainzSubmitListens,
                        onListenBrainzSubmitCurrentTrackFeedback = state::setListenBrainzSubmitCurrentTrackFeedback,
                    ),
                )
            }
            metadataEditorTrack?.let { editing ->
                val latest = catalog.tracksByParent.values
                    .asSequence()
                    .flatten()
                    .firstOrNull { it.id == editing.id } ?: editing
                MetadataEditorOverlay(
                    track = latest,
                    compact = compact,
                    onDismiss = { metadataEditorTrack = null },
                    onSave = { update ->
                        state.updateTrackMetadata(update)
                        metadataEditorTrack = null
                    },
                )
            }
        }

        AnimatedVisibility(
            visible = busy && screen != AppScreen.SignIn,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier.fillMaxSize().align(Alignment.Center),
        ) {
            val overlayInteraction = remember { MutableInteractionSource() }
            Box(
                Modifier
                    .fillMaxSize()
                    .background(PhoebeUi.overlayScrim)
                    .clickable(indication = null, interactionSource = overlayInteraction) {},
            ) {
                Column(
                    Modifier.align(Alignment.Center),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(20.dp),
                ) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(48.dp),
                        color = PhoebeUi.accentLight,
                        strokeWidth = 3.dp,
                        trackColor = PhoebeUi.progressTrack,
                    )
                    Text("Please wait", color = PhoebeUi.primaryText, fontSize = 17.sp, fontWeight = FontWeight.SemiBold)
                    Text(
                        text = message.ifBlank { "Finishing up…" },
                        color = PhoebeUi.secondaryText,
                        fontSize = 14.sp,
                        lineHeight = 20.sp,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.widthIn(max = 320.dp),
                    )
                }
            }
        }
        PlaybackFailureSnackbar(
            message = playbackSnackbar,
            onDismiss = state::dismissPlaybackSnackbar,
            modifier = Modifier.align(Alignment.BottomCenter),
        )
            }
    }
    // Drag-ghost overlay — must be the LAST child of the wrapper Box so it draws above the
    // rest of the UI. Renders nothing until a drag is in flight.
    DragGhost()
    }
    }
}

@Composable
private fun PlaybackFailureSnackbar(
    message: String?,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    LaunchedEffect(message) {
        if (message != null) {
            delay(5_000L)
            onDismiss()
        }
    }
    AnimatedVisibility(
        visible = message != null,
        enter = slideInVertically(tween(180, easing = FastOutSlowInEasing)) { it / 2 } + fadeIn(tween(180)),
        exit = slideOutVertically(tween(160, easing = FastOutSlowInEasing)) { it / 2 } + fadeOut(tween(160)),
        modifier = modifier
            .navigationBarsPadding()
            .padding(horizontal = 18.dp, vertical = 18.dp)
            .zIndex(20f),
    ) {
        Surface(
            color = PhoebeUi.panel.copy(alpha = 0.96f),
            contentColor = PhoebeUi.primaryText,
            shape = RoundedCornerShape(8.dp),
            border = BorderStroke(1.dp, PhoebeUi.border),
            shadowElevation = 12.dp,
            modifier = Modifier.widthIn(max = 520.dp),
        ) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(14.dp),
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
            ) {
                Text(
                    text = message.orEmpty(),
                    color = PhoebeUi.primaryText,
                    fontSize = 14.sp,
                    lineHeight = 19.sp,
                    modifier = Modifier.weight(1f),
                )
                TextButton(onClick = onDismiss) {
                    Text("Dismiss")
                }
            }
        }
    }
}

private fun catalogHasContentForSurface(
    catalog: CatalogSnapshot,
    screen: AppScreen,
    browseSection: BrowseSection,
    selectedPlaylistId: String?,
    libraryFilter: LibraryFilterTab,
): Boolean {
    selectedPlaylistId?.let { return catalog.tracksByParent[it].orEmpty().isNotEmpty() }
    return when (screen) {
        is AppScreen.AlbumDetail -> catalog.tracksByParent[screen.album.id].orEmpty().isNotEmpty()
        is AppScreen.ArtistDetail -> catalogAlbumsForArtist(catalog, screen.artist.title).isNotEmpty() ||
            catalogTracksForArtist(catalog, screen.artist.title).isNotEmpty()
        is AppScreen.PlaylistDetail -> catalog.tracksByParent[screen.playlist.id].orEmpty().isNotEmpty()
        is AppScreen.SongDetail -> true
        is AppScreen.Lyrics -> true
        is AppScreen.RecentlyAdded -> catalog.tracksByParent.values.any { it.isNotEmpty() } ||
            catalog.albums.isNotEmpty() ||
            catalog.artists.isNotEmpty()
        is AppScreen.PlayHistory -> true
        AppScreen.FavoritePlaylists -> catalog.playlists.any { it.favorite }
        AppScreen.FavoriteArtists -> catalog.artists.any { it.favorite }
        AppScreen.FavoriteAlbums -> catalog.albums.any { it.favorite }
        is AppScreen.Collections -> when (screen.entry.target) {
            CollectionTarget.Artists -> catalog.artists.isNotEmpty()
            CollectionTarget.Albums -> catalog.albums.isNotEmpty()
        }
        is AppScreen.CollectionItems -> when (screen.entry.target) {
            CollectionTarget.Artists -> catalog.artists.isNotEmpty()
            CollectionTarget.Albums -> catalog.albums.isNotEmpty()
        }
        AppScreen.Home -> when (browseSection) {
            BrowseSection.Home -> catalog.artists.isNotEmpty() ||
                catalog.albums.isNotEmpty() ||
                catalog.playlists.isNotEmpty() ||
                catalog.tracksByParent.values.any { it.isNotEmpty() }
            BrowseSection.Search -> catalog.tracksByParent.values.any { it.isNotEmpty() } ||
                catalog.artists.isNotEmpty() ||
                catalog.albums.isNotEmpty()
            BrowseSection.Library -> when (libraryFilter) {
                LibraryFilterTab.Artists -> catalog.artists.isNotEmpty()
                LibraryFilterTab.Albums -> catalog.albums.isNotEmpty()
                LibraryFilterTab.Songs -> catalog.tracksByParent.values.any { it.isNotEmpty() }
            }
            BrowseSection.Lyrics -> true
            BrowseSection.Playlists -> catalog.playlists.isNotEmpty()
            BrowseSection.Settings -> true
        }
        AppScreen.SignIn,
        AppScreen.ServerPicker,
        AppScreen.LibraryPicker,
        AppScreen.Player,
        -> true
    }
}

@Composable
private fun LyricsScreenHost(
    appState: AppState,
    track: Track?,
    currentTrackId: String?,
    lyricsState: LyricsLoadState,
    onBack: () -> Unit,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val player by appState.player.collectAsState()
    val chromePadding = LocalMobileChromePadding.current
    LyricsView(
        track = track,
        currentTrackId = currentTrackId,
        positionMs = player.positionMs,
        state = lyricsState,
        modifier = modifier.padding(bottom = chromePadding.bottom),
        onBack = onBack,
        onRetry = onRetry,
    )
}

@Composable
private fun MobilePlayerHost(
    appState: AppState,
    track: Track?,
    upNext: List<Track>,
    previousTrack: Track?,
    currentIndex: Int,
    playbackStarting: Boolean = false,
    castState: CastState,
    remotePlaybackTarget: String?,
    onToggle: () -> Unit,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
    onSkipQueueBy: (Int) -> Unit,
    onShuffle: () -> Unit,
    onRepeat: () -> Unit,
    onSeek: (Long) -> Unit,
    onPlayQueue: (Int) -> Unit,
    onMoveUpNext: (Int, Int) -> Unit,
    onRemoveUpNext: (Int) -> Unit,
    onOpenSongDetail: (Track) -> Unit,
    onCast: () -> Unit,
    onLyrics: () -> Unit,
    onBack: () -> Unit,
    onSwipeDismiss: () -> Unit,
    handleSystemBack: Boolean = true,
) {
    val player by appState.player.collectAsState()
    val appSettings by appState.appSettings.collectAsState()
    val equalizerProfile by appState.equalizerProfile.collectAsState()
    val equalizerRemoteUnavailable by appState.equalizerRemoteUnavailable.collectAsState()
    val listenBrainzFeedbackTarget by appState.listenBrainzFeedbackTarget.collectAsState()
    val showStartingState = playbackStarting && track?.id != player.currentTrack?.id
    MobilePlayer(
        track = track,
        upNext = upNext,
        previousTrack = previousTrack,
        isPlaying = if (showStartingState) false else player.isPlaying,
        isBuffering = player.isBuffering || showStartingState,
        shuffle = player.shuffle,
        repeat = player.repeat,
        positionMs = if (showStartingState) 0L else player.positionMs,
        bufferedPositionMs = if (showStartingState) 0L else player.bufferedPositionMs,
        currentIndex = currentIndex,
        castState = castState,
        remotePlaybackTarget = remotePlaybackTarget,
        listenBrainzFeedbackTarget = listenBrainzFeedbackTarget,
        equalizerProfile = equalizerProfile,
        persistEqualizerSettings = appSettings.persistEqualizerSettings,
        equalizerRemoteUnavailable = equalizerRemoteUnavailable,
        onToggle = onToggle,
        onPrevious = onPrevious,
        onNext = onNext,
        onSkipQueueBy = onSkipQueueBy,
        onShuffle = onShuffle,
        onRepeat = onRepeat,
        onSeek = onSeek,
        onPlayQueue = onPlayQueue,
        onMoveUpNext = onMoveUpNext,
        onRemoveUpNext = onRemoveUpNext,
        onOpenSongDetail = onOpenSongDetail,
        onCast = onCast,
        onLyrics = onLyrics,
        onEqualizerEnabled = appState::setEqualizerEnabled,
        onEqualizerBandCount = appState::setEqualizerBandCount,
        onEqualizerGain = appState::setEqualizerGain,
        onEqualizerReset = appState::resetEqualizer,
        onPersistEqualizerSettings = appState::setPersistEqualizerSettings,
        onListenBrainzFeedback = appState::submitListenBrainzFeedback,
        onBack = onBack,
        onSwipeDismiss = onSwipeDismiss,
        handleSystemBack = handleSystemBack,
    )
}

private fun resolveArtistForTrack(catalog: CatalogSnapshot, track: Track): Artist? {
    val title = track.artist.trim()
    if (title.isBlank()) return null
    return catalog.artists.firstOrNull { it.title.equals(title, ignoreCase = true) }
        ?: resolveAlbumForTrack(catalog, track)?.let { album ->
            catalog.artists.firstOrNull { it.title.equals(album.artist, ignoreCase = true) }
        }
        ?: catalog.artists.firstOrNull { artist ->
            catalogAlbumsForArtist(catalog, artist.title).any { album ->
                album.title.equals(track.album, ignoreCase = true)
            }
        }
}

private fun resolveAlbumForTrack(catalog: CatalogSnapshot, track: Track): Album? {
    track.parentAlbumId?.let { parentAlbumId ->
        catalog.albums.firstOrNull { it.id == parentAlbumId }?.let { return it }
    }
    val albumTitle = track.album.trim()
    if (albumTitle.isBlank()) return null
    return catalog.albums.firstOrNull { album ->
        album.title.equals(albumTitle, ignoreCase = true) &&
            album.artist.equals(track.artist, ignoreCase = true)
    } ?: catalog.albums.firstOrNull { album ->
        album.title.equals(albumTitle, ignoreCase = true)
    }
}
