package com.phoebe.app.player

import com.phoebe.app.AppDependencies

object AndroidPlaybackRuntime {
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
}
