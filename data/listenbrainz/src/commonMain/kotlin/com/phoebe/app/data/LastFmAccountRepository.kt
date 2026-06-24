package com.phoebe.app.data

import com.phoebe.app.domain.LastFmSettings
import com.phoebe.app.platform.SecureCredentialAvailability
import com.phoebe.app.platform.SecureCredentialKey
import com.phoebe.app.platform.SecureCredentialStore
import com.phoebe.app.platform.currentTimeMs
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn

interface LastFmAccountActions {
    suspend fun disconnect(lastError: String? = null)
    suspend fun markNowPlayingSubmitted()
    suspend fun markScrobbleSubmitted()
    suspend fun markScrobbleSubmissionFailed(message: String)
}

@SingleIn(AppScope::class)
@Inject
class LastFmAccountRepository(
    private val client: LastFmClient,
    private val appSettingsRepository: AppSettingsRepository,
    private val credentialStore: SecureCredentialStore,
) : LastFmAccountActions {
    val storageAvailability: SecureCredentialAvailability
        get() = credentialStore.availability

    suspend fun restore() {
        val settings = appSettingsRepository.settings.value.lastFm
        if (!settings.connected) return
        val credentials = readCredentialsOrNull(settings.apiKey)
        if (credentials == null) {
            appSettingsRepository.setLastFmSettings(
                LastFmSettings.Disconnected.copy(
                    storageStatus = credentialStore.availability.status,
                    lastError = "Last.fm credentials were not found.",
                ),
            )
        } else if (settings.storageStatus != credentialStore.availability.status) {
            appSettingsRepository.updateLastFmSettings {
                it.copy(storageStatus = credentialStore.availability.status)
            }
        }
    }

    suspend fun connect(apiKey: String, sharedSecret: String, sessionKey: String): LastFmSessionValidation {
        val normalizedApiKey = apiKey.trim()
        val normalizedSecret = sharedSecret.trim()
        val normalizedSessionKey = sessionKey.trim()
        if (normalizedApiKey.isBlank()) error("Enter a Last.fm API key.")
        if (normalizedSecret.isBlank()) error("Enter a Last.fm shared secret.")
        if (normalizedSessionKey.isBlank()) error("Enter a Last.fm session key.")
        if (!credentialStore.availability.canWrite) error(credentialStore.availability.description)

        return try {
            val validation = client.validateSession(normalizedApiKey, normalizedSecret, normalizedSessionKey)
            if (!validation.valid || validation.username.isNullOrBlank()) {
                deleteCredentials()
                appSettingsRepository.updateLastFmSettings {
                    it.copy(
                        enabled = false,
                        username = null,
                        apiKey = normalizedApiKey,
                        storageStatus = credentialStore.availability.status,
                        lastValidatedAtMs = currentTimeMs(),
                        lastError = "Last.fm session key is invalid.",
                    )
                }
                error("Last.fm session key is invalid.")
            }

            credentialStore.write(SecureCredentialKey.LastFmSharedSecret, normalizedSecret)
            credentialStore.write(SecureCredentialKey.LastFmSessionKey, normalizedSessionKey)
            val now = currentTimeMs()
            appSettingsRepository.setLastFmSettings(
                LastFmSettings(
                    enabled = true,
                    username = validation.username,
                    apiKey = normalizedApiKey,
                    submitNowPlaying = true,
                    submitScrobbles = true,
                    storageStatus = credentialStore.availability.status,
                    connectedAtMs = now,
                    lastValidatedAtMs = now,
                ),
            )
            validation
        } catch (error: Exception) {
            val message = error.message ?: "Couldn't connect Last.fm."
            appSettingsRepository.updateLastFmSettings {
                it.copy(
                    apiKey = normalizedApiKey.takeIf { key -> key.isNotBlank() },
                    storageStatus = credentialStore.availability.status,
                    lastValidatedAtMs = currentTimeMs(),
                    lastError = message,
                )
            }
            throw error
        }
    }

    suspend fun recordAuthorizationFailure(apiKey: String, message: String) {
        val normalizedApiKey = apiKey.trim()
        appSettingsRepository.updateLastFmSettings {
            it.copy(
                apiKey = normalizedApiKey.takeIf { key -> key.isNotBlank() },
                storageStatus = credentialStore.availability.status,
                lastValidatedAtMs = currentTimeMs(),
                lastError = message,
            )
        }
    }

    override suspend fun disconnect(lastError: String?) {
        deleteCredentials()
        appSettingsRepository.setLastFmSettings(
            LastFmSettings.Disconnected.copy(
                storageStatus = credentialStore.availability.status,
                lastError = lastError,
            ),
        )
    }

    suspend fun setSubmitNowPlaying(enabled: Boolean) {
        appSettingsRepository.updateLastFmSettings { it.copy(submitNowPlaying = enabled) }
    }

    suspend fun setSubmitScrobbles(enabled: Boolean) {
        appSettingsRepository.updateLastFmSettings { it.copy(submitScrobbles = enabled) }
    }

    suspend fun readCredentialsOrNull(apiKey: String?): LastFmCredentials? {
        val normalizedApiKey = apiKey?.trim()?.takeIf { it.isNotBlank() } ?: return null
        val sharedSecret = credentialStore.read(SecureCredentialKey.LastFmSharedSecret)?.trim()?.takeIf { it.isNotBlank() }
            ?: return null
        val sessionKey = credentialStore.read(SecureCredentialKey.LastFmSessionKey)?.trim()?.takeIf { it.isNotBlank() }
            ?: return null
        return LastFmCredentials(normalizedApiKey, sharedSecret, sessionKey)
    }

    override suspend fun markNowPlayingSubmitted() {
        appSettingsRepository.updateLastFmSettings {
            val now = currentTimeMs()
            it.copy(lastSubmittedAtMs = now, lastNowPlayingSubmittedAtMs = now, lastError = null)
        }
    }

    override suspend fun markScrobbleSubmitted() {
        appSettingsRepository.updateLastFmSettings {
            val now = currentTimeMs()
            it.copy(
                lastSubmittedAtMs = now,
                lastScrobbleSubmittedAtMs = now,
                lastScrobbleError = null,
                lastError = null,
            )
        }
    }

    override suspend fun markScrobbleSubmissionFailed(message: String) {
        appSettingsRepository.updateLastFmSettings {
            it.copy(lastScrobbleError = message, lastError = message)
        }
    }

    private suspend fun deleteCredentials() {
        credentialStore.delete(SecureCredentialKey.LastFmSharedSecret)
        credentialStore.delete(SecureCredentialKey.LastFmSessionKey)
    }
}

data class LastFmCredentials(
    val apiKey: String,
    val sharedSecret: String,
    val sessionKey: String,
)
