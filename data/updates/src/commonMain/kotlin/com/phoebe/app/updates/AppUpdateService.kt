package com.phoebe.app.updates

import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn

@SingleIn(AppScope::class)
@Inject
class AppUpdateService(
    private val coordinator: AppUpdateCoordinator,
) {
    val state = coordinator.state
    val pendingInstallConfirmation = coordinator.pendingInstallConfirmation

    suspend fun checkForUpdates(onFailure: (Throwable) -> Unit = {}) {
        coordinator.checkForUpdates(onFailure)
    }

    suspend fun installAvailableUpdate(onMessage: (String) -> Unit = {}) {
        coordinator.installAvailableUpdate(onMessage)
    }

    fun respondToInstallConfirmation(install: Boolean) {
        coordinator.respondToInstallConfirmation(install)
    }
}
