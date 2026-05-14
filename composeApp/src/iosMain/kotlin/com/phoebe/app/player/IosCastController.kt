package com.phoebe.app.player

import com.phoebe.app.domain.Track
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update

actual fun createCastController(audioPlayer: AudioPlayer): CastController = IosCastController().also {
    IosCastBridge.attach(it)
}

class IosCastController : CastController {
    private val mutableState = MutableStateFlow(
        CastState(message = "Chromecast on iOS needs the Google Cast SDK in the host app."),
    )
    override val state: StateFlow<CastState> = mutableState

    override fun showDevicePicker() {
        val handled = IosCastBridge.showDevicePicker()
        if (!handled) {
            mutableState.update {
                it.copy(message = "Chromecast on iOS needs the Google Cast SDK in the host app.")
            }
        }
    }

    override fun disconnect() {
        IosCastBridge.disconnect()
        mutableState.update {
            it.copy(isConnected = false, deviceName = null, isPlaying = false, isBuffering = false)
        }
    }

    override fun loadQueue(queue: List<Track>, startIndex: Int) {
        if (!queue.isChromecastPlayableQueue()) {
            mutableState.update { it.copy(message = "Chromecast can play Plex streaming songs only.") }
            return
        }
        val index = startIndex.coerceIn(queue.indices)
        val track = queue[index]
        mutableState.update {
            it.copy(
                queue = queue,
                currentIndex = index,
                isBuffering = true,
                isPlaying = false,
                positionMs = 0L,
                durationMs = track.durationMs,
                message = null,
            )
        }
        if (!IosCastBridge.loadMedia(track)) {
            mutableState.update { it.copy(isBuffering = false, message = "Choose a Chromecast before casting.") }
        }
    }

    override fun togglePlayPause() {
        IosCastBridge.togglePlayPause()
        mutableState.update { it.copy(isPlaying = !it.isPlaying, isBuffering = false) }
    }

    override fun next() {
        val current = mutableState.value
        val target = current.currentIndex + 1
        if (target in current.queue.indices) {
            loadQueue(current.queue, target)
        }
    }

    override fun previous() {
        val current = mutableState.value
        val target = (current.currentIndex - 1).coerceAtLeast(0)
        if (target in current.queue.indices) {
            loadQueue(current.queue, target)
        }
    }

    override fun seekTo(positionMs: Long) {
        IosCastBridge.seekTo(positionMs)
        mutableState.update { it.copy(positionMs = positionMs.coerceAtLeast(0L)) }
    }

    internal fun updateFromHost(state: CastState) {
        mutableState.value = state
    }
}

/**
 * Thin bridge for the Swift/iOS host app to connect Google Cast SDK callbacks to
 * the shared Phoebe player state. The checked-in repo currently contains only
 * KMP assets for iOS, so the host app supplies the actual GCKCastContext wiring.
 */
object IosCastBridge {
    private var controller: IosCastController? = null
    var onShowDevicePicker: (() -> Unit)? = null
    var onDisconnect: (() -> Unit)? = null
    var onLoadMedia: ((url: String, title: String, artist: String, album: String, imageUrl: String?, durationMs: Long) -> Unit)? = null
    var onTogglePlayPause: (() -> Unit)? = null
    var onSeekTo: ((positionMs: Long) -> Unit)? = null

    internal fun attach(controller: IosCastController) {
        this.controller = controller
    }

    fun setAvailable(isAvailable: Boolean, message: String? = null) {
        controller?.let { target ->
            target.updateFromHost(target.state.value.copy(isAvailable = isAvailable, message = message))
        }
    }

    fun sessionStarted(deviceName: String?) {
        controller?.let { target ->
            target.updateFromHost(
                target.state.value.copy(
                    isAvailable = true,
                    isConnected = true,
                    deviceName = deviceName,
                    isBuffering = false,
                    message = null,
                ),
            )
        }
    }

    fun sessionEnded() {
        controller?.let { target ->
            target.updateFromHost(
                target.state.value.copy(
                    isConnected = false,
                    deviceName = null,
                    isPlaying = false,
                    isBuffering = false,
                ),
            )
        }
    }

    fun playbackChanged(positionMs: Long, durationMs: Long, isPlaying: Boolean, isBuffering: Boolean) {
        controller?.let { target ->
            target.updateFromHost(
                target.state.value.copy(
                    positionMs = positionMs.coerceAtLeast(0L),
                    durationMs = durationMs.takeIf { it > 0L } ?: target.state.value.durationMs,
                    isPlaying = isPlaying,
                    isBuffering = isBuffering,
                ),
            )
        }
    }

    internal fun showDevicePicker(): Boolean = onShowDevicePicker?.let {
        it()
        true
    } ?: false

    internal fun disconnect() {
        onDisconnect?.invoke()
    }

    internal fun loadMedia(track: Track): Boolean = onLoadMedia?.let {
        it(track.streamUrl, track.title, track.artist, track.album, track.thumbUrl, track.durationMs)
        true
    } ?: false

    internal fun togglePlayPause() {
        onTogglePlayPause?.invoke()
    }

    internal fun seekTo(positionMs: Long) {
        onSeekTo?.invoke(positionMs)
    }
}
