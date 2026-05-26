package com.phoebe.app.platform

import com.phoebe.app.AppDependencies
import com.phoebe.app.data.CatalogRepository
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

internal object AndroidDownloadRuntime {
    private val installMutex = Mutex()

    @Volatile
    private var dependencies: AppDependencies? = null

    fun install(dependencies: AppDependencies) {
        this.dependencies = dependencies
    }

    suspend fun catalogRepository(): CatalogRepository {
        dependencies?.catalogRepository?.let { return it }
        return installMutex.withLock {
            dependencies?.catalogRepository ?: AppDependencies.create()
                .also { dependencies = it }
                .catalogRepository
        }
    }
}
