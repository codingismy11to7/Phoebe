package com.phoebe.app.data

import com.phoebe.app.domain.PlayerState
import com.phoebe.app.domain.PlexSession
import com.phoebe.app.domain.Track
import com.phoebe.app.domain.isPlexLibraryTrack
import com.phoebe.app.domain.serverAuthToken
import com.phoebe.app.player.AudioPlayer
import com.phoebe.app.platform.PhoebeLog
import com.phoebe.app.platform.currentTimeMs
import com.phoebe.app.sources.CatalogMerge
import kotlin.random.Random
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch

/**
 * Reports Plex library playback to the server's timeline API so Plex can mark tracks played
 * and scrobble to linked services (ListenBrainz, Last.fm, etc.).
 */
class PlexPlaybackReporter(
    private val plexClient: PlexClient,
    private val audioPlayer: AudioPlayer,
    private val session: StateFlow<PlexSession?>,
) {
    private val playbackSessionId = newPlaybackSessionId()
    private var machineIdentifier: String? = null
    private var playQueueItemByRatingKey: Map<String, Long> = emptyMap()
    private var lastPlayQueueSignature: String? = null
    private var failedPlayQueueSignature: String? = null
    private var failedPlayQueueRetryAtMs: Long = 0L

    fun start(scope: CoroutineScope) {
        scope.launch { watchPlaybackState() }
        scope.launch { periodicTimelineWhilePlaying() }
    }

    private suspend fun watchPlaybackState() {
        var lastTrack: Track? = null
        var lastPositionMs: Long = 0L
        var lastIsPlaying: Boolean? = null

        combine(audioPlayer.state, session) { player, sess -> player to sess }
            .collect { (player, sess) ->
                val track = player.currentTrack
                if (track == null || !track.isPlexLibraryTrack()) {
                    if (lastTrack != null) {
                        reportStopped(lastTrack!!, lastPositionMs, sess, continuing = false)
                    }
                    clearPlayQueue()
                    lastTrack = null
                    lastPositionMs = 0L
                    lastIsPlaying = null
                    return@collect
                }

                if (lastTrack != null && lastTrack!!.id != track.id) {
                    reportStopped(lastTrack!!, lastPositionMs, sess, continuing = true)
                }

                ensurePlayQueue(sess, player)

                val isPlaying = player.isPlaying
                if (lastTrack?.id != track.id || lastIsPlaying != isPlaying) {
                    val state = if (isPlaying) PlexTimelineState.Playing else PlexTimelineState.Paused
                    reportTimeline(sess, track, player, state)
                }

                lastTrack = track
                lastPositionMs = player.positionMs
                lastIsPlaying = isPlaying
            }
    }

    private suspend fun periodicTimelineWhilePlaying() {
        while (true) {
            delay(TimelineIntervalMs)
            val player = audioPlayer.state.value
            val track = player.currentTrack ?: continue
            if (!player.isPlaying || !track.isPlexLibraryTrack()) continue
            ensurePlayQueue(session.value, player)
            reportTimeline(session.value, track, player, PlexTimelineState.Playing)
        }
    }

    private suspend fun ensurePlayQueue(sess: PlexSession?, player: PlayerState) {
        runCatching {
            val server = sess?.selectedServer ?: return@runCatching
            val token = sess.serverAuthToken() ?: return@runCatching
            val queueWindow = plexPlayQueueWindow(player)
            val ratingKeys = queueWindow.mapNotNull { plexRatingKey(it.id) }
            if (ratingKeys.isEmpty()) return@runCatching
            val signature = ratingKeys.joinToString(",")
            if (signature == lastPlayQueueSignature && playQueueItemByRatingKey.isNotEmpty()) return@runCatching
            val now = currentTimeMs()
            if (signature == failedPlayQueueSignature && now < failedPlayQueueRetryAtMs) return@runCatching

            val startKey = player.currentTrack?.let { plexRatingKey(it.id) } ?: return@runCatching
            val machineId = machineIdentifier
                ?: plexClient.machineIdentifier(server, token).also { machineIdentifier = it }

            val queue = plexClient.createAudioPlayQueue(server, token, machineId, ratingKeys, startKey)
            if (queue == null) {
                failedPlayQueueSignature = signature
                failedPlayQueueRetryAtMs = now + PlayQueueFailureBackoffMs
                return@runCatching
            }
            playQueueItemByRatingKey = queue.itemIdByRatingKey
            lastPlayQueueSignature = signature
            failedPlayQueueSignature = null
            failedPlayQueueRetryAtMs = 0L
        }.onFailure { e ->
            PhoebeLog.d("PlexPlaybackReporter") { "play queue setup failed: ${e.message}" }
        }
    }

    private fun clearPlayQueue() {
        playQueueItemByRatingKey = emptyMap()
        lastPlayQueueSignature = null
        failedPlayQueueSignature = null
        failedPlayQueueRetryAtMs = 0L
    }

    private fun plexPlayQueueWindow(player: PlayerState): List<Track> {
        if (player.queue.size <= MaxPlayQueueItems) return player.queue
        val currentId = player.currentTrack?.id
        val currentIndex = player.queue.indexOfFirst { it.id == currentId }
            .takeIf { it >= 0 }
            ?: player.currentIndex.coerceIn(0, player.queue.lastIndex)
        val halfWindow = MaxPlayQueueItems / 2
        val start = (currentIndex - halfWindow)
            .coerceIn(0, (player.queue.size - MaxPlayQueueItems).coerceAtLeast(0))
        return player.queue.subList(start, start + MaxPlayQueueItems)
    }

    private suspend fun reportStopped(
        track: Track,
        positionMs: Long,
        sess: PlexSession?,
        continuing: Boolean,
    ) {
        val ratingKey = plexRatingKey(track.id) ?: return
        val server = sess?.selectedServer ?: return
        val token = sess.serverAuthToken() ?: return
        val durationMs = track.durationMs.coerceAtLeast(0L)
        val timeMs = if (durationMs > 0L) positionMs.coerceAtMost(durationMs) else positionMs
        runCatching {
            plexClient.reportTimeline(
                server = server,
                token = token,
                sessionIdentifier = playbackSessionId,
                ratingKey = ratingKey,
                timeMs = timeMs,
                durationMs = durationMs,
                state = PlexTimelineState.Stopped,
                continuing = continuing,
                playQueueItemId = playQueueItemByRatingKey[ratingKey],
            )
        }.onFailure { e ->
            PhoebeLog.d("PlexPlaybackReporter") { "stopped timeline failed: ${e.message}" }
        }
    }

    private suspend fun reportTimeline(
        sess: PlexSession?,
        track: Track,
        player: PlayerState,
        state: PlexTimelineState,
    ) {
        val ratingKey = plexRatingKey(track.id) ?: return
        val server = sess?.selectedServer ?: return
        val token = sess.serverAuthToken() ?: return
        val durationMs = track.durationMs.takeIf { it > 0L } ?: player.durationMs
        runCatching {
            plexClient.reportTimeline(
                server = server,
                token = token,
                sessionIdentifier = playbackSessionId,
                ratingKey = ratingKey,
                timeMs = player.positionMs,
                durationMs = durationMs,
                state = state,
                playQueueItemId = playQueueItemByRatingKey[ratingKey],
            )
        }.onFailure { e ->
            PhoebeLog.d("PlexPlaybackReporter") { "timeline failed: ${e.message}" }
        }
    }

    internal companion object {
        const val TimelineIntervalMs = 10_000L
        const val PlayQueueFailureBackoffMs = 10L * 60L * 1000L
        const val MaxPlayQueueItems = 200

        fun plexRatingKey(trackId: String): String? =
            CatalogMerge.stripPlexId(trackId).takeIf { trackId.startsWith("plex:") }

        fun newPlaybackSessionId(): String =
            "phoebe-${currentTimeMs()}-${Random.nextInt(1_000_000)}"
    }
}
