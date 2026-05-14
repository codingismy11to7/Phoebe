@file:OptIn(ExperimentalWasmJsInterop::class)

package com.phoebe.app.player

import com.phoebe.app.domain.Track
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

actual fun createCastController(audioPlayer: AudioPlayer): CastController = WebCastController()

@OptIn(ExperimentalWasmJsInterop::class)
private class WebCastController : CastController {
    private val mutableState = MutableStateFlow(
        CastState(
            isAvailable = webCastAvailable(),
            message = if (webCastAvailable()) null else "Chromecast requires Chrome with Cast support.",
        ),
    )
    override val state: StateFlow<CastState> = mutableState

    override fun showDevicePicker() {
        if (!webCastAvailable()) {
            mutableState.update { it.copy(isAvailable = false, message = "Chromecast requires Chrome with Cast support.") }
            return
        }
        webCastRequestSession()
        syncSession()
    }

    override fun disconnect() {
        webCastDisconnect()
        mutableState.update {
            it.copy(
                isConnected = false,
                deviceName = null,
                isPlaying = false,
                isBuffering = false,
                message = null,
            )
        }
    }

    override fun loadQueue(queue: List<Track>, startIndex: Int) {
        if (!queue.isChromecastPlayableQueue()) {
            mutableState.update { it.copy(message = "Chromecast can play Plex streaming songs only.") }
            return
        }
        val index = startIndex.coerceIn(queue.indices)
        val track = queue[index]
        val payload = Json.encodeToString(
            WebCastMedia(
                url = track.streamUrl,
                title = track.title,
                artist = track.artist,
                album = track.album,
                imageUrl = track.thumbUrl,
                durationMs = track.durationMs,
            ),
        )
        mutableState.update {
            it.copy(
                isAvailable = true,
                isConnected = true,
                queue = queue,
                currentIndex = index,
                isPlaying = false,
                isBuffering = true,
                positionMs = 0L,
                durationMs = track.durationMs,
                message = null,
            )
        }
        webCastLoadMedia(payload)
        mutableState.update { it.copy(isPlaying = true, isBuffering = false) }
        syncSession()
    }

    override fun togglePlayPause() {
        webCastTogglePlayPause()
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
        webCastSeek(positionMs.toDouble() / 1000.0)
        mutableState.update { it.copy(positionMs = positionMs.coerceAtLeast(0L)) }
    }

    private fun syncSession() {
        mutableState.update {
            it.copy(
                isAvailable = webCastAvailable(),
                isConnected = webCastConnected(),
                deviceName = webCastDeviceName().takeIf { name -> name.isNotBlank() },
            )
        }
    }
}

@Serializable
private data class WebCastMedia(
    val url: String,
    val title: String,
    val artist: String,
    val album: String,
    val imageUrl: String?,
    val durationMs: Long,
)

@JsFun(
    """() => {
        const context = globalThis.cast?.framework?.CastContext?.getInstance?.();
        if (!context || !globalThis.chrome?.cast) return false;
        const state = context.getCastState?.();
        return state !== globalThis.cast.framework.CastState.NO_DEVICES_AVAILABLE;
    }""",
)
private external fun webCastAvailable(): Boolean

@JsFun(
    """() => {
        const context = globalThis.cast?.framework?.CastContext?.getInstance?.();
        if (!context) return;
        context.requestSession?.();
    }""",
)
private external fun webCastRequestSession()

@JsFun(
    """() => {
        const session = globalThis.cast?.framework?.CastContext?.getInstance?.().getCurrentSession?.();
        return !!session;
    }""",
)
private external fun webCastConnected(): Boolean

@JsFun(
    """() => {
        const session = globalThis.cast?.framework?.CastContext?.getInstance?.().getCurrentSession?.();
        return session?.getCastDevice?.().friendlyName || "";
    }""",
)
private external fun webCastDeviceName(): String

@JsFun(
    """(payload) => {
        const session = globalThis.cast?.framework?.CastContext?.getInstance?.().getCurrentSession?.();
        if (!session || !globalThis.chrome?.cast?.media) return;
        const item = JSON.parse(payload);
        const mediaInfo = new globalThis.chrome.cast.media.MediaInfo(item.url, "audio/mpeg");
        mediaInfo.metadata = new globalThis.chrome.cast.media.MusicTrackMediaMetadata();
        mediaInfo.metadata.title = item.title;
        mediaInfo.metadata.artist = item.artist;
        mediaInfo.metadata.albumName = item.album;
        if (item.imageUrl) mediaInfo.metadata.images = [{ url: item.imageUrl }];
        mediaInfo.duration = item.durationMs / 1000;
        const request = new globalThis.chrome.cast.media.LoadRequest(mediaInfo);
        request.autoplay = true;
        session.loadMedia(request);
    }""",
)
private external fun webCastLoadMedia(payload: String)

@JsFun(
    """() => {
        const media = globalThis.cast?.framework?.CastContext?.getInstance?.().getCurrentSession?.()?.getMediaSession?.();
        if (!media) return;
        if (media.playerState === globalThis.chrome.cast.media.PlayerState.PLAYING) media.pause(null);
        else media.play(null);
    }""",
)
private external fun webCastTogglePlayPause()

@JsFun(
    """(seconds) => {
        const media = globalThis.cast?.framework?.CastContext?.getInstance?.().getCurrentSession?.()?.getMediaSession?.();
        if (!media || !globalThis.chrome?.cast?.media) return;
        const request = new globalThis.chrome.cast.media.SeekRequest();
        request.currentTime = seconds;
        media.seek(request);
    }""",
)
private external fun webCastSeek(seconds: Double)

@JsFun(
    """() => {
        const session = globalThis.cast?.framework?.CastContext?.getInstance?.().getCurrentSession?.();
        session?.endSession?.(true);
    }""",
)
private external fun webCastDisconnect()
