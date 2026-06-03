package com.phoebe.app

import com.phoebe.app.domain.Album
import com.phoebe.app.domain.AppSettings
import com.phoebe.app.domain.Artist
import com.phoebe.app.domain.CatalogSnapshot
import com.phoebe.app.domain.CollectionFacet
import com.phoebe.app.domain.EqualizerProfile
import com.phoebe.app.domain.ArtistRadioAvailability
import com.phoebe.app.domain.HomeSection
import com.phoebe.app.domain.JellyfinSyncMode
import com.phoebe.app.domain.JellyfinLibraryPageKind
import com.phoebe.app.domain.LibraryColumnVisibility
import com.phoebe.app.domain.LibrarySortBy
import com.phoebe.app.domain.LibraryTab
import com.phoebe.app.domain.LyricsLoadState
import com.phoebe.app.domain.MediaProviderType
import com.phoebe.app.domain.MusicLibrary
import com.phoebe.app.domain.Playlist
import com.phoebe.app.domain.PlayerQueueSnapshot
import com.phoebe.app.domain.PlayerState
import com.phoebe.app.domain.ShellPlaybackState
import com.phoebe.app.domain.PlexPin
import com.phoebe.app.domain.PlexRadioStation
import com.phoebe.app.domain.PlexServer
import com.phoebe.app.domain.PlexSession
import com.phoebe.app.domain.PersonalMixPreferences
import com.phoebe.app.domain.RepeatMode
import com.phoebe.app.domain.Track
import com.phoebe.app.domain.TrackMetadataUpdate
import com.phoebe.app.domain.canAddToLocalPlaylist
import com.phoebe.app.domain.canAddToPlexPlaylist
import com.phoebe.app.domain.canTogglePlexLike
import com.phoebe.app.domain.hasPlayableSource
import com.phoebe.app.domain.isLocalPlaylist
import com.phoebe.app.domain.isRemoteProviderPlaylist
import com.phoebe.app.domain.isEmbyFamily
import com.phoebe.app.domain.isFromLocalFolder
import com.phoebe.app.domain.isMusicAssistant
import com.phoebe.app.domain.isNavidrome
import com.phoebe.app.domain.isPlex
import com.phoebe.app.domain.serverAuthToken
import com.phoebe.app.domain.supportsCollectionEntry
import com.phoebe.app.domain.displayName
import com.phoebe.app.domain.supportsRemotePlaylists
import com.phoebe.app.domain.supportsRemoteRatings
import com.phoebe.app.domain.isJellyfin
import com.phoebe.app.domain.providerLabel
import com.phoebe.app.data.DownloadBatchResult
import com.phoebe.app.data.FavoriteSyncResult
import com.phoebe.app.data.FavoritePlaylistsExport
import com.phoebe.app.data.JellyfinQuickConnectResult
import com.phoebe.app.data.JellyfinPlayHistorySyncResult
import com.phoebe.app.data.ListenBrainzFeedbackScore
import com.phoebe.app.data.NavidromePlayHistorySyncResult
import com.phoebe.app.data.PlexPlayHistorySyncResult
import com.phoebe.app.data.PlexClient
import com.phoebe.app.data.defaultPlexRadioStations
import com.phoebe.app.playlists.PlaylistExportFormat
import com.phoebe.app.playlists.PlaylistExporter
import com.phoebe.app.player.asPlayerState
import com.phoebe.app.player.isPlaybackActive
import com.phoebe.app.domain.RecentSearchItem
import com.phoebe.app.platform.PhoebeLog
import com.phoebe.app.platform.currentTimeMs
import com.phoebe.app.platform.discoverJellyfinServers as discoverJellyfinServersOnNetwork
import com.phoebe.app.platform.openExternalUrl
import io.ktor.http.Url
import io.ktor.http.encodeURLParameter
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class MusicAssistantRemotePlayback(
    val tracks: List<Track>,
    val index: Int,
    val target: String,
)

data class CollectionMixSeed(
    val facet: CollectionFacet,
    val value: String,
)

sealed interface AppNavigationRequest {
    data object SignIn : AppNavigationRequest
    data object ServerPicker : AppNavigationRequest
    data object LibraryPicker : AppNavigationRequest
    data object Home : AppNavigationRequest
    data object Player : AppNavigationRequest
    data class PlaylistDetail(val playlistId: String) : AppNavigationRequest
}

class AppState(
    private val dependencies: AppDependencies,
    private val scope: CoroutineScope,
) {
    val session = dependencies.sessionRepository.session
    val catalog = dependencies.catalogRepository.catalog
    val downloads = dependencies.catalogRepository.downloads
    val downloadEvents = dependencies.catalogRepository.downloadEvents
    val catalogRefreshing: StateFlow<Boolean> = dependencies.catalogRepository.catalogRefreshing
    val catalogSyncState = dependencies.catalogRepository.catalogSyncState
    val tracksLoading = dependencies.catalogRepository.tracksLoading
    val mediaSources = dependencies.mediaSourcesRepository.state
    val cast = dependencies.castController.state
    private val mutableMusicAssistantRemotePlayback = MutableStateFlow<MusicAssistantRemotePlayback?>(null)
    val musicAssistantRemotePlayback = mutableMusicAssistantRemotePlayback.asStateFlow()
    val player: StateFlow<PlayerState> = combine(
        dependencies.audioPlayer.state,
        dependencies.castController.state,
        mutableMusicAssistantRemotePlayback,
    ) { audio, castState, musicAssistantRemote ->
        when {
            castState.isPlaybackActive -> castState.asPlayerState(audio)
            musicAssistantRemote != null -> PlayerState(
                queue = musicAssistantRemote.tracks,
                currentIndex = musicAssistantRemote.index,
                isPlaying = true,
                bufferedPositionMs = musicAssistantRemote.tracks.getOrNull(musicAssistantRemote.index)?.durationMs ?: 0L,
                durationMs = musicAssistantRemote.tracks.getOrNull(musicAssistantRemote.index)?.durationMs ?: 0L,
                volume = audio.volume,
            )
            else -> audio
        }
    }.stateIn(scope, SharingStarted.Eagerly, dependencies.audioPlayer.state.value)
    val shellPlayback: StateFlow<ShellPlaybackState> = player
        .map { playback ->
            ShellPlaybackState(
                currentTrack = playback.currentTrack,
                isPlaying = playback.isPlaying,
                isBuffering = playback.isBuffering,
            )
        }
        .distinctUntilChanged()
        .stateIn(
            scope,
            SharingStarted.Eagerly,
            ShellPlaybackState(
                currentTrack = player.value.currentTrack,
                isPlaying = player.value.isPlaying,
                isBuffering = player.value.isBuffering,
            ),
        )
    val playerQueue: StateFlow<PlayerQueueSnapshot> = player
        .map { playback ->
            PlayerQueueSnapshot(
                queue = playback.queue,
                currentIndex = playback.currentIndex,
            )
        }
        .distinctUntilChanged()
        .stateIn(
            scope,
            SharingStarted.Eagerly,
            PlayerQueueSnapshot(
                queue = player.value.queue,
                currentIndex = player.value.currentIndex,
            ),
        )
    val libraryUi = dependencies.libraryUiRepository.preferences
    val appSettings = dependencies.appSettingsRepository.settings
    val listenBrainzFeedbackTarget = dependencies.listenBrainzPlaybackReporter.feedbackTarget
    val listenBrainzCredentialAvailability = dependencies.listenBrainzAccountRepository.storageAvailability
    private val mutablePersistEqualizerSettings = MutableStateFlow(false)
    private val mutableEqualizerProfile = MutableStateFlow(EqualizerProfile.Default.normalized())
    val equalizerProfile: StateFlow<EqualizerProfile> = mutableEqualizerProfile.asStateFlow()
    val equalizerRemoteUnavailable: StateFlow<Boolean> = combine(
        cast,
        mutableMusicAssistantRemotePlayback,
    ) { castState, musicAssistantRemote ->
        castState.isPlaybackActive || musicAssistantRemote != null
    }.stateIn(scope, SharingStarted.Eagerly, false)
    val lastPlayedByArtist = dependencies.playHistoryRepository.lastPlayedByArtist
    val lastPlayedByAlbum = dependencies.playHistoryRepository.lastPlayedByAlbum
    val lastPlayedByTrack = dependencies.playHistoryRepository.lastPlayedByTrack
    val playCountsByTrack = dependencies.playHistoryRepository.playCountsByTrack
    val playEventsByTrack = dependencies.playHistoryRepository.playEventsByTrack
    val topMostPlayed = dependencies.playHistoryRepository.topMostPlayed
    val topRecentlyPlayed = dependencies.playHistoryRepository.topRecentlyPlayed
    val recentSearchItems = dependencies.searchHistoryRepository.items
    val defaultDownloadDirectoryLabel: String = dependencies.platformStorage.defaultDownloadDirectoryLabel()

    private val mutableNavigationRequests = MutableSharedFlow<AppNavigationRequest>(
        replay = 1,
        extraBufferCapacity = 32,
    )
    val navigationRequests: SharedFlow<AppNavigationRequest> = mutableNavigationRequests.asSharedFlow()

    private val mutableTab = MutableStateFlow(LibraryTab.Albums)
    val tab: StateFlow<LibraryTab> = mutableTab

    private val mutablePin = MutableStateFlow<PlexPin?>(null)
    val pin: StateFlow<PlexPin?> = mutablePin

    private val mutableServers = MutableStateFlow<List<PlexServer>>(emptyList())
    val servers: StateFlow<List<PlexServer>> = mutableServers

    private val mutableJellyfinServers = MutableStateFlow<List<PlexServer>>(emptyList())
    val jellyfinServers: StateFlow<List<PlexServer>> = mutableJellyfinServers

    private val mutableJellyfinDiscoveryLoading = MutableStateFlow(false)
    val jellyfinDiscoveryLoading: StateFlow<Boolean> = mutableJellyfinDiscoveryLoading

    private val mutableJellyfinQuickConnect = MutableStateFlow<JellyfinQuickConnectResult?>(null)
    val jellyfinQuickConnect: StateFlow<JellyfinQuickConnectResult?> = mutableJellyfinQuickConnect

    private val mutableLibraries = MutableStateFlow<List<MusicLibrary>>(emptyList())
    val libraries: StateFlow<List<MusicLibrary>> = mutableLibraries

    private val mutableBusy = MutableStateFlow(false)
    val busy: StateFlow<Boolean> = mutableBusy

    private val mutableServersLoading = MutableStateFlow(false)
    val serversLoading: StateFlow<Boolean> = mutableServersLoading

    private val mutableLibrariesLoading = MutableStateFlow(false)
    val librariesLoading: StateFlow<Boolean> = mutableLibrariesLoading

    private val mutableAuthInProgress = MutableStateFlow(false)
    val authInProgress: StateFlow<Boolean> = mutableAuthInProgress

    private val mutableMessage = MutableStateFlow("Sign in to your provider, or add a local music folder to get started.")
    val message: StateFlow<String> = mutableMessage

    private val mutablePlaybackSnackbar = MutableStateFlow<String?>(null)
    val playbackSnackbar: StateFlow<String?> = mutablePlaybackSnackbar.asStateFlow()

    private val mutableDecadeMixNotice = MutableStateFlow<String?>(null)
    val decadeMixNotice: StateFlow<String?> = mutableDecadeMixNotice

    private val mutableRadioStations = MutableStateFlow<List<PlexRadioStation>>(emptyList())
    val radioStations: StateFlow<List<PlexRadioStation>> = mutableRadioStations

    private val mutableRadioStartingIds = MutableStateFlow<Set<String>>(emptySet())
    val radioStartingIds: StateFlow<Set<String>> = mutableRadioStartingIds

    private val mutableArtistRadioAvailability = MutableStateFlow<Map<String, ArtistRadioAvailability>>(emptyMap())
    val artistRadioAvailability: StateFlow<Map<String, ArtistRadioAvailability>> = mutableArtistRadioAvailability

    private val mutableDownloadDirectory = MutableStateFlow<String?>(null)
    val downloadDirectory: StateFlow<String?> = mutableDownloadDirectory

    private val mutableActiveDownloadJobCount = MutableStateFlow(0)
    val activeDownloadJobCount: StateFlow<Int> = mutableActiveDownloadJobCount

    private var collectionMixGeneration = 0
    private var recentAlbumWarmSignature: String? = null
    private var playedAlbumWarmSignature: String? = null
    private var mostPlayedWarmSignature: String? = null
    private val prefetchedArtistIds = mutableSetOf<String>()
    private val prefetchedAlbumIds = mutableSetOf<String>()
    private var catalogRefreshJob: Job? = null
    private var playHistorySyncJob: Job? = null
    private var downloadedArtworkCacheJob: Job? = null
    private val activeDownloadJobs = mutableSetOf<Job>()

    private fun publishActiveDownloadJobCount() {
        mutableActiveDownloadJobCount.value = activeDownloadJobs.size
    }

    init {
        scope.launch {
            PhoebeLog.d("AppState") { "startup restore begin" }
            // Session and local folders are restored in [AppDependencies.create] so the first frame
            // can skip Sign-in; repeat here for callers that inject dependencies without that path.
            dependencies.sessionRepository.restore(refreshConnections = false)
            dependencies.mediaSourcesRepository.restore()
            dependencies.appSettingsRepository.restore()
            dependencies.listenBrainzAccountRepository.restore()
            val restoredSettings = appSettings.value
            mutablePersistEqualizerSettings.value = restoredSettings.persistEqualizerSettings
            val startupEqualizer = if (restoredSettings.persistEqualizerSettings) {
                restoredSettings.equalizerProfile.normalized()
            } else {
                EqualizerProfile.Default.normalized()
            }
            mutableEqualizerProfile.value = startupEqualizer
            dependencies.audioPlayer.setEqualizer(startupEqualizer)
            dependencies.libraryUiRepository.restore()
            dependencies.searchHistoryRepository.restore()
            dependencies.audioPlayer.setCrossfadeDurationMs(appSettings.value.crossfadeSeconds * 1_000L)
            dependencies.playHistoryRepository.restore()
            mutableDownloadDirectory.value = dependencies.platformStorage.readDownloadDirectory()
            requestNavigation(defaultBrowseRequest(session.value))
            if (session.value?.token?.isNotBlank() == true && session.value?.selectedServer == null) {
                refreshServers()
            }
            dependencies.catalogRepository.restoreCachedCatalog()
            syncRemotePlayHistoryInBackground()
            val hasRemoteLibrary = session.value?.selectedLibrary != null
            val hasLocalFolders = mediaSources.value.localFolders.any { it.enabled }
            if (appSettings.value.scanLibraryOnLaunch && (hasRemoteLibrary || hasLocalFolders)) {
                delay(500)
                if (session.value.isPlex()) {
                    dependencies.sessionRepository.refreshSelectedServerConnections()
                    dependencies.sessionRepository.warmServerConnection()
                }
                refreshCatalogSuspended(catalogMessage = null, backgroundIfCached = true)
            }
            if (session.value.isEmbyFamily() &&
                session.value?.selectedLibrary != null &&
                !dependencies.catalogRepository.catalog.value.hasBrowseableContent()
            ) {
                refreshCatalogSuspended(catalogMessage = "Library refreshed.")
            }
            cacheDownloadedArtworkInBackground()
            warmPlaylistTracksInBackground()
            ensureLikedSongsPlaylistIfPossible()
            if (session.value?.token?.isNotBlank() == true &&
                session.value?.selectedServer != null &&
                session.value.isPlex() &&
                !appSettings.value.scanLibraryOnLaunch
            ) {
                launch {
                    dependencies.sessionRepository.refreshSelectedServerConnections()
                    dependencies.sessionRepository.warmServerConnection()
                }
            }
            PhoebeLog.d("AppState") {
                "startup restore complete → destination=${defaultBrowseRequest(session.value)}, " +
                    "session=${session.value?.userName ?: "none"}, " +
                    "localFolders=${mediaSources.value.localFolders.size}"
            }
        }
        bindSystemVolume()
        bindAppSettingsToPlayback()
        recordPlaybackHistory()
        surfacePlaybackFailures()
        surfaceCastMessages()
        dependencies.plexPlaybackReporter.start(scope)
        dependencies.listenBrainzPlaybackReporter.start(scope)
    }

    private fun bindAppSettingsToPlayback() {
        scope.launch {
            appSettings.collect { settings ->
                mutablePersistEqualizerSettings.value = settings.persistEqualizerSettings
                dependencies.audioPlayer.setCrossfadeDurationMs(settings.crossfadeSeconds * 1_000L)
                if (settings.persistEqualizerSettings) {
                    val profile = settings.equalizerProfile.normalized()
                    if (mutableEqualizerProfile.value != profile) {
                        mutableEqualizerProfile.value = profile
                        dependencies.audioPlayer.setEqualizer(profile)
                    }
                }
            }
        }
    }

    private fun surfacePlaybackFailures() {
        scope.launch {
            var lastSerial = dependencies.audioPlayer.state.value.playbackErrorSerial
            dependencies.audioPlayer.state.collect { state ->
                if (state.playbackErrorSerial == lastSerial) return@collect
                lastSerial = state.playbackErrorSerial
                val title = state.currentTrack?.title?.takeIf { it.isNotBlank() }
                val notice = state.playbackErrorMessage
                    ?: title?.let { "Couldn't play $it." }
                    ?: "Couldn't play that song."
                mutableMessage.value = notice
                mutablePlaybackSnackbar.value = notice
            }
        }
        scope.launch {
            var lastSerial = dependencies.audioPlayer.state.value.playbackNoticeSerial
            dependencies.audioPlayer.state.collect { state ->
                if (state.playbackNoticeSerial == lastSerial) return@collect
                lastSerial = state.playbackNoticeSerial
                val notice = state.playbackNoticeMessage ?: return@collect
                mutableMessage.value = notice
                mutablePlaybackSnackbar.value = notice
            }
        }
    }

    private fun surfaceCastMessages() {
        scope.launch {
            dependencies.castController.state
                .map { it.message }
                .distinctUntilChanged()
                .collect { notice ->
                    val message = notice?.takeIf { it.isNotBlank() } ?: return@collect
                    if (message == "Chromecast requires Chrome with Cast support.") return@collect
                    if (message.startsWith("Sending ") && message.endsWith(" to Chromecast...")) return@collect
                    mutableMessage.value = message
                    mutablePlaybackSnackbar.value = message
                }
        }
    }

    fun dismissPlaybackSnackbar() {
        mutablePlaybackSnackbar.value = null
    }

    private fun surfaceTransientNotice(notice: String) {
        mutableMessage.value = notice
        mutablePlaybackSnackbar.value = notice
    }

    /**
     * Each time the audio player transitions to a new track, record a play event
     * so the Library UI can surface "last played" timestamps per artist / album / song.
     * We watch [Track.id] rather than the [PlayerState] object so toggling pause /
     * seeking doesn't double-record the same play.
     */
    private fun recordPlaybackHistory() {
        scope.launch {
            var lastRecordedTrackId: String? = null
            dependencies.audioPlayer.state.collect { state ->
                val track = state.currentTrack ?: run {
                    lastRecordedTrackId = null
                    return@collect
                }
                if (track.id == lastRecordedTrackId) return@collect
                lastRecordedTrackId = track.id
                runCatching {
                    dependencies.playHistoryRepository.recordPlay(track, currentTimeMs())
                }
            }
        }
    }

    /**
     * When the OS exposes a system volume, the slider mirrors it: the per-player output
     * stays at unity and hardware volume keys / rockers propagate into PlayerState.volume
     * so the UI updates live. On platforms without system volume the slider keeps
     * controlling the per-player output volume directly.
     */
    private fun bindSystemVolume() {
        val controller = dependencies.systemVolume
        controller.start(scope)
        if (controller.controlsPlayerOutput) {
            dependencies.audioPlayer.setUnityOutputVolume()
            dependencies.audioPlayer.updateReportedVolume(controller.volume.value)
            scope.launch {
                controller.volume.collect { v ->
                    dependencies.audioPlayer.updateReportedVolume(v)
                }
            }
        } else if (controller.isSupported) {
            dependencies.audioPlayer.setSystemVolumeScale(controller.volume.value)
            scope.launch {
                controller.volume.collect { scale ->
                    dependencies.audioPlayer.setSystemVolumeScale(scale)
                }
            }
        }
    }

    /**
     * If the saved session (or local folders) implies a browse flow, notify the root coordinator.
     * Covers startup races and missed navigation after async restore.
     */
    fun reconcileBrowseScreenIfNeeded() {
        val target = restoredBrowseRequest()
        if (target != AppNavigationRequest.SignIn) {
            requestNavigation(target)
        }
    }

    /** Destination implied by a restored remote session only (not local folders). */
    private fun restoredBrowseRequest(sessionSnapshot: PlexSession? = session.value): AppNavigationRequest {
        return when {
            sessionSnapshot?.selectedLibrary != null -> AppNavigationRequest.Home
            sessionSnapshot?.selectedServer != null -> AppNavigationRequest.LibraryPicker
            sessionSnapshot?.token?.isNotBlank() == true -> AppNavigationRequest.ServerPicker
            else -> AppNavigationRequest.SignIn
        }
    }

    fun initialNavigationRequest(): AppNavigationRequest = defaultBrowseRequest()

    private fun defaultBrowseRequest(sessionSnapshot: PlexSession? = session.value): AppNavigationRequest {
        return when {
            sessionSnapshot?.selectedLibrary != null -> AppNavigationRequest.Home
            sessionSnapshot?.selectedServer != null -> AppNavigationRequest.LibraryPicker
            sessionSnapshot?.token?.isNotBlank() == true -> AppNavigationRequest.ServerPicker
            mediaSources.value.localFolders.any { it.enabled } -> AppNavigationRequest.Home
            else -> AppNavigationRequest.SignIn
        }
    }

    private fun requestNavigation(request: AppNavigationRequest) {
        mutableNavigationRequests.tryEmit(request)
    }

    private fun CatalogSnapshot.hasBrowseableContent(): Boolean =
        artists.isNotEmpty() ||
            albums.isNotEmpty() ||
            playlists.isNotEmpty() ||
            tracksByParent.values.any { it.isNotEmpty() }

    fun startPlexSignIn() = scope.launchBusy {
        val newPin = dependencies.sessionRepository.createPin()
        mutablePin.value = newPin
        openExternalUrl(newPin.authUrl)
        mutableMessage.value = "Plex opened in your browser. Approve code ${newPin.code}, then finish sign-in."
    }

    fun finishPlexSignIn() = scope.launch {
        val currentPin = mutablePin.value ?: return@launch
        mutableBusy.value = true
        mutableMessage.value = "Signing in with Plex…"
        val servers = runCatching {
            dependencies.sessionRepository.completePinAndListServers(currentPin)
        }.getOrNull()
        mutableBusy.value = false
        if (servers == null) {
            mutableMessage.value = "That Plex code is not approved yet."
            return@launch
        }
        requestNavigation(AppNavigationRequest.ServerPicker)
        mutableServers.value = servers
        mutableLibraries.value = emptyList()
        mutableLibrariesLoading.value = false
        mutableMessage.value = "Signed in. Pick the Plex server that hosts your music."
    }

    fun signInJellyfin(serverUrl: String, username: String, password: String) = scope.launch {
        mutableAuthInProgress.value = true
        try {
            mutableMessage.value = "Signing in with Jellyfin…"
            val server = runCatching {
                dependencies.sessionRepository.signInJellyfin(serverUrl, username, password)
            }.onFailure { error ->
                mutableMessage.value = error.message ?: "Couldn't sign in to Jellyfin."
            }.getOrNull() ?: return@launch
            mutableServers.value = listOf(server)
            mutableLibraries.value = emptyList()
            mutableLibrariesLoading.value = true
            requestNavigation(AppNavigationRequest.LibraryPicker)
            runCatching {
                mutableLibraries.value = dependencies.sessionRepository.libraries(server)
            }.onFailure { error ->
                mutableMessage.value = error.message ?: "Couldn't load Jellyfin libraries."
            }
            mutableLibrariesLoading.value = false
            mutableMessage.value = "Signed in. Pick the Jellyfin music library to browse."
        } finally {
            mutableAuthInProgress.value = false
        }
    }

    fun signInProvider(
        type: MediaProviderType,
        serverUrl: String,
        username: String,
        password: String,
        syncMode: JellyfinSyncMode? = null,
    ) = scope.launch {
        mutableAuthInProgress.value = true
        try {
            mutableMessage.value = "Signing in with ${type.displayName}…"
            val server = runCatching {
                dependencies.sessionRepository.signInProvider(type, serverUrl, username, password)
            }.onFailure { error ->
                mutableMessage.value = error.message ?: "Couldn't sign in to ${type.displayName}."
            }.getOrNull() ?: return@launch
            mutableServers.value = listOf(server)
            mutableLibraries.value = emptyList()
            if (type.skipsLibraryPicker()) {
                mutableLibrariesLoading.value = false
                runCatching {
                    dependencies.sessionRepository.selectLibrary(type.defaultLibrarySelection(), syncMode ?: JellyfinSyncMode.Quick)
                    requestNavigation(AppNavigationRequest.Home)
                    mutableMessage.value = if ((syncMode ?: JellyfinSyncMode.Quick) == JellyfinSyncMode.Full) {
                        "Starting full ${type.displayName} sync…"
                    } else {
                        "Loading ${type.displayName}…"
                    }
                    refreshCatalogSuspended(catalogMessage = "${type.displayName} ready.")
                }.onFailure { error ->
                    mutableMessage.value = error.message ?: "Couldn't load ${type.displayName}."
                }
                return@launch
            }
            mutableLibrariesLoading.value = true
            requestNavigation(AppNavigationRequest.LibraryPicker)
            val libraries = runCatching {
                dependencies.sessionRepository.libraries(server)
            }.onFailure { error ->
                mutableMessage.value = error.message ?: "Couldn't load ${type.displayName} libraries."
            }.getOrNull().orEmpty()
            mutableLibraries.value = libraries
            mutableLibrariesLoading.value = false
            if (type.autoSelectSingleLibrary() && libraries.size == 1) {
                runCatching {
                    dependencies.sessionRepository.selectLibrary(libraries.single())
                    requestNavigation(AppNavigationRequest.Home)
                    mutableMessage.value = "Loading ${type.displayName}…"
                    refreshCatalogSuspended(catalogMessage = "${type.displayName} ready.")
                }.onFailure { error ->
                    mutableMessage.value = error.message ?: "Couldn't load ${type.displayName}."
                }
                return@launch
            }
            mutableMessage.value = when (type) {
                MediaProviderType.Navidrome -> "Signed in. Pick the Subsonic music folder to browse."
                MediaProviderType.MusicAssistant -> "Signed in. Pick the Music Assistant source to browse."
                else -> "Signed in. Pick the ${type.displayName} music library to browse."
            }
        } finally {
            mutableAuthInProgress.value = false
        }
    }

    private fun MediaProviderType.autoSelectSingleLibrary(): Boolean =
        this == MediaProviderType.MusicAssistant

    private fun MediaProviderType.skipsLibraryPicker(): Boolean =
        this == MediaProviderType.Navidrome

    private fun MediaProviderType.defaultLibrarySelection(): MusicLibrary =
        when (this) {
            MediaProviderType.Navidrome -> MusicLibrary("all", "All Music")
            MediaProviderType.MusicAssistant -> MusicLibrary("music-assistant", "Music Assistant Library")
            else -> MusicLibrary("music", "Music")
        }

    fun discoverJellyfinServers() = scope.launch {
        mutableJellyfinDiscoveryLoading.value = true
        mutableMessage.value = "Searching your local network for Jellyfin servers…"
        val found = runCatching { discoverJellyfinServersOnNetwork() }
            .onFailure { error ->
                mutableMessage.value = error.message ?: "Couldn't search for Jellyfin servers."
            }
            .getOrDefault(emptyList())
        mutableJellyfinServers.value = found
        mutableJellyfinDiscoveryLoading.value = false
        mutableMessage.value = if (found.isEmpty()) {
            "No Jellyfin servers found on this network. Enter the server URL manually to continue."
        } else {
            "Found ${found.size} Jellyfin server${if (found.size == 1) "" else "s"} nearby."
        }
    }

    fun startJellyfinQuickConnect(serverUrl: String) = scope.launch {
        if (serverUrl.isBlank()) {
            mutableMessage.value = "Enter or choose a Jellyfin server URL first."
            return@launch
        }
        mutableMessage.value = "Starting Jellyfin Quick Connect…"
        val quickConnect = runCatching {
            dependencies.sessionRepository.startJellyfinQuickConnect(serverUrl)
        }.onFailure { error ->
            mutableMessage.value = error.message ?: "Couldn't start Jellyfin Quick Connect."
        }.getOrNull() ?: return@launch
        mutableJellyfinQuickConnect.value = quickConnect
        quickConnect.ServerUrl?.let { openExternalUrl(it) }
        mutableMessage.value = "Approve Jellyfin Quick Connect code ${quickConnect.Code}, then finish sign-in."
    }

    fun finishJellyfinQuickConnect() = scope.launch {
        val quickConnect = mutableJellyfinQuickConnect.value ?: run {
            mutableMessage.value = "Start Jellyfin Quick Connect first."
            return@launch
        }
        val serverUrl = quickConnect.ServerUrl ?: run {
            mutableMessage.value = "Jellyfin server URL is missing. Start Quick Connect again."
            return@launch
        }
        mutableAuthInProgress.value = true
        try {
            mutableMessage.value = "Finishing Jellyfin Quick Connect…"
            val server = runCatching {
                dependencies.sessionRepository.completeJellyfinQuickConnect(serverUrl, quickConnect.Secret)
            }.onFailure { error ->
                mutableMessage.value = error.message ?: "That Jellyfin Quick Connect code is not approved yet."
            }.getOrNull() ?: return@launch
            mutableJellyfinQuickConnect.value = null
            mutableServers.value = listOf(server)
            mutableLibraries.value = emptyList()
            mutableLibrariesLoading.value = true
            requestNavigation(AppNavigationRequest.LibraryPicker)
            runCatching {
                mutableLibraries.value = dependencies.sessionRepository.libraries(server)
            }.onFailure { error ->
                mutableMessage.value = error.message ?: "Couldn't load Jellyfin libraries."
            }
            mutableLibrariesLoading.value = false
            mutableMessage.value = "Signed in. Pick the Jellyfin music library to browse."
        } finally {
            mutableAuthInProgress.value = false
        }
    }

    fun loadServers() = scope.launch {
        mutableLibrariesLoading.value = false
        requestNavigation(AppNavigationRequest.ServerPicker)
        refreshServers()
    }

    fun returnToServerPicker() = scope.launch {
        mutableLibrariesLoading.value = false
        requestNavigation(AppNavigationRequest.ServerPicker)
        refreshServers()
    }

    private fun refreshServers() = scope.launch {
        if (session.value?.token.isNullOrBlank()) {
            mutableServers.value = emptyList()
            return@launch
        }
        mutableServersLoading.value = true
        runCatching {
            mutableServers.value = dependencies.sessionRepository.servers()
        }.onFailure { mutableMessage.value = it.message ?: "Couldn't load ${session.value.providerLabel()} servers." }
        mutableServersLoading.value = false
    }

    fun selectServer(server: PlexServer) = scope.launch {
        cancelRemotePlayHistorySync()
        mutableLibraries.value = emptyList()
        mutableLibrariesLoading.value = true
        val resolved = runCatching {
            dependencies.sessionRepository.selectServer(server, refreshConnections = false)
        }.onFailure {
            mutableLibrariesLoading.value = false
            mutableMessage.value = it.message ?: "Couldn't select ${session.value.providerLabel()} server."
        }.getOrNull() ?: return@launch
        requestNavigation(AppNavigationRequest.LibraryPicker)
        runCatching {
            mutableLibraries.value = dependencies.sessionRepository.libraries(resolved)
        }.onFailure {
            mutableMessage.value = it.message ?: "Couldn't load ${session.value.providerLabel()} libraries."
        }
        mutableLibrariesLoading.value = false
    }

    fun selectLibrary(library: MusicLibrary, jellyfinSyncMode: JellyfinSyncMode? = null) = scope.launch {
        cancelRemotePlayHistorySync()
        catalogRefreshJob?.cancel()
        dependencies.catalogRepository.clearActiveSyncProgress()
        if (session.value == null) {
            mutableMessage.value = "Session expired. Sign in again."
            return@launch
        }
        runCatching {
            dependencies.sessionRepository.selectLibrary(library, jellyfinSyncMode)
            requestNavigation(AppNavigationRequest.Home)
            mutableMessage.value = if (session.value.isJellyfin() && (jellyfinSyncMode ?: session.value?.jellyfinSyncMode) == JellyfinSyncMode.Full) {
                "Starting full Jellyfin sync…"
            } else {
                "Loading library…"
            }
        }.onFailure { mutableMessage.value = it.message ?: "Something went sideways." }

        launch(Dispatchers.Default) {
            runCatching { dependencies.catalogRepository.restoreCachedCatalog() }
        }
        runCatching {
            refreshCatalogSuspended(catalogMessage = "Library ready.")
        }.onFailure { error ->
            if (error is CancellationException) {
                dependencies.catalogRepository.clearActiveSyncProgress()
            } else {
                mutableMessage.value = error.message ?: "Something went sideways."
            }
        }
    }

    /**
     * Suspends until the catalog is rebuilt from the current session and media sources.
     * Prefer this from [LaunchedEffect] so in-flight work is cancelled when dependencies change,
     * avoiding stale empty Plex refreshes overwriting a newer library load.
     */
    suspend fun refreshCatalogSuspended(catalogMessage: String? = "Library refreshed.", backgroundIfCached: Boolean = false) {
        val currentJob = currentCoroutineContext()[Job]
        catalogRefreshJob?.takeIf { it != currentJob }?.cancel()
        catalogRefreshJob = currentJob
        try {
            withContext(Dispatchers.Default) {
                dependencies.catalogRepository.refreshAggregated(session.value, backgroundIfCached = backgroundIfCached)
                ensureLikedSongsPlaylistIfPossible()
            }
            warmPlaylistTracksInBackground()
            if (session.value.isPlex() || session.value.isEmbyFamily() || session.value.isNavidrome()) {
                syncRemotePlayHistory(showMessage = false, recentOnly = backgroundIfCached)
            } else {
                syncRemotePlayHistoryInBackground()
            }
            cacheDownloadedArtworkInBackground()
            if (catalogMessage != null) mutableMessage.value = catalogMessage
        } catch (error: CancellationException) {
            PhoebeLog.d("AppState") { "catalog refresh cancelled" }
            dependencies.catalogRepository.clearActiveSyncProgress()
            throw error
        } catch (error: Throwable) {
            mutableMessage.value = error.message ?: "Something went sideways."
        } finally {
            if (catalogRefreshJob == currentJob) {
                catalogRefreshJob = null
            }
        }
    }

    private fun warmPlaylistTracksInBackground() {
        if (!session.value.supportsRemotePlaylists()) return
        scope.launch {
            runCatching {
                dependencies.catalogRepository.warmPlaylistTracks(session.value)
            }.onFailure { error ->
                PhoebeLog.d("AppState") { "playlist warm failed: ${error.message}" }
            }
        }
    }

    private fun cacheDownloadedArtworkInBackground() {
        if (activeDownloadJobs.isNotEmpty()) return
        downloadedArtworkCacheJob?.cancel()
        downloadedArtworkCacheJob = scope.launch {
            runCatching {
                if (activeDownloadJobs.isNotEmpty()) return@runCatching 0
                dependencies.catalogRepository.cacheDownloadedArtwork()
            }.onSuccess { cached ->
                if (cached > 0) {
                    PhoebeLog.d("AppState") { "cached artwork for $cached downloaded tracks" }
                }
            }.onFailure { error ->
                PhoebeLog.d("AppState") { "downloaded artwork cache failed: ${error.message}" }
            }
        }
    }

    private suspend fun ensureLikedSongsPlaylistIfPossible(): Playlist? {
        if (!session.value.supportsRemotePlaylists()) return null
        return runCatching {
            dependencies.catalogRepository.ensureLocalLikedSongsPlaylist(session.value)
        }.onFailure { error ->
            PhoebeLog.d("AppState") { "Liked Songs setup failed: ${error.message}" }
        }.getOrNull()
    }

    fun openLikedSongsPlaylist() = scope.launch {
        val playlist = ensureLikedSongsPlaylistIfPossible()
        if (playlist == null) {
            mutableMessage.value = "Couldn't create Liked Songs yet."
            return@launch
        }
        requestNavigation(AppNavigationRequest.PlaylistDetail(playlist.id))
        syncLikedSongsInBackground()
    }

    fun refreshCatalog() = scope.launch {
        refreshCatalogSuspended()
    }

    fun loadJellyfinLibraryPage(kind: JellyfinLibraryPageKind, pageIndex: Int) = scope.launch {
        runCatching {
            dependencies.catalogRepository.loadJellyfinLibraryPage(session.value, kind, pageIndex)
        }.onFailure { error ->
            mutableMessage.value = error.message ?: "Couldn't load that Jellyfin page."
        }
    }

    fun refreshPlexPlayHistory() = startRemotePlayHistorySync(showMessage = true)

    fun refreshPlayHistory() = startRemotePlayHistorySync(showMessage = true)

    private fun syncRemotePlayHistoryInBackground() = startRemotePlayHistorySync(showMessage = false)

    private fun startRemotePlayHistorySync(showMessage: Boolean) {
        val currentSession = session.value
        if (!currentSession.isPlex() && !currentSession.isEmbyFamily() && !currentSession.isNavidrome()) {
            PhoebeLog.d("AppState") {
                "play history sync skipped: provider=${currentSession?.providerType?.name ?: "none"}"
            }
            if (showMessage) mutableMessage.value = "${currentSession.providerLabel()} play history sync is handled from playback progress."
            return
        }
        if (showMessage) mutableMessage.value = "Syncing ${currentSession.providerLabel()} play history..."
        PhoebeLog.d("AppState") {
            "play history sync requested provider=${currentSession.providerLabel()} " +
                "showMessage=$showMessage hasServer=${currentSession?.selectedServer != null} " +
                "hasLibrary=${currentSession?.selectedLibrary != null}"
        }
        playHistorySyncJob?.cancel()
        playHistorySyncJob = scope.launch {
            syncRemotePlayHistory(showMessage = showMessage, recentOnly = true)
        }
    }

    private fun cancelRemotePlayHistorySync() {
        playHistorySyncJob?.cancel()
        playHistorySyncJob = null
    }

    private fun cancelCatalogRefresh() {
        catalogRefreshJob?.cancel()
        catalogRefreshJob = null
        dependencies.catalogRepository.clearActiveSyncProgress()
    }

    private suspend fun syncRemotePlayHistory(showMessage: Boolean, recentOnly: Boolean): Any? {
        return runCatching {
            val currentSession = session.value
            PhoebeLog.d("AppState") {
                "play history sync started provider=${currentSession.providerLabel()} " +
                    "recentOnly=$recentOnly catalogTracks=${catalog.value.tracksByParent.values.sumOf { it.size }}"
            }
            val result = if (currentSession.isPlex()) {
                if (!recentOnly) {
                    runCatching {
                        dependencies.catalogRepository.warmPlexHistoryTracks(currentSession)
                    }.onFailure { error ->
                        PhoebeLog.d("AppState") { "Plex history track warm failed: ${error.message}" }
                    }
                }
                val syncResult = if (recentOnly) {
                    dependencies.plexPlayHistorySyncer.syncRecent(currentSession, catalog.value)
                } else {
                    dependencies.plexPlayHistorySyncer.sync(currentSession, catalog.value)
                }
                if (!recentOnly) {
                    val refreshTrackIds = buildList {
                        addAll(dependencies.playHistoryRepository.queryTopMostPlayed(30).map { it.trackId })
                        addAll(dependencies.playHistoryRepository.queryTopRecentlyPlayed(30).map { it.trackId })
                    }.distinct()
                    dependencies.plexPlayHistorySyncer.refreshViewCountsForTrackIds(
                        currentSession,
                        refreshTrackIds,
                    )
                }
                syncResult
            } else if (currentSession.isNavidrome()) {
                dependencies.navidromePlayHistorySyncer.sync(currentSession, catalog.value)
            } else {
                dependencies.jellyfinPlayHistorySyncer.sync(currentSession, catalog.value)
            }
            warmTracksForMostPlayed()
            result
        }.onSuccess { result ->
            if (showMessage) {
                mutableMessage.value = when (result) {
                    PlexPlayHistorySyncResult.Skipped -> "Plex play history is not available yet."
                    is PlexPlayHistorySyncResult.Synced -> {
                        if (result.imported > 0) "Synced ${result.imported} Plex plays."
                        else "Plex play history is up to date."
                    }
                    JellyfinPlayHistorySyncResult.Skipped -> "${session.value.providerLabel()} play history is not available yet."
                    is JellyfinPlayHistorySyncResult.Synced -> {
                        val provider = session.value.providerLabel()
                        if (result.imported > 0) "Synced ${result.imported} $provider plays."
                        else "$provider play history is up to date."
                    }
                    NavidromePlayHistorySyncResult.Skipped -> "Subsonic play history is not available yet."
                    is NavidromePlayHistorySyncResult.Synced -> {
                        if (result.imported > 0) "Synced ${result.imported} Subsonic plays."
                        else "Subsonic play history is up to date."
                    }
                    else -> "Play history is up to date."
                }
            }
        }.onFailure { error ->
            if (error is CancellationException) throw error
            PhoebeLog.d("AppState") { "play history sync failed: ${error.message}" }
            if (showMessage) mutableMessage.value = error.message ?: "Couldn't sync play history."
        }.getOrNull()
    }

    fun setTab(tab: LibraryTab) {
        mutableTab.value = tab
        requestNavigation(defaultBrowseRequest())
    }

    fun preloadArtistDetail(artist: Artist) {
        scope.launch {
            runCatching {
                dependencies.catalogRepository.ensurePopularTracksForArtist(session.value, artist)
            }.onFailure {
                PhoebeLog.d("AppState") { "artist popular tracks preload failed for '${artist.title}': ${it.message}" }
            }
            runCatching {
                dependencies.catalogRepository.ensureSimilarArtistsForArtist(session.value, artist)
            }.onFailure {
                PhoebeLog.d("AppState") { "artist similar preload failed for '${artist.title}': ${it.message}" }
            }
            runCatching {
                dependencies.catalogRepository.ensureTracksForArtistAlbums(session.value, artist.title)
            }
        }
    }

    fun preloadAlbumDetail(album: Album) {
        scope.launch {
            runCatching {
                dependencies.catalogRepository.tracksForAlbum(session.value, album)
            }.onFailure {
                mutableMessage.value = it.message ?: "Couldn't load album tracks."
            }
        }
    }

    fun preloadPlaylistDetail(playlist: Playlist) {
        scope.launch {
            runCatching {
                dependencies.catalogRepository.tracksForPlaylist(session.value, playlist)
            }.onFailure {
                mutableMessage.value = it.message ?: "Couldn't load playlist tracks."
            }
        }
    }

    fun preloadCollections(entry: com.phoebe.app.domain.CollectionEntry) {
        scope.launch {
            if (!session.value.supportsCollectionEntry(entry)) return@launch
            runCatching {
                dependencies.catalogRepository.ensureCollectionValues(session.value, entry)
            }.onFailure {
                mutableMessage.value = it.message ?: "Couldn't load collections."
            }
        }
    }

    fun preloadCollectionItems(entry: com.phoebe.app.domain.CollectionEntry, value: String) {
        scope.launch {
            if (!session.value.supportsCollectionEntry(entry)) return@launch
            runCatching {
                dependencies.catalogRepository.ensureCollectionItems(session.value, entry, value)
            }.onFailure {
                mutableMessage.value = it.message ?: "Couldn't load collection."
            }
        }
    }

    fun prefetchHomeArtistStats(artist: Artist) {
        if (!session.value.canUsePlexBackgroundFetches()) return
        if (!prefetchedArtistIds.add(artist.id)) return
        scope.launch {
            runCatching {
                dependencies.catalogRepository.ensureTracksForArtistAlbums(session.value, artist.title)
            }
        }
    }

    fun prefetchHomeAlbumStats(album: Album) {
        if (!session.value.canUsePlexBackgroundFetches()) return
        if (!prefetchedAlbumIds.add(album.id)) return
        scope.launch {
            runCatching {
                dependencies.catalogRepository.tracksForAlbum(session.value, album)
            }
        }
    }

    fun warmRecentAlbumTracks(cutoffMs: Long, maxAlbums: Int = 10) {
        if (!session.value.canUsePlexBackgroundFetches()) return
        val snapshot = catalog.value
        val albumIds = snapshot.albums
            .asSequence()
            .filter { (it.dateAddedMs ?: Long.MIN_VALUE) >= cutoffMs }
            .filter { snapshot.tracksByParent[it.id].isNullOrEmpty() }
            .sortedByDescending { it.dateAddedMs ?: 0L }
            .take(maxAlbums)
            .map { it.id }
            .toList()
        if (albumIds.isEmpty()) return
        val signature = albumIds.joinToString("|")
        if (signature == recentAlbumWarmSignature) return
        recentAlbumWarmSignature = signature
        scope.launch {
            dependencies.catalogRepository.warmRecentAlbumTracks(session.value, cutoffMs, maxAlbums)
        }
    }

    fun warmPlayedAlbumTracks(albumTitles: List<String>, maxAlbums: Int = 10) {
        if (!session.value.canUsePlexBackgroundFetches()) return
        val normalizedTitles = albumTitles
            .asSequence()
            .map { it.trim() }
            .filter { it.isNotBlank() }
            .distinctBy { it.lowercase() }
            .take(maxAlbums)
            .toList()
        if (normalizedTitles.isEmpty()) return
        val signature = normalizedTitles.joinToString("|") { it.lowercase() }
        if (signature == playedAlbumWarmSignature) return
        playedAlbumWarmSignature = signature
        scope.launch {
            dependencies.catalogRepository.warmAlbumTracksByTitle(session.value, normalizedTitles, maxAlbums)
        }
    }

    suspend fun resolveTracksByIds(trackIds: Collection<String>): Map<String, Track> =
        dependencies.catalogRepository.resolveTracksByIds(trackIds)

    fun warmTracksForMostPlayed(maxTracks: Int = 20) {
        if (!session.value.canUsePlexBackgroundFetches()) return
        val entries = buildList {
            addAll(topMostPlayed.value.take(maxTracks))
            topRecentlyPlayed.value.take(maxTracks).forEach { recent ->
                if (none { it.trackId == recent.trackId }) {
                    add(
                        com.phoebe.app.domain.MostPlayedEntry(
                            trackId = recent.trackId,
                            playCount = playCountsByTrack.value[recent.trackId] ?: 0L,
                            lastPlayedMs = recent.lastPlayedMs,
                            artist = recent.artist,
                            album = recent.album,
                        ),
                    )
                }
            }
        }
        if (entries.isEmpty()) return
        val signature = entries.joinToString("|") { "${it.trackId}:${it.playCount}:${it.lastPlayedMs}" }
        if (signature == mostPlayedWarmSignature) return
        mostPlayedWarmSignature = signature
        scope.launch {
            dependencies.catalogRepository.warmTracksForMostPlayed(session.value, entries, maxTracks)
        }
    }

    suspend fun ensurePersonalMixTracks(limit: Int) {
        if (!session.value.canUsePlexBackgroundFetches()) return
        runCatching {
            dependencies.catalogRepository.warmTracksForPersonalMix(session.value, limit)
        }.onFailure { error ->
            PhoebeLog.d("AppState") { "personal mix track warm failed: ${error.message}" }
        }
    }

    fun playDecadeMix(decade: Int) = scope.launch {
        mutableDecadeMixNotice.value = "Searching the ${decade}s…"
        val firstTracks = runCatching {
            dependencies.catalogRepository.firstTracksForDecade(session.value, decade).shuffled()
        }.getOrElse { error ->
            val notice = error.message ?: "Couldn't search the ${decade}s."
            mutableDecadeMixNotice.value = notice
            mutableMessage.value = notice
            return@launch
        }
        if (firstTracks.isEmpty()) {
            val notice = "No songs found for the ${decade}s."
            mutableDecadeMixNotice.value = notice
            mutableMessage.value = notice
            return@launch
        }
        mutableDecadeMixNotice.value = null
        playTracks(firstTracks, 0)
        requestNavigation(AppNavigationRequest.Player)
        mutableMessage.value = "Playing ${firstTracks.size} songs from the ${decade}s."
        scope.launch {
            val initialIds = firstTracks.map { it.id }.toSet()
            val moreTracks = runCatching {
                dependencies.catalogRepository.tracksForDecade(session.value, decade)
                    .filterNot { it.id in initialIds }
                    .shuffled()
            }.getOrDefault(emptyList())
            if (moreTracks.isNotEmpty()) {
                appendToQueue(moreTracks)
                mutableMessage.value = "Added ${moreTracks.size} more songs from the ${decade}s."
            }
        }
    }

    fun refreshRadioStations() = scope.launch {
        val currentSession = session.value
        val selectedLibrary = currentSession?.selectedLibrary
        if (!currentSession.isPlex() || currentSession?.selectedServer == null || selectedLibrary == null || currentSession.serverAuthToken() == null) {
            mutableRadioStations.value = emptyList()
            return@launch
        }
        mutableRadioStations.value = defaultPlexRadioStations(selectedLibrary)
        val stations = runCatching {
            dependencies.catalogRepository.plexRadioStations(currentSession)
        }.onFailure { error ->
            PhoebeLog.d("AppState") { "Plex radio station load failed: ${error.message}" }
        }.getOrDefault(defaultPlexRadioStations(selectedLibrary))
        mutableRadioStations.value = stations
    }

    fun playRadioStation(station: PlexRadioStation) = scope.launch {
        val radioId = station.key
        if (radioId in mutableRadioStartingIds.value) return@launch
        mutableRadioStartingIds.update { it + radioId }
        try {
            val tracks = runCatching {
                dependencies.catalogRepository.playRadioStation(session.value, station)
            }.getOrElse { error ->
                surfaceTransientNotice(error.message ?: "Couldn't start ${station.title}.")
                return@launch
            }
            if (tracks.isEmpty()) {
                surfaceTransientNotice("No songs found for ${station.title}.")
                return@launch
            }
            playTracks(tracks, 0)
            requestNavigation(AppNavigationRequest.Player)
        } finally {
            mutableRadioStartingIds.update { it - radioId }
        }
    }

    fun playArtistRadio(artist: Artist) = scope.launch {
        if (!artist.id.startsWith("plex:") && !artist.id.startsWith("jellyfin:")) {
            mutableMessage.value = "Artist Radio is available for streaming-library artists."
            return@launch
        }
        if (mutableArtistRadioAvailability.value[artist.id] == ArtistRadioAvailability.Unavailable) {
            mutableMessage.value = "Artist Radio isn't available for ${artist.title}."
            return@launch
        }
        if (artist.id in mutableRadioStartingIds.value) return@launch
        mutableRadioStartingIds.update { it + artist.id }
        try {
            mutableMessage.value = "Starting ${artist.title} Radio..."
            val tracks = runCatching {
                dependencies.catalogRepository.playArtistRadio(session.value, artist)
            }.getOrElse { error ->
                val notice = error.message ?: "Couldn't start radio for ${artist.title}."
                mutableMessage.value = notice
                return@launch
            }
            if (tracks.isEmpty()) {
                mutableArtistRadioAvailability.update { it + (artist.id to ArtistRadioAvailability.Unavailable) }
                mutableMessage.value = "Artist Radio isn't available for ${artist.title}."
                return@launch
            }
            mutableArtistRadioAvailability.update { it + (artist.id to ArtistRadioAvailability.Available) }
            playTracks(tracks, 0)
            requestNavigation(AppNavigationRequest.Player)
            mutableMessage.value = "Playing ${artist.title} Radio."
        } finally {
            mutableRadioStartingIds.update { it - artist.id }
        }
    }

    fun probeArtistRadio(artist: Artist) = scope.launch {
        if (!artist.id.startsWith("plex:") && !artist.id.startsWith("jellyfin:")) {
            mutableArtistRadioAvailability.update { it + (artist.id to ArtistRadioAvailability.Unavailable) }
            return@launch
        }
        if (mutableArtistRadioAvailability.value[artist.id] == ArtistRadioAvailability.Available) return@launch
        val available = runCatching {
            dependencies.catalogRepository.artistRadioStation(session.value, artist) != null
        }.onFailure { error ->
            PhoebeLog.d("AppState") { "Artist radio probe failed for '${artist.title}': ${error.message}" }
        }.getOrDefault(false)
        mutableArtistRadioAvailability.update {
            it + (artist.id to if (available) ArtistRadioAvailability.Available else ArtistRadioAvailability.Unavailable)
        }
    }

    fun playPlaylistShuffled(playlist: Playlist) = scope.launch {
        val tracks = runCatching {
            dependencies.catalogRepository.tracksForPlaylist(session.value, playlist)
        }.getOrElse { error ->
            mutableMessage.value = error.message ?: "Couldn't load ${playlist.title}."
            return@launch
        }
        if (tracks.isEmpty()) {
            mutableMessage.value = "${playlist.title} has no songs to shuffle."
            return@launch
        }
        playTracks(tracks.shuffled(), 0)
        dependencies.audioPlayer.setShuffle(true)
        requestNavigation(AppNavigationRequest.Player)
        mutableMessage.value = "Shuffling ${playlist.title}."
    }

    fun clearDecadeMixNotice() {
        mutableDecadeMixNotice.value = null
    }

    fun playTracks(
        tracks: List<Track>,
        index: Int = 0,
        collectionMixSeed: CollectionMixSeed? = null,
    ) {
        collectionMixGeneration++
        val playbackTracks = tracks.withFreshPlaybackUrls(session.value)
        val track = playbackTracks.getOrNull(index)
        if (dependencies.castController.state.value.isConnected) {
            mutableMusicAssistantRemotePlayback.value = null
            val support = dependencies.castController.canLoadQueue(playbackTracks)
            if (!support.isSupported) {
                mutableMessage.value = support.message ?: "This queue can't be cast to Chromecast."
                return
            }
            dependencies.castController.loadQueue(playbackTracks, index)
            return
        }
        if (session.value.isMusicAssistant() && track?.localUri.isNullOrBlank() && track?.streamUrl.isNullOrBlank()) {
            val musicAssistantTrack = track ?: return
            scope.launch {
                mutableMessage.value = "Starting ${musicAssistantTrack.title} in Music Assistant..."
                runCatching {
                    dependencies.providerRegistry.adapterFor(session.value)?.playRemote(session.value!!, playbackTracks, index)
                }.onSuccess { target ->
                    if (target.isNullOrBlank()) {
                        mutableMessage.value = "Couldn't find a Music Assistant player for ${musicAssistantTrack.title}."
                        return@onSuccess
                    }
                    mutableMusicAssistantRemotePlayback.value = MusicAssistantRemotePlayback(playbackTracks, index, target)
                    mutableMessage.value = "Playing ${musicAssistantTrack.title} on Music Assistant: $target."
                }.onFailure { error ->
                    mutableMessage.value = error.message ?: "Couldn't start Music Assistant playback."
                }
            }
            return
        }
        if (track != null && !track.hasPlayableSource()) {
            surfaceTransientNotice("Couldn't find a playable stream for ${track.title}. Try refreshing the library.")
            return
        }
        mutableMusicAssistantRemotePlayback.value = null
        dependencies.audioPlayer.play(playbackTracks, index)
        collectionMixSeed?.toCollectionMix()?.let { mix ->
            scheduleCollectionMix(mix, playbackTracks.map { it.id }.toSet())
        }
    }

    private fun scheduleCollectionMix(mix: CollectionMix, queuedTrackIds: Set<String>) {
        if (!session.value.canUsePlexBackgroundFetches()) return
        val mixGeneration = collectionMixGeneration
        scope.launch {
            appendCollectionMix(mix, queuedTrackIds, mixGeneration)
        }
    }

    private fun CollectionMixSeed.toCollectionMix(): CollectionMix? {
        if (facet != CollectionFacet.Mood && facet != CollectionFacet.Style) return null
        val value = value.trim()
        if (value.isBlank()) return null
        return CollectionMix(facet, value)
    }

    private suspend fun appendCollectionMix(
        mix: CollectionMix,
        queuedTrackIds: Set<String>,
        mixGeneration: Int,
    ) {
        if (mixGeneration != collectionMixGeneration) return
        val excludeIds = queuedTrackIds.toMutableSet()
        val firstTracks = runCatching {
            dependencies.catalogRepository.firstTracksForCollectionFacet(session.value, mix.facet, mix.value)
                .filterNot { it.id in excludeIds }
                .shuffled()
        }.getOrDefault(emptyList())
        if (mixGeneration != collectionMixGeneration) return
        if (firstTracks.isNotEmpty()) {
            appendToQueue(firstTracks)
            excludeIds += firstTracks.map { it.id }
        }
        val moreTracks = runCatching {
            dependencies.catalogRepository.tracksForCollectionFacet(session.value, mix.facet, mix.value)
                .filterNot { it.id in excludeIds }
                .shuffled()
        }.getOrDefault(emptyList())
        if (mixGeneration != collectionMixGeneration) return
        if (moreTracks.isNotEmpty()) {
            appendToQueue(moreTracks)
        }
    }

    private data class CollectionMix(val facet: CollectionFacet, val value: String)

    fun togglePlayPause() {
        val remoteTarget = mutableMusicAssistantRemotePlayback.value?.target
        if (remoteTarget != null) {
            mutableMessage.value = "Music Assistant playback is running on $remoteTarget. Use Music Assistant for pause/resume."
        } else if (dependencies.castController.state.value.isPlaybackActive) {
            dependencies.castController.togglePlayPause()
        } else {
            dependencies.audioPlayer.togglePlayPause()
        }
    }

    /** Play / pause / toggle keys: no-op when no track is loaded. */
    fun mediaKeyTogglePlayPause() {
        if (player.value.currentTrack != null) {
            togglePlayPause()
        }
    }

    fun mediaKeyPlay() {
        val s = player.value
        if (s.currentTrack != null && !s.isPlaying) {
            togglePlayPause()
        }
    }

    fun mediaKeyPause() {
        if (player.value.isPlaying) {
            togglePlayPause()
        }
    }

    fun clearQueue() {
        if (mutableMusicAssistantRemotePlayback.value != null) {
            mutableMusicAssistantRemotePlayback.value = null
        } else if (dependencies.castController.state.value.isPlaybackActive) {
            dependencies.castController.disconnect()
        } else {
            dependencies.audioPlayer.clearQueue()
        }
    }

    private fun stopPlayback() {
        mutableMusicAssistantRemotePlayback.value = null
        dependencies.castController.disconnect()
        dependencies.audioPlayer.stopPlayback()
    }
    fun addToUpNext(track: Track) = dependencies.audioPlayer.addToUpNext(track)
    fun appendToQueue(tracks: List<Track>) = dependencies.audioPlayer.appendToQueue(tracks)
    fun moveUpNext(fromIndex: Int, toIndex: Int) = dependencies.audioPlayer.moveUpNext(fromIndex, toIndex)
    fun removeUpNext(index: Int) = dependencies.audioPlayer.removeUpNext(index)
    fun playUpNext(index: Int) {
        val current = player.value
        val target = current.currentIndex + 1 + index
        if (target in current.queue.indices) {
            playTracks(current.queue, target)
        }
    }
    fun next() {
        val remote = mutableMusicAssistantRemotePlayback.value
        if (remote != null) {
            playTracks(remote.tracks, (remote.index + 1).coerceIn(0, remote.tracks.lastIndex))
        } else if (dependencies.castController.state.value.isPlaybackActive) {
            dependencies.castController.next()
        } else {
            dependencies.audioPlayer.next()
        }
    }
    fun previous() {
        val remote = mutableMusicAssistantRemotePlayback.value
        if (remote != null) {
            playTracks(remote.tracks, (remote.index - 1).coerceIn(0, remote.tracks.lastIndex))
        } else if (dependencies.castController.state.value.isPlaybackActive) {
            dependencies.castController.previous()
        } else {
            dependencies.audioPlayer.previous()
        }
    }
    fun skipQueueBy(delta: Int) {
        if (delta == 0) return
        val current = player.value
        if (current.currentIndex < 0 || current.queue.isEmpty()) return
        val target = (current.currentIndex + delta).coerceIn(0, current.queue.lastIndex)
        if (target == current.currentIndex) return
        playTracks(current.queue, target)
    }
    fun seekTo(positionMs: Long) {
        if (dependencies.castController.state.value.isPlaybackActive) {
            dependencies.castController.seekTo(positionMs)
        } else {
            dependencies.audioPlayer.seekTo(positionMs)
        }
    }
    suspend fun loadLyrics(track: Track, forceRefresh: Boolean = false): LyricsLoadState =
        dependencies.lyricsRepository.lyricsFor(track, forceRefresh)

    fun toggleShuffle() = dependencies.audioPlayer.setShuffle(!player.value.shuffle)
    fun cycleRepeat() {
        val next = when (player.value.repeat) {
            RepeatMode.Off -> RepeatMode.One
            RepeatMode.One -> RepeatMode.All
            RepeatMode.All -> RepeatMode.Off
        }
        dependencies.audioPlayer.setRepeat(next)
    }
    fun setVolume(volume: Float) {
        if (dependencies.castController.state.value.isConnected && dependencies.castController.setVolume(volume)) {
            return
        }
        val controller = dependencies.systemVolume
        if (controller.controlsPlayerOutput) {
            controller.setVolume(volume)
        } else {
            dependencies.audioPlayer.setVolume(volume)
        }
    }

    fun showCastPicker() {
        if (dependencies.castController.state.value.isAvailable) {
            dependencies.castController.showDevicePicker()
        } else {
            mutableMessage.value = dependencies.castController.state.value.message
                ?: "Chromecast is available on Android, iOS, desktop, and Chrome web."
        }
    }

    fun setLibrarySortBy(sortBy: LibrarySortBy) = scope.launch {
        dependencies.libraryUiRepository.setSortBy(sortBy)
    }

    fun setLibrarySortAscending(ascending: Boolean) = scope.launch {
        dependencies.libraryUiRepository.setAscending(ascending)
    }

    fun setLibraryColumns(columns: LibraryColumnVisibility) {
        dependencies.libraryUiRepository.applyColumns(columns)
        scope.launch(Dispatchers.Default) {
            dependencies.libraryUiRepository.persistCurrentToDisk()
        }
    }

    fun setHomeSections(sections: List<HomeSection>) = scope.launch {
        dependencies.libraryUiRepository.setHomeSections(sections)
    }

    fun setPersonalMixPreferences(preferences: PersonalMixPreferences) = scope.launch {
        dependencies.libraryUiRepository.setPersonalMix(preferences)
    }

    fun setGridColumns(gridColumns: Int) = scope.launch {
        dependencies.libraryUiRepository.setGridColumns(gridColumns)
    }

    fun prependRecentSearch(item: RecentSearchItem) = scope.launch {
        dependencies.searchHistoryRepository.prepend(item)
    }

    fun removeRecentSearch(item: RecentSearchItem) = scope.launch {
        dependencies.searchHistoryRepository.remove(item)
    }

    fun clearRecentSearches() = scope.launch {
        dependencies.searchHistoryRepository.clear()
    }

    fun setCrossfadeSeconds(seconds: Int) = scope.launch {
        dependencies.appSettingsRepository.setCrossfadeSeconds(seconds)
    }

    fun setScanLibraryOnLaunch(enabled: Boolean) = scope.launch {
        dependencies.appSettingsRepository.setScanLibraryOnLaunch(enabled)
    }

    fun setNotifyWhenDownloadFinishes(enabled: Boolean) = scope.launch {
        dependencies.appSettingsRepository.setNotifyWhenDownloadFinishes(enabled)
    }

    fun connectListenBrainz(userToken: String) = scope.launch {
        mutableMessage.value = "Connecting ListenBrainz…"
        runCatching {
            kotlinx.coroutines.withTimeout(LISTEN_BRAINZ_CONNECT_TIMEOUT_MS) {
                dependencies.listenBrainzAccountRepository.connect(userToken)
            }
        }.onSuccess { validation ->
            mutableMessage.value = "ListenBrainz connected as ${validation.username}."
        }.onFailure { error ->
            mutableMessage.value = error.message ?: "Couldn't connect ListenBrainz."
        }
    }

    fun disconnectListenBrainz() = scope.launch {
        runCatching {
            dependencies.listenBrainzAccountRepository.disconnect()
        }.onSuccess {
            mutableMessage.value = "ListenBrainz disconnected."
        }.onFailure { error ->
            mutableMessage.value = error.message ?: "Couldn't disconnect ListenBrainz."
        }
    }

    fun setListenBrainzSubmitNowPlaying(enabled: Boolean) = scope.launch {
        dependencies.listenBrainzAccountRepository.setSubmitNowPlaying(enabled)
    }

    fun setListenBrainzSubmitListens(enabled: Boolean) = scope.launch {
        dependencies.listenBrainzAccountRepository.setSubmitListens(enabled)
    }

    fun setListenBrainzSubmitCurrentTrackFeedback(enabled: Boolean) = scope.launch {
        dependencies.listenBrainzAccountRepository.setSubmitCurrentTrackFeedback(enabled)
    }

    fun submitListenBrainzFeedback(score: ListenBrainzFeedbackScore) = scope.launch {
        val submitted = dependencies.listenBrainzPlaybackReporter.submitCurrentTrackFeedback(score)
        mutableMessage.value = when {
            !submitted -> "ListenBrainz feedback is not available for this play yet."
            score == ListenBrainzFeedbackScore.Love -> "Marked loved on ListenBrainz."
            score == ListenBrainzFeedbackScore.Hate -> "Marked hated on ListenBrainz."
            else -> "Cleared ListenBrainz feedback."
        }
    }

    fun setEqualizerEnabled(enabled: Boolean) {
        applyEqualizerProfile(mutableEqualizerProfile.value.withEnabled(enabled))
    }

    fun setEqualizerBandCount(count: Int) {
        applyEqualizerProfile(mutableEqualizerProfile.value.withBandCount(count))
    }

    fun setEqualizerGain(index: Int, gainDb: Float) {
        val current = mutableEqualizerProfile.value
        val next = current
            .withEnabled(true)
            .withGain(index, gainDb)
        applyEqualizerProfile(next)
    }

    fun resetEqualizer() {
        val current = mutableEqualizerProfile.value.normalized()
        applyEqualizerProfile(
            EqualizerProfile.Default
                .withBandCount(current.bandCount)
                .withEnabled(current.enabled),
        )
    }

    fun setPersistEqualizerSettings(enabled: Boolean) {
        mutablePersistEqualizerSettings.value = enabled
        scope.launch(start = CoroutineStart.UNDISPATCHED) {
            dependencies.appSettingsRepository.setPersistEqualizerSettings(enabled, mutableEqualizerProfile.value)
        }
    }

    private fun applyEqualizerProfile(profile: EqualizerProfile) {
        val normalized = profile.normalized()
        mutableEqualizerProfile.value = normalized
        dependencies.audioPlayer.setEqualizer(normalized)
        if (mutablePersistEqualizerSettings.value) {
            scope.launch(start = CoroutineStart.UNDISPATCHED) {
                dependencies.appSettingsRepository.setEqualizerProfile(normalized)
            }
        }
        if (equalizerRemoteUnavailable.value && normalized.enabled) {
            surfaceTransientNotice("Equalizer changes apply on this device. Chromecast and remote players use their own audio path.")
        }
    }

    fun download(track: Track) = launchDownload {
        mutableMessage.value = "Downloading ${track.title}…"
        val result = dependencies.catalogRepository.download(track)
        mutableMessage.value = downloadMessage(result, singular = "song", plural = "songs")
        result
    }

    fun download(album: Album) = launchDownload {
        mutableMessage.value = "Downloading ${album.title}…"
        val result = dependencies.catalogRepository.downloadAlbum(session.value, album)
        mutableMessage.value = downloadMessage(result, singular = "song from ${album.title}", plural = "songs from ${album.title}")
        result
    }

    fun download(artist: Artist) = launchDownload {
        mutableMessage.value = "Downloading ${artist.title}…"
        val result = dependencies.catalogRepository.downloadArtist(session.value, artist)
        mutableMessage.value = downloadMessage(result, singular = "song by ${artist.title}", plural = "songs by ${artist.title}")
        result
    }

    fun download(playlist: Playlist) = launchDownload {
        mutableMessage.value = "Downloading ${playlist.title}…"
        dependencies.catalogRepository.previewQueuedDownloadsForPlaylist(playlist)
        val result = dependencies.catalogRepository.downloadPlaylist(session.value, playlist)
        mutableMessage.value = downloadMessage(result, singular = "song from ${playlist.title}", plural = "songs from ${playlist.title}")
        result
    }

    private fun launchDownload(block: suspend () -> DownloadBatchResult): Job {
        lateinit var downloadJob: Job
        downloadJob = scope.launch {
            try {
                downloadedArtworkCacheJob?.cancel()
                val result = block()
                notifyDownloadFinishedIfNeeded(result)
            } catch (error: CancellationException) {
                throw error
            } catch (error: Throwable) {
                PhoebeLog.d("AppState") { "download failed: ${error.message}" }
                mutableMessage.value = error.message?.takeIf { it.isNotBlank() }
                    ?.let { "Download failed: $it" }
                    ?: "Download failed."
            } finally {
                activeDownloadJobs.remove(downloadJob)
                publishActiveDownloadJobCount()
            }
        }
        activeDownloadJobs += downloadJob
        publishActiveDownloadJobCount()
        return downloadJob
    }

    private suspend fun notifyDownloadFinishedIfNeeded(result: DownloadBatchResult) {
        if (!appSettings.value.notifyWhenDownloadFinishes || result.completed <= 0) return
        val title = "Download complete"
        val body = if (result.completed == 1) {
            "Downloaded 1 song."
        } else {
            "Downloaded ${result.completed} songs."
        }
        dependencies.downloadNotifier.notifyDownloadFinished(title, body)
    }

    fun setDownloadDirectory(uri: String?) = scope.launch {
        dependencies.platformStorage.writeDownloadDirectory(uri)
        mutableDownloadDirectory.value = dependencies.platformStorage.readDownloadDirectory()
        mutableMessage.value = if (mutableDownloadDirectory.value == null) {
            "Downloads will use ${dependencies.platformStorage.defaultDownloadDirectoryLabel()}."
        } else {
            "Download location updated."
        }
    }

    fun resetDownloadDirectory() = setDownloadDirectory(null)

    fun deleteAllDownloads() = scope.launch {
        val deleted = dependencies.catalogRepository.deleteAllDownloads()
        mutableMessage.value = if (deleted == 0) {
            "No downloads to delete."
        } else {
            "Deleted $deleted downloaded ${if (deleted == 1) "song" else "songs"}."
        }
    }

    fun deleteDownloads(tracks: List<Track>) = scope.launch {
        deleteResolvedDownloads(tracks)
    }

    fun deleteDownloads(playlist: Playlist) = scope.launch {
        val tracks = dependencies.catalogRepository.tracksForPlaylist(session.value, playlist)
        deleteResolvedDownloads(tracks)
    }

    fun cancelDownloads(tracks: List<Track>) = scope.launch {
        cancelResolvedDownloads(tracks)
    }

    fun cancelDownloads(playlist: Playlist) = scope.launch {
        mutableMessage.value = "Preparing to cancel ${playlist.title}…"
        val tracks = dependencies.catalogRepository.tracksForPlaylist(session.value, playlist)
        cancelResolvedDownloads(tracks)
    }

    private suspend fun deleteResolvedDownloads(tracks: List<Track>) {
        val deleted = dependencies.catalogRepository.deleteDownloadsForTracks(tracks)
        mutableMessage.value = if (deleted == 0) {
            "No downloaded songs to delete."
        } else {
            "Deleted $deleted downloaded ${if (deleted == 1) "song" else "songs"}."
        }
    }

    private suspend fun cancelResolvedDownloads(tracks: List<Track>) {
        mutableMessage.value = "Cancelling download…"
        val jobs = activeDownloadJobs.toList()
        dependencies.catalogRepository.cancelDownloadsForTracks(tracks)
        jobs.forEach { it.cancel() }
        val deleted = dependencies.catalogRepository.deleteDownloadsForTracks(tracks)
        jobs.forEach { it.join() }
        mutableMessage.value = if (deleted == 0) {
            "Cancelled download."
        } else {
            "Cancelled download and deleted $deleted downloaded ${if (deleted == 1) "song" else "songs"}."
        }
    }

    private fun downloadMessage(result: DownloadBatchResult, singular: String, plural: String): String =
        when {
            result.total == 0 -> "Nothing to download yet."
            result.failed == 0 && result.completed == result.total -> {
                val noun = if (result.completed == 1) singular else plural
                "Downloaded ${result.completed} $noun."
            }
            result.failed == 0 && result.completed > 0 && result.skipped > 0 -> {
                val noun = if (result.completed == 1) singular else plural
                "Downloaded ${result.completed} $noun. ${result.skipped} unavailable."
            }
            result.failed == 0 && result.skipped > 0 -> "No downloadable songs found."
            result.completed > 0 -> {
                val percent = ((result.completed.toFloat() / result.total.toFloat()) * 100f).toInt().coerceIn(0, 100)
                val skipped = result.skipped.takeIf { it > 0 }?.let { " $it unavailable." }.orEmpty()
                "Downloaded ${result.completed} of ${result.total} songs ($percent%). " +
                    "${result.failed} failed.$skipped${result.downloadFailureDetailMessage()}"
            }
            else -> "Couldn't download those songs. 0% downloaded.${result.downloadFailureDetailMessage()}"
        }

    private fun DownloadBatchResult.downloadFailureDetailMessage(): String {
        val topReason = failureReasons.firstOrNull() ?: return ""
        val count = topReason.count.takeIf { it > 1 }?.let { " ($it)" }.orEmpty()
        val sample = failedSamples.firstOrNull()
            ?.title
            ?.takeIf { it.isNotBlank() }
            ?.let { " Example: ${it.compactDownloadMessageDetail(52)}." }
            .orEmpty()
        return " Top reason: ${topReason.reason.compactDownloadMessageDetail(96)}$count.$sample"
    }

    private fun String.compactDownloadMessageDetail(maxLength: Int): String {
        val compact = replace(Regex("\\s+"), " ").trim()
        return if (compact.length <= maxLength) compact else compact.take(maxLength - 1).trimEnd() + "…"
    }

    /**
     * Create a new playlist. Remote playlists require a signed-in provider session with a music library;
     * local playlists require at least one enabled local folder and only accept local audio files.
     */
    fun createPlaylist(
        title: String,
        initialTracks: List<Track> = emptyList(),
        onCreated: ((com.phoebe.app.domain.Playlist) -> Unit)? = null,
    ) = scope.launch {
        val allLocalEligible = initialTracks.isNotEmpty() && initialTracks.all { it.canAddToLocalPlaylist() }
        val allPlexEligible = initialTracks.isNotEmpty() && initialTracks.all { it.canAddToPlexPlaylist() }
        val hasLocalOnlyTracks = initialTracks.any { it.canAddToLocalPlaylist() && !it.canAddToPlexPlaylist() }
        val hasPlexTracks = initialTracks.any { it.canAddToPlexPlaylist() }
        if (hasLocalOnlyTracks && hasPlexTracks) {
            mutableMessage.value = "Can't mix local files and streaming songs in one playlist."
            return@launch
        }
        val playlist = when {
            allPlexEligible && session.value.supportsRemotePlaylists() -> {
                if (initialTracks.any { !it.canAddToPlexPlaylist() }) {
                    mutableMessage.value = "Only streaming library songs can be added to streaming playlists."
                    return@launch
                }
                dependencies.catalogRepository.createPlaylist(session.value, title, initialTracks)
            }
            allLocalEligible || (initialTracks.isEmpty() && !session.value.supportsRemotePlaylists() && hasEnabledLocalFolders()) -> {
                if (!hasEnabledLocalFolders()) {
                    mutableMessage.value = "Add a local music folder to create playlists."
                    return@launch
                }
                if (initialTracks.any { !it.canAddToLocalPlaylist() }) {
                    mutableMessage.value = "Only local audio files can be added to local playlists."
                    return@launch
                }
                dependencies.catalogRepository.createLocalPlaylist(title, initialTracks)
            }
            initialTracks.isEmpty() && session.value.supportsRemotePlaylists() -> {
                dependencies.catalogRepository.createPlaylist(session.value, title, initialTracks)
            }
            else -> {
                mutableMessage.value = "Sign in to your provider, or add a local music folder to use playlists."
                return@launch
            }
        }
        if (playlist != null) {
            onCreated?.invoke(playlist)
        } else {
            mutableMessage.value = "Couldn't create playlist '$title'."
        }
    }

    /** Append [track] to [playlist] when the session, playlist type, and track are eligible. */
    fun addToPlaylist(playlist: com.phoebe.app.domain.Playlist, track: Track) = scope.launch {
        if (playlist.isLocalPlaylist()) {
            if (!track.canAddToLocalPlaylist()) {
                mutableMessage.value = "Only local audio files can be added to local playlists."
                return@launch
            }
            dependencies.catalogRepository.addTracksToPlaylist(session.value, playlist, listOf(track))
            return@launch
        }
        if (!session.value.supportsRemotePlaylists()) {
            mutableMessage.value = "Sign in and select a music library to use streaming playlists."
            return@launch
        }
        if (!playlist.isRemoteProviderPlaylist()) {
            mutableMessage.value = "This playlist can't be edited in Phoebe."
            return@launch
        }
        if (!track.canAddToPlexPlaylist()) {
            mutableMessage.value = "Only streaming library songs can be added to streaming playlists."
            return@launch
        }
        PhoebeLog.d("AppState") { "addToPlaylist → playlist='${playlist.title}' (${playlist.id}), track='${track.title}' (${track.id})" }
        dependencies.catalogRepository.addTracksToPlaylist(session.value, playlist, listOf(track))
    }

    fun movePlaylistTrack(playlist: Playlist, fromIndex: Int, toIndex: Int) = scope.launch {
        val moved = dependencies.catalogRepository.movePlaylistTrack(session.value, playlist, fromIndex, toIndex)
        if (!moved && fromIndex != toIndex) {
            mutableMessage.value = "Couldn't reorder ${playlist.title}."
        }
    }

    fun toggleLikedTrack(track: Track) = scope.launch {
        if (!track.canTogglePlexLike()) {
            mutableMessage.value = "Liked Songs syncs streaming library songs only."
            return@launch
        }
        if (!session.value.supportsRemotePlaylists()) {
            mutableMessage.value = "Sign in and select a music library to like songs."
            return@launch
        }
        val liked = runCatching {
            dependencies.catalogRepository.toggleLikedTrackLocally(session.value, track)
        }.getOrElse { error ->
            PhoebeLog.d("AppState") { "toggleLikedTrack failed for '${track.title}': ${error.message}" }
            mutableMessage.value = error.message ?: "Couldn't update Liked Songs."
            return@launch
        }
        mutableMessage.value = if (liked) "Song is in Liked Songs." else "Removed from Liked Songs."
        syncLikedSongsInBackground(track, liked)
    }

    private fun syncLikedSongsInBackground(track: Track? = null, liked: Boolean? = null) {
        scope.launch {
            val synced = runCatching {
                if (track != null && liked != null) {
                    dependencies.catalogRepository.syncLikedTrackChange(session.value, track, liked)
                } else {
                    dependencies.catalogRepository.syncLikedSongsPlaylist(session.value)
                }
            }.getOrElse { error ->
                PhoebeLog.d("AppState") { "Liked Songs sync failed: ${error.message}" }
                false
            }
            if (track != null && liked != null && synced != true) {
                val provider = session.value.providerLabel()
                mutableMessage.value = if (session.value.isNavidrome()) {
                    "Liked locally. Subsonic favorites sync failed — check your connection and try again."
                } else {
                    "Liked Songs updated locally. $provider sync will retry later."
                }
            }
        }
    }

    fun copyPlaylistIntoPlaylist(
        source: com.phoebe.app.domain.Playlist,
        target: com.phoebe.app.domain.Playlist,
    ) = scope.launch {
        if (source.id == target.id) return@launch
        if (!source.id.startsWith("plex:") || !target.id.startsWith("plex:")) {
            mutableMessage.value = "Playlist copying supports Plex playlists only."
            return@launch
        }
        val copied = dependencies.catalogRepository.copyPlexPlaylistIntoPlaylist(session.value, source, target)
        mutableMessage.value = if (copied > 0) {
            "Copied $copied songs to ${target.title}."
        } else {
            "No new songs to copy."
        }
    }

    fun exportLocalPlaylist(
        playlist: com.phoebe.app.domain.Playlist,
        format: PlaylistExportFormat,
    ) = scope.launch {
        if (!playlist.isLocalPlaylist()) {
            mutableMessage.value = "Only local playlists can be exported."
            return@launch
        }
        val tracks = dependencies.catalogRepository.tracksForPlaylist(session.value, playlist)
        if (tracks.isEmpty()) {
            mutableMessage.value = "Nothing to export — playlist is empty."
            return@launch
        }
        val content = PlaylistExporter.export(tracks, format)
        val fileName = PlaylistExporter.suggestedFileName(playlist.title, format)
        runCatching {
            dependencies.platformStorage.writeText("exports/$fileName", content)
        }.onSuccess {
            mutableMessage.value = "Exported ${tracks.size} songs to $fileName."
        }.onFailure {
            mutableMessage.value = it.message ?: "Couldn't export playlist."
        }
    }

    private fun hasEnabledLocalFolders(): Boolean =
        mediaSources.value.localFolders.any { it.enabled }

    private fun shouldStopPlaybackForRemovedLocalFolder(folderId: String): Boolean {
        val playback = player.value
        if (playback.queue.any { it.isFromLocalFolder(folderId) }) return true
        val enabledAfterRemoval = mediaSources.value.localFolders.any { it.enabled && it.id != folderId }
        if (enabledAfterRemoval || session.value?.token?.isNotBlank() == true) return false
        return playback.currentTrack != null || playback.upNext.isNotEmpty()
    }

    fun updateTrackMetadata(update: TrackMetadataUpdate) = scope.launch {
        val result = dependencies.catalogRepository.updateTrackMetadata(session.value, update)
        val provider = session.value.providerLabel()
        mutableMessage.value = when {
            !result.savedLocally -> "Couldn't find that song in the library."
            result.plexAttempted && result.plexSynced -> "Metadata saved and synced to $provider."
            result.plexAttempted -> "Metadata saved locally, but $provider sync failed."
            else -> "Metadata saved."
        }
    }

    fun rateTrack(track: Track, rating: Float?) = scope.launch {
        val result = dependencies.catalogRepository.rateTrack(session.value, track, rating)
        mutableMessage.value = ratingMessage(result.savedLocally, result.plexAttempted, result.plexSynced)
    }

    fun rateArtist(artist: Artist, rating: Float?) = scope.launch {
        val result = dependencies.catalogRepository.rateArtist(session.value, artist, rating)
        mutableMessage.value = ratingMessage(result.savedLocally, result.plexAttempted, result.plexSynced)
    }

    fun rateAlbum(album: Album, rating: Float?) = scope.launch {
        val result = dependencies.catalogRepository.rateAlbum(session.value, album, rating)
        mutableMessage.value = ratingMessage(result.savedLocally, result.plexAttempted, result.plexSynced)
    }

    fun ratePlaylist(playlist: Playlist, rating: Float?) = scope.launch {
        val result = dependencies.catalogRepository.ratePlaylist(session.value, playlist, rating)
        mutableMessage.value = ratingMessage(result.savedLocally, result.plexAttempted, result.plexSynced)
    }

    fun toggleFavoriteArtist(artist: Artist) = scope.launch {
        mutableMessage.value = favoriteMessage(
            label = "Artist",
            result = dependencies.catalogRepository.toggleFavoriteArtist(session.value, artist),
            plexUnavailableMessage = null,
        )
    }

    fun toggleFavoriteAlbum(album: Album) = scope.launch {
        mutableMessage.value = favoriteMessage(
            label = "Album",
            result = dependencies.catalogRepository.toggleFavoriteAlbum(session.value, album),
            plexUnavailableMessage = null,
        )
    }

    fun toggleFavoritePlaylist(playlist: Playlist) = scope.launch {
        mutableMessage.value = favoriteMessage(
            label = "Playlist",
            result = dependencies.catalogRepository.toggleFavoritePlaylist(session.value, playlist),
            plexUnavailableMessage = null,
        )
    }

    fun exportFavoritePlaylists() = scope.launch {
        val export = dependencies.catalogRepository.favoritePlaylistsExport()
        if (export.playlists.isEmpty()) {
            mutableMessage.value = "No favorite playlists to export."
            return@launch
        }
        runCatching {
            dependencies.platformStorage.writeText(
                FavoritePlaylistsExportPath,
                PlexClient.PlexJson.encodeToString(FavoritePlaylistsExport.serializer(), export),
            )
        }.onSuccess {
            mutableMessage.value = "Exported ${export.playlists.size} favorite playlists."
        }.onFailure { error ->
            mutableMessage.value = error.message ?: "Couldn't export favorite playlists."
        }
    }

    fun importFavoritePlaylists() = scope.launch {
        val content = dependencies.platformStorage.readText(FavoritePlaylistsExportPath)
        if (content.isNullOrBlank()) {
            mutableMessage.value = "No favorite playlist export found."
            return@launch
        }
        runCatching {
            val export = PlexClient.PlexJson.decodeFromString(FavoritePlaylistsExport.serializer(), content)
            dependencies.catalogRepository.importFavoritePlaylists(export)
        }.onSuccess { imported ->
            mutableMessage.value = if (imported > 0) {
                "Imported $imported favorite playlists."
            } else {
                "No matching playlists found to import."
            }
        }.onFailure { error ->
            mutableMessage.value = error.message ?: "Couldn't import favorite playlists."
        }
    }

    private fun favoriteMessage(
        label: String,
        result: FavoriteSyncResult,
        plexUnavailableMessage: String?,
    ): String {
        val provider = session.value.providerLabel()
        return when (result.favorite) {
            null -> "Couldn't find that item in the library."
            true -> when {
                result.plexAttempted && result.plexSynced -> "$label added to favorites and synced to $provider."
                result.plexAttempted -> "$label added to favorites, but $provider sync failed."
                plexUnavailableMessage != null -> "$label added to favorites. $plexUnavailableMessage"
                else -> "$label added to favorites."
            }
            false -> when {
                result.plexAttempted && result.plexSynced -> "$label removed from favorites and synced to $provider."
                result.plexAttempted -> "$label removed from favorites, but $provider sync failed."
                plexUnavailableMessage != null -> "$label removed from favorites. $plexUnavailableMessage"
                else -> "$label removed from favorites."
            }
        }
    }

    private fun ratingMessage(savedLocally: Boolean, plexAttempted: Boolean, plexSynced: Boolean): String =
        when {
            !savedLocally -> "Couldn't find that item in the library."
            plexAttempted && plexSynced -> "Rating saved and synced to ${session.value.providerLabel()}."
            plexAttempted -> "Rating saved locally, but ${session.value.providerLabel()} sync failed."
            session.value.supportsRemoteRatings() -> "Rating saved."
            else -> "Rating saved locally."
        }

    fun addLocalFolderFromUri(rootUri: String?) = scope.launch {
        if (rootUri.isNullOrBlank()) return@launch
        val label = rootUri.trimEnd('/').substringAfterLast('/').substringBefore('?', "Local").ifBlank { "Local" }
        dependencies.mediaSourcesRepository.addLocalFolder(rootUri, label)
        refreshCatalogSuspended(catalogMessage = null)
        mutableMessage.value = "Added local music folder."
        if (defaultBrowseRequest() == AppNavigationRequest.Home) {
            requestNavigation(AppNavigationRequest.Home)
        }
    }

    fun removeLocalFolder(id: String) = scope.launch {
        val shouldStopPlayback = shouldStopPlaybackForRemovedLocalFolder(id)
        dependencies.mediaSourcesRepository.removeLocalFolder(id)
        if (shouldStopPlayback) {
            stopPlayback()
        }
        refreshCatalogSuspended(catalogMessage = null)
        mutableMessage.value = "Removed local folder."
        if (defaultBrowseRequest() == AppNavigationRequest.SignIn) {
            requestNavigation(AppNavigationRequest.SignIn)
        }
    }

    fun setLocalFolderEnabled(id: String, enabled: Boolean) = scope.launch {
        dependencies.mediaSourcesRepository.setLocalFolderEnabled(id, enabled)
        refreshCatalogSuspended(catalogMessage = null)
        if (defaultBrowseRequest() == AppNavigationRequest.SignIn) {
            requestNavigation(AppNavigationRequest.SignIn)
        }
    }

    fun signOut() {
        val refreshJob = catalogRefreshJob
        val historyJob = playHistorySyncJob
        catalogRefreshJob = null
        playHistorySyncJob = null
        refreshJob?.cancel()
        historyJob?.cancel()
        dependencies.catalogRepository.clearActiveSyncProgress()
        mostPlayedWarmSignature = null
        recentAlbumWarmSignature = null
        playedAlbumWarmSignature = null
        prefetchedArtistIds.clear()
        prefetchedAlbumIds.clear()
        stopPlayback()
        mutablePin.value = null
        mutableLibraries.value = emptyList()
        mutableLibrariesLoading.value = false
        mutableAuthInProgress.value = false
        requestNavigation(AppNavigationRequest.SignIn)
        mutableMessage.value = "Signing out…"
        scope.launch {
            val signedOut = runCatching {
                refreshJob?.cancelAndJoin()
                historyJob?.cancelAndJoin()
                dependencies.sessionRepository.signOut()
                dependencies.deleteDatabaseDataForSignOut()
            }.onFailure {
                mutableMessage.value = it.message ?: "Something went sideways."
            }.isSuccess
            if (signedOut) {
                mutableMessage.value = "Signed out."
            }
        }
    }

    private fun CoroutineScope.launchBusy(
        loadingMessage: String? = null,
        block: suspend () -> Unit,
    ) = launch {
        mutableBusy.value = true
        if (loadingMessage != null) {
            mutableMessage.value = loadingMessage
        }
        runCatching { block() }
            .onFailure { mutableMessage.value = it.message ?: "Something went sideways." }
        mutableBusy.value = false
    }
}

private fun PlexSession?.canUsePlexBackgroundFetches(): Boolean {
    val server = this?.selectedServer ?: return false
    if (isNavidrome() || isEmbyFamily()) return server.uri.isNotBlank()
    return server.connectionUris.isNotEmpty() ||
        server.advertisedConnectionUris.isNotEmpty() ||
        server.localConnectionUris.isNotEmpty()
}

internal fun List<Track>.withFreshPlaybackUrls(session: PlexSession?): List<Track> {
    if (session == null || isEmpty()) return this
    var changed = false
    val refreshed = map { track ->
        val next = track.withFreshPlaybackUrls(session)
        if (next !== track) changed = true
        next
    }
    return if (changed) refreshed else this
}

internal fun Track.withFreshPlaybackUrls(session: PlexSession): Track {
    val refreshedStreamUrl = streamUrl.withFreshPlaybackAuth(session)
    val refreshedDownloadUrl = downloadUrl.withFreshPlaybackAuth(session)
    if (refreshedStreamUrl == streamUrl && refreshedDownloadUrl == downloadUrl) return this
    return copy(
        streamUrl = refreshedStreamUrl,
        downloadUrl = refreshedDownloadUrl,
    )
}

internal fun String.withFreshPlaybackAuth(session: PlexSession): String {
    if (isBlank() || session.token.isBlank()) return this
    val parsed = runCatching { Url(this) }.getOrNull() ?: return this
    if (parsed.protocol.name != "http" && parsed.protocol.name != "https") return this
    return when (session.providerType) {
        MediaProviderType.Plex -> withQueryParameter(parsed, "X-Plex-Token", session.token)
        MediaProviderType.Jellyfin,
        MediaProviderType.Emby -> withQueryParameter(parsed, "api_key", session.token)
        MediaProviderType.Navidrome -> withQueryParameters(
            parsed,
            "u" to session.userName,
            "p" to session.token,
        )
        MediaProviderType.MusicAssistant -> this
    }
}

private fun withQueryParameter(url: Url, name: String, value: String): String =
    withQueryParameters(url, name to value)

private fun withQueryParameters(url: Url, vararg replacements: Pair<String, String>): String {
    val original = url.toString()
    val fragment = original.substringAfter('#', missingDelimiterValue = "")
    val withoutFragment = original.substringBefore('#')
    val base = withoutFragment.substringBefore('?')
    val query = withoutFragment.substringAfter('?', missingDelimiterValue = "")
    val replacementMap = replacements
        .filter { (_, value) -> value.isNotBlank() }
        .associate { (name, value) -> name to value }
    if (replacementMap.isEmpty()) return original
    val seen = mutableSetOf<String>()
    val pairs = query
        .split('&')
        .filter { it.isNotBlank() }
        .mapNotNull { pair ->
            val name = pair.substringBefore('=')
            val replacement = replacementMap[name] ?: return@mapNotNull pair
            seen += name
            "$name=${replacement.encodeURLParameter()}"
        }
        .toMutableList()
    replacementMap.forEach { (name, value) ->
        if (name !in seen) pairs += "$name=${value.encodeURLParameter()}"
    }
    val rebuilt = buildString {
        append(base)
        if (pairs.isNotEmpty()) {
            append('?')
            append(pairs.joinToString("&"))
        }
        if (fragment.isNotBlank()) {
            append('#')
            append(fragment)
        }
    }
    return rebuilt
}

private const val FavoritePlaylistsExportPath = "exports/favorite-playlists.json"
private const val LISTEN_BRAINZ_CONNECT_TIMEOUT_MS = 45_000L
