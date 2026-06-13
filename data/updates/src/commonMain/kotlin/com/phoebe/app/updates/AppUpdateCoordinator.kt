package com.phoebe.app.updates

import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

@SingleIn(AppScope::class)
@Inject
class AppUpdateCoordinator(
    private val repository: GitHubReleaseUpdateRepository,
    private val installer: PlatformUpdateInstaller,
) {
    private val mutableState = MutableStateFlow<AppUpdateState>(AppUpdateState.Idle)
    val state: StateFlow<AppUpdateState> = mutableState.asStateFlow()

    private val mutablePendingInstallConfirmation = MutableStateFlow<AvailableUpdate?>(null)
    val pendingInstallConfirmation: StateFlow<AvailableUpdate?> =
        mutablePendingInstallConfirmation.asStateFlow()

    private var pendingInstallConfirmationResponse: CompletableDeferred<Boolean>? = null

    suspend fun checkForUpdates(
        onFailure: (Throwable) -> Unit = {},
    ) {
        if (mutableState.value is AppUpdateState.Installing) return
        mutableState.value = AppUpdateState.Checking
        runCatching {
            repository.checkForUpdate()
        }.onSuccess { update ->
            mutableState.value = if (update == null) {
                AppUpdateState.Current
            } else {
                AppUpdateState.Available(update)
            }
        }.onFailure { error ->
            onFailure(error)
            mutableState.value = AppUpdateState.Failed(error.message ?: "Couldn't check for updates.")
        }
    }

    suspend fun installAvailableUpdate(
        onMessage: (String) -> Unit = {},
    ) {
        val update = when (val state = mutableState.value) {
            is AppUpdateState.Available -> state.update
            is AppUpdateState.Failed -> state.lastKnownUpdate
            is AppUpdateState.Installing -> return
            else -> null
        } ?: return

        val initialMessage = "Downloading Phoebe ${update.versionName}..."
        mutableState.value = AppUpdateState.Installing(update, initialMessage)
        onMessage(initialMessage)
        runCatching {
            installer.install(
                update = update,
                onProgress = { progress ->
                    mutableState.value = AppUpdateState.Installing(
                        update = update,
                        message = progress.message,
                        progress = progress.fraction,
                    )
                },
                confirmInstall = { readyUpdate ->
                    mutableState.value = AppUpdateState.Installing(
                        update = update,
                        message = "Ready to install Phoebe ${readyUpdate.versionName}.",
                        progress = 1f,
                    )
                    requestInstallConfirmation(readyUpdate)
                },
            )
        }.onSuccess { result ->
            onMessage(result.message)
            mutableState.value = when (result) {
                is UpdateInstallResult.Started -> AppUpdateState.Installing(update, result.message)
                is UpdateInstallResult.OpenedReleasePage,
                is UpdateInstallResult.RequiresUserAction,
                -> AppUpdateState.Available(update)
            }
        }.onFailure { error ->
            val message = error.message ?: "Couldn't install the update."
            onMessage(message)
            mutableState.value = AppUpdateState.Failed(message, update)
        }
    }

    fun respondToInstallConfirmation(install: Boolean) {
        pendingInstallConfirmationResponse?.complete(install)
    }

    private suspend fun requestInstallConfirmation(update: AvailableUpdate): Boolean {
        pendingInstallConfirmationResponse?.complete(false)
        val response = CompletableDeferred<Boolean>()
        pendingInstallConfirmationResponse = response
        mutablePendingInstallConfirmation.value = update
        return try {
            response.await()
        } finally {
            if (pendingInstallConfirmationResponse === response) {
                pendingInstallConfirmationResponse = null
                mutablePendingInstallConfirmation.value = null
            }
        }
    }
}
