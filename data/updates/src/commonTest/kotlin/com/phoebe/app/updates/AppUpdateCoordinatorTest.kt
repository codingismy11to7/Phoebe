package com.phoebe.app.updates

import com.phoebe.app.data.PhoebeDataJson
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.MockRequestHandleScope
import io.ktor.client.engine.mock.respond
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.http.HttpHeaders
import io.ktor.http.headersOf
import io.ktor.serialization.kotlinx.json.json
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotNull

class AppUpdateCoordinatorTest {
    @Test
    fun checkForUpdatesPublishesAvailableUpdate() = runTest {
        val coordinator = AppUpdateCoordinator(
            repository = updateRepositoryForVersion("1.2.3"),
            installer = ConfirmingInstaller(UpdatePlatform.Android),
        )

        coordinator.checkForUpdates()

        val state = assertIs<AppUpdateState.Available>(coordinator.state.value)
        assertEquals("1.2.3", state.update.versionName)
        assertEquals("Phoebe 1.2.3", state.update.releaseName)
    }

    @Test
    fun installAvailableUpdateExposesConfirmationAndPublishesMessages() = runTest {
        val installer = ConfirmingInstaller(UpdatePlatform.MacOs)
        val coordinator = AppUpdateCoordinator(
            repository = updateRepositoryForVersion("1.2.3"),
            installer = installer,
        )
        val messages = mutableListOf<String>()
        coordinator.checkForUpdates()

        val installJob = backgroundScope.launch {
            coordinator.installAvailableUpdate(messages::add)
        }
        runCurrent()

        val pendingUpdate = assertNotNull(coordinator.pendingInstallConfirmation.value)
        assertEquals("1.2.3", pendingUpdate.versionName)
        val installing = assertIs<AppUpdateState.Installing>(coordinator.state.value)
        assertEquals("Ready to install Phoebe 1.2.3.", installing.message)
        assertEquals(1f, installing.progress)

        coordinator.respondToInstallConfirmation(true)
        installJob.join()

        assertEquals(
            listOf("Downloading Phoebe 1.2.3...", "Installer started"),
            messages,
        )
        val finalState = assertIs<AppUpdateState.Installing>(coordinator.state.value)
        assertEquals("Installer started", finalState.message)
        assertEquals(true, installer.confirmed)
    }

    @Test
    fun appUpdateServiceDelegatesStateAndCommands() = runTest {
        val installer = ConfirmingInstaller(UpdatePlatform.Android)
        val service = AppUpdateService(
            AppUpdateCoordinator(
                repository = updateRepositoryForVersion("2.0.0"),
                installer = installer,
            ),
        )

        service.checkForUpdates()
        val available = assertIs<AppUpdateState.Available>(service.state.value)
        assertEquals("2.0.0", available.update.versionName)

        val installJob = backgroundScope.launch {
            service.installAvailableUpdate()
        }
        runCurrent()

        assertEquals("2.0.0", service.pendingInstallConfirmation.value?.versionName)
        service.respondToInstallConfirmation(true)
        installJob.join()

        assertEquals(true, installer.confirmed)
        val installing = assertIs<AppUpdateState.Installing>(service.state.value)
        assertEquals("Installer started", installing.message)
    }
}

private class ConfirmingInstaller(
    override val platform: UpdatePlatform,
) : PlatformUpdateInstaller {
    var confirmed: Boolean? = null
        private set

    override suspend fun install(
        update: AvailableUpdate,
        onProgress: (UpdateInstallProgress) -> Unit,
        confirmInstall: suspend (AvailableUpdate) -> Boolean,
    ): UpdateInstallResult {
        onProgress(UpdateInstallProgress(UpdateInstallPhase.Downloading, "Downloading", 0.5f))
        confirmed = confirmInstall(update)
        return if (confirmed == true) {
            UpdateInstallResult.Started("Installer started")
        } else {
            UpdateInstallResult.RequiresUserAction("Install cancelled")
        }
    }
}

private fun updateRepositoryForVersion(version: String): GitHubReleaseUpdateRepository =
    GitHubReleaseUpdateRepository(
        httpClient = coordinatorTestHttpClient(
            MockEngine {
                respondJson(
                    """
                    {
                      "tag_name": "release/$version",
                      "html_url": "https://github.com/j-roskopf/Phoebe/releases/tag/release/$version",
                      "name": "Phoebe $version",
                      "body": "Bug fixes",
                      "draft": false,
                      "prerelease": false,
                      "assets": [
                        {
                          "name": "Phoebe-$version.apk",
                          "browser_download_url": "https://example.test/Phoebe-$version.apk",
                          "size": 42,
                          "digest": null,
                          "state": "uploaded"
                        }
                      ]
                    }
                    """.trimIndent(),
                )
            },
        ),
        installer = ConfirmingInstaller(UpdatePlatform.Android),
        currentVersionName = "1.0.0",
        githubOwner = "j-roskopf",
        githubRepo = "Phoebe",
    )

private fun MockRequestHandleScope.respondJson(content: String) = respond(
    content = content,
    headers = headersOf(HttpHeaders.ContentType, "application/json"),
)

private fun coordinatorTestHttpClient(engine: MockEngine): HttpClient =
    HttpClient(engine) {
        install(ContentNegotiation) {
            json(PhoebeDataJson)
        }
    }
