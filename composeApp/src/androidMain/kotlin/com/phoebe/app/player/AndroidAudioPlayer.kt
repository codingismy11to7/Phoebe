package com.phoebe.app.player

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.SystemClock
import androidx.media3.common.AudioAttributes
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import com.phoebe.app.AndroidContextHolder
import com.phoebe.app.domain.EqualizerProfile
import com.phoebe.app.domain.Track
import com.phoebe.app.platform.PhoebeLog
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

actual fun createAudioPlayer(): AudioPlayer = AndroidAudioPlayerHolder.instance

internal object AndroidAudioPlayerHolder {
    private val player: AndroidAudioPlayer by lazy { AndroidAudioPlayer() }

    val instance: AudioPlayer
        get() = player

    fun ensureConnected() {
        player.ensureConnected()
    }
}

private data class PendingControllerTarget(
    val queueIds: List<String>,
    val platformIndex: Int,
    val generation: Int,
)

private data class LoadedPlatformQueue(
    val queueIds: List<String>,
    val firstAppIndex: Int,
    val itemCount: Int,
) {
    fun platformIndexFor(appIndex: Int): Int? =
        (appIndex - firstAppIndex).takeIf { it in 0 until itemCount }

    fun appIndexFor(platformIndex: Int): Int? =
        (firstAppIndex + platformIndex).takeIf { platformIndex in 0 until itemCount }
}

internal class AndroidAudioPlayer(
    private val diagnostics: PlaybackDiagnostics = AndroidPlaybackDiagnostics.diagnostics,
) : SimpleAudioPlayer() {
    override val useProgressTicker: Boolean = false

    private val appContext: Context
        get() = AndroidContextHolder.application

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var controller: MediaController? = null
    private var positionSyncJob: Job? = null
    private var platformLoadJob: Job? = null
    private var platformStopJob: Job? = null
    private var seekJob: Job? = null
    private var bufferingTimeoutJob: Job? = null
    private var retryJob: Job? = null
    private var crossfadeJob: Job? = null
    private var crossfadePlayer: ExoPlayer? = null
    private var crossfadeGeneration = -1
    private var crossfadeOwnedTrackId: String? = null
    private var retryGeneration = -1
    private var retryCount = 0
    private var pendingAutoplayGeneration = -1
    private var pendingAutoplayStartedAtMs = 0L
    private val controllerMutex = Mutex()
    private var loadedPlatformQueue: LoadedPlatformQueue? = null
    private var appControllerMutationInProgress = false
    private var pendingControllerTarget: PendingControllerTarget? = null

    private val controllerListener = object : Player.Listener {
        override fun onIsPlayingChanged(isPlaying: Boolean) {
            syncFromController()
        }

        override fun onPlaybackStateChanged(playbackState: Int) {
            syncFromController()
        }

        override fun onPlayWhenReadyChanged(playWhenReady: Boolean, reason: Int) {
            syncFromController()
        }

        override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
            syncFromController()
        }

        override fun onPlayerError(error: PlaybackException) {
            PhoebeLog.d("AndroidAudioPlayer") { "playback failed: ${error.message}" }
            diagnostics.playbackError(PlaybackEnginePath.Media3, error.message)
            pendingControllerTarget = null
            stopBufferingTimeout()
            schedulePlaybackRetry(error, activePlayGeneration)
            stopPositionSyncLoop()
        }
    }

    init {
        AndroidPlaybackDiagnostics.diagnostics = diagnostics
        AndroidPlaybackBridge.onSkipNext = { next() }
        AndroidPlaybackBridge.onSkipPrevious = { previous() }
        AndroidPlaybackBridge.onTrackEnded = { next() }
        AndroidPlaybackBridge.onPlayQueue = { queue, index -> play(queue, index) }
        AndroidPlaybackBridge.onAdoptQueue = { queue, index, playing ->
            loadedPlatformQueue = LoadedPlatformQueue(
                queueIds = queue.map { it.id },
                firstAppIndex = 0,
                itemCount = queue.size,
            )
            adoptQueueState(queue, index, playing)
        }
        AndroidPlaybackBridge.onEnsureLocalPlaybackPaused = { forceLocalPlaybackPaused() }
        scope.launch { ensureController() }
    }

    fun ensureConnected() {
        if (controller == null) {
            scope.launch { ensureController() }
        }
    }

    internal suspend fun releaseForTests() {
        withContext(Dispatchers.Main.immediate) {
            platformLoadJob?.cancel()
            platformLoadJob = null
            platformStopJob?.cancel()
            platformStopJob = null
            clearPendingAutoplay()
            pendingControllerTarget = null
            seekJob?.cancel()
            seekJob = null
            stopAndroidCrossfade()
            stopPositionSyncLoop()
            stopBufferingTimeout()
            stopRetry()
            controllerMutex.withLock {
                controller?.removeListener(controllerListener)
                controller?.run {
                    pause()
                    stop()
                    clearMediaItems()
                    release()
                }
                controller = null
            }
            appContext.stopService(Intent(appContext, PlaybackService::class.java))
            AndroidPlaybackDiagnostics.reset()
        }
    }

    override fun skipToInQueueOnPlatform(
        queue: List<Track>,
        startIndex: Int,
        track: Track,
        generation: Int,
    ) {
        runPlatformLoad(generation) { player ->
            val targetIndex = startIndex.coerceIn(queue.indices)
            val queueIds = queue.map { it.id }
            val loaded = loadedPlatformQueue
            val platformIndex = loaded
                ?.takeIf { it.queueIds == queueIds && player.mediaItemCount == it.itemCount }
                ?.platformIndexFor(targetIndex)
            if (platformIndex != null) {
                expectControllerTarget(queueIds, platformIndex, generation)
                player.pause()
                player.seekTo(platformIndex, 0L)
                updateOptimisticLocalBufferedPosition(track, generation)
                player.volume = effectiveOutputVolume()
                if (playWhenReady) {
                    markPendingAutoplay(generation)
                    player.play()
                }
            } else {
                loadQueueOnPlayer(player, queue, targetIndex, queueIds, generation)
            }
        }
    }

    override fun playQueueOnPlatform(
        queue: List<Track>,
        startIndex: Int,
        track: Track,
        generation: Int,
    ) {
        runPlatformLoad(generation) { player ->
            loadQueueOnPlayer(player, queue, startIndex.coerceIn(queue.indices), queue.map { it.id }, generation)
        }
    }

    override fun stopCurrentPlaybackImmediately() {
        platformLoadJob?.cancel()
        platformLoadJob = null
        stopAndroidCrossfade()
        stopBufferingTimeout()
        stopRetry()
        loadedPlatformQueue = null
        clearPendingAutoplay()
        platformStopJob?.cancel()
        platformStopJob = scope.launch {
            controllerMutex.withLock {
                activeLocalPlayer()?.run {
                    pause()
                    stop()
                    clearMediaItems()
                }
            }
        }
    }

    override fun pause() {
        clearPendingAutoplay()
        scope.launch {
            val ownedPlayer = ownedCrossfadePlayer()
            if (ownedPlayer != null) {
                ownedPlayer.pause()
                syncFromCrossfadePlayer(ownedPlayer)
            } else {
                controllerMutex.withLock { activeLocalPlayer()?.pause() }
                syncFromController()
            }
        }
    }

    override fun resume() {
        scope.launch {
            val ownedPlayer = ownedCrossfadePlayer()
            if (ownedPlayer != null) {
                ownedPlayer.volume = effectiveOutputVolume()
                ownedPlayer.play()
                syncFromCrossfadePlayer(ownedPlayer)
            } else {
                controllerMutex.withLock {
                    activeLocalPlayer()?.run {
                        markPendingAutoplay(activePlayGeneration)
                        volume = effectiveOutputVolume()
                        play()
                    }
                }
                syncFromController()
            }
        }
    }

    override fun seek(positionMs: Long) {
        seekJob?.cancel()
        val generation = activePlayGeneration
        seekJob = scope.launch {
            val ownedPlayer = ownedCrossfadePlayer()
            if (ownedPlayer != null) {
                if (!isPlayRequestCurrent(generation)) return@launch
                ownedPlayer.seekTo(positionMs)
                syncFromCrossfadePlayer(ownedPlayer, generation)
            } else {
                controllerMutex.withLock {
                    if (!isPlayRequestCurrent(generation)) return@withLock
                    activeLocalPlayer()?.seekTo(positionMs)
                }
                syncFromController(generation)
            }
        }
    }

    override fun setOutputVolume(volume: Float) {
        scope.launch {
            val ownedPlayer = ownedCrossfadePlayer()
            if (ownedPlayer != null) {
                ownedPlayer.volume = volume
                return@launch
            }
            controllerMutex.withLock { activeLocalPlayer()?.volume = volume }
        }
    }

    override fun applyEqualizer(profile: EqualizerProfile) {
        val normalized = profile.normalized()
        AndroidEqualizerState.profile = normalized
        AndroidPlaybackBridge.servicePlayer?.applyPhoebeAudioOffloadPreference(normalized)
    }

    override fun startCrossfadeOnPlatform(
        queue: List<Track>,
        targetIndex: Int,
        track: Track,
        durationMs: Long,
        baseVolume: Float,
        generation: Int,
    ): Boolean {
        if (crossfadeGeneration == generation) return true
        if (targetIndex !in queue.indices) return false
        val ownedOutgoing = ownedCrossfadePlayer()
        if (ownedOutgoing == null && controller == null) return false
        crossfadeGeneration = generation
        crossfadeJob?.cancel()
        crossfadeJob = scope.launch {
            var incoming: ExoPlayer? = null
            var incomingOwnedByPlayback = false
            AndroidPlaybackBridge.suppressServiceEndedCallback = true
            try {
                val outgoingOwnedByPlayback = ownedCrossfadePlayer()
                val outgoing: Player = outgoingOwnedByPlayback ?: activeLocalPlayer() ?: return@launch
                diagnostics.crossfadeStarted(
                    engine = PlaybackEnginePath.Media3Crossfade,
                    outgoingTrackId = state.value.currentTrack?.id,
                    incomingTrackId = track.id,
                    durationMs = durationMs,
                )
                incoming = AndroidPlaybackDiagnostics.newPlayerBuilder(appContext, PlaybackEnginePath.Media3Crossfade)
                    .setAudioAttributes(AudioAttributes.DEFAULT, /* handleAudioFocus= */ false)
                    .build()
                incoming.volume = 0f
                incoming.setMediaItem(playbackMediaItem(track, inAppPlayback = true))
                incoming.prepare()
                incoming.play()
                if (!waitUntilReady(incoming, generation, CrossfadePrepareTimeoutMs)) return@launch
                if (!isPlayRequestCurrent(generation)) return@launch
                if (outgoingOwnedByPlayback == null && activeLocalPlayer() !== outgoing) return@launch
                if (outgoingOwnedByPlayback != null && crossfadePlayer !== outgoingOwnedByPlayback) return@launch

                val remainingMs = outgoing.duration
                    .takeIf { it > 0L }
                    ?.let { duration -> duration - outgoing.currentPosition.coerceAtLeast(0L) }
                    ?: durationMs
                val fadeDurationMs = remainingMs
                    .coerceAtMost(durationMs)
                    .coerceAtLeast(CrossfadeMinimumFadeMs)
                fadeVolumes(outgoing, incoming, fadeDurationMs, baseVolume, generation)
                if (!isPlayRequestCurrent(generation)) return@launch
                if (outgoingOwnedByPlayback == null && activeLocalPlayer() !== outgoing) return@launch
                if (outgoingOwnedByPlayback != null && crossfadePlayer !== outgoingOwnedByPlayback) return@launch

                if (outgoingOwnedByPlayback != null) {
                    outgoing.pause()
                    outgoing.volume = 0f
                    outgoingOwnedByPlayback.release()
                } else {
                    controllerMutex.withLock {
                        if (!isPlayRequestCurrent(generation) || activeLocalPlayer() !== outgoing) return@withLock
                        outgoing.pause()
                        outgoing.volume = 0f
                    }
                }
                if (!isPlayRequestCurrent(generation)) return@launch
                incoming.volume = effectiveOutputVolume()
                incomingOwnedByPlayback = true
                crossfadePlayer = incoming
                crossfadeOwnedTrackId = track.id
                adoptCrossfadeTarget(queue, targetIndex, incoming.currentPosition.coerceAtLeast(0L), generation)
                diagnostics.crossfadeCommitted(PlaybackEnginePath.Media3Crossfade, track.id)
                updateOptimisticLocalBufferedPosition(track, generation)
                startCrossfadeOwnedSync(incoming, queue, targetIndex, generation)
            } catch (error: CancellationException) {
                throw error
            } catch (error: Throwable) {
                PhoebeLog.d("AndroidAudioPlayer") { "android crossfade failed: ${error.message}" }
                diagnostics.playbackError(PlaybackEnginePath.Media3Crossfade, error.message)
            } finally {
                AndroidPlaybackBridge.suppressServiceEndedCallback = false
                if (!incomingOwnedByPlayback) {
                    incoming?.release()
                    if (crossfadePlayer === incoming) crossfadePlayer = null
                }
                if (crossfadeGeneration == generation) crossfadeGeneration = -1
            }
        }
        return true
    }

    override fun playUri(uri: String) {
        if (uri.isBlank()) return
        val generation = activePlayGeneration
        runPlatformLoad(generation) { player ->
            player.setMediaItem(MediaItem.fromUri(uri))
            player.prepare()
            if (playWhenReady) {
                markPendingAutoplay(generation)
                player.play()
            }
        }
    }

    private fun forceLocalPlaybackPaused() {
        cancelPlayIntent()
        clearPendingAutoplay()
        stopPositionSyncLoop()
        stopBufferingTimeout()
        val current = state.value
        val positionMs = AndroidPlaybackBridge.servicePlayer?.currentPosition?.coerceAtLeast(0L)
            ?: current.positionMs
        applyPlatformPlayback(
            positionMs = positionMs,
            durationMs = current.durationMs,
            isPlaying = false,
            isBuffering = false,
        )
    }

    private fun activeLocalPlayer(): Player? =
        AndroidPlaybackBridge.servicePlayer ?: controller

    private fun markPendingAutoplay(generation: Int) {
        if (pendingAutoplayGeneration != generation) {
            pendingAutoplayStartedAtMs = SystemClock.elapsedRealtime()
        }
        pendingAutoplayGeneration = generation
    }

    private fun clearPendingAutoplay() {
        pendingAutoplayGeneration = -1
        pendingAutoplayStartedAtMs = 0L
    }

    private fun stopAndroidCrossfade() {
        crossfadeJob?.cancel()
        crossfadeJob = null
        crossfadeGeneration = -1
        crossfadeOwnedTrackId = null
        AndroidPlaybackBridge.suppressServiceEndedCallback = false
        crossfadePlayer?.release()
        crossfadePlayer = null
    }

    private fun ownedCrossfadePlayer(): ExoPlayer? =
        crossfadePlayer?.takeIf { crossfadeOwnedTrackId != null }

    private suspend fun waitUntilReady(player: Player, generation: Int, timeoutMs: Long): Boolean {
        var waitedMs = 0L
        while (waitedMs < timeoutMs && isPlayRequestCurrent(generation)) {
            if (player.playbackState == Player.STATE_READY && player.playWhenReady) return true
            if (player.playbackState == Player.STATE_ENDED) return false
            delay(50)
            waitedMs += 50
        }
        return false
    }

    private suspend fun fadeVolumes(
        outgoing: Player,
        incoming: Player,
        durationMs: Long,
        baseVolume: Float,
        generation: Int,
    ) {
        val stepDelayMs = (durationMs / CrossfadeSteps).coerceAtLeast(16L)
        repeat(CrossfadeSteps) { index ->
            if (!isPlayRequestCurrent(generation)) return
            val progress = (index + 1).toFloat() / CrossfadeSteps.toFloat()
            val outgoingVolume = (baseVolume * (1f - progress)).coerceIn(0f, 1f)
            val incomingVolume = (baseVolume * progress).coerceIn(0f, 1f)
            diagnostics.crossfadeVolume(
                engine = PlaybackEnginePath.Media3Crossfade,
                step = index + 1,
                outgoingVolume = outgoingVolume,
                incomingVolume = incomingVolume,
            )
            outgoing.volume = outgoingVolume
            incoming.volume = incomingVolume
            delay(stepDelayMs)
        }
    }

    private fun startCrossfadeOwnedSync(
        player: Player,
        queue: List<Track>,
        targetIndex: Int,
        generation: Int,
    ) {
        stopPositionSyncLoop()
        positionSyncJob = scope.launch {
            while (isActive && isPlayRequestCurrent(generation) && crossfadePlayer === player) {
                val positionMs = player.currentPosition.coerceAtLeast(0L)
                reportPlaybackDiagnostics(
                    engine = PlaybackEnginePath.Media3Crossfade,
                    positionMs = positionMs,
                    durationMs = player.duration.coerceAtLeast(queue.getOrNull(targetIndex)?.durationMs ?: 0L),
                    isPlaying = player.isPlaying,
                )
                applyPlatformPlayback(
                    positionMs = positionMs,
                    durationMs = player.duration.coerceAtLeast(queue.getOrNull(targetIndex)?.durationMs ?: 0L),
                    isPlaying = player.isPlaying,
                    isBuffering = player.playbackState == Player.STATE_BUFFERING,
                    bufferedPositionMs = player.bufferedPosition.coerceAtLeast(positionMs).coerceAtLeast(0L),
                    generation = generation,
                )
                if (player.playbackState == Player.STATE_ENDED) {
                    next()
                    break
                }
                delay(FinePositionSyncIntervalMs)
            }
        }
    }

    private fun syncFromCrossfadePlayer(
        player: Player,
        generation: Int = activePlayGeneration,
    ) {
        if (!isPlayRequestCurrent(generation) || crossfadePlayer !== player) return
        val positionMs = player.currentPosition.coerceAtLeast(0L)
        reportPlaybackDiagnostics(
            engine = PlaybackEnginePath.Media3Crossfade,
            positionMs = positionMs,
            durationMs = player.duration.coerceAtLeast(state.value.currentTrack?.durationMs ?: 0L),
            isPlaying = player.isPlaying && player.playbackState != Player.STATE_BUFFERING,
        )
        applyPlatformPlayback(
            positionMs = positionMs,
            durationMs = player.duration.coerceAtLeast(state.value.currentTrack?.durationMs ?: 0L),
            isPlaying = player.isPlaying && player.playbackState != Player.STATE_BUFFERING,
            isBuffering = player.playbackState == Player.STATE_BUFFERING,
            bufferedPositionMs = player.bufferedPosition.coerceAtLeast(positionMs).coerceAtLeast(0L),
            generation = generation,
        )
    }

    private fun runPlatformLoad(generation: Int, block: suspend (Player) -> Unit) {
        platformLoadJob?.cancel()
        platformStopJob?.cancel()
        platformStopJob = null
        stopAndroidCrossfade()
        seekJob?.cancel()
        stopBufferingTimeout()
        stopRetry()
        resetRetries(generation)
        diagnostics.engineSelected(PlaybackEnginePath.Media3)
        platformLoadJob = scope.launch {
            try {
                startPlaybackService()
                ensureController()
                controllerMutex.withLock {
                    val player = activeLocalPlayer() ?: return@withLock
                    if (!isPlayRequestCurrent(generation)) return@withLock
                    appControllerMutationInProgress = true
                    try {
                        block(player)
                        if (isPlayRequestCurrent(generation)) {
                            syncFromController(generation)
                        }
                    } finally {
                        appControllerMutationInProgress = false
                    }
                }
            } catch (e: CancellationException) {
                throw e
            } catch (error: Throwable) {
                PhoebeLog.d("AndroidAudioPlayer") { "platform load failed: ${error.message}" }
                pendingControllerTarget = null
                clearPendingAutoplay()
                stopBufferingTimeout()
                markPlaybackFailed(generation)
            }
        }
    }

    private fun loadQueueOnPlayer(
        player: Player,
        queue: List<Track>,
        targetIndex: Int,
        queueIds: List<String>,
        generation: Int,
    ) {
        val windowStartIndex = targetIndex
        val windowTracks = queue.subList(
            windowStartIndex,
            (windowStartIndex + MaxPlatformQueueItems).coerceAtMost(queue.size),
        )
        expectControllerTarget(queueIds, platformIndex = 0, generation)
        player.pause()
        player.stop()
        player.clearMediaItems()
        player.volume = effectiveOutputVolume()
        player.setMediaItems(windowTracks.map { playbackMediaItem(it, inAppPlayback = true) }, 0, 0L)
        player.prepare()
        loadedPlatformQueue = LoadedPlatformQueue(
            queueIds = queueIds,
            firstAppIndex = windowStartIndex,
            itemCount = windowTracks.size,
        )
        queue.getOrNull(targetIndex)?.let { updateOptimisticLocalBufferedPosition(it, generation) }
        if (playWhenReady) {
            markPendingAutoplay(generation)
            player.play()
        }
    }

    private fun updateOptimisticLocalBufferedPosition(track: Track, generation: Int) {
        val durationMs = track.durationMs.takeIf { it > 0L } ?: return
        val uri = track.localUri ?: track.streamUrl
        if (uri.isBlank()) return
        if (!uri.isHttpUrl()) {
            updateBufferedPosition(durationMs, generation)
        }
    }

    private fun startPlaybackService() {
        appContext.startService(
            Intent(appContext, PlaybackService::class.java),
        )
    }

    private suspend fun ensureController() {
        if (controller != null) return
        val token = SessionToken(appContext, ComponentName(appContext, PlaybackService::class.java))
        val connected = MediaController.Builder(appContext, token).buildAsync().await()
        connected.addListener(controllerListener)
        controller = connected
        syncFromController()
    }

    private fun syncFromController(generation: Int = activePlayGeneration) {
        if (!isPlayRequestCurrent(generation)) return
        if (crossfadePlayer != null && crossfadeOwnedTrackId != null) return
        val player = activeLocalPlayer() ?: return
        val appState = state.value
        val controllerIndex = player.currentMediaItemIndex
        val loaded = loadedPlatformQueue
        val appControllerIndex = loaded?.appIndexFor(controllerIndex) ?: controllerIndex
        if (pendingControllerTarget != null) {
            val queueIds = appState.queue.map { it.id }
            if (isWaitingForControllerTarget(queueIds, controllerIndex, generation)) return
        }
        if (appState.currentIndex >= 0 &&
            appControllerIndex >= 0 &&
            appControllerIndex != appState.currentIndex
        ) {
            if (appControllerMutationInProgress) return
            val queueIds = appState.queue.map { it.id }
            if (loaded?.queueIds == queueIds && appControllerIndex in appState.queue.indices) {
                adoptQueueState(appState.queue, appControllerIndex, player.isPlaying)
            } else {
                return
            }
        }
        val controllerPosition = player.currentPosition.coerceAtLeast(0L)
        if (appState.isBuffering &&
            appState.positionMs == 0L &&
            controllerPosition > 1_500L
        ) {
            return
        }
        val buffering = player.playbackState == Player.STATE_BUFFERING
        val autoplayPending = pendingAutoplayGeneration == generation &&
            playWhenReady &&
            appState.currentTrack != null &&
            player.playbackState != Player.STATE_ENDED &&
            !player.isPlaying
        if (autoplayPending) {
            if (!appControllerMutationInProgress) {
                player.play()
            }
            val autoplayElapsedMs = SystemClock.elapsedRealtime() - pendingAutoplayStartedAtMs
            if (autoplayElapsedMs >= AutoplayStartRetryMs &&
                player.playbackState == Player.STATE_READY &&
                player.playbackSuppressionReason == Player.PLAYBACK_SUPPRESSION_REASON_NONE
            ) {
                schedulePlaybackRetry(null, generation)
                return
            }
            reportPlaybackDiagnostics(
                engine = PlaybackEnginePath.Media3,
                positionMs = controllerPosition,
                durationMs = player.duration.coerceAtLeast(0L),
                isPlaying = false,
            )
            applyPlatformPlayback(
                positionMs = controllerPosition,
                durationMs = player.duration.coerceAtLeast(0L),
                isPlaying = false,
                isBuffering = true,
                bufferedPositionMs = player.bufferedPosition
                    .coerceAtLeast(controllerPosition)
                    .coerceAtLeast(0L),
                generation = generation,
            )
            startBufferingTimeout(generation)
            return
        }
        if (player.isPlaying && pendingAutoplayGeneration == generation) {
            clearPendingAutoplay()
        }
        val transientPauseDuringAppLoad = playWhenReady && appState.isBuffering && !player.playWhenReady
        if (transientPauseDuringAppLoad) {
            startBufferingTimeout(generation)
            return
        }
        if (!appControllerMutationInProgress) {
            adoptPlatformPlayIntent(player.playWhenReady)
        }
        reportPlaybackDiagnostics(
            engine = PlaybackEnginePath.Media3,
            positionMs = controllerPosition,
            durationMs = player.duration.coerceAtLeast(0L),
            isPlaying = player.isPlaying && !buffering,
        )
        applyPlatformPlayback(
            positionMs = controllerPosition,
            durationMs = player.duration.coerceAtLeast(0L),
            isPlaying = player.isPlaying && !buffering,
            isBuffering = buffering,
            bufferedPositionMs = player.bufferedPosition.coerceAtLeast(controllerPosition).coerceAtLeast(0L),
            generation = generation,
        )
        if (player.isPlaying && playWhenReady) {
            stopBufferingTimeout()
            resetRetries(generation)
            startPositionSyncLoop(generation)
        } else {
            stopPositionSyncLoop()
            if (buffering && playWhenReady) {
                startBufferingTimeout(generation)
            } else {
                stopBufferingTimeout()
            }
        }
    }

    private fun expectControllerTarget(queueIds: List<String>, platformIndex: Int, generation: Int) {
        pendingControllerTarget = PendingControllerTarget(
            queueIds = queueIds,
            platformIndex = platformIndex,
            generation = generation,
        )
    }

    private fun isWaitingForControllerTarget(
        queueIds: List<String>,
        controllerIndex: Int,
        generation: Int,
    ): Boolean {
        val pending = pendingControllerTarget ?: return false
        if (pending.generation != generation || pending.queueIds != queueIds) {
            pendingControllerTarget = null
            return false
        }
        if (controllerIndex == pending.platformIndex) {
            pendingControllerTarget = null
            return false
        }
        return true
    }

    private fun controllerMatchesAppState(player: Player, generation: Int): Boolean {
        if (!isPlayRequestCurrent(generation)) return false
        val appIndex = state.value.currentIndex
        if (appIndex < 0) return true
        val controllerIndex = player.currentMediaItemIndex
        val appControllerIndex = loadedPlatformQueue?.appIndexFor(controllerIndex) ?: controllerIndex
        return controllerIndex < 0 || appControllerIndex == appIndex
    }

    private fun startPositionSyncLoop(generation: Int) {
        if (positionSyncJob?.isActive == true) return
        positionSyncJob = scope.launch {
            while (isActive) {
                val player = activeLocalPlayer() ?: break
                delay(positionSyncIntervalMs(player))
                if (!player.isPlaying || !controllerMatchesAppState(player, generation)) break
                reportPlaybackDiagnostics(
                    engine = PlaybackEnginePath.Media3,
                    positionMs = player.currentPosition.coerceAtLeast(0L),
                    durationMs = player.duration.coerceAtLeast(0L),
                    isPlaying = true,
                )
                applyPlatformPlayback(
                    positionMs = player.currentPosition.coerceAtLeast(0L),
                    durationMs = player.duration.coerceAtLeast(0L),
                    isPlaying = true,
                    isBuffering = false,
                    bufferedPositionMs = player.bufferedPosition
                        .coerceAtLeast(player.currentPosition)
                        .coerceAtLeast(0L),
                    generation = generation,
                )
            }
        }
    }

    private fun positionSyncIntervalMs(player: Player): Long {
        val durationMs = state.value.durationMs.takeIf { it > 0L } ?: return NormalPositionSyncIntervalMs
        val remainingMs = durationMs - player.currentPosition.coerceAtLeast(0L)
        return if (remainingMs in 0L..FinePositionSyncWindowMs) {
            FinePositionSyncIntervalMs
        } else {
            NormalPositionSyncIntervalMs
        }
    }

    private fun stopPositionSyncLoop() {
        positionSyncJob?.cancel()
        positionSyncJob = null
    }

    private fun startBufferingTimeout(generation: Int) {
        if (bufferingTimeoutJob?.isActive == true) return
        bufferingTimeoutJob = scope.launch {
            delay(PlaybackBufferingTimeoutMs)
            if (!isPlayRequestCurrent(generation) || !state.value.isBuffering) return@launch
            PhoebeLog.d("AndroidAudioPlayer") { "playback timed out while buffering" }
            schedulePlaybackRetry(null, generation)
        }
    }

    private fun stopBufferingTimeout() {
        bufferingTimeoutJob?.cancel()
        bufferingTimeoutJob = null
    }

    private fun schedulePlaybackRetry(error: PlaybackException?, generation: Int) {
        if (!isPlayRequestCurrent(generation) || !playWhenReady) return
        if (error != null && !error.isRecoverableStreamError()) {
            clearPendingAutoplay()
            markPlaybackFailed(generation)
            return
        }
        if (retryGeneration != generation) {
            retryGeneration = generation
            retryCount = 0
        }
        if (retryCount >= MaxStreamRetryCount) {
            PhoebeLog.d("AndroidAudioPlayer") { "stream retry exhausted" }
            clearPendingAutoplay()
            markPlaybackFailed(generation)
            return
        }
        retryCount++
        retryJob?.cancel()
        val delayMs = StreamRetryBaseDelayMs * retryCount
        retryJob = scope.launch {
            val player = activeLocalPlayer() ?: return@launch
            val positionMs = player.currentPosition.coerceAtLeast(0L)
            applyPlatformPlayback(
                positionMs = positionMs,
                durationMs = player.duration.coerceAtLeast(0L),
                isPlaying = false,
                isBuffering = true,
                bufferedPositionMs = player.bufferedPosition.coerceAtLeast(positionMs).coerceAtLeast(0L),
                generation = generation,
            )
            delay(delayMs)
            if (!isPlayRequestCurrent(generation) || !playWhenReady) return@launch
            controllerMutex.withLock {
                val retryPlayer = activeLocalPlayer() ?: return@withLock
                retryPlayer.seekTo(positionMs)
                retryPlayer.prepare()
                markPendingAutoplay(generation)
                retryPlayer.play()
            }
            syncFromController(generation)
        }
    }

    private fun resetRetries(generation: Int) {
        retryGeneration = generation
        retryCount = 0
        retryJob?.cancel()
        retryJob = null
    }

    private fun stopRetry() {
        retryJob?.cancel()
        retryJob = null
        retryGeneration = -1
        retryCount = 0
    }

    private fun PlaybackException.isRecoverableStreamError(): Boolean =
        errorCode == PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_FAILED ||
            errorCode == PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_TIMEOUT ||
            errorCode == PlaybackException.ERROR_CODE_IO_BAD_HTTP_STATUS ||
            errorCode == PlaybackException.ERROR_CODE_IO_UNSPECIFIED

    private fun reportPlaybackDiagnostics(
        engine: PlaybackEnginePath,
        positionMs: Long,
        durationMs: Long,
        isPlaying: Boolean,
    ) {
        diagnostics.playbackProgress(engine, positionMs, durationMs)
        if (isPlaying) {
            diagnostics.platformPlaying(engine, positionMs, durationMs)
        }
    }

    private suspend fun <T> com.google.common.util.concurrent.ListenableFuture<T>.await(): T =
        suspendCancellableCoroutine { continuation ->
            addListener(
                {
                    try {
                        continuation.resume(get())
                    } catch (error: Throwable) {
                        continuation.resumeWithException(error)
                    }
                },
                { command -> command.run() },
            )
        }

    private companion object {
        const val PlaybackBufferingTimeoutMs = 30_000L
        const val AutoplayStartRetryMs = 2_000L
        const val MaxStreamRetryCount = 5
        const val StreamRetryBaseDelayMs = 1_000L
        const val NormalPositionSyncIntervalMs = 1_000L
        const val FinePositionSyncIntervalMs = 250L
        const val FinePositionSyncWindowMs = 12_000L
        const val MaxPlatformQueueItems = 24
        const val CrossfadeSteps = 24
        const val CrossfadePrepareTimeoutMs = 5_000L
        const val CrossfadeMinimumFadeMs = 500L
    }
}

private fun String.isHttpUrl(): Boolean =
    startsWith("http://", ignoreCase = true) || startsWith("https://", ignoreCase = true)
