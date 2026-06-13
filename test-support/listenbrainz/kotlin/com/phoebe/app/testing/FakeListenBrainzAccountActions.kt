package com.phoebe.app.testing

import com.phoebe.app.data.ListenBrainzAccountActions
import com.phoebe.app.domain.AppSettings
import com.phoebe.app.domain.ListenBrainzSettings
import com.phoebe.app.platform.SecureCredentialKey
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update

class FakeListenBrainzAccountActions(
    private val settings: MutableStateFlow<AppSettings>,
    private val credentialStore: FakeSecureCredentialStore,
    private val nowMs: () -> Long = { 1_700_000_000_000L },
) : ListenBrainzAccountActions {
    var submittedCount = 0
        private set
    var nowPlayingSubmittedCount = 0
        private set
    var listenSubmittedCount = 0
        private set
    var listenFailureCount = 0
        private set
    var disconnectCount = 0
        private set

    override suspend fun disconnect(lastError: String?) {
        disconnectCount++
        credentialStore.delete(SecureCredentialKey.ListenBrainzUserToken)
        settings.update {
            it.copy(
                listenBrainz = ListenBrainzSettings.Disconnected.copy(
                    storageStatus = credentialStore.availability.status,
                    lastError = lastError,
                ),
            )
        }
    }

    override suspend fun markSubmitted() {
        submittedCount++
        settings.update {
            it.copy(
                listenBrainz = it.listenBrainz.copy(
                    lastSubmittedAtMs = nowMs(),
                    lastError = null,
                ),
            )
        }
    }

    override suspend fun markNowPlayingSubmitted() {
        nowPlayingSubmittedCount++
        settings.update {
            val now = nowMs()
            it.copy(
                listenBrainz = it.listenBrainz.copy(
                    lastSubmittedAtMs = now,
                    lastNowPlayingSubmittedAtMs = now,
                ),
            )
        }
    }

    override suspend fun markListenSubmitted() {
        listenSubmittedCount++
        settings.update {
            val now = nowMs()
            it.copy(
                listenBrainz = it.listenBrainz.copy(
                    lastSubmittedAtMs = now,
                    lastListenSubmittedAtMs = now,
                    lastListenError = null,
                    lastError = null,
                ),
            )
        }
    }

    override suspend fun markListenSubmissionFailed(message: String) {
        listenFailureCount++
        settings.update {
            it.copy(
                listenBrainz = it.listenBrainz.copy(
                    lastListenError = message,
                    lastError = message,
                ),
            )
        }
    }
}
