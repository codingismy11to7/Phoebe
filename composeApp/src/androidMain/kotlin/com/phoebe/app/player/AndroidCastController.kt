package com.phoebe.app.player

import android.content.Intent
import android.content.Context
import android.media.AudioManager
import com.google.android.gms.cast.MediaInfo
import com.google.android.gms.cast.MediaLoadRequestData
import com.google.android.gms.cast.MediaMetadata
import com.google.android.gms.cast.MediaSeekOptions
import com.google.android.gms.cast.framework.CastContext
import com.google.android.gms.cast.framework.CastSession
import com.google.android.gms.cast.framework.SessionManagerListener
import com.google.android.gms.cast.framework.media.RemoteMediaClient
import com.google.android.gms.common.api.ResultCallback
import com.phoebe.app.AndroidContextHolder
import com.phoebe.app.domain.Track
import com.phoebe.app.platform.PhoebeLog
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

actual fun createCastController(audioPlayer: AudioPlayer): CastController =
    AndroidCastControllerHolder.instance.also { it.bindAudioPlayer(audioPlayer) }

private object AndroidCastControllerHolder {
    val instance: AndroidCastController by lazy { AndroidCastController() }
}

private data class PendingCastHandoff(
    val queue: List<Track>,
    val index: Int,
    val positionMs: Long,
    val wasLocalPlaying: Boolean,
    val requestId: Long,
)

private class AndroidCastController : CastController {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val appContext get() = AndroidContextHolder.application
    private val castContext: CastContext? get() = runCatching { CastContext.getSharedInstance(appContext) }.getOrNull()
    private var positionJob: Job? = null
    private var loadTimeoutJob: Job? = null
    private var audioPlayer: AudioPlayer? = null
    private var sessionListenerRegistered = false
    private var pendingHandoff: PendingCastHandoff? = null
    private var loadRequestId = 0L

    private val mutableState = MutableStateFlow(
        CastState(
            isAvailable = true,
        ),
    )
    override val state: StateFlow<CastState> = mutableState

    private val remoteMediaClientListener = object : RemoteMediaClient.Callback() {
        override fun onStatusUpdated() {
            syncRemotePlayback()
        }

        override fun onMetadataUpdated() = Unit
        override fun onQueueStatusUpdated() = Unit
        override fun onPreloadStatusUpdated() = Unit
    }

    private val sessionListener = object : SessionManagerListener<CastSession> {
        override fun onSessionStarted(session: CastSession, sessionId: String) = connect(session)
        override fun onSessionResumed(session: CastSession, wasSuspended: Boolean) = connect(session)
        override fun onSessionEnded(session: CastSession, error: Int) = disconnectState()
        override fun onSessionSuspended(session: CastSession, reason: Int) {
            mutableState.update { it.copy(isBuffering = true) }
        }

        override fun onSessionResumeFailed(session: CastSession, error: Int) = disconnectState()
        override fun onSessionStarting(session: CastSession) {
            mutableState.update { it.copy(isAvailable = true, isBuffering = true, message = null) }
        }

        override fun onSessionStartFailed(session: CastSession, error: Int) {
            mutableState.update { it.copy(isBuffering = false, message = "Couldn't start Chromecast session.") }
        }

        override fun onSessionEnding(session: CastSession) = Unit
        override fun onSessionResuming(session: CastSession, sessionId: String) = Unit
    }

    init {
        AndroidPlaybackBridge.isCastActive = { mutableState.value.isConnected }
        AndroidPlaybackBridge.onCastTogglePlayPause = { togglePlayPause() }
        AndroidPlaybackBridge.onCastPlay = { playCast() }
        AndroidPlaybackBridge.onCastPause = { pauseCast() }
        AndroidPlaybackBridge.onCastSkipNext = { next() }
        AndroidPlaybackBridge.onCastSkipPrevious = { previous() }
        AndroidPlaybackBridge.onCastVolume = { volume -> applyCastVolume(volume) }
        AndroidPlaybackBridge.readCastVolume = { readCastVolumeNormalized() }
        AndroidPlaybackBridge.applyCastVolume = { volume -> applyCastVolume(volume) }
        AndroidPlaybackBridge.adjustCastVolumeStep = { up -> adjustCastVolumeStep(up) }
        ensureCastSessionListener()
    }

    fun bindAudioPlayer(audioPlayer: AudioPlayer) {
        this.audioPlayer = audioPlayer
        ensureCastSessionListener()
    }

    override fun showDevicePicker() {
        val activity = AndroidContextHolder.activity
        ensureCastSessionListener()
        if (activity == null) {
            mutableState.update { it.copy(message = "Chromecast is not available right now.") }
            return
        }
        mutableState.update { it.copy(isAvailable = true, message = null) }
        if (!activity.showCastRoutePicker()) {
            mutableState.update { it.copy(message = "Couldn't open Chromecast picker.") }
        }
    }

    override fun disconnect() {
        castContext?.sessionManager?.endCurrentSession(true)
        disconnectState()
    }

    override fun loadQueue(queue: List<Track>, startIndex: Int) {
        loadQueue(queue, startIndex, startPositionMs = 0L)
    }

    private fun loadQueue(queue: List<Track>, startIndex: Int, startPositionMs: Long) {
        if (!queue.isChromecastPlayableQueue()) {
            mutableState.update { it.copy(message = "Chromecast can play Plex streaming songs only.") }
            return
        }
        val session = castContext?.sessionManager?.currentCastSession
        val client = session?.remoteMediaClient
        if (session == null || client == null) {
            mutableState.update { it.copy(message = "Choose a Chromecast before casting.") }
            showDevicePicker()
            return
        }
        val index = startIndex.coerceIn(queue.indices)
        val track = queue[index]
        val positionMs = startPositionMs.coerceAtLeast(0L)
        val localState = audioPlayer?.state?.value
        val servicePlaying = AndroidPlaybackBridge.servicePlayer?.isPlaying == true
        val wasLocalPlaying = localState?.isPlaying == true || servicePlaying
        loadRequestId++
        val requestId = loadRequestId
        pendingHandoff = PendingCastHandoff(
            queue = queue,
            index = index,
            positionMs = positionMs,
            wasLocalPlaying = wasLocalPlaying,
            requestId = requestId,
        )
        mutableState.update {
            it.copy(
                isAvailable = true,
                isConnected = true,
                deviceName = session.castDevice?.friendlyName,
                queue = queue,
                currentIndex = index,
                isPlaying = false,
                isBuffering = true,
                positionMs = positionMs,
                durationMs = track.durationMs,
                message = null,
            )
        }
        val pendingResult = client.load(
            MediaLoadRequestData.Builder()
                .setMediaInfo(track.toMediaInfo())
                .setAutoplay(true)
                .setCurrentTime(positionMs)
                .build(),
        )
        pendingResult.setResultCallback(
            ResultCallback { result ->
                scope.launch {
                    handleCastLoadResult(requestId, result)
                }
            },
        )
        scheduleLoadTimeout(requestId)
        startPositionSync()
    }

    override fun togglePlayPause() {
        val client = remoteMediaClient() ?: return
        if (mutableState.value.isPlaying) {
            pauseCast()
        } else {
            playCast()
        }
    }

    private fun playCast() {
        remoteMediaClient()?.play()
        mutableState.update { it.copy(isPlaying = true, isBuffering = false) }
    }

    private fun pauseCast() {
        remoteMediaClient()?.pause()
        mutableState.update { it.copy(isPlaying = false, isBuffering = false) }
    }

    private fun readCastVolumeNormalized(): Float {
        val session = castContext?.sessionManager?.currentCastSession ?: return 0.7f
        return session.volume.toFloat().coerceIn(0f, 1f)
    }

    private fun applyCastVolume(volume: Float) {
        val session = castContext?.sessionManager?.currentCastSession ?: return
        val normalized = volume.toDouble().coerceIn(0.0, 1.0)
        runCatching { session.volume = normalized }
        AndroidPlaybackBridge.onCastVolumeChanged?.invoke(normalized.toFloat())
    }

    private fun adjustCastVolumeStep(up: Boolean): Boolean {
        if (!mutableState.value.isConnected) return false
        val session = castContext?.sessionManager?.currentCastSession ?: return false
        val step = localMusicVolumeStep()
        val next = (session.volume + if (up) step else -step).coerceIn(0.0, 1.0)
        applyCastVolume(next.toFloat())
        return true
    }

    private fun localMusicVolumeStep(): Double {
        val audioManager = appContext.getSystemService(Context.AUDIO_SERVICE) as AudioManager
        val max = audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC).coerceAtLeast(1)
        return 1.0 / max
    }

    private fun syncCastVolumeFromLocalMusicStream() {
        if (!mutableState.value.isConnected) return
        val audioManager = appContext.getSystemService(Context.AUDIO_SERVICE) as AudioManager
        val current = audioManager.getStreamVolume(AudioManager.STREAM_MUSIC)
        val max = audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
        if (max <= 0) return
        applyCastVolume((current.toFloat() / max.toFloat()).coerceIn(0f, 1f))
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
        remoteMediaClient()?.seek(
            MediaSeekOptions.Builder()
                .setPosition(positionMs.coerceAtLeast(0L))
                .build(),
        )
        mutableState.update { it.copy(positionMs = positionMs.coerceAtLeast(0L)) }
    }

    private fun connect(session: CastSession) {
        ensurePlaybackServiceRunning()
        session.remoteMediaClient?.registerCallback(remoteMediaClientListener)
        mutableState.update {
            it.copy(
                isAvailable = true,
                isConnected = true,
                deviceName = session.castDevice?.friendlyName,
                isBuffering = false,
                message = null,
            )
        }
        castCurrentLocalQueueIfPossible()
        syncCastVolumeFromLocalMusicStream()
        syncRemotePlayback()
        startPositionSync()
    }

    private fun disconnectState() {
        loadTimeoutJob?.cancel()
        loadTimeoutJob = null
        val pending = pendingHandoff
        pendingHandoff = null
        val previous = mutableState.value
        val localPlayer = audioPlayer
        if (pending != null) {
            restoreLocalPlayback(pending)
        } else if (previous.isConnected && previous.queue.isNotEmpty() && previous.currentIndex in previous.queue.indices) {
            localPlayer?.play(previous.queue, previous.currentIndex)
            if (previous.positionMs > 0L) {
                localPlayer?.seekTo(previous.positionMs)
            }
        }
        positionJob?.cancel()
        positionJob = null
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

    private fun syncRemotePlayback() {
        val client = remoteMediaClient() ?: return
        val previous = mutableState.value
        val isPlaying = client.isPlaying
        val isBuffering = client.isBuffering
        val positionMs = client.approximateStreamPosition.coerceAtLeast(0L)
        val durationMs = client.streamDuration.coerceAtLeast(0L).takeIf { duration -> duration > 0L }
            ?: previous.durationMs
        if (previous.isPlaying != isPlaying ||
            previous.isBuffering != isBuffering ||
            previous.durationMs != durationMs ||
            kotlin.math.abs(previous.positionMs - positionMs) > 750L
        ) {
            mutableState.update {
                it.copy(
                    isPlaying = isPlaying,
                    isBuffering = isBuffering,
                    positionMs = positionMs,
                    durationMs = durationMs,
                )
            }
        }
        val localPlayer = audioPlayer
        if (client.isPlaying && localPlayer?.state?.value?.isPlaying == true) {
            localPlayer.togglePlayPause()
        }
        if (AndroidPlaybackBridge.servicePlayer?.isPlaying == true) {
            suspendLocalPlayback()
        }
    }

    private fun suspendLocalPlayback() {
        AndroidPlaybackBridge.pauseLocalPlaybackImmediately()
    }

    private fun startPositionSync() {
        positionJob?.cancel()
        positionJob = scope.launch {
            while (isActive && mutableState.value.isConnected) {
                syncRemotePlayback()
                delay(1000L)
            }
        }
    }

    private fun remoteMediaClient(): RemoteMediaClient? =
        castContext?.sessionManager?.currentCastSession?.remoteMediaClient

    private fun ensureCastSessionListener() {
        if (sessionListenerRegistered) return
        val context = castContext ?: return
        context.sessionManager.addSessionManagerListener(sessionListener, CastSession::class.java)
        sessionListenerRegistered = true
        mutableState.update { it.copy(isAvailable = true, message = null) }
        context.sessionManager.currentCastSession?.let(::connect)
    }

    private fun restoreLocalPlayback(handoff: PendingCastHandoff) {
        val localPlayer = audioPlayer ?: return
        localPlayer.play(handoff.queue, handoff.index)
        if (handoff.positionMs > 0L) {
            localPlayer.seekTo(handoff.positionMs)
        }
        if (handoff.wasLocalPlaying && !localPlayer.state.value.isPlaying) {
            localPlayer.togglePlayPause()
        }
    }

    private fun scheduleLoadTimeout(requestId: Long) {
        loadTimeoutJob?.cancel()
        loadTimeoutJob = scope.launch {
            delay(LOAD_TIMEOUT_MS)
            if (pendingHandoff?.requestId == requestId) {
                onCastLoadFailed(requestId, "Chromecast didn't respond in time. Playing on this device.")
            }
        }
    }

    private fun handleCastLoadResult(requestId: Long, result: RemoteMediaClient.MediaChannelResult) {
        if (pendingHandoff?.requestId != requestId) return
        loadTimeoutJob?.cancel()
        val status = result.status
        val mediaError = result.mediaError
        if (status.isSuccess && mediaError == null) {
            onCastLoadSucceeded(requestId)
        } else {
            PhoebeLog.d("AndroidCastController") {
                "cast load failed status=${status.statusCode} mediaError=${mediaError?.detailedErrorCode}"
            }
            val message = mediaError?.detailedErrorCode?.let { code ->
                "Couldn't load on Chromecast (error $code). Playing on this device."
            } ?: "Couldn't load on Chromecast. Playing on this device."
            onCastLoadFailed(requestId, message)
        }
    }

    private fun onCastLoadSucceeded(requestId: Long) {
        if (pendingHandoff?.requestId != requestId) return
        pendingHandoff = null
        suspendLocalPlayback()
        syncRemotePlayback()
    }

    private fun onCastLoadFailed(requestId: Long, message: String) {
        val handoff = pendingHandoff?.takeIf { it.requestId == requestId } ?: return
        pendingHandoff = null
        restoreLocalPlayback(handoff)
        mutableState.update {
            it.copy(
                queue = emptyList(),
                currentIndex = -1,
                isPlaying = false,
                isBuffering = false,
                positionMs = 0L,
                message = message,
            )
        }
    }

    private fun ensurePlaybackServiceRunning() {
        appContext.startService(Intent(appContext, PlaybackService::class.java))
    }

    private fun castCurrentLocalQueueIfPossible() {
        val localPlayer = audioPlayer ?: return
        val current = localPlayer.state.value
        val index = current.currentIndex
        if (index !in current.queue.indices) return
        if (!current.queue.isChromecastPlayableQueue()) {
            mutableState.update { it.copy(message = "Chromecast can play Plex streaming songs only.") }
            return
        }
        loadQueue(current.queue, index, current.positionMs)
    }

    private fun Track.toMediaInfo(): MediaInfo {
        val metadata = MediaMetadata(MediaMetadata.MEDIA_TYPE_MUSIC_TRACK).apply {
            putString(MediaMetadata.KEY_TITLE, title)
            putString(MediaMetadata.KEY_ARTIST, artist)
            putString(MediaMetadata.KEY_ALBUM_TITLE, album)
            thumbUrl?.let { addImage(com.google.android.gms.common.images.WebImage(android.net.Uri.parse(it))) }
        }
        val mediaUrl = chromecastMediaUrl()
        val contentType = chromecastContentType(mediaUrl)
        PhoebeLog.d("AndroidCastController") {
            "loading cast media id=$id codec=$audioCodec contentType=$contentType transcode=${mediaUrl != streamUrl}"
        }
        return MediaInfo.Builder(mediaUrl)
            .setStreamType(MediaInfo.STREAM_TYPE_BUFFERED)
            .setContentType(contentType)
            .setMetadata(metadata)
            .setStreamDuration(durationMs)
            .build()
    }

    private fun Track.chromecastMediaUrl(): String {
        if (hasChromecastDirectPlayableCodec()) return streamUrl
        val ratingKey = id.removePrefix("plex:").takeIf { id.startsWith("plex:") && it.isNotBlank() }
            ?: return streamUrl
        val uri = android.net.Uri.parse(streamUrl)
        val token = uri.getQueryParameter("X-Plex-Token").orEmpty()
        if (uri.scheme.isNullOrBlank() || uri.authority.isNullOrBlank() || token.isBlank()) return streamUrl
        return uri.buildUpon()
            .encodedPath("/music/:/transcode/universal/start.mp3")
            .clearQuery()
            .appendQueryParameter("path", "/library/metadata/$ratingKey")
            .appendQueryParameter("mediaIndex", "0")
            .appendQueryParameter("partIndex", "0")
            .appendQueryParameter("protocol", "http")
            .appendQueryParameter("format", "mp3")
            .appendQueryParameter("audioCodec", "mp3")
            .appendQueryParameter("directPlay", "0")
            .appendQueryParameter("directStream", "0")
            .appendQueryParameter("X-Plex-Token", token)
            .build()
            .toString()
    }

    private fun Track.hasChromecastDirectPlayableCodec(): Boolean =
        when (audioCodec?.lowercase()) {
            "aac", "mp3", "mp4", "m4a" -> true
            else -> {
                val path = filepath ?: streamUrl.substringBefore('?').substringBefore('#')
                when (path.substringAfterLast('.', missingDelimiterValue = "").lowercase()) {
                    "aac", "mp3", "m4a", "mp4" -> true
                    else -> false
                }
            }
        }

    private fun Track.chromecastContentType(mediaUrl: String): String =
        if (mediaUrl != streamUrl) {
            "audio/mpeg"
        } else {
            chromecastDirectContentType()
        }

    private fun Track.chromecastDirectContentType(): String =
        when (audioCodec?.lowercase()) {
            "aac" -> "audio/aac"
            "mp3" -> "audio/mpeg"
            "alac", "m4a", "mp4" -> "audio/mp4"
            "flac" -> "audio/flac"
            "ogg", "opus", "vorbis" -> "audio/ogg"
            "wav" -> "audio/wav"
            else -> {
                val path = filepath ?: streamUrl.substringBefore('?').substringBefore('#')
                when (path.substringAfterLast('.', missingDelimiterValue = "").lowercase()) {
                    "aac" -> "audio/aac"
                    "m4a", "mp4" -> "audio/mp4"
                    "flac" -> "audio/flac"
                    "ogg", "oga", "opus" -> "audio/ogg"
                    "wav" -> "audio/wav"
                    else -> "audio/mpeg"
                }
            }
        }

    private companion object {
        const val LOAD_TIMEOUT_MS = 30_000L
    }
}
