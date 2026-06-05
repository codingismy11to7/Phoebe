package com.phoebe.app

import com.phoebe.app.data.AppSettingsRepository
import com.phoebe.app.data.CatalogRepository
import com.phoebe.app.data.EmbyClient
import com.phoebe.app.data.LibraryUiRepository
import com.phoebe.app.data.JellyfinClient
import com.phoebe.app.data.JellyfinPlayHistorySyncer
import com.phoebe.app.data.ListenBrainzAccountRepository
import com.phoebe.app.data.ListenBrainzClient
import com.phoebe.app.data.ListenBrainzPlaybackReporter
import com.phoebe.app.data.LyricsRepository
import com.phoebe.app.data.MediaSourcesRepository
import com.phoebe.app.data.MusicAssistantClient
import com.phoebe.app.data.MusicAssistantProviderAdapter
import com.phoebe.app.data.MusicProviderRegistry
import com.phoebe.app.data.NavidromePlayHistorySyncer
import com.phoebe.app.data.NavidromeProviderAdapter
import com.phoebe.app.data.PlayHistoryRepository
import com.phoebe.app.data.PlexClient
import com.phoebe.app.data.PlexPlayHistorySyncer
import com.phoebe.app.data.PlexPlaybackReporter
import com.phoebe.app.data.EmbyProviderAdapter
import com.phoebe.app.data.JellyfinProviderAdapter
import com.phoebe.app.data.SearchHistoryRepository
import com.phoebe.app.data.SessionRepository
import com.phoebe.app.data.SubsonicClient
import com.phoebe.app.data.db.DatabaseWriteGate
import com.phoebe.app.data.db.clearAllAppData
import com.phoebe.app.data.db.createPhoebeDatabase
import com.phoebe.app.db.PhoebeDatabase
import com.phoebe.app.platform.PlatformStorage
import com.phoebe.app.platform.DownloadNotifier
import com.phoebe.app.platform.SecureCredentialStore
import com.phoebe.app.platform.createPlatformHttpClient
import com.phoebe.app.platform.createSecureCredentialStore
import com.phoebe.app.player.AudioPlayer
import com.phoebe.app.player.CastController
import com.phoebe.app.player.SystemVolumeController
import com.phoebe.app.player.createAudioPlayer
import com.phoebe.app.player.createCastController
import com.phoebe.app.player.createSystemVolumeController
import com.phoebe.app.updates.GitHubReleaseUpdateRepository
import com.phoebe.app.updates.PlatformUpdateInstaller
import com.phoebe.app.updates.createPlatformUpdateInstaller

class AppDependencies(
    val database: PhoebeDatabase,
    val databaseWriteGate: DatabaseWriteGate,
    val sessionRepository: SessionRepository,
    val mediaSourcesRepository: MediaSourcesRepository,
    val catalogRepository: CatalogRepository,
    val libraryUiRepository: LibraryUiRepository,
    val lyricsRepository: LyricsRepository,
    val playHistoryRepository: PlayHistoryRepository,
    val appSettingsRepository: AppSettingsRepository,
    val searchHistoryRepository: SearchHistoryRepository,
    val providerRegistry: MusicProviderRegistry,
    val plexPlayHistorySyncer: PlexPlayHistorySyncer,
    val jellyfinPlayHistorySyncer: JellyfinPlayHistorySyncer,
    val navidromePlayHistorySyncer: NavidromePlayHistorySyncer,
    val plexPlaybackReporter: PlexPlaybackReporter,
    val listenBrainzAccountRepository: ListenBrainzAccountRepository,
    val listenBrainzPlaybackReporter: ListenBrainzPlaybackReporter,
    val secureCredentialStore: SecureCredentialStore,
    val audioPlayer: AudioPlayer,
    val castController: CastController,
    val systemVolume: SystemVolumeController,
    val downloadNotifier: DownloadNotifier,
    val updateRepository: GitHubReleaseUpdateRepository,
    val updateInstaller: PlatformUpdateInstaller,
    /** File-backed on desktop; NSUserDefaults keys on iOS; etc. Used for lightweight UI prefs. */
    val platformStorage: PlatformStorage,
) {
    suspend fun deleteDatabaseDataForSignOut() {
        catalogRepository.awaitDatabaseIdle()
        listenBrainzAccountRepository.disconnect()
        databaseWriteGate.withWrite {
            database.clearAllAppData(clearPlayHistory = true)
        }
        listOf("session.json", "catalog.json", "media_sources.json", "library_ui_prefs.json").forEach {
            platformStorage.delete(it)
        }
        catalogRepository.clearInMemoryCatalog()
        mediaSourcesRepository.clearInMemoryState()
        libraryUiRepository.resetInMemoryState()
        appSettingsRepository.resetInMemoryState()
        searchHistoryRepository.clear()
        lyricsRepository.clearMemoryCache()
    }

    companion object {
        suspend fun create(): AppDependencies {
            val httpClient = createPlatformHttpClient()
            val plexClient = PlexClient(httpClient)
            val jellyfinClient = JellyfinClient(httpClient)
            val embyClient = EmbyClient(httpClient)
            val subsonicClient = SubsonicClient(httpClient)
            val musicAssistantClient = MusicAssistantClient(httpClient)
            val listenBrainzClient = ListenBrainzClient(httpClient)
            val providerRegistry = MusicProviderRegistry(
                listOf(
                    JellyfinProviderAdapter(jellyfinClient),
                    EmbyProviderAdapter(embyClient),
                    NavidromeProviderAdapter(subsonicClient),
                    MusicAssistantProviderAdapter(musicAssistantClient),
                ),
            )
            val storage = PlatformStorage()
            val updateInstaller = createPlatformUpdateInstaller()
            val secureCredentialStore = createSecureCredentialStore()
            val database = createPhoebeDatabase()
            val databaseWriteGate = DatabaseWriteGate()
            val mediaSourcesRepository = MediaSourcesRepository(database, storage)
            val libraryUiRepository = LibraryUiRepository(database, storage)
            val appSettingsRepository = AppSettingsRepository(database)
            val listenBrainzAccountRepository = ListenBrainzAccountRepository(
                client = listenBrainzClient,
                appSettingsRepository = appSettingsRepository,
                credentialStore = secureCredentialStore,
            )
            val playHistoryRepository = PlayHistoryRepository(database)
            val searchHistoryRepository = SearchHistoryRepository(storage)
            val audioPlayer = createAudioPlayer()
            val castController = createCastController(audioPlayer)
            val sessionRepository = SessionRepository(
                plexClient = plexClient,
                jellyfinClient = jellyfinClient,
                providerRegistry = providerRegistry,
                database = database,
                storage = storage,
                databaseWriteGate = databaseWriteGate,
            )
            sessionRepository.restore(refreshConnections = false)
            mediaSourcesRepository.restore()
            searchHistoryRepository.restore()
            val catalogRepository = CatalogRepository(
                plexClient = plexClient,
                jellyfinClient = jellyfinClient,
                embyClient = embyClient,
                subsonicClient = subsonicClient,
                providerRegistry = providerRegistry,
                database = database,
                storage = storage,
                httpClient = httpClient,
                mediaSourcesRepository = mediaSourcesRepository,
                databaseWriteGate = databaseWriteGate,
            )
            return AppDependencies(
                database = database,
                databaseWriteGate = databaseWriteGate,
                sessionRepository = sessionRepository,
                mediaSourcesRepository = mediaSourcesRepository,
                catalogRepository = catalogRepository,
                libraryUiRepository = libraryUiRepository,
                lyricsRepository = LyricsRepository(database, httpClient),
                playHistoryRepository = playHistoryRepository,
                appSettingsRepository = appSettingsRepository,
                searchHistoryRepository = searchHistoryRepository,
                providerRegistry = providerRegistry,
                plexPlayHistorySyncer = PlexPlayHistorySyncer(
                    plexClient = plexClient,
                    playHistoryRepository = playHistoryRepository,
                    catalogRepository = catalogRepository,
                ),
                jellyfinPlayHistorySyncer = JellyfinPlayHistorySyncer(
                    jellyfinClient = jellyfinClient,
                    embyClient = embyClient,
                    playHistoryRepository = playHistoryRepository,
                    catalogRepository = catalogRepository,
                ),
                navidromePlayHistorySyncer = NavidromePlayHistorySyncer(
                    subsonicClient = subsonicClient,
                    playHistoryRepository = playHistoryRepository,
                    catalogRepository = catalogRepository,
                ),
                plexPlaybackReporter = PlexPlaybackReporter(
                    plexClient = plexClient,
                    jellyfinClient = jellyfinClient,
                    providerRegistry = providerRegistry,
                    audioPlayer = audioPlayer,
                    session = sessionRepository.session,
                ),
                listenBrainzAccountRepository = listenBrainzAccountRepository,
                listenBrainzPlaybackReporter = ListenBrainzPlaybackReporter(
                    client = listenBrainzClient,
                    credentialStore = secureCredentialStore,
                    accountRepository = listenBrainzAccountRepository,
                    audioPlayer = audioPlayer,
                    appSettings = appSettingsRepository.settings,
                ),
                secureCredentialStore = secureCredentialStore,
                audioPlayer = audioPlayer,
                castController = castController,
                systemVolume = createSystemVolumeController(),
                downloadNotifier = DownloadNotifier(),
                updateRepository = GitHubReleaseUpdateRepository(
                    httpClient = httpClient,
                    installer = updateInstaller,
                ),
                updateInstaller = updateInstaller,
                platformStorage = storage,
            )
        }
    }
}
