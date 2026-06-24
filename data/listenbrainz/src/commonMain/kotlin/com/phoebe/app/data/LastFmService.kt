package com.phoebe.app.data

import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import kotlinx.coroutines.withTimeout

@SingleIn(AppScope::class)
@Inject
class LastFmService(
    private val accountRepository: LastFmAccountRepository,
    private val client: LastFmClient,
) {
    suspend fun connect(apiKey: String, sharedSecret: String, sessionKey: String, timeoutMs: Long): String {
        val validation = withTimeout(timeoutMs) {
            accountRepository.connect(apiKey, sharedSecret, sessionKey)
        }
        return "Last.fm connected as ${validation.username}."
    }

    suspend fun createAuthorizationRequest(apiKey: String, sharedSecret: String, timeoutMs: Long): LastFmAuthorizationRequest {
        val normalizedApiKey = apiKey.trim()
        val normalizedSecret = sharedSecret.trim()
        if (normalizedApiKey.isBlank()) error("Enter a Last.fm API key.")
        if (normalizedSecret.isBlank()) error("Enter a Last.fm shared secret.")
        val token = try {
            withTimeout(timeoutMs) {
                client.getToken(normalizedApiKey, normalizedSecret)
            }
        } catch (error: Exception) {
            accountRepository.recordAuthorizationFailure(
                normalizedApiKey,
                error.message ?: "Couldn't start Last.fm authorization.",
            )
            throw error
        }
        return LastFmAuthorizationRequest(
            apiKey = normalizedApiKey,
            sharedSecret = normalizedSecret,
            token = token,
            authorizationUrl = client.authorizationUrl(normalizedApiKey, token),
        )
    }

    suspend fun connectAuthorizedToken(apiKey: String, sharedSecret: String, token: String, timeoutMs: Long): String {
        val session = withTimeout(timeoutMs) {
            client.getSession(apiKey, sharedSecret, token)
        }
        return connect(apiKey, sharedSecret, session.key, timeoutMs)
    }

    suspend fun disconnect(): String {
        accountRepository.disconnect()
        return "Last.fm disconnected."
    }

    suspend fun setSubmitNowPlaying(enabled: Boolean) {
        accountRepository.setSubmitNowPlaying(enabled)
    }

    suspend fun setSubmitScrobbles(enabled: Boolean) {
        accountRepository.setSubmitScrobbles(enabled)
    }
}

data class LastFmAuthorizationRequest(
    val apiKey: String,
    val sharedSecret: String,
    val token: String,
    val authorizationUrl: String,
)
