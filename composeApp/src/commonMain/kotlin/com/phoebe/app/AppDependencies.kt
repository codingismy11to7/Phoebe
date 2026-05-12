package com.phoebe.app

import com.phoebe.app.data.CatalogRepository
import com.phoebe.app.data.LibraryUiRepository
import com.phoebe.app.data.MediaSourcesRepository
import com.phoebe.app.data.PlayHistoryRepository
import com.phoebe.app.data.PlexClient
import com.phoebe.app.data.SessionRepository
import com.phoebe.app.data.db.createPhoebeDatabase
import com.phoebe.app.db.PhoebeDatabase
import com.phoebe.app.platform.PlatformStorage
import com.phoebe.app.platform.createPlatformHttpClient
import com.phoebe.app.player.AudioPlayer
import com.phoebe.app.player.SystemVolumeController
import com.phoebe.app.player.createAudioPlayer
import com.phoebe.app.player.createSystemVolumeController

class AppDependencies(
    val database: PhoebeDatabase,
    val sessionRepository: SessionRepository,
    val mediaSourcesRepository: MediaSourcesRepository,
    val catalogRepository: CatalogRepository,
    val libraryUiRepository: LibraryUiRepository,
    val playHistoryRepository: PlayHistoryRepository,
    val audioPlayer: AudioPlayer,
    val systemVolume: SystemVolumeController,
    /** File-backed on desktop; NSUserDefaults keys on iOS; etc. Used for lightweight UI prefs. */
    val platformStorage: PlatformStorage,
) {
    companion object {
        suspend fun create(): AppDependencies {
            val httpClient = createPlatformHttpClient()
            val plexClient = PlexClient(httpClient)
            val storage = PlatformStorage()
            val database = createPhoebeDatabase()
            val mediaSourcesRepository = MediaSourcesRepository(database, storage)
            val libraryUiRepository = LibraryUiRepository(database, storage)
            return AppDependencies(
                database = database,
                sessionRepository = SessionRepository(plexClient, database, storage),
                mediaSourcesRepository = mediaSourcesRepository,
                catalogRepository = CatalogRepository(
                    plexClient = plexClient,
                    database = database,
                    storage = storage,
                    httpClient = httpClient,
                    mediaSourcesRepository = mediaSourcesRepository,
                ),
                libraryUiRepository = libraryUiRepository,
                playHistoryRepository = PlayHistoryRepository(database),
                audioPlayer = createAudioPlayer(),
                systemVolume = createSystemVolumeController(),
                platformStorage = storage,
            )
        }
    }
}
