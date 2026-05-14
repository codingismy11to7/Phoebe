package com.phoebe.app

import com.phoebe.app.domain.AppScreen
import com.phoebe.app.domain.LibraryColumnVisibility
import com.phoebe.app.domain.LibrarySortBy
import com.phoebe.app.domain.LibraryTab
import com.phoebe.app.domain.MusicLibrary
import com.phoebe.app.domain.PlexPin
import com.phoebe.app.domain.PlexServer
import com.phoebe.app.domain.PlexSession
import com.phoebe.app.domain.RepeatMode
import com.phoebe.app.domain.Track
import com.phoebe.app.domain.TrackMetadataUpdate
import com.phoebe.app.domain.isLocalMediaPlayback
import com.phoebe.app.domain.isPlexLibraryTrack
import com.phoebe.app.domain.supportsPlexPlaylists
import com.phoebe.app.player.asPlayerState
import com.phoebe.app.player.isChromecastPlayableQueue
import com.phoebe.app.platform.currentTimeMs
import com.phoebe.app.platform.openExternalUrl
import com.phoebe.app.sources.LocalLibraryIO
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.collections.ArrayDeque

class AppState(
    private val dependencies: AppDependencies,
    private val scope: CoroutineScope,
) {
    val session = dependencies.sessionRepository.session
    val catalog = dependencies.catalogRepository.catalog
    val catalogRefreshing: StateFlow<Boolean> = dependencies.catalogRepository.catalogRefreshing
    val catalogSyncState = dependencies.catalogRepository.catalogSyncState
    val mediaSources = dependencies.mediaSourcesRepository.state
    val cast = dependencies.castController.state
    val player: StateFlow<com.phoebe.app.domain.PlayerState> = combine(
        dependencies.audioPlayer.state,
        dependencies.castController.state,
    ) { audio, castState ->
        if (castState.isConnected && castState.queue.isNotEmpty()) castState.asPlayerState(audio) else audio
    }.stateIn(scope, SharingStarted.Eagerly, dependencies.audioPlayer.state.value)
    val libraryUi = dependencies.libraryUiRepository.preferences
    val lastPlayedByArtist = dependencies.playHistoryRepository.lastPlayedByArtist
    val lastPlayedByAlbum = dependencies.playHistoryRepository.lastPlayedByAlbum
    val lastPlayedByTrack = dependencies.playHistoryRepository.lastPlayedByTrack

    private val mutableScreen = MutableStateFlow(defaultBrowseScreen())
    val screen: StateFlow<AppScreen> = mutableScreen

    private val mutableTab = MutableStateFlow(LibraryTab.Albums)
    val tab: StateFlow<LibraryTab> = mutableTab

    private val mutablePin = MutableStateFlow<PlexPin?>(null)
    val pin: StateFlow<PlexPin?> = mutablePin

    private val mutableServers = MutableStateFlow<List<PlexServer>>(emptyList())
    val servers: StateFlow<List<PlexServer>> = mutableServers

    private val mutableLibraries = MutableStateFlow<List<MusicLibrary>>(emptyList())
    val libraries: StateFlow<List<MusicLibrary>> = mutableLibraries

    private val mutableBusy = MutableStateFlow(false)
    val busy: StateFlow<Boolean> = mutableBusy

    private val mutableServersLoading = MutableStateFlow(false)
    val serversLoading: StateFlow<Boolean> = mutableServersLoading

    private val mutableMessage = MutableStateFlow("Sign in to Plex or add a local music folder to get started.")
    val message: StateFlow<String> = mutableMessage

    private val detailStack = ArrayDeque<AppScreen>()
    private var playRequestGeneration = 0

    init {
        scope.launch {
            // Session and local folders are restored in [AppDependencies.create] so the first frame
            // can skip Sign-in; repeat here for callers that inject dependencies without that path.
            dependencies.sessionRepository.restore()
            dependencies.mediaSourcesRepository.restore()
            dependencies.libraryUiRepository.restore()
            dependencies.playHistoryRepository.restore()
            mutableScreen.value = defaultBrowseScreen(session.value)
            if (session.value?.token?.isNotBlank() == true && session.value?.selectedServer == null) {
                refreshServers()
            }
            dependencies.catalogRepository.restoreCachedCatalog()
            refreshCatalogSuspended(catalogMessage = null)
        }
        bindSystemVolume()
        recordPlaybackHistory()
        dependencies.plexPlaybackReporter.start(scope)
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
        if (controller.isSupported) {
            dependencies.audioPlayer.setUnityOutputVolume()
            dependencies.audioPlayer.updateReportedVolume(controller.volume.value)
            scope.launch {
                controller.volume.collect { v ->
                    dependencies.audioPlayer.updateReportedVolume(v)
                }
            }
        }
    }

    /**
     * If the UI is still on [AppScreen.SignIn] but the saved session (or local folders) implies a browse flow,
     * jump to the correct screen. Covers startup races and missed navigation after async restore.
     */
    fun reconcileBrowseScreenIfNeeded() {
        if (mutableScreen.value != AppScreen.SignIn) return
        val target = defaultBrowseScreen()
        if (target != AppScreen.SignIn) {
            detailStack.clear()
            mutableScreen.value = target
        }
    }

    private fun defaultBrowseScreen(sessionSnapshot: PlexSession? = session.value): AppScreen {
        return when {
            sessionSnapshot?.selectedLibrary != null -> AppScreen.Home
            sessionSnapshot?.selectedServer != null -> AppScreen.LibraryPicker
            sessionSnapshot?.token?.isNotBlank() == true -> AppScreen.ServerPicker
            mediaSources.value.localFolders.any { it.enabled } -> AppScreen.Home
            else -> AppScreen.SignIn
        }
    }

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
        detailStack.clear()
        mutableScreen.value = AppScreen.ServerPicker
        mutableServers.value = servers
        mutableMessage.value = "Signed in. Pick the Plex server that hosts your music."
    }

    fun loadServers() = scope.launch {
        detailStack.clear()
        mutableScreen.value = AppScreen.ServerPicker
        refreshServers()
    }

    fun returnToServerPicker() = scope.launch {
        detailStack.clear()
        mutableScreen.value = AppScreen.ServerPicker
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
        }.onFailure { mutableMessage.value = it.message ?: "Couldn't load Plex servers." }
        mutableServersLoading.value = false
    }

    fun selectServer(server: PlexServer) = scope.launchBusy {
        val resolved = dependencies.sessionRepository.selectServer(server)
        mutableLibraries.value = dependencies.sessionRepository.libraries(resolved)
        detailStack.clear()
        mutableScreen.value = AppScreen.LibraryPicker
    }

    fun selectLibrary(library: MusicLibrary) = scope.launch {
        mutableBusy.value = true
        if (session.value == null) {
            mutableMessage.value = "Session expired. Sign in again."
            mutableBusy.value = false
            return@launch
        }
        runCatching {
            dependencies.sessionRepository.selectLibrary(library)
            detailStack.clear()
            mutableScreen.value = AppScreen.Home
            mutableMessage.value = "Loading library…"
        }.onFailure { mutableMessage.value = it.message ?: "Something went sideways." }
        mutableBusy.value = false

        runCatching {
            refreshCatalogSuspended(catalogMessage = "Library ready.")
        }.onFailure { mutableMessage.value = it.message ?: "Something went sideways." }
    }

    /**
     * Suspends until the catalog is rebuilt from the current session and media sources.
     * Prefer this from [LaunchedEffect] so in-flight work is cancelled when dependencies change,
     * avoiding stale empty Plex refreshes overwriting a newer library load.
     */
    suspend fun refreshCatalogSuspended(catalogMessage: String? = "Library refreshed.") {
        runCatching {
            withContext(Dispatchers.Default) {
                dependencies.catalogRepository.refreshAggregated(session.value)
            }
            if (catalogMessage != null) mutableMessage.value = catalogMessage
        }.onFailure { mutableMessage.value = it.message ?: "Something went sideways." }
    }

    fun refreshCatalog() = scope.launch {
        refreshCatalogSuspended()
    }

    fun setTab(tab: LibraryTab) {
        mutableTab.value = tab
        dismissDetailsToHome()
    }

    fun open(screen: AppScreen) {
        when (screen) {
            AppScreen.SignIn, AppScreen.ServerPicker, AppScreen.LibraryPicker, AppScreen.Home -> {
                detailStack.clear()
                mutableScreen.value = screen
            }
            AppScreen.Player -> {
                val cur = mutableScreen.value
                if (cur != AppScreen.Player) {
                    detailStack.addLast(cur)
                    mutableScreen.value = screen
                }
            }
            is AppScreen.ArtistDetail, is AppScreen.AlbumDetail, is AppScreen.PlaylistDetail -> {
                val cur = mutableScreen.value
                if (cur != screen) {
                    detailStack.addLast(cur)
                    mutableScreen.value = screen
                }
                when (screen) {
                    is AppScreen.ArtistDetail -> scope.launch {
                        runCatching {
                            dependencies.catalogRepository.ensureTracksForArtistAlbums(session.value, screen.artist.title)
                        }
                    }
                    is AppScreen.AlbumDetail -> scope.launch {
                        runCatching {
                            dependencies.catalogRepository.tracksForAlbum(session.value, screen.album)
                        }.onFailure {
                            mutableMessage.value = it.message ?: "Couldn't load album tracks."
                        }
                    }
                    is AppScreen.PlaylistDetail -> scope.launch {
                        runCatching {
                            dependencies.catalogRepository.tracksForPlaylist(session.value, screen.playlist)
                        }.onFailure {
                            mutableMessage.value = it.message ?: "Couldn't load playlist tracks."
                        }
                    }
                    else -> Unit
                }
            }
        }
    }

    fun popDetail() {
        mutableScreen.value = detailStack.removeLastOrNull() ?: defaultBrowseScreen()
    }

    fun canHandleBack(screenSnapshot: AppScreen = mutableScreen.value): Boolean =
        when (screenSnapshot) {
            AppScreen.SignIn, AppScreen.Home -> false
            AppScreen.Player, AppScreen.ServerPicker, AppScreen.LibraryPicker -> true
            is AppScreen.ArtistDetail, is AppScreen.AlbumDetail, is AppScreen.PlaylistDetail -> true
        }

    fun handleBack() {
        when (mutableScreen.value) {
            AppScreen.SignIn, AppScreen.Home -> Unit
            AppScreen.Player -> popDetail()
            AppScreen.ServerPicker -> {
                detailStack.clear()
                mutableScreen.value = AppScreen.SignIn
            }
            AppScreen.LibraryPicker -> returnToServerPicker()
            is AppScreen.ArtistDetail, is AppScreen.AlbumDetail, is AppScreen.PlaylistDetail -> popDetail()
        }
    }

    fun dismissDetailsToHome() {
        detailStack.clear()
        mutableScreen.value = defaultBrowseScreen()
    }

    fun backHome() {
        dismissDetailsToHome()
    }

    fun playTracks(tracks: List<Track>, index: Int = 0) {
        val requestGeneration = ++playRequestGeneration
        val track = tracks.getOrNull(index)
        if (dependencies.castController.state.value.isConnected) {
            if (!tracks.isChromecastPlayableQueue()) {
                mutableMessage.value = "Chromecast can play Plex streaming songs only."
                return
            }
            if (dependencies.audioPlayer.state.value.isPlaying) {
                dependencies.audioPlayer.togglePlayPause()
            }
            dependencies.castController.loadQueue(tracks, index)
            return
        }
        if (track?.localUri.isNullOrBlank()) {
            dependencies.audioPlayer.play(tracks, index)
            return
        }

        scope.launch {
            val ok = runCatching {
                LocalLibraryIO.fileExists(track.localUri!!)
            }.getOrDefault(false)
            if (requestGeneration != playRequestGeneration) return@launch
            if (ok) {
                dependencies.audioPlayer.play(tracks, index)
            } else {
                mutableMessage.value = "Could not open file (missing or inaccessible): ${track.title}"
            }
        }
    }

    fun togglePlayPause() {
        if (dependencies.castController.state.value.isConnected) {
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
        if (dependencies.castController.state.value.isConnected) {
            dependencies.castController.disconnect()
        } else {
            dependencies.audioPlayer.clearQueue()
        }
    }
    fun addToUpNext(track: Track) = dependencies.audioPlayer.addToUpNext(track)
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
        if (dependencies.castController.state.value.isConnected) {
            dependencies.castController.next()
        } else {
            dependencies.audioPlayer.next()
        }
    }
    fun previous() {
        if (dependencies.castController.state.value.isConnected) {
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
        if (dependencies.castController.state.value.isConnected) {
            dependencies.castController.seekTo(positionMs)
        } else {
            dependencies.audioPlayer.seekTo(positionMs)
        }
    }
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
        val controller = dependencies.systemVolume
        if (controller.isSupported) {
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
                ?: "Chromecast is available on Android, iOS, and Chrome web."
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

    fun download(track: Track) = scope.launch {
        dependencies.catalogRepository.download(track)
    }

    /**
     * Create a new Plex playlist. Requires Plex with a selected music library; only Plex library
     * tracks may be used as seeds.
     */
    fun createPlaylist(
        title: String,
        initialTracks: List<Track> = emptyList(),
        onCreated: ((com.phoebe.app.domain.Playlist) -> Unit)? = null,
    ) = scope.launch {
        if (!session.value.supportsPlexPlaylists()) {
            mutableMessage.value = "Sign in to Plex and select a music library to use playlists."
            return@launch
        }
        if (initialTracks.any { it.isLocalMediaPlayback() || !it.isPlexLibraryTrack() }) {
            mutableMessage.value = "Only Plex library songs can be added to playlists."
            return@launch
        }
        val playlist = dependencies.catalogRepository.createPlaylist(session.value, title, initialTracks)
        if (playlist != null) {
            onCreated?.invoke(playlist)
        } else {
            mutableMessage.value = "Couldn't create playlist '$title'."
        }
    }

    /** Append [track] to [playlist] on Plex when the session and track are eligible. */
    fun addToPlaylist(playlist: com.phoebe.app.domain.Playlist, track: Track) = scope.launch {
        if (!session.value.supportsPlexPlaylists()) {
            mutableMessage.value = "Sign in to Plex and select a music library to use playlists."
            return@launch
        }
        if (!playlist.id.startsWith("plex:")) {
            mutableMessage.value = "This playlist can't be edited in Phoebe."
            return@launch
        }
        if (track.isLocalMediaPlayback() || !track.isPlexLibraryTrack()) {
            mutableMessage.value = "Only Plex library songs can be added to playlists."
            return@launch
        }
        println("[AppState] addToPlaylist invoked → playlist='${playlist.title}' (${playlist.id}), track='${track.title}' (${track.id})")
        dependencies.catalogRepository.addTracksToPlaylist(session.value, playlist, listOf(track))
    }

    fun updateTrackMetadata(update: TrackMetadataUpdate) = scope.launch {
        val result = dependencies.catalogRepository.updateTrackMetadata(session.value, update)
        mutableMessage.value = when {
            !result.savedLocally -> "Couldn't find that song in the library."
            result.plexAttempted && result.plexSynced -> "Metadata saved and synced to Plex."
            result.plexAttempted -> "Metadata saved locally, but Plex sync failed."
            else -> "Metadata saved."
        }
    }

    fun addLocalFolderFromUri(rootUri: String?) = scope.launch {
        if (rootUri.isNullOrBlank()) return@launch
        val label = rootUri.trimEnd('/').substringAfterLast('/').substringBefore('?', "Local").ifBlank { "Local" }
        dependencies.mediaSourcesRepository.addLocalFolder(rootUri, label)
        refreshCatalogSuspended(catalogMessage = null)
        mutableMessage.value = "Added local music folder."
        if (defaultBrowseScreen() == AppScreen.Home) {
            mutableScreen.value = AppScreen.Home
        }
    }

    fun removeLocalFolder(id: String) = scope.launch {
        dependencies.mediaSourcesRepository.removeLocalFolder(id)
        refreshCatalogSuspended(catalogMessage = null)
        mutableMessage.value = "Removed local folder."
        if (defaultBrowseScreen() == AppScreen.SignIn) {
            detailStack.clear()
            mutableScreen.value = AppScreen.SignIn
        }
    }

    fun setLocalFolderEnabled(id: String, enabled: Boolean) = scope.launch {
        dependencies.mediaSourcesRepository.setLocalFolderEnabled(id, enabled)
        refreshCatalogSuspended(catalogMessage = null)
        if (defaultBrowseScreen() == AppScreen.SignIn) {
            detailStack.clear()
            mutableScreen.value = AppScreen.SignIn
        }
    }

    fun signOut() {
        dependencies.castController.disconnect()
        dependencies.audioPlayer.clearQueue()
        mutableBusy.value = true
        detailStack.clear()
        mutablePin.value = null
        mutableScreen.value = AppScreen.SignIn
        mutableMessage.value = "Signing out…"
        scope.launch {
            runCatching {
                dependencies.sessionRepository.signOut()
                refreshCatalogSuspended(catalogMessage = null)
            }.onFailure { mutableMessage.value = it.message ?: "Something went sideways." }
            mutableMessage.value = "Signed out."
            mutableBusy.value = false
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
