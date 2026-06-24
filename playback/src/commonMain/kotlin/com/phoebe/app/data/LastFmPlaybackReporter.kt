package com.phoebe.app.data

import com.phoebe.app.domain.AppSettings
import com.phoebe.app.domain.PlayerState
import com.phoebe.app.domain.Track
import com.phoebe.app.platform.PhoebeLog
import com.phoebe.app.platform.currentTimeMs
import com.phoebe.app.player.AudioPlayer
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

class LastFmPlaybackReporter(
    private val client: LastFmClient,
    private val accountRepository: LastFmAccountRepository,
    private val audioPlayer: AudioPlayer,
    private val appSettings: StateFlow<AppSettings>,
    private val nowMs: () -> Long = ::currentTimeMs,
) {
    private val submissionMutex = Mutex()
    private val retryQueue = mutableListOf<LastFmQueuedScrobble>()
    private var activeScrobble: ActiveScrobble? = null

    fun start(scope: CoroutineScope) {
        scope.launch { watchPlaybackState() }
        scope.launch { retryQueuedScrobbles() }
    }

    internal fun queuedRetryCount(): Int = retryQueue.size

    private suspend fun watchPlaybackState() {
        try {
            combine(audioPlayer.state, appSettings) { player, settings -> player to settings }
                .collect { (player, settings) ->
                    val observedAtMs = nowMs()
                    val lastFm = settings.lastFm
                    val observedActive = activeScrobble?.observedAt(observedAtMs)
                    activeScrobble = observedActive
                    if (!lastFm.connected) {
                        activeScrobble = null
                        return@collect
                    }
                    val track = player.currentTrack
                    val active = observedActive

                    if (track == null) {
                        submitScrobbleIfEligible(active, lastFm.submitScrobbles)
                        activeScrobble = null
                        return@collect
                    }

                    if (active == null || active.track.id != track.id) {
                        submitScrobbleIfEligible(active, lastFm.submitScrobbles)
                        val positionMs = player.positionMs.coerceAtLeast(0L)
                        activeScrobble = ActiveScrobble(
                            track = track,
                            startedAtMs = (observedAtMs - positionMs).coerceAtLeast(0L),
                            lastObservedAtMs = observedAtMs,
                            lastObservedPositionMs = positionMs,
                            maxObservedPositionMs = positionMs,
                            durationMs = player.lastFmDurationMs(track),
                            lastObservedPlaying = player.isPlaying,
                        )
                    } else {
                        activeScrobble = active.observedPlayback(player, observedAtMs)
                    }

                    val current = activeScrobble ?: return@collect
                    if (player.isPlaying && lastFm.submitNowPlaying && !current.nowPlayingSubmitted) {
                        submitNowPlaying(current)
                    }

                    submitScrobbleIfEligible(activeScrobble, lastFm.submitScrobbles)
                    if (!player.isPlaying && player.isStoppedAtEnd(track)) {
                        submitScrobbleIfEligible(activeScrobble, lastFm.submitScrobbles)
                    }
                }
        } finally {
            withContext(NonCancellable) {
                submitScrobbleIfEligible(activeScrobble, appSettings.value.lastFm.submitScrobbles)
            }
        }
    }

    private suspend fun submitNowPlaying(active: ActiveScrobble) {
        val credentials = accountRepository.readCredentialsOrNull(appSettings.value.lastFm.apiKey) ?: return
        try {
            client.updateNowPlaying(credentials.apiKey, credentials.sharedSecret, credentials.sessionKey, active.track)
            activeScrobble = activeScrobble?.takeIf { it.track.id == active.track.id }?.copy(nowPlayingSubmitted = true)
            accountRepository.markNowPlayingSubmitted()
        } catch (error: Throwable) {
            handleSubmissionFailure(error)
        }
    }

    private suspend fun submitScrobbleIfEligible(active: ActiveScrobble?, enabled: Boolean) {
        if (!enabled || active == null || active.scrobbleSubmitted || !active.hasReachedScrobbleThreshold()) return
        submitScrobbleOrQueue(LastFmQueuedScrobble(active.track, active.startedAtMs))
        activeScrobble = activeScrobble?.takeIf { it.track.id == active.track.id }?.copy(scrobbleSubmitted = true)
    }

    private suspend fun submitScrobbleOrQueue(scrobble: LastFmQueuedScrobble) {
        val credentials = accountRepository.readCredentialsOrNull(appSettings.value.lastFm.apiKey) ?: return
        submissionMutex.withLock {
            try {
                flushRetryQueueLocked(credentials)
                client.scrobble(credentials.apiKey, credentials.sharedSecret, credentials.sessionKey, scrobble)
                accountRepository.markScrobbleSubmitted()
                PhoebeLog.d("LastFmPlaybackReporter") { "submitted scrobble: ${scrobble.track.title}" }
            } catch (error: Throwable) {
                if (error is CancellationException) throw error
                if (error is LastFmUnauthorizedException) {
                    handleSubmissionFailure(error, holdsLock = true)
                } else if (error.isRetryableScrobbleFailure()) {
                    retryQueue += scrobble
                    val message = "Last.fm scrobble queued for retry: ${error.lastFmMessage()}"
                    accountRepository.markScrobbleSubmissionFailed(message)
                    PhoebeLog.d("LastFmPlaybackReporter") { message }
                } else {
                    val message = error.lastFmMessage()
                    accountRepository.markScrobbleSubmissionFailed(message)
                    PhoebeLog.d("LastFmPlaybackReporter") { "scrobble failed: $message" }
                }
            }
        }
    }

    private suspend fun retryQueuedScrobbles() {
        while (true) {
            delay(RetryIntervalMs)
            val settings = appSettings.value.lastFm
            if (!settings.connected || !settings.submitScrobbles || retryQueue.isEmpty()) continue
            val credentials = accountRepository.readCredentialsOrNull(settings.apiKey) ?: continue
            submissionMutex.withLock {
                try {
                    flushRetryQueueLocked(credentials)
                } catch (error: Throwable) {
                    if (error is CancellationException) throw error
                    if (error is LastFmUnauthorizedException) {
                        handleSubmissionFailure(error, holdsLock = true)
                    } else {
                        val message = if (error.isRetryableScrobbleFailure()) {
                            "Last.fm scrobble retry delayed: ${error.lastFmMessage()}"
                        } else {
                            error.lastFmMessage()
                        }
                        accountRepository.markScrobbleSubmissionFailed(message)
                        PhoebeLog.d("LastFmPlaybackReporter") { message }
                    }
                }
            }
        }
    }

    private suspend fun flushRetryQueueLocked(credentials: LastFmCredentials) {
        var processed = 0
        while (retryQueue.isNotEmpty() && processed < MaxRetryBatchSize) {
            val scrobble = retryQueue.first()
            client.scrobble(credentials.apiKey, credentials.sharedSecret, credentials.sessionKey, scrobble)
            retryQueue.removeFirst()
            processed++
        }
        if (processed > 0) {
            accountRepository.markScrobbleSubmitted()
        }
    }

    private suspend fun handleSubmissionFailure(error: Throwable, holdsLock: Boolean = false) {
        if (error is CancellationException) throw error
        if (error is LastFmUnauthorizedException) {
            if (holdsLock) {
                retryQueue.clear()
            } else {
                submissionMutex.withLock {
                    retryQueue.clear()
                }
            }
            activeScrobble = null
            accountRepository.disconnect(lastError = "Last.fm credentials are invalid.")
            return
        }
        PhoebeLog.d("LastFmPlaybackReporter") { "submission failed: ${error.message}" }
    }

    private fun Throwable.isRetryableScrobbleFailure(): Boolean =
        this !is LastFmRequestException || statusCode == 408 || statusCode == 429 || statusCode >= 500

    private fun Throwable.lastFmMessage(): String =
        message?.takeIf { it.isNotBlank() } ?: "Last.fm scrobble failed."

    private data class ActiveScrobble(
        val track: Track,
        val startedAtMs: Long,
        val nowPlayingSubmitted: Boolean = false,
        val scrobbleSubmitted: Boolean = false,
        val lastObservedAtMs: Long,
        val lastObservedPositionMs: Long,
        val maxObservedPositionMs: Long,
        val accumulatedPlayingMs: Long = 0L,
        val durationMs: Long = track.durationMs.coerceAtLeast(0L),
        val lastObservedPlaying: Boolean = false,
    ) {
        fun observedAt(observedAtMs: Long): ActiveScrobble {
            val elapsedMs = if (lastObservedPlaying) (observedAtMs - lastObservedAtMs).coerceAtLeast(0L) else 0L
            return copy(lastObservedAtMs = observedAtMs, accumulatedPlayingMs = accumulatedPlayingMs + elapsedMs)
        }

        fun observedPlayback(player: PlayerState, observedAtMs: Long): ActiveScrobble {
            val positionMs = player.positionMs.coerceAtLeast(0L)
            return observedAt(observedAtMs).copy(
                lastObservedPositionMs = positionMs,
                maxObservedPositionMs = maxOf(maxObservedPositionMs, positionMs),
                durationMs = player.lastFmDurationMs(track),
                lastObservedPlaying = player.isPlaying,
            )
        }

        fun hasReachedScrobbleThreshold(): Boolean =
            accumulatedPlayingMs >= scrobbleThresholdMs(durationMs.takeIf { it > 0L } ?: track.durationMs.takeIf { it > 0L })
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

        private fun scrobbleThresholdMs(durationMs: Long?): Long =
            durationMs?.let { (it * PlayedFraction).toLong().coerceAtMost(MaxThresholdMs) } ?: MaxThresholdMs
    }
}

private const val StopNearEndGraceMs = 2_000L

private fun PlayerState.lastFmDurationMs(track: Track): Long =
    (track.durationMs.takeIf { it > 0L } ?: durationMs.takeIf { it > 0L } ?: 0L).coerceAtLeast(0L)
