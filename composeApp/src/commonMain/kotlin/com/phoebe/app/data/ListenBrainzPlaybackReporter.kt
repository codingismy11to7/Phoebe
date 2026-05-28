package com.phoebe.app.data

import com.phoebe.app.domain.AppSettings
import com.phoebe.app.domain.PlayerState
import com.phoebe.app.domain.Track
import com.phoebe.app.platform.PhoebeLog
import com.phoebe.app.platform.SecureCredentialKey
import com.phoebe.app.platform.SecureCredentialStore
import com.phoebe.app.platform.currentTimeMs
import com.phoebe.app.player.AudioPlayer
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

data class ListenBrainzFeedbackTarget(
    val trackId: String? = null,
    val recordingMsid: String? = null,
    val score: ListenBrainzFeedbackScore? = null,
    val submittingScore: ListenBrainzFeedbackScore? = null,
    val loadingScore: Boolean = false,
    val enabled: Boolean = false,
) {
    val available: Boolean
        get() = enabled && !recordingMsid.isNullOrBlank()
}

class ListenBrainzPlaybackReporter(
    private val client: ListenBrainzClient,
    private val credentialStore: SecureCredentialStore,
    private val accountRepository: ListenBrainzAccountActions,
    private val audioPlayer: AudioPlayer,
    private val appSettings: StateFlow<AppSettings>,
    private val nowMs: () -> Long = ::currentTimeMs,
) {
    private val mutableFeedbackTarget = MutableStateFlow(ListenBrainzFeedbackTarget())
    val feedbackTarget: StateFlow<ListenBrainzFeedbackTarget> = mutableFeedbackTarget.asStateFlow()

    private val submissionMutex = Mutex()
    private val retryQueue = mutableListOf<ListenBrainzQueuedListen>()
    private val feedbackScoreCache = mutableMapOf<String, ListenBrainzFeedbackScore?>()
    private var activeListen: ActiveListen? = null

    fun start(scope: CoroutineScope) {
        scope.launch { watchPlaybackState() }
        scope.launch { retryQueuedListens() }
    }

    suspend fun submitCurrentTrackFeedback(score: ListenBrainzFeedbackScore): Boolean {
        val target = feedbackTarget.value
        val msid = target.recordingMsid ?: return false
        if (!target.enabled || target.submittingScore != null) return false
        val token = readTokenOrNull() ?: return false
        val submittingScore = if (score == ListenBrainzFeedbackScore.Clear) {
            target.score ?: ListenBrainzFeedbackScore.Clear
        } else {
            score
        }
        updateFeedbackTarget(target.trackId, msid) {
            it.copy(submittingScore = submittingScore)
        }
        return try {
            client.submitRecordingFeedback(token, msid, score)
            accountRepository.markSubmitted()
            val storedScore = score.takeUnless { it == ListenBrainzFeedbackScore.Clear }
            activeListen = activeListen?.takeIf { it.track.id == target.trackId }?.copy(feedbackScore = storedScore)
            feedbackScoreCache[msid] = storedScore
            updateFeedbackTarget(target.trackId, msid) {
                it.copy(score = storedScore, submittingScore = null)
            }
            true
        } catch (error: Throwable) {
            handleSubmissionFailure(error)
            updateFeedbackTarget(target.trackId, msid) {
                it.copy(submittingScore = null)
            }
            false
        }
    }

    internal fun queuedRetryCount(): Int = retryQueue.size

    private suspend fun watchPlaybackState() {
        try {
            combine(audioPlayer.state, appSettings) { player, settings -> player to settings }
                .collect { (player, settings) ->
                    val observedAtMs = nowMs()
                    val listenBrainz = settings.listenBrainz
                    val feedbackEnabled = listenBrainz.submitCurrentTrackFeedback && listenBrainz.submitNowPlaying
                    val observedActive = activeListen?.observedAt(observedAtMs)
                    activeListen = observedActive
                    if (!listenBrainz.connected) {
                        activeListen = null
                        mutableFeedbackTarget.value = ListenBrainzFeedbackTarget()
                        return@collect
                    }
                    val track = player.currentTrack
                    val active = observedActive

                    if (track == null) {
                        submitPermanentListenIfEligible(active, listenBrainz.submitListens)
                        clearPlayingNowIfNeeded(active)
                        activeListen = null
                        mutableFeedbackTarget.value = ListenBrainzFeedbackTarget()
                        return@collect
                    }

                    if (active == null || active.track.id != track.id) {
                        submitPermanentListenIfEligible(active, listenBrainz.submitListens)
                        clearPlayingNowIfNeeded(active)
                        val positionMs = player.positionMs.coerceAtLeast(0L)
                        val startedAtMs = (observedAtMs - positionMs).coerceAtLeast(0L)
                        activeListen = ActiveListen(
                            track = track,
                            startedAtMs = startedAtMs,
                            lastObservedAtMs = observedAtMs,
                            lastObservedPositionMs = positionMs,
                            maxObservedPositionMs = positionMs,
                            durationMs = player.listenBrainzDurationMs(track),
                            lastObservedPlaying = player.isPlaying,
                        )
                        mutableFeedbackTarget.value = ListenBrainzFeedbackTarget(
                            trackId = track.id,
                            enabled = feedbackEnabled,
                        )
                    } else {
                        activeListen = active.observedPlayback(player, observedAtMs)
                        mutableFeedbackTarget.value = mutableFeedbackTarget.value.copy(
                            enabled = feedbackEnabled,
                        )
                    }

                    val current = activeListen ?: return@collect
                    if (player.isPlaying && listenBrainz.submitNowPlaying && !current.playingNowSubmitted) {
                        submitPlayingNow(current, feedbackEnabled, listenBrainz.username)
                    }

                    submitPermanentListenIfEligible(activeListen, listenBrainz.submitListens)

                    if (!player.isPlaying && player.isStoppedAtEnd(track)) {
                        submitPermanentListenIfEligible(activeListen, listenBrainz.submitListens)
                        clearPlayingNowIfNeeded(activeListen)
                    }
                }
        } finally {
            withContext(NonCancellable) {
                clearPlayingNowIfNeeded(activeListen)
            }
        }
    }

    private suspend fun submitPlayingNow(active: ActiveListen, feedbackEnabled: Boolean, username: String?) {
        val token = readTokenOrNull() ?: return
        try {
            val recordingMsid = client.submitPlayingNow(token, active.track)
            val initialScore = recordingMsid?.let { feedbackScoreCache[it] } ?: active.feedbackScore
            val needsFeedbackLookup = feedbackEnabled &&
                !recordingMsid.isNullOrBlank() &&
                !username.isNullOrBlank() &&
                !feedbackScoreCache.containsKey(recordingMsid)
            activeListen = activeListen?.takeIf { it.track.id == active.track.id }?.copy(
                playingNowSubmitted = true,
                recordingMsid = recordingMsid,
                feedbackScore = initialScore,
            )
            mutableFeedbackTarget.value = ListenBrainzFeedbackTarget(
                trackId = active.track.id,
                recordingMsid = recordingMsid,
                score = initialScore,
                loadingScore = needsFeedbackLookup,
                enabled = feedbackEnabled,
            )
            if (needsFeedbackLookup) {
                loadFeedbackScore(active.track.id, recordingMsid.orEmpty(), username.orEmpty())
            }
            accountRepository.markNowPlayingSubmitted()
        } catch (error: Throwable) {
            handleSubmissionFailure(error)
        }
    }

    private suspend fun submitPermanentListen(active: ActiveListen) {
        val listen = ListenBrainzQueuedListen(track = active.track, listenedAtMs = active.startedAtMs)
        submitListenOrQueue(listen)
        activeListen = activeListen?.takeIf { it.track.id == active.track.id }?.copy(listenSubmitted = true)
    }

    private suspend fun submitPermanentListenIfEligible(active: ActiveListen?, enabled: Boolean) {
        if (!enabled || active == null || active.listenSubmitted || !active.hasReachedListenThreshold()) return
        submitPermanentListen(active)
    }

    private suspend fun submitListenOrQueue(listen: ListenBrainzQueuedListen) {
        val token = readTokenOrNull() ?: return
        submissionMutex.withLock {
            try {
                flushRetryQueueLocked(token)
                client.submitListen(token, listen)
                accountRepository.markListenSubmitted()
                PhoebeLog.d("ListenBrainzPlaybackReporter") { "submitted listen: ${listen.track.title}" }
            } catch (error: Throwable) {
                if (error is CancellationException) throw error
                if (error is ListenBrainzUnauthorizedException) {
                    handleSubmissionFailure(error)
                } else if (error.isRetryableListenSubmitFailure()) {
                    retryQueue += listen
                    val message = "ListenBrainz listen queued for retry: ${error.listenBrainzMessage()}"
                    accountRepository.markListenSubmissionFailed(message)
                    PhoebeLog.d("ListenBrainzPlaybackReporter") { message }
                } else {
                    val message = error.listenBrainzMessage()
                    accountRepository.markListenSubmissionFailed(message)
                    PhoebeLog.d("ListenBrainzPlaybackReporter") { "listen submit failed: $message" }
                }
            }
        }
    }

    private suspend fun retryQueuedListens() {
        while (true) {
            delay(RetryIntervalMs)
            val settings = appSettings.value.listenBrainz
            if (!settings.connected || !settings.submitListens || retryQueue.isEmpty()) continue
            val token = readTokenOrNull() ?: continue
            submissionMutex.withLock {
                try {
                    flushRetryQueueLocked(token)
                } catch (error: Throwable) {
                    if (error is CancellationException) throw error
                    if (error is ListenBrainzUnauthorizedException) {
                        handleSubmissionFailure(error)
                    } else {
                        val message = if (error.isRetryableListenSubmitFailure()) {
                            "ListenBrainz listen retry delayed: ${error.listenBrainzMessage()}"
                        } else {
                            error.listenBrainzMessage()
                        }
                        accountRepository.markListenSubmissionFailed(message)
                        PhoebeLog.d("ListenBrainzPlaybackReporter") { message }
                    }
                }
            }
        }
    }

    private suspend fun flushRetryQueueLocked(token: String) {
        if (retryQueue.isEmpty()) return
        val batch = retryQueue.take(MaxRetryBatchSize)
        client.submitListens(token, batch)
        repeat(batch.size) { retryQueue.removeFirst() }
        accountRepository.markListenSubmitted()
    }

    private suspend fun clearPlayingNowIfNeeded(active: ActiveListen?) {
        if (active?.playingNowSubmitted != true) return
        val token = readTokenOrNull() ?: return
        try {
            client.deletePlayingNow(token)
            activeListen = activeListen?.takeIf { it.track.id == active.track.id }?.copy(playingNowSubmitted = false)
        } catch (error: Throwable) {
            handleSubmissionFailure(error)
        }
    }

    private suspend fun readTokenOrNull(): String? =
        credentialStore.read(SecureCredentialKey.ListenBrainzUserToken)?.trim()?.takeIf { it.isNotBlank() }

    private suspend fun loadFeedbackScore(trackId: String, recordingMsid: String, username: String) {
        try {
            val score = client.getUserFeedbackForRecordingMsids(username, listOf(recordingMsid))[recordingMsid]
            feedbackScoreCache[recordingMsid] = score
            activeListen = activeListen?.takeIf { it.track.id == trackId }?.copy(feedbackScore = score)
            updateFeedbackTarget(trackId, recordingMsid) {
                it.copy(score = score, loadingScore = false)
            }
        } catch (error: Throwable) {
            if (error is CancellationException) throw error
            updateFeedbackTarget(trackId, recordingMsid) {
                it.copy(loadingScore = false)
            }
            PhoebeLog.d("ListenBrainzPlaybackReporter") { "feedback lookup failed: ${error.message}" }
        }
    }

    private fun updateFeedbackTarget(
        trackId: String?,
        recordingMsid: String,
        transform: (ListenBrainzFeedbackTarget) -> ListenBrainzFeedbackTarget,
    ) {
        val current = mutableFeedbackTarget.value
        if (current.trackId == trackId && current.recordingMsid == recordingMsid) {
            mutableFeedbackTarget.value = transform(current)
        }
    }

    private suspend fun handleSubmissionFailure(error: Throwable) {
        if (error is CancellationException) throw error
        if (error is ListenBrainzUnauthorizedException) {
            retryQueue.clear()
            activeListen = null
            mutableFeedbackTarget.value = ListenBrainzFeedbackTarget()
            accountRepository.disconnect(lastError = "ListenBrainz token is invalid.")
            return
        }
        PhoebeLog.d("ListenBrainzPlaybackReporter") { "submission failed: ${error.message}" }
    }

    private fun Throwable.isRetryableListenSubmitFailure(): Boolean =
        this !is ListenBrainzRequestException || statusCode == 408 || statusCode == 429 || statusCode >= 500

    private fun Throwable.listenBrainzMessage(): String =
        message?.takeIf { it.isNotBlank() } ?: "ListenBrainz listen submit failed."

    private data class ActiveListen(
        val track: Track,
        val startedAtMs: Long,
        val playingNowSubmitted: Boolean = false,
        val listenSubmitted: Boolean = false,
        val recordingMsid: String? = null,
        val feedbackScore: ListenBrainzFeedbackScore? = null,
        val lastObservedAtMs: Long,
        val lastObservedPositionMs: Long,
        val maxObservedPositionMs: Long,
        val accumulatedPlayingMs: Long = 0L,
        val durationMs: Long = track.durationMs.coerceAtLeast(0L),
        val lastObservedPlaying: Boolean = false,
    ) {
        fun observedAt(observedAtMs: Long): ActiveListen {
            val elapsedMs = if (lastObservedPlaying) {
                (observedAtMs - lastObservedAtMs).coerceAtLeast(0L)
            } else {
                0L
            }
            return copy(
                lastObservedAtMs = observedAtMs,
                accumulatedPlayingMs = accumulatedPlayingMs + elapsedMs,
            )
        }

        fun observedPlayback(player: PlayerState, observedAtMs: Long): ActiveListen {
            val positionMs = player.positionMs.coerceAtLeast(0L)
            return observedAt(observedAtMs).copy(
                lastObservedPositionMs = positionMs,
                maxObservedPositionMs = maxOf(maxObservedPositionMs, positionMs),
                durationMs = player.listenBrainzDurationMs(track),
                lastObservedPlaying = player.isPlaying,
            )
        }

        fun hasReachedListenThreshold(): Boolean {
            val duration = durationMs.takeIf { it > 0L } ?: track.durationMs.takeIf { it > 0L }
            val threshold = listenThresholdMs(duration)
            return accumulatedPlayingMs >= threshold
        }
    }

    internal companion object {
        const val PlayedFraction = 0.5
        const val MaxThresholdMs = 4L * 60L * 1000L
        const val RetryIntervalMs = 60_000L
        const val MaxRetryBatchSize = 25

        fun PlayerState.isStoppedAtEnd(track: Track): Boolean {
            val duration = track.durationMs.takeIf { it > 0L } ?: durationMs.takeIf { it > 0L } ?: return false
            return positionMs >= (duration - StopNearEndGraceMs).coerceAtLeast((duration * 0.9).toLong())
        }

        fun PlayerState.hasReachedListenThreshold(track: Track): Boolean =
            positionMs.coerceAtLeast(0L) >= listenThresholdMs(track.durationMs.takeIf { it > 0L } ?: durationMs.takeIf { it > 0L })

        private fun listenThresholdMs(durationMs: Long?): Long =
            durationMs
                ?.let { (it * PlayedFraction).toLong().coerceAtMost(MaxThresholdMs) }
                ?: MaxThresholdMs
    }
}

private const val StopNearEndGraceMs = 2_000L

private fun PlayerState.listenBrainzDurationMs(track: Track): Long =
    (track.durationMs.takeIf { it > 0L } ?: durationMs.takeIf { it > 0L } ?: 0L).coerceAtLeast(0L)
