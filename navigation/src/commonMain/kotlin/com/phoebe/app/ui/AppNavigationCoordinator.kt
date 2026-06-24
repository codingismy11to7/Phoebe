package com.phoebe.app.ui

import com.phoebe.app.domain.PlexSession
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow

@SingleIn(AppScope::class)
@Inject
class AppNavigationCoordinator {
    private val mutableRequests = MutableSharedFlow<AppNavigationRequest>(
        replay = 1,
        extraBufferCapacity = 32,
    )

    val requests: SharedFlow<AppNavigationRequest> = mutableRequests.asSharedFlow()

    fun request(request: AppNavigationRequest) {
        mutableRequests.tryEmit(request)
    }

    fun initialRequest(
        session: PlexSession?,
        hasEnabledLocalFolders: Boolean,
    ): AppNavigationRequest = defaultBrowseRequest(
        session = session,
        hasEnabledLocalFolders = hasEnabledLocalFolders,
    )

    fun reconcileBrowseScreenIfNeeded(session: PlexSession?) {
        val target = restoredBrowseRequest(session)
        if (target != AppNavigationRequest.SignIn) {
            request(target)
        }
    }

    fun restoredBrowseRequest(session: PlexSession?): AppNavigationRequest = when {
        session?.selectedLibrary != null -> AppNavigationRequest.Home
        session?.selectedServer != null -> AppNavigationRequest.LibraryPicker
        session?.token?.isNotBlank() == true -> AppNavigationRequest.ServerPicker
        else -> AppNavigationRequest.SignIn
    }

    fun defaultBrowseRequest(
        session: PlexSession?,
        hasEnabledLocalFolders: Boolean,
    ): AppNavigationRequest = when {
        session?.selectedLibrary != null -> AppNavigationRequest.Home
        session?.selectedServer != null -> AppNavigationRequest.LibraryPicker
        session?.token?.isNotBlank() == true -> AppNavigationRequest.ServerPicker
        hasEnabledLocalFolders -> AppNavigationRequest.Home
        else -> AppNavigationRequest.SignIn
    }
}
