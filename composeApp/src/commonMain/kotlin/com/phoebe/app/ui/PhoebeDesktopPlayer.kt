package com.phoebe.app.ui

import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionLayout
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.phoebe.app.domain.AppScreen
import com.phoebe.app.domain.JellyfinSyncMode
import com.phoebe.app.domain.MediaProviderType
import com.phoebe.app.domain.PlayerState
import com.phoebe.app.domain.isEmbyFamily
import com.phoebe.app.domain.isNavidrome
import kotlinx.coroutines.flow.StateFlow

@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
internal fun DesktopPlayer(
    playerFlow: StateFlow<PlayerState>? = null,
    shellState: DesktopShellState,
    playbackState: PlaybackUiState,
    playbackActions: PlaybackActions,
    browseState: BrowseUiState,
    browseActions: BrowseActions,
    authSetupState: AuthSetupState,
    authSetupActions: AuthSetupActions,
    settingsState: SettingsUiState,
    settingsActions: SettingsActions,
) {
    val screen = shellState.screen
    val routes = shellState.routes
    val catalog = shellState.catalog
    val catalogRefreshing = shellState.catalogRefreshing
    val session = shellState.session
    val mediaSources = shellState.mediaSources
    val section = shellState.section
    val selectedPlaylistId = shellState.selectedPlaylistId
    val showQueue = shellState.showQueue
    val compact = shellState.compact
    val busy = shellState.busy
    val shellPlayback = playbackState.shellPlayback
    val player = rememberDesktopPlayerState(playerFlow, playbackState.player)
    val track = playbackState.track
    val upNext = playbackState.upNext
    val lyricsTrack = playbackState.lyricsTrack
    val lyricsState = playbackState.lyricsState
    val castState = playbackState.castState
    val remotePlaybackTarget = playbackState.remotePlaybackTarget
    val homeUiState = browseState.homeUiState
    val playHistory = browseState.playHistory
    val searchQuery = browseState.searchQuery
    val libraryFilter = browseState.libraryFilter
    val libraryUi = browseState.libraryUi
    val supportedCollectionEntries = browseState.supportedCollectionEntries
    val decadeMixNotice = browseState.decadeMixNotice
    val radioStations = browseState.radioStations
    val artistRadioAvailability = browseState.artistRadioAvailability
    val radioStartingIds = browseState.radioStartingIds
    val appMessage = authSetupState.appMessage
    val pinCode = authSetupState.pinCode
    val authInProgress = authSetupState.authInProgress
    val serversLoading = authSetupState.serversLoading
    val jellyfinServers = authSetupState.jellyfinServers
    val jellyfinDiscoveryLoading = authSetupState.jellyfinDiscoveryLoading
    val jellyfinQuickConnect = authSetupState.jellyfinQuickConnect
    val servers = authSetupState.servers
    val libraries = authSetupState.libraries
    val librariesLoading = authSetupState.librariesLoading
    val appSettings = settingsState.appSettings
    val downloadDirectory = settingsState.downloadDirectory
    val downloadCount = settingsState.downloadCount
    val defaultDownloadDirectoryLabel = settingsState.defaultDownloadDirectoryLabel
    val useLightAppearance = settingsState.useLightAppearance
    val appearanceTintId = settingsState.appearanceTintId
    val settingsInitialCategory = settingsState.settingsInitialCategory
    val onNavigate = browseActions.onNavigate
    val onSearchQuery = browseActions.onSearchQuery
    val onLibraryFilter = browseActions.onLibraryFilter
    val onPlaylist = browseActions.onPlaylist
    val onArtist = browseActions.onArtist
    val onAlbum = browseActions.onAlbum
    val onSong = browseActions.onSong
    val onOpenLyrics = browseActions.onOpenLyrics
    val onRecentSongs = browseActions.onRecentSongs
    val onRecentArtists = browseActions.onRecentArtists
    val onRecentAlbums = browseActions.onRecentAlbums
    val onFavoritePlaylists = browseActions.onFavoritePlaylists
    val onFavoriteArtists = browseActions.onFavoriteArtists
    val onFavoriteAlbums = browseActions.onFavoriteAlbums
    val onRecentlyPlayed = browseActions.onRecentlyPlayed
    val onMostPlayed = browseActions.onMostPlayed
    val onCollections = browseActions.onCollections
    val onCollectionValue = browseActions.onCollectionValue
    val onRefreshRandomArtists = browseActions.onRefreshRandomArtists
    val onRefreshRandomAlbums = browseActions.onRefreshRandomAlbums
    val onPrefetchHomeArtist = browseActions.onPrefetchHomeArtist
    val onPrefetchHomeAlbum = browseActions.onPrefetchHomeAlbum
    val onPlayDecadeMix = browseActions.onPlayDecadeMix
    val onClearDecadeMixNotice = browseActions.onClearDecadeMixNotice
    val onPlayRadioStation = browseActions.onPlayRadioStation
    val onPlayPersonalMix = browseActions.onPlayPersonalMix
    val onPopDetail = browseActions.onPopDetail
    val onPlayTracks = browseActions.onPlayTracks
    val onAddToUpNext = browseActions.onAddToUpNext
    val onDownload = browseActions.onDownload
    val onDownloadArtist = browseActions.onDownloadArtist
    val onProbeArtistRadio = browseActions.onProbeArtistRadio
    val onPlayArtistRadio = browseActions.onPlayArtistRadio
    val onDownloadAlbum = browseActions.onDownloadAlbum
    val onDownloadPlaylist = browseActions.onDownloadPlaylist
    val onLibrarySortBy = browseActions.onLibrarySortBy
    val onLibraryAscending = browseActions.onLibraryAscending
    val onLibraryColumns = browseActions.onLibraryColumns
    val onToggle = playbackActions.onToggle
    val onPrevious = playbackActions.onPrevious
    val onNext = playbackActions.onNext
    val onShuffle = playbackActions.onShuffle
    val onRepeat = playbackActions.onRepeat
    val onVolume = playbackActions.onVolume
    val onSeek = playbackActions.onSeek
    val onCast = playbackActions.onCast
    val onLyrics = playbackActions.onLyrics
    val onPlayQueue = playbackActions.onPlayQueue
    val onClearQueue = playbackActions.onClearQueue
    val onMoveUpNext = playbackActions.onMoveUpNext
    val onRemoveUpNext = playbackActions.onRemoveUpNext
    val onRetryLyrics = playbackActions.onRetryLyrics
    val onStartSignIn = authSetupActions.onStartSignIn
    val onFinishSignIn = authSetupActions.onFinishSignIn
    val onSignInJellyfin = authSetupActions.onSignInJellyfin
    val onSignInProvider = authSetupActions.onSignInProvider
    val onDiscoverJellyfinServers = authSetupActions.onDiscoverJellyfinServers
    val onStartJellyfinQuickConnect = authSetupActions.onStartJellyfinQuickConnect
    val onFinishJellyfinQuickConnect = authSetupActions.onFinishJellyfinQuickConnect
    val onSignOut = authSetupActions.onSignOut
    val onAddLocalFolder = authSetupActions.onAddLocalFolder
    val onRemoveLocalFolder = authSetupActions.onRemoveLocalFolder
    val onToggleLocalFolder = authSetupActions.onToggleLocalFolder
    val onRefreshLibrary = authSetupActions.onRefreshLibrary
    val onJellyfinPage = authSetupActions.onJellyfinPage
    val onSelectServer = authSetupActions.onSelectServer
    val onSelectLibrary = authSetupActions.onSelectLibrary
    val onCancelPlexSetup = authSetupActions.onCancelPlexSetup
    val onBackToServerPicker = authSetupActions.onBackToServerPicker
    val onRetryServers = authSetupActions.onRetryServers
    val onHomeSections = settingsActions.onHomeSections
    val onPersonalMix = settingsActions.onPersonalMix
    val onGridColumns = settingsActions.onGridColumns
    val onExportFavoritePlaylists = settingsActions.onExportFavoritePlaylists
    val onImportFavoritePlaylists = settingsActions.onImportFavoritePlaylists
    val onCrossfadeSeconds = settingsActions.onCrossfadeSeconds
    val onScanLibraryOnLaunch = settingsActions.onScanLibraryOnLaunch
    val onNotifyWhenDownloadFinishes = settingsActions.onNotifyWhenDownloadFinishes
    val onDownloadDirectory = settingsActions.onDownloadDirectory
    val onDeleteAllDownloads = settingsActions.onDeleteAllDownloads
    val onUseLightAppearanceChange = settingsActions.onUseLightAppearanceChange
    val onAppearanceTintChange = settingsActions.onAppearanceTintChange
    val isPlaying = shellPlayback.isPlaying
    val isBuffering = shellPlayback.isBuffering
    val positionMs = player.positionMs
    val bufferedPositionMs = player.bufferedPositionMs
    val shuffle = player.shuffle
    val repeat = player.repeat
    val volume = player.volume
    val displayRoutes = routes.ifEmpty { previewRoutesFor(screen, section) }
    var desktopUpNextExpanded by remember { mutableStateOf(true) }
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Surface(
            modifier = Modifier
                .fillMaxSize(),
            shape = RoundedCornerShape(0.dp),
            color = Color.Transparent,
        ) {
            Box(
                Modifier.background(
                    Brush.radialGradient(
                        colors = listOf(PhoebeUi.shellRadialTint, Color.Transparent),
                        center = Offset(500f, 20f),
                        radius = 560f,
                    ),
                ).background(Brush.verticalGradient(listOf(PhoebeUi.shellTop, PhoebeUi.shellBottom)))
            ) {
                Row(Modifier.fillMaxSize()) {
                    Sidebar(
                        catalog = catalog,
                        catalogRefreshing = catalogRefreshing,
                        session = session,
                        mediaSources = mediaSources,
                        activeSection = section,
                        selectedPlaylistId = selectedPlaylistId,
                        onNavigate = onNavigate,
                        onPlaylist = onPlaylist,
                        onSignOut = onSignOut,
                        onAddLocalFolder = onAddLocalFolder,
                        onRemoveLocalFolder = onRemoveLocalFolder,
                        onToggleLocalFolder = onToggleLocalFolder,
                        onRefreshLibrary = onRefreshLibrary,
                    )
                    Column(Modifier.weight(1f).fillMaxHeight()) {
                        Row(Modifier.weight(1f).fillMaxWidth()) {
                            SharedTransitionLayout(Modifier.weight(1f).fillMaxHeight()) {
                                val sharedTransitionScope = this
                                var previousScreen by remember { mutableStateOf<AppScreen?>(null) }
                                val sharedElementsEnabled = LocalSharedElementTransitionsEnabled.current &&
                                    shouldUseDesktopSharedElements(previousScreen, screen)
                                LaunchedEffect(screen) {
                                    previousScreen = screen
                                }
                                CompositionLocalProvider(
                                    LocalSharedTransitionScope provides sharedTransitionScope,
                                    LocalSharedElementTransitionsEnabled provides sharedElementsEnabled,
                                ) {
                                    PhoebeNavDisplay(
                                        backStack = displayRoutes,
                                        modifier = Modifier.fillMaxSize(),
                                        onBack = onPopDetail,
                                    ) { targetRoute ->
                                        val targetResolution = resolvePhoebeRoute(targetRoute, catalog, track)
                                        val missingRoute = targetResolution as? PhoebeRouteResolution.Missing
                                        val targetScreen = (targetResolution as? PhoebeRouteResolution.Resolved)?.screen ?: AppScreen.Home
                                        if (missingRoute != null) {
                                            MissingRouteFallback(
                                                title = missingRoute.title,
                                                message = missingRoute.message,
                                                onBack = onPopDetail,
                                                modifier = Modifier.fillMaxSize(),
                                            )
                                        } else {
                            when (targetScreen) {
                                is AppScreen.ServerPicker -> PlexServerPickerPanel(
                                    servers = servers,
                                    busy = busy,
                                    serversLoading = serversLoading,
                                    onSelectServer = onSelectServer,
                                    onCancel = onCancelPlexSetup,
                                    onRetry = onRetryServers,
                                    modifier = Modifier.fillMaxSize(),
                                )
                                is AppScreen.LibraryPicker -> PlexLibraryPickerPanel(
                                    libraries = libraries,
                                    serverName = session?.selectedServer?.name,
                                    providerType = session?.providerType ?: MediaProviderType.Plex,
                                    busy = busy,
                                    librariesLoading = librariesLoading,
                                    isJellyfin = session.isEmbyFamily(),
                                    onSelectLibrary = onSelectLibrary,
                                    onBack = onBackToServerPicker,
                                    onCancel = onCancelPlexSetup,
                                    modifier = Modifier.fillMaxSize(),
                                )
                                is AppScreen.SignIn -> SignInWelcomeScreen(
                                    message = appMessage,
                                    pinCode = pinCode,
                                    jellyfinServers = jellyfinServers,
                                    jellyfinDiscoveryLoading = jellyfinDiscoveryLoading,
                                    jellyfinQuickConnect = jellyfinQuickConnect,
                                    authInProgress = authInProgress,
                                    onStartSignIn = onStartSignIn,
                                    onFinishSignIn = onFinishSignIn,
                                    onSignInJellyfin = onSignInJellyfin,
                                    onSignInProvider = onSignInProvider,
                                    onDiscoverJellyfinServers = onDiscoverJellyfinServers,
                                    onStartJellyfinQuickConnect = onStartJellyfinQuickConnect,
                                    onFinishJellyfinQuickConnect = onFinishJellyfinQuickConnect,
                                    showLocalFolderHint = true,
                                    modifier = Modifier.fillMaxSize(),
                                )
                                is AppScreen.ArtistDetail -> Column(Modifier.fillMaxSize()) {
                                    LibraryTopBar(searchQuery = searchQuery, onSearchQuery = onSearchQuery)
                                    ArtistDetailPanel(
                                        artist = targetScreen.artist,
                                        catalog = catalog,
                                        libraryUi = libraryUi,
                                        catalogRefreshing = catalogRefreshing,
                                        modifier = Modifier.weight(1f).fillMaxWidth(),
                                        searchQuery = searchQuery,
                                        onBack = onPopDetail,
                                        onAlbum = onAlbum,
                                        onPlayTracks = onPlayTracks,
                                        onAddToUpNext = onAddToUpNext,
                                        onDownload = onDownload,
                                        onDownloadArtist = onDownloadArtist,
                                        artistRadioAvailability = artistRadioAvailability[targetScreen.artist.id],
                                        artistRadioStarting = targetScreen.artist.id in radioStartingIds,
                                        onProbeArtistRadio = onProbeArtistRadio,
                                        onPlayArtistRadio = onPlayArtistRadio,
                                        onArtist = onArtist,
                                        onLibraryColumns = onLibraryColumns,
                                    )
                                }
                                is AppScreen.AlbumDetail -> Column(Modifier.fillMaxSize()) {
                                    LibraryTopBar(searchQuery = searchQuery, onSearchQuery = onSearchQuery)
                                    AlbumDetailPanel(
                                        album = targetScreen.album,
                                        catalog = catalog,
                                        libraryUi = libraryUi,
                                        catalogRefreshing = catalogRefreshing,
                                        modifier = Modifier.weight(1f).fillMaxWidth(),
                                        searchQuery = searchQuery,
                                        onBack = onPopDetail,
                                        onPlayTracks = onPlayTracks,
                                        onAddToUpNext = onAddToUpNext,
                                        onDownload = onDownload,
                                        onDownloadAlbum = onDownloadAlbum,
                                        onArtist = onArtist,
                                        onLibraryColumns = onLibraryColumns,
                                    )
                                }
                                is AppScreen.SongDetail -> Column(Modifier.fillMaxSize()) {
                                    LibraryTopBar(searchQuery = searchQuery, onSearchQuery = onSearchQuery)
                                    SongDetailPanel(
                                        track = targetScreen.track,
                                        modifier = Modifier.weight(1f).fillMaxWidth(),
                                        onBack = onPopDetail,
                                        onPlay = { onPlayTracks(listOf(targetScreen.track), 0) },
                                        onAddToUpNext = onAddToUpNext,
                                        onDownload = onDownload,
                                        onOpenLyrics = onOpenLyrics,
                                    )
                                }
                                is AppScreen.Lyrics -> LyricsView(
                                    track = lyricsTrack,
                                    currentTrackId = track?.id,
                                    positionMs = positionMs,
                                    state = lyricsState,
                                    modifier = Modifier.fillMaxSize(),
                                    onBack = onPopDetail,
                                    onRetry = onRetryLyrics,
                                )
                                is AppScreen.RecentlyAdded -> Column(Modifier.fillMaxSize()) {
                                    LibraryTopBar(searchQuery = searchQuery, onSearchQuery = onSearchQuery)
                                    RecentlyAddedScreen(
                                        kind = targetScreen.kind,
                                        catalog = catalog,
                                        nowMs = LocalNowMs.current,
                                        modifier = Modifier.weight(1f).fillMaxWidth(),
                                        onBack = onPopDetail,
                                        onArtist = onArtist,
                                        onAlbum = onAlbum,
                                        onPlayTracks = onPlayTracks,
                                        onAddToUpNext = onAddToUpNext,
                                        onDownload = onDownload,
                                    )
                                }
                                is AppScreen.Collections -> Column(Modifier.fillMaxSize()) {
                                    LibraryTopBar(searchQuery = searchQuery, onSearchQuery = onSearchQuery)
                                    CollectionsScreen(
                                        entry = targetScreen.entry,
                                        catalog = catalog,
                                        gridColumns = libraryUi.gridColumns,
                                        searchQuery = searchQuery,
                                        modifier = Modifier.weight(1f).fillMaxWidth(),
                                        onBack = onPopDetail,
                                        onCollectionValue = { entry, value -> onCollectionValue(entry, value) },
                                    )
                                }
                                is AppScreen.CollectionItems -> Column(Modifier.fillMaxSize()) {
                                    LibraryTopBar(searchQuery = searchQuery, onSearchQuery = onSearchQuery)
                                    CollectionItemsScreen(
                                        entry = targetScreen.entry,
                                        value = targetScreen.value,
                                        catalog = catalog,
                                        gridColumns = libraryUi.gridColumns,
                                        searchQuery = searchQuery,
                                        modifier = Modifier.weight(1f).fillMaxWidth(),
                                        onBack = onPopDetail,
                                        onArtist = onArtist,
                                        onAlbum = onAlbum,
                                    )
                                }
                                is AppScreen.PlayHistory -> Column(Modifier.fillMaxSize()) {
                                    LibraryTopBar(searchQuery = searchQuery, onSearchQuery = onSearchQuery)
                                    PlayHistoryScreen(
                                        kind = targetScreen.kind,
                                        catalog = catalog,
                                        playHistory = playHistory,
                                        resolvedTracksById = browseState.resolvedTracksById,
                                        modifier = Modifier.weight(1f).fillMaxWidth(),
                                        onBack = onPopDetail,
                                        onPlayTracks = onPlayTracks,
                                        onAddToUpNext = onAddToUpNext,
                                        onDownload = onDownload,
                                    )
                                }
                                AppScreen.FavoritePlaylists -> FavoritePlaylistsDesktopView(
                                    playlists = LocalPlaylistActions.current.playlists,
                                    searchQuery = searchQuery,
                                    onSearchQuery = onSearchQuery,
                                    onPlaylist = onPlaylist,
                                    onBack = onPopDetail,
                                    modifier = Modifier.fillMaxSize(),
                                )
                                AppScreen.FavoriteArtists -> FavoriteArtistsDesktopView(
                                    catalog = catalog,
                                    libraryUi = libraryUi,
                                    searchQuery = searchQuery,
                                    onSearchQuery = onSearchQuery,
                                    onLibrarySortBy = onLibrarySortBy,
                                    onLibraryAscending = onLibraryAscending,
                                    onArtist = onArtist,
                                    onBack = onPopDetail,
                                    modifier = Modifier.fillMaxSize(),
                                )
                                AppScreen.FavoriteAlbums -> FavoriteAlbumsDesktopView(
                                    catalog = catalog,
                                    libraryUi = libraryUi,
                                    searchQuery = searchQuery,
                                    onSearchQuery = onSearchQuery,
                                    onLibrarySortBy = onLibrarySortBy,
                                    onLibraryAscending = onLibraryAscending,
                                    onAlbum = onAlbum,
                                    onBack = onPopDetail,
                                    modifier = Modifier.fillMaxSize(),
                                )
                                else -> when {
                                    section == BrowseSection.Home && selectedPlaylistId == null -> {
                                        val homeListState = RetainedLazyListStates.remember("desktop-home")
                                        DesktopHomeScreen(
                                        state = homeUiState,
                                        catalogRefreshing = catalogRefreshing,
                                        listState = homeListState,
                                        modifier = Modifier.fillMaxSize(),
                                        onTrack = onSong,
                                        onArtist = onArtist,
                                        onAlbum = onAlbum,
                                        onPlaylist = onPlaylist,
                                        onRecentSongs = onRecentSongs,
                                        onRecentArtists = onRecentArtists,
                                        onRecentAlbums = onRecentAlbums,
                                        onFavoritePlaylists = onFavoritePlaylists,
                                        onFavoriteArtists = onFavoriteArtists,
                                        onFavoriteAlbums = onFavoriteAlbums,
                                        onRecentlyPlayed = onRecentlyPlayed,
                                        onMostPlayed = onMostPlayed,
                                        onCollections = onCollections,
                                        onRefreshArtists = onRefreshRandomArtists,
                                        onRefreshAlbums = onRefreshRandomAlbums,
                                        onPrefetchArtist = onPrefetchHomeArtist,
                                        onPrefetchAlbum = onPrefetchHomeAlbum,
                                        onPlayDecadeMix = onPlayDecadeMix,
                                        decadeMixNotice = decadeMixNotice,
                                        onClearDecadeMixNotice = onClearDecadeMixNotice,
                                        radioStations = radioStations,
                                        radioStartingIds = radioStartingIds,
                                        onPlayRadioStation = onPlayRadioStation,
                                        onPlayPersonalMix = onPlayPersonalMix,
                                        onPlayTracks = onPlayTracks,
                                        onAddToUpNext = onAddToUpNext,
                                        onDownload = onDownload,
                                        homeSections = libraryUi.homeSections,
                                        supportedCollectionEntries = supportedCollectionEntries,
                                    )
                                    }
                                    section == BrowseSection.Search && selectedPlaylistId == null -> SearchDesktopView(
                                        catalog = catalog,
                                        catalogRefreshing = catalogRefreshing,
                                        searchQuery = searchQuery,
                                        modifier = Modifier.fillMaxSize(),
                                        onSearchQuery = onSearchQuery,
                                        onArtist = onArtist,
                                        onAlbum = onAlbum,
                                        onPlayTracks = onPlayTracks,
                                        onAddToUpNext = onAddToUpNext,
                                        onDownload = onDownload,
                                    )
                                    section == BrowseSection.Library && selectedPlaylistId == null -> {
                                        LibraryDesktopView(
                                            catalog = catalog,
                                            catalogRefreshing = catalogRefreshing,
                                            filter = libraryFilter,
                                            libraryUi = libraryUi,
                                            jellyfinPagination = (session.isEmbyFamily() || session.isNavidrome()) && session?.jellyfinSyncMode == JellyfinSyncMode.Quick,
                                            onJellyfinPage = onJellyfinPage,
                                            onFilter = onLibraryFilter,
                                            onLibrarySortBy = onLibrarySortBy,
                                            onLibraryAscending = onLibraryAscending,
                                            onLibraryColumns = onLibraryColumns,
                                            onArtist = onArtist,
                                            onAlbum = onAlbum,
                                            onPlayTracks = onPlayTracks,
                                            searchQuery = searchQuery,
                                            onSearchQuery = onSearchQuery,
                                            onAddToUpNext = onAddToUpNext,
                                            onDownload = onDownload,
                                            modifier = Modifier.fillMaxSize(),
                                        )
                                    }
                                    section == BrowseSection.Lyrics && selectedPlaylistId == null -> LyricsView(
                                        track = lyricsTrack,
                                        currentTrackId = track?.id,
                                        positionMs = positionMs,
                                        state = lyricsState,
                                        modifier = Modifier.fillMaxSize(),
                                        onBack = null,
                                        onRetry = onRetryLyrics,
                                    )
                                    section == BrowseSection.Settings && selectedPlaylistId == null -> SettingsDesktopView(
                                        isLightMode = useLightAppearance,
                                        onLightModeChange = onUseLightAppearanceChange,
                                        tintId = appearanceTintId,
                                        onTintChange = onAppearanceTintChange,
                                        homeScreenLayoutMode = settingsState.homeScreenLayoutMode,
                                        onHomeScreenLayoutModeChange = settingsActions.onHomeScreenLayoutModeChange,
                                        downloadDirectory = downloadDirectory,
                                        downloadCount = downloadCount,
                                        appSettings = appSettings,
                                        libraryUi = libraryUi,
                                        defaultDownloadDirectoryLabel = defaultDownloadDirectoryLabel,
                                        onDownloadDirectory = onDownloadDirectory,
                                        onDeleteAllDownloads = onDeleteAllDownloads,
                                        onCrossfadeSeconds = onCrossfadeSeconds,
                                        onScanLibraryOnLaunch = onScanLibraryOnLaunch,
                                        onNotifyWhenDownloadFinishes = onNotifyWhenDownloadFinishes,
                                        onHomeSections = onHomeSections,
                                        onPersonalMix = onPersonalMix,
                                        onGridColumns = onGridColumns,
                                        onExportFavoritePlaylists = onExportFavoritePlaylists,
                                        onImportFavoritePlaylists = onImportFavoritePlaylists,
                                        session = session,
                                        modifier = Modifier.fillMaxSize(),
                                        initialCategory = settingsInitialCategory,
                                    )
                                    else -> DesktopContent(
                                        catalog = catalog,
                                        catalogRefreshing = catalogRefreshing,
                                        jellyfinPagination = (session.isEmbyFamily() || session.isNavidrome()) && session?.jellyfinSyncMode == JellyfinSyncMode.Quick,
                                        onJellyfinPage = onJellyfinPage,
                                        section = section,
                                        selectedPlaylistId = selectedPlaylistId,
                                        searchQuery = searchQuery,
                                        libraryFilter = libraryFilter,
                                        libraryUi = libraryUi,
                                        modifier = Modifier.fillMaxSize(),
                                        onSearchQuery = onSearchQuery,
                                        onLibraryFilter = onLibraryFilter,
                                        onPlaylist = onPlaylist,
                                        onArtist = onArtist,
                                        onAlbum = onAlbum,
                                        onPlayTracks = onPlayTracks,
                                        onAddToUpNext = onAddToUpNext,
                                        onDownload = onDownload,
                                        onLibrarySortBy = onLibrarySortBy,
                                        onLibraryAscending = onLibraryAscending,
                                        onLibraryColumns = onLibraryColumns,
                                        onDownloadPlaylist = onDownloadPlaylist,
                                    )
                                }
                            }
                                }
                                    }
                                }
                            }
                            if (showQueue && desktopUpNextExpanded) {
                                Box(
                                    Modifier
                                        .fillMaxHeight()
                                        .padding(top = 132.dp, bottom = 24.dp)
                                        .width(1.dp)
                                        .background(PhoebeUi.border),
                                )
                                QueuePanel(
                                    upNext = upNext,
                                    currentTrack = track,
                                    repeat = repeat,
                                    modifier = Modifier.width(330.dp).fillMaxHeight().padding(start = 24.dp),
                                    onPlayQueue = onPlayQueue,
                                    onClearQueue = onClearQueue,
                                    onMoveUpNext = onMoveUpNext,
                                    onRemoveUpNext = onRemoveUpNext,
                                    onOpenTrackDetail = onSong,
                                    currentTrackClickOpensDetail = true,
                                )
                            }
                        }
                        DesktopTransport(
                            track = track,
                            isPlaying = isPlaying,
                            isBuffering = isBuffering,
                            positionMs = positionMs,
                            bufferedPositionMs = bufferedPositionMs,
                            shuffle = shuffle,
                            repeat = repeat,
                            volume = volume,
                            castState = castState,
                            remotePlaybackTarget = remotePlaybackTarget,
                            compact = compact,
                            lyricsVisible = section == BrowseSection.Lyrics && selectedPlaylistId == null,
                            upNextVisible = showQueue && desktopUpNextExpanded,
                            upNextToggleEnabled = showQueue,
                            onToggle = onToggle,
                            onPrevious = onPrevious,
                            onNext = onNext,
                            onShuffle = onShuffle,
                            onRepeat = onRepeat,
                            onVolume = onVolume,
                            onSeek = onSeek,
                            onLyrics = onLyrics,
                            onToggleUpNext = { desktopUpNextExpanded = !desktopUpNextExpanded },
                            onCast = onCast,
                        )
                    }
                }
            }
        }
    }
}

private fun shouldUseDesktopSharedElements(initial: AppScreen?, target: AppScreen): Boolean =
    initial != null &&
        initial.hasDesktopSharedElements() &&
        target.hasDesktopSharedElements()

private fun AppScreen.hasDesktopSharedElements(): Boolean = when (this) {
    AppScreen.Home,
    is AppScreen.AlbumDetail,
    is AppScreen.ArtistDetail,
    is AppScreen.CollectionItems,
    is AppScreen.PlayHistory,
    AppScreen.FavoritePlaylists,
    AppScreen.FavoriteArtists,
    AppScreen.FavoriteAlbums,
    is AppScreen.PlaylistDetail,
    is AppScreen.RecentlyAdded,
    is AppScreen.SongDetail,
    is AppScreen.Lyrics,
    -> true

    is AppScreen.Collections,
    AppScreen.LibraryPicker,
    AppScreen.Player,
    AppScreen.ServerPicker,
    AppScreen.SignIn,
    -> false
}

private fun previewRoutesFor(screen: AppScreen, section: BrowseSection): List<PhoebeRoute> {
    val root = PhoebeRoute.Browse(section)
    val route = when (screen) {
        AppScreen.SignIn -> return listOf(PhoebeRoute.SignIn)
        AppScreen.ServerPicker -> return listOf(PhoebeRoute.SignIn, PhoebeRoute.ServerPicker)
        AppScreen.LibraryPicker -> return listOf(PhoebeRoute.SignIn, PhoebeRoute.ServerPicker, PhoebeRoute.LibraryPicker)
        AppScreen.Home -> root
        is AppScreen.Collections -> PhoebeRoute.Collections(screen.entry)
        is AppScreen.CollectionItems -> PhoebeRoute.CollectionItems(screen.entry, screen.value)
        is AppScreen.AlbumDetail -> PhoebeRoute.AlbumDetail(screen.album.id)
        is AppScreen.ArtistDetail -> PhoebeRoute.ArtistDetail(screen.artist.id)
        is AppScreen.SongDetail -> PhoebeRoute.SongDetail(screen.track.id)
        is AppScreen.Lyrics -> PhoebeRoute.Lyrics(screen.track?.id)
        is AppScreen.RecentlyAdded -> PhoebeRoute.RecentlyAdded(screen.kind)
        is AppScreen.PlayHistory -> PhoebeRoute.PlayHistory(screen.kind)
        AppScreen.FavoritePlaylists -> PhoebeRoute.FavoritePlaylists
        AppScreen.FavoriteArtists -> PhoebeRoute.FavoriteArtists
        AppScreen.FavoriteAlbums -> PhoebeRoute.FavoriteAlbums
        is AppScreen.PlaylistDetail -> PhoebeRoute.PlaylistDetail(screen.playlist.id)
        AppScreen.Player -> PhoebeRoute.Player
    }
    return if (route == root) listOf(root) else listOf(root, route)
}

@Composable
private fun rememberDesktopPlayerState(
    playerFlow: StateFlow<PlayerState>?,
    fallback: PlayerState,
): PlayerState {
    if (playerFlow == null) return fallback
    val player by playerFlow.collectAsState()
    return player
}
