package com.phoebe.app.player

import com.phoebe.app.AppDependencies
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

object AndroidPlaybackRuntime {
    private val installScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    @Volatile
    var catalogBrowseSource: CatalogBrowseSource? = null
        private set

    fun install(dependencies: AppDependencies) {
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
            if (catalogBrowseSource != null) return@launch
            install(AppDependencies.create())
        }
    }
}
