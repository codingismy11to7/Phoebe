package com.phoebe.app.player

import com.phoebe.app.AndroidContextHolder
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.io.File

object AndroidPlaybackRuntime {
    private val installScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val installMutex = Mutex()

    @Volatile
    var catalogBrowseSource: CatalogBrowseSource? = null
        private set

    @Volatile
    var artworkFileResolver: ArtworkFileResolver? = null
        private set

    private var dependenciesFactory: (suspend () -> PlaybackRuntimeDependencies)? = null

    fun installFactory(factory: suspend () -> PlaybackRuntimeDependencies) {
        dependenciesFactory = factory
    }

    fun install(dependencies: PlaybackRuntimeDependencies) {
        catalogBrowseSource = CatalogBrowseSourceImpl(
            database = dependencies.database,
            catalogRepository = dependencies.catalogRepository,
            sessionRepository = dependencies.sessionRepository,
        )
        artworkFileResolver = ArtworkFileResolver(
            database = dependencies.database,
            catalogRepository = dependencies.catalogRepository,
            cacheDir = File(AndroidContextHolder.application.cacheDir, "aaos-artwork"),
        )
    }

    /** Warm the browse tree before Compose starts (Android Auto can connect first). */
    fun ensureInstalled() {
        if (catalogBrowseSource != null) return
        installScope.launch {
            ensureInstalledNow()
        }
    }

    suspend fun ensureInstalledNow(): CatalogBrowseSource {
        catalogBrowseSource?.let { return it }
        return installMutex.withLock {
            catalogBrowseSource?.let { return@withLock it }
            val factory = dependenciesFactory ?: error("Android playback runtime has not been installed.")
            install(factory())
            checkNotNull(catalogBrowseSource)
        }
    }

    suspend fun ensureArtworkResolver(): ArtworkFileResolver {
        ensureInstalledNow()
        return checkNotNull(artworkFileResolver)
    }
}
