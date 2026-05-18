package com.phoebe.app

import com.phoebe.app.data.CatalogRepository
import com.phoebe.app.data.EmbyClient
import com.phoebe.app.data.LibraryUiRepository
import com.phoebe.app.data.JellyfinClient
import com.phoebe.app.data.JellyfinPlayHistorySyncer
import com.phoebe.app.data.LyricsRepository
import com.phoebe.app.data.MediaSourcesRepository
import com.phoebe.app.data.MusicAssistantClient
import com.phoebe.app.data.MusicAssistantProviderAdapter
import com.phoebe.app.data.MusicProviderRegistry
import com.phoebe.app.data.NavidromeProviderAdapter
import com.phoebe.app.data.PlayHistoryRepository
import com.phoebe.app.data.PlexClient
import com.phoebe.app.data.PlexPlayHistorySyncer
import com.phoebe.app.data.PlexPlaybackReporter
import com.phoebe.app.data.EmbyProviderAdapter
import com.phoebe.app.data.JellyfinProviderAdapter
import com.phoebe.app.data.SessionRepository
import com.phoebe.app.data.SubsonicClient
import com.phoebe.app.data.db.clearAllAppData
import com.phoebe.app.data.db.createPhoebeDatabase
import com.phoebe.app.db.PhoebeDatabase
import com.phoebe.app.platform.PlatformStorage
import com.phoebe.app.platform.createPlatformHttpClient
import com.phoebe.app.player.AudioPlayer
import com.phoebe.app.player.CastController
import com.phoebe.app.player.SystemVolumeController
import com.phoebe.app.player.createAudioPlayer
import com.phoebe.app.player.createCastController
import com.phoebe.app.player.createSystemVolumeController

class AppDependencies(
    val database: PhoebeDatabase,
    val sessionRepository: SessionRepository,
    val mediaSourcesRepository: MediaSourcesRepository,
    val catalogRepository: CatalogRepository,
    val libraryUiRepository: LibraryUiRepository,
    val lyricsRepository: LyricsRepository,
    val playHistoryRepository: PlayHistoryRepository,
    val providerRegistry: MusicProviderRegistry,
    val plexPlayHistorySyncer: PlexPlayHistorySyncer,
    val jellyfinPlayHistorySyncer: JellyfinPlayHistorySyncer,
    val plexPlaybackReporter: PlexPlaybackReporter,
    val audioPlayer: AudioPlayer,
    val castController: CastController,
    val systemVolume: SystemVolumeController,
    /** File-backed on desktop; NSUserDefaults keys on iOS; etc. Used for lightweight UI prefs. */
    val platformStorage: PlatformStorage,
) {
    suspend fun deleteDatabaseDataForSignOut() {
        database.clearAllAppData(clearPlayHistory = false)
        listOf("session.json", "catalog.json", "media_sources.json", "library_ui_prefs.json").forEach {
            platformStorage.delete(it)
        }
        catalogRepository.clearInMemoryCatalog()
        mediaSourcesRepository.clearInMemoryState()
        libraryUiRepository.resetInMemoryState()
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
            val providerRegistry = MusicProviderRegistry(
                listOf(
                    JellyfinProviderAdapter(jellyfinClient),
                    EmbyProviderAdapter(embyClient),
                    NavidromeProviderAdapter(subsonicClient),
                    MusicAssistantProviderAdapter(musicAssistantClient),
                ),
            )
            val storage = PlatformStorage()
            val database = createPhoebeDatabase()
            val mediaSourcesRepository = MediaSourcesRepository(database, storage)
            val libraryUiRepository = LibraryUiRepository(database, storage)
            val playHistoryRepository = PlayHistoryRepository(database)
            val audioPlayer = createAudioPlayer()
            val castController = createCastController(audioPlayer)
            val sessionRepository = SessionRepository(plexClient, jellyfinClient, providerRegistry, database, storage)
            sessionRepository.restore(refreshConnections = false)
            mediaSourcesRepository.restore()
            return AppDependencies(
                database = database,
                sessionRepository = sessionRepository,
                mediaSourcesRepository = mediaSourcesRepository,
                catalogRepository = CatalogRepository(
                    plexClient = plexClient,
                    jellyfinClient = jellyfinClient,
                    embyClient = embyClient,
                    providerRegistry = providerRegistry,
                    database = database,
                    storage = storage,
                    httpClient = httpClient,
                    mediaSourcesRepository = mediaSourcesRepository,
                ),
                libraryUiRepository = libraryUiRepository,
                lyricsRepository = LyricsRepository(database, httpClient),
                playHistoryRepository = playHistoryRepository,
                providerRegistry = providerRegistry,
                plexPlayHistorySyncer = PlexPlayHistorySyncer(
                    plexClient = plexClient,
                    playHistoryRepository = playHistoryRepository,
                ),
                jellyfinPlayHistorySyncer = JellyfinPlayHistorySyncer(
                    jellyfinClient = jellyfinClient,
                    embyClient = embyClient,
                    playHistoryRepository = playHistoryRepository,
                ),
                plexPlaybackReporter = PlexPlaybackReporter(
                    plexClient = plexClient,
                    jellyfinClient = jellyfinClient,
                    providerRegistry = providerRegistry,
                    audioPlayer = audioPlayer,
                    session = sessionRepository.session,
                ),
                audioPlayer = audioPlayer,
                castController = castController,
                systemVolume = createSystemVolumeController(),
                platformStorage = storage,
            )
        }
    }
}
