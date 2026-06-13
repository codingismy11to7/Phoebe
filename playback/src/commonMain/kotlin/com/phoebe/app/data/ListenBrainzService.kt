package com.phoebe.app.data

import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import kotlinx.coroutines.withTimeout

@SingleIn(AppScope::class)
@Inject
class ListenBrainzService(
    private val accountRepository: ListenBrainzAccountRepository,
    private val playbackReporter: ListenBrainzPlaybackReporter,
) {
    suspend fun connect(userToken: String, timeoutMs: Long): String {
        val validation = withTimeout(timeoutMs) {
            accountRepository.connect(userToken)
        }
        return "ListenBrainz connected as ${validation.username}."
    }

    suspend fun disconnect(): String {
        accountRepository.disconnect()
        return "ListenBrainz disconnected."
    }

    suspend fun setSubmitNowPlaying(enabled: Boolean) {
        accountRepository.setSubmitNowPlaying(enabled)
    }

    suspend fun setSubmitListens(enabled: Boolean) {
        accountRepository.setSubmitListens(enabled)
    }

    suspend fun setSubmitCurrentTrackFeedback(enabled: Boolean) {
        accountRepository.setSubmitCurrentTrackFeedback(enabled)
    }

    suspend fun submitCurrentTrackFeedback(score: ListenBrainzFeedbackScore): String {
        val submitted = playbackReporter.submitCurrentTrackFeedback(score)
        return when {
            !submitted -> "ListenBrainz feedback is not available for this play yet."
            score == ListenBrainzFeedbackScore.Love -> "Marked loved on ListenBrainz."
            score == ListenBrainzFeedbackScore.Hate -> "Marked hated on ListenBrainz."
            else -> "Cleared ListenBrainz feedback."
        }
    }
}
