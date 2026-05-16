package com.phoebe.app.player

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import com.phoebe.app.AndroidContextHolder
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

private class AndroidAudioPlayer : SimpleAudioPlayer() {
    override val useProgressTicker: Boolean = false

    private val appContext: Context
        get() = AndroidContextHolder.application

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var controller: MediaController? = null
    private var positionSyncJob: Job? = null
    private var platformLoadJob: Job? = null
    private var seekJob: Job? = null
    private var bufferingTimeoutJob: Job? = null
    private val controllerMutex = Mutex()
    private var loadedQueueIds: List<String>? = null

    private val controllerListener = object : Player.Listener {
        override fun onIsPlayingChanged(isPlaying: Boolean) {
            syncFromController()
        }

        override fun onPlaybackStateChanged(playbackState: Int) {
            syncFromController()
        }

        override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
            syncFromController()
        }

        override fun onPlayerError(error: PlaybackException) {
            PhoebeLog.d("AndroidAudioPlayer") { "playback failed: ${error.message}" }
            stopBufferingTimeout()
            markPlaybackFailed()
            stopPositionSyncLoop()
        }
    }

    init {
        AndroidPlaybackBridge.onSkipNext = { next() }
        AndroidPlaybackBridge.onSkipPrevious = { previous() }
        AndroidPlaybackBridge.onTrackEnded = { next() }
        AndroidPlaybackBridge.onPlayQueue = { queue, index -> play(queue, index) }
        AndroidPlaybackBridge.onAdoptQueue = { queue, index, playing ->
            loadedQueueIds = queue.map { it.id }
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

    override fun skipToInQueueOnPlatform(
        queue: List<Track>,
        startIndex: Int,
        track: Track,
        generation: Int,
    ) {
        runPlatformLoad(generation) { player ->
            val targetIndex = startIndex.coerceIn(queue.indices)
            val queueIds = queue.map { it.id }
            if (loadedQueueIds == queueIds &&
                player.mediaItemCount == queue.size &&
                targetIndex < player.mediaItemCount
            ) {
                player.pause()
                player.seekTo(targetIndex, 0L)
                player.volume = effectiveOutputVolume()
                if (playWhenReady) {
                    player.play()
                }
            } else {
                loadQueueOnPlayer(player, queue, targetIndex, queueIds)
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
            loadQueueOnPlayer(player, queue, startIndex.coerceIn(queue.indices), queue.map { it.id })
        }
    }

    override fun stopCurrentPlaybackImmediately() {
        platformLoadJob?.cancel()
        platformLoadJob = null
        stopBufferingTimeout()
        loadedQueueIds = null
        scope.launch {
            controllerMutex.withLock {
                controller?.run {
                    pause()
                    stop()
                    clearMediaItems()
                }
            }
        }
    }

    override fun pause() {
        scope.launch {
            controllerMutex.withLock { controller?.pause() }
            syncFromController()
        }
    }

    override fun resume() {
        scope.launch {
            controllerMutex.withLock {
                controller?.run {
                    volume = effectiveOutputVolume()
                    play()
                }
            }
            syncFromController()
        }
    }

    override fun seek(positionMs: Long) {
        seekJob?.cancel()
        val generation = activePlayGeneration
        seekJob = scope.launch {
            controllerMutex.withLock {
                if (!isPlayRequestCurrent(generation)) return@withLock
                controller?.seekTo(positionMs)
            }
            syncFromController(generation)
        }
    }

    override fun setOutputVolume(volume: Float) {
        scope.launch {
            controllerMutex.withLock { controller?.volume = volume }
        }
    }

    override fun playUri(uri: String) {
        if (uri.isBlank()) return
        val generation = activePlayGeneration
        runPlatformLoad(generation) { player ->
            player.setMediaItem(MediaItem.fromUri(uri))
            player.prepare()
            if (playWhenReady) {
                player.play()
            }
        }
    }

    private fun forceLocalPlaybackPaused() {
        cancelPlayIntent()
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

    private fun runPlatformLoad(generation: Int, block: suspend (Player) -> Unit) {
        platformLoadJob?.cancel()
        seekJob?.cancel()
        stopBufferingTimeout()
        platformLoadJob = scope.launch {
            try {
                startPlaybackService()
                ensureController()
                controllerMutex.withLock {
                    val player = controller ?: return@withLock
                    if (!isPlayRequestCurrent(generation)) return@withLock
                    block(player)
                    if (isPlayRequestCurrent(generation)) {
                        syncFromController(generation)
                    }
                }
            } catch (e: CancellationException) {
                throw e
            } catch (error: Throwable) {
                PhoebeLog.d("AndroidAudioPlayer") { "platform load failed: ${error.message}" }
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
    ) {
        player.pause()
        player.stop()
        player.clearMediaItems()
        player.volume = effectiveOutputVolume()
        player.setMediaItems(queue.map { playbackMediaItem(it, inAppPlayback = true) }, targetIndex, 0L)
        player.prepare()
        loadedQueueIds = queueIds
        if (playWhenReady) {
            player.play()
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
        val player = controller ?: return
        val appState = state.value
        val controllerIndex = player.currentMediaItemIndex
        if (appState.currentIndex >= 0 &&
            controllerIndex >= 0 &&
            controllerIndex != appState.currentIndex
        ) {
            val queueIds = appState.queue.map { it.id }
            if (loadedQueueIds == queueIds && controllerIndex in appState.queue.indices) {
                adoptQueueState(appState.queue, controllerIndex, player.isPlaying)
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
        applyPlatformPlayback(
            positionMs = controllerPosition,
            durationMs = player.duration.coerceAtLeast(0L),
            isPlaying = player.isPlaying && !buffering,
            isBuffering = buffering,
            generation = generation,
        )
        if (player.isPlaying && playWhenReady) {
            stopBufferingTimeout()
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

    private fun controllerMatchesAppState(player: Player, generation: Int): Boolean {
        if (!isPlayRequestCurrent(generation)) return false
        val appIndex = state.value.currentIndex
        if (appIndex < 0) return true
        val controllerIndex = player.currentMediaItemIndex
        return controllerIndex < 0 || controllerIndex == appIndex
    }

    private fun startPositionSyncLoop(generation: Int) {
        if (positionSyncJob?.isActive == true) return
        positionSyncJob = scope.launch {
            while (isActive) {
                delay(250)
                val player = controller ?: break
                if (!player.isPlaying || !controllerMatchesAppState(player, generation)) break
                applyPlatformPlayback(
                    positionMs = player.currentPosition.coerceAtLeast(0L),
                    durationMs = player.duration.coerceAtLeast(0L),
                    isPlaying = true,
                    isBuffering = false,
                    generation = generation,
                )
            }
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
            controllerMutex.withLock {
                controller?.stop()
            }
            markPlaybackFailed(generation)
        }
    }

    private fun stopBufferingTimeout() {
        bufferingTimeoutJob?.cancel()
        bufferingTimeoutJob = null
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
    }
}
