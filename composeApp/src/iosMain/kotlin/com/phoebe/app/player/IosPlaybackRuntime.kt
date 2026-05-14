package com.phoebe.app.player

import com.phoebe.app.AppDependencies
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

object IosPlaybackRuntime {
    private val installScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    private var catalogBrowseSource: IosCatalogBrowseSource? = null

    internal fun browseSource(): IosCatalogBrowseSource? = catalogBrowseSource

    fun install(dependencies: AppDependencies) {
        catalogBrowseSource = IosCatalogBrowseSource(
            database = dependencies.database,
            catalogRepository = dependencies.catalogRepository,
            sessionRepository = dependencies.sessionRepository,
        )
    }

    /** Warm browse data before Compose starts (CarPlay can connect first). */
    fun ensureInstalled() {
        if (catalogBrowseSource != null) return
        installScope.launch {
            if (catalogBrowseSource != null) return@launch
            install(AppDependencies.create())
        }
    }
}
