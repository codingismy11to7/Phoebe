package com.phoebe.app.ui

import com.phoebe.app.domain.PlexSession
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import kotlinx.coroutines.flow.SharedFlow

@SingleIn(AppScope::class)
@Inject
class AppNavigationService(
    private val coordinator: AppNavigationCoordinator,
) {
    val requests: SharedFlow<AppNavigationRequest> = coordinator.requests

    fun request(request: AppNavigationRequest) {
        coordinator.request(request)
    }

    fun initialRequest(
        session: PlexSession?,
        hasEnabledLocalFolders: Boolean,
    ): AppNavigationRequest =
        coordinator.initialRequest(
            session = session,
            hasEnabledLocalFolders = hasEnabledLocalFolders,
        )

    fun reconcileBrowseScreenIfNeeded(session: PlexSession?) {
        coordinator.reconcileBrowseScreenIfNeeded(session)
    }
}
