package com.phoebe.app.player

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

object AndroidPlaybackRuntime {
    private val installScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val installMutex = Mutex()

    @Volatile
    var catalogBrowseSource: CatalogBrowseSource? = null
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
}
