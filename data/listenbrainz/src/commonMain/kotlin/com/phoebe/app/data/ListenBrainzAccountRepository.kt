package com.phoebe.app.data

import com.phoebe.app.domain.ListenBrainzCredentialStorageStatus
import com.phoebe.app.domain.ListenBrainzSettings
import com.phoebe.app.platform.SecureCredentialAvailability
import com.phoebe.app.platform.SecureCredentialKey
import com.phoebe.app.platform.SecureCredentialStore
import com.phoebe.app.platform.currentTimeMs
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn

interface ListenBrainzAccountActions {
    suspend fun disconnect(lastError: String? = null)
    suspend fun markSubmitted()
    suspend fun markNowPlayingSubmitted()
    suspend fun markListenSubmitted()
    suspend fun markListenSubmissionFailed(message: String)
}

@SingleIn(AppScope::class)
@Inject
class ListenBrainzAccountRepository(
    private val client: ListenBrainzClient,
    private val appSettingsRepository: AppSettingsRepository,
    private val credentialStore: SecureCredentialStore,
) : ListenBrainzAccountActions {
    val storageAvailability: SecureCredentialAvailability
        get() = credentialStore.availability

    suspend fun restore() {
        val settings = appSettingsRepository.settings.value.listenBrainz
        if (!settings.connected) return
        val token = credentialStore.read(SecureCredentialKey.ListenBrainzUserToken)
        if (token.isNullOrBlank()) {
            appSettingsRepository.setListenBrainzSettings(
                ListenBrainzSettings.Disconnected.copy(
                    storageStatus = credentialStore.availability.status,
                    lastError = "ListenBrainz credential was not found.",
                ),
            )
        } else if (settings.storageStatus != credentialStore.availability.status) {
            appSettingsRepository.updateListenBrainzSettings {
                it.copy(storageStatus = credentialStore.availability.status)
            }
        }
    }

    suspend fun connect(userToken: String): ListenBrainzTokenValidation {
        val token = userToken.trim()
        if (token.isBlank()) error("Enter a ListenBrainz user token.")
        if (!credentialStore.availability.canWrite) error(credentialStore.availability.description)

        return try {
            val validation = client.validateToken(token)
            if (!validation.valid || validation.username.isNullOrBlank()) {
                credentialStore.delete(SecureCredentialKey.ListenBrainzUserToken)
                appSettingsRepository.updateListenBrainzSettings {
                    it.copy(
                        enabled = false,
                        username = null,
                        storageStatus = credentialStore.availability.status,
                        lastValidatedAtMs = currentTimeMs(),
                        lastError = validation.message ?: "ListenBrainz token is invalid.",
                    )
                }
                error(validation.message ?: "ListenBrainz token is invalid.")
            }

            credentialStore.write(SecureCredentialKey.ListenBrainzUserToken, token)
            val now = currentTimeMs()
            appSettingsRepository.setListenBrainzSettings(
                ListenBrainzSettings(
                    enabled = true,
                    username = validation.username,
                    submitNowPlaying = true,
                    submitListens = true,
                    submitCurrentTrackFeedback = true,
                    storageStatus = credentialStore.availability.status,
                    connectedAtMs = now,
                    lastValidatedAtMs = now,
                ),
            )
            validation
        } catch (error: Exception) {
            if (error.message != "Enter a ListenBrainz user token." &&
                error.message != credentialStore.availability.description
            ) {
                val message = error.message ?: "Couldn't connect ListenBrainz."
                appSettingsRepository.updateListenBrainzSettings {
                    it.copy(
                        storageStatus = credentialStore.availability.status,
                        lastValidatedAtMs = currentTimeMs(),
                        lastError = message,
                    )
                }
            }
            throw error
        }
    }

    override suspend fun disconnect(lastError: String?) {
        credentialStore.delete(SecureCredentialKey.ListenBrainzUserToken)
        appSettingsRepository.setListenBrainzSettings(
            ListenBrainzSettings.Disconnected.copy(
                storageStatus = credentialStore.availability.status,
                lastError = lastError,
            ),
        )
    }

    suspend fun setSubmitNowPlaying(enabled: Boolean) {
        appSettingsRepository.updateListenBrainzSettings { it.copy(submitNowPlaying = enabled) }
    }

    suspend fun setSubmitListens(enabled: Boolean) {
        appSettingsRepository.updateListenBrainzSettings { it.copy(submitListens = enabled) }
    }

    suspend fun setSubmitCurrentTrackFeedback(enabled: Boolean) {
        appSettingsRepository.updateListenBrainzSettings { it.copy(submitCurrentTrackFeedback = enabled) }
    }

    override suspend fun markSubmitted() {
        appSettingsRepository.updateListenBrainzSettings {
            it.copy(lastSubmittedAtMs = currentTimeMs(), lastError = null)
        }
    }

    override suspend fun markNowPlayingSubmitted() {
        appSettingsRepository.updateListenBrainzSettings {
            val now = currentTimeMs()
            it.copy(
                lastSubmittedAtMs = now,
                lastNowPlayingSubmittedAtMs = now,
            )
        }
    }

    override suspend fun markListenSubmitted() {
        appSettingsRepository.updateListenBrainzSettings {
            val now = currentTimeMs()
            it.copy(
                lastSubmittedAtMs = now,
                lastListenSubmittedAtMs = now,
                lastListenError = null,
                lastError = null,
            )
        }
    }

    override suspend fun markListenSubmissionFailed(message: String) {
        appSettingsRepository.updateListenBrainzSettings {
            it.copy(
                lastListenError = message,
                lastError = message,
            )
        }
    }

    suspend fun clearError() {
        appSettingsRepository.updateListenBrainzSettings { it.copy(lastListenError = null, lastError = null) }
    }
}

internal fun SecureCredentialAvailability.statusLabel(): String =
    when (status) {
        ListenBrainzCredentialStorageStatus.PersistentSecure -> description
        ListenBrainzCredentialStorageStatus.PersistentBrowser -> description
        ListenBrainzCredentialStorageStatus.SessionOnly -> description
        ListenBrainzCredentialStorageStatus.Unavailable -> description
        ListenBrainzCredentialStorageStatus.Unknown -> "Credential storage unknown"
    }
