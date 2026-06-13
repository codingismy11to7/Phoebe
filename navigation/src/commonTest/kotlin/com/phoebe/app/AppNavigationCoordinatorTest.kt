package com.phoebe.app

import com.phoebe.app.domain.MusicLibrary
import com.phoebe.app.domain.PlexServer
import com.phoebe.app.domain.PlexSession
import com.phoebe.app.ui.AppNavigationCoordinator
import com.phoebe.app.ui.AppNavigationRequest
import com.phoebe.app.ui.AppNavigationService
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class AppNavigationCoordinatorTest {
    @Test
    fun initialRequestUsesEnabledLocalFoldersAsBrowseHome() {
        val coordinator = AppNavigationCoordinator()

        assertEquals(
            AppNavigationRequest.Home,
            coordinator.initialRequest(
                session = null,
                hasEnabledLocalFolders = true,
            ),
        )
    }

    @Test
    fun restoredBrowseRequestIgnoresLocalOnlyStartupState() {
        val coordinator = AppNavigationCoordinator()

        coordinator.reconcileBrowseScreenIfNeeded(session = null)

        assertTrue(coordinator.requests.replayCache.isEmpty())
    }

    @Test
    fun restoredBrowseRequestPublishesRemoteSessionDestination() {
        val coordinator = AppNavigationCoordinator()

        coordinator.reconcileBrowseScreenIfNeeded(
            session = PlexSession(
                token = "token",
                selectedServer = PlexServer(
                    id = "server-1",
                    name = "Music",
                    uri = "https://music.example",
                    owned = true,
                ),
            ),
        )

        assertEquals(
            listOf(AppNavigationRequest.LibraryPicker),
            coordinator.requests.replayCache,
        )
    }

    @Test
    fun selectedLibraryWinsOverEarlierSetupRoutes() {
        val coordinator = AppNavigationCoordinator()

        assertEquals(
            AppNavigationRequest.Home,
            coordinator.initialRequest(
                session = PlexSession(
                    token = "token",
                    selectedServer = PlexServer(
                        id = "server-1",
                        name = "Music",
                        uri = "https://music.example",
                        owned = true,
                    ),
                    selectedLibrary = MusicLibrary(
                        key = "library-1",
                        title = "Music",
                    ),
                ),
                hasEnabledLocalFolders = false,
            ),
        )
    }

    @Test
    fun navigationServiceDelegatesRequestStream() {
        val coordinator = AppNavigationCoordinator()
        val service = AppNavigationService(coordinator)

        service.request(AppNavigationRequest.Player)

        assertEquals(
            listOf(AppNavigationRequest.Player),
            service.requests.replayCache,
        )
        assertEquals(
            service.requests.replayCache,
            coordinator.requests.replayCache,
        )
    }

    @Test
    fun navigationServiceKeepsInitialRequestPolicy() {
        val service = AppNavigationService(AppNavigationCoordinator())

        assertEquals(
            AppNavigationRequest.Home,
            service.initialRequest(
                session = null,
                hasEnabledLocalFolders = true,
            ),
        )
    }
}
