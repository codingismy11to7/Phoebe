package com.phoebe.app.player

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import com.phoebe.app.AndroidContextHolder
import com.phoebe.app.domain.Track
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
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
    private val pendingActions = mutableListOf<() -> Unit>()

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
    }

    init {
        AndroidPlaybackBridge.onSkipNext = { next() }
        AndroidPlaybackBridge.onSkipPrevious = { previous() }
        AndroidPlaybackBridge.onTrackEnded = { next() }
        AndroidPlaybackBridge.onPlayQueue = { queue, index -> play(queue, index) }
        AndroidPlaybackBridge.onAdoptQueue = { queue, index, playing ->
            adoptQueueState(queue, index, playing)
        }
        scope.launch { ensureController() }
    }

    fun ensureConnected() {
        if (controller == null) {
            scope.launch { ensureController() }
        }
    }

    override fun playTrack(track: Track) {
        withController {
            setMediaItem(playbackMediaItem(track))
            prepare()
            play()
        }
    }

    override fun pause() {
        withController { pause() }
    }

    override fun resume() {
        withController { play() }
    }

    override fun seek(positionMs: Long) {
        withController { seekTo(positionMs) }
    }

    override fun setOutputVolume(volume: Float) {
        withController { this.volume = volume }
    }

  override fun playUri(uri: String) {
        if (uri.isBlank()) return
        withController {
            setMediaItem(MediaItem.fromUri(uri))
            prepare()
            play()
        }
    }

    private suspend fun ensureController() {
        if (controller != null) return
        appContext.startForegroundService(Intent(appContext, PlaybackService::class.java))
        val token = SessionToken(appContext, ComponentName(appContext, PlaybackService::class.java))
        val connected = MediaController.Builder(appContext, token).buildAsync().await()
        connected.addListener(controllerListener)
        controller = connected
        val queued = pendingActions.toList()
        pendingActions.clear()
        queued.forEach { it.invoke() }
        syncFromController()
    }

    private fun withController(block: Player.() -> Unit) {
        val current = controller
        if (current != null) {
            current.block()
            syncFromController()
            return
        }
        pendingActions.add {
            controller?.block()
            syncFromController()
        }
        scope.launch { ensureController() }
    }

    private fun syncFromController() {
        val player = controller ?: return
        applyPlatformPlayback(
            positionMs = player.currentPosition.coerceAtLeast(0L),
            durationMs = player.duration.coerceAtLeast(0L),
            isPlaying = player.isPlaying,
        )
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
}
