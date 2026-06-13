package com.phoebe.app.player

import android.content.Intent
import android.content.Context
import android.media.AudioManager
import com.google.android.gms.cast.MediaInfo
import com.google.android.gms.cast.MediaLoadRequestData
import com.google.android.gms.cast.MediaMetadata
import com.google.android.gms.cast.MediaQueueData
import com.google.android.gms.cast.MediaQueueItem
import com.google.android.gms.cast.MediaSeekOptions
import com.google.android.gms.cast.MediaStatus
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
import org.json.JSONObject

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

private data class CastLoadRequest(
    val requestData: MediaLoadRequestData,
    val receiverQueueSize: Int,
    val estimatedBytes: Int,
)

private data class AppQueueSnapshot(
    val queue: List<Track>,
    val currentIndex: Int,
)

private data class RemoteQueueEntry(
    val track: Track,
    val castUrl: String?,
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
    private var expectedRemoteHandoff: PendingCastHandoff? = null
    private var appQueueSnapshot: AppQueueSnapshot? = null
    private var loadRequestId = 0L

    private val mutableState = MutableStateFlow(
        CastState(
            isAvailable = true,
        ),
    )
    override val state: StateFlow<CastState> = mutableState

    override fun canLoadQueue(queue: List<Track>): CastQueueSupport =
        queue.plexChromecastQueueSupport()

    private val remoteMediaClientListener = object : RemoteMediaClient.Callback() {
        override fun onStatusUpdated() {
            syncRemotePlayback()
        }

        override fun onMetadataUpdated() {
            syncRemotePlayback()
        }
        override fun onQueueStatusUpdated() {
            syncRemotePlayback()
        }
        override fun onPreloadStatusUpdated() = Unit
    }

    private val sessionListener = object : SessionManagerListener<CastSession> {
        override fun onSessionStarted(session: CastSession, sessionId: String) =
            connect(session, castLocalQueueIfReceiverEmpty = true)

        override fun onSessionResumed(session: CastSession, wasSuspended: Boolean) =
            connect(session, castLocalQueueIfReceiverEmpty = false)

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
        AndroidPlaybackBridge.onCastSeekTo = { positionMs -> seekTo(positionMs) }
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

    override fun loadQueue(queue: List<Track>, startIndex: Int, startPositionMs: Long) {
        loadQueueInternal(queue, startIndex, startPositionMs = startPositionMs)
    }

    private fun loadQueueInternal(queue: List<Track>, startIndex: Int, startPositionMs: Long) {
        val support = canLoadQueue(queue)
        if (!support.isSupported) {
            mutableState.update { it.copy(message = support.message) }
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
        val servicePlaying = AndroidPlaybackBridge.isServicePlaybackActive()
        val wasLocalPlaying = localState?.isPlaying == true || servicePlaying
        loadRequestId++
        val requestId = loadRequestId
        rememberAppQueue(queue, index)
        val handoff = PendingCastHandoff(
            queue = queue,
            index = index,
            positionMs = positionMs,
            wasLocalPlaying = wasLocalPlaying,
            requestId = requestId,
        )
        pendingHandoff = handoff
        expectedRemoteHandoff = handoff
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
        val loadRequest = buildCastLoadRequest(queue, index, positionMs)
        PhoebeLog.d("AndroidCastController") {
            "loading cast queue receiverItems=${loadRequest.receiverQueueSize}/${queue.size} bytes=${loadRequest.estimatedBytes}"
        }
        val pendingResult = runCatching {
            client.load(loadRequest.requestData)
        }.getOrElse { error ->
            PhoebeLog.d("AndroidCastController") { "cast load request rejected: ${error.message}" }
            onCastLoadFailed(requestId, "Couldn't load this playlist on Chromecast. Playing on this device.")
            return
        }
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

    private fun connect(session: CastSession, castLocalQueueIfReceiverEmpty: Boolean) {
        ensurePlaybackServiceRunning()
        val client = session.remoteMediaClient
        client?.registerCallback(remoteMediaClientListener)
        client?.requestStatus()
        mutableState.update {
            it.copy(
                isAvailable = true,
                isConnected = true,
                deviceName = session.castDevice?.friendlyName,
                isBuffering = false,
                message = null,
            )
        }
        syncRemotePlayback()
        if (castLocalQueueIfReceiverEmpty && client?.mediaInfo == null) {
            castCurrentLocalQueueIfPossible()
        }
        startPositionSync()
    }

    private fun disconnectState() {
        loadTimeoutJob?.cancel()
        loadTimeoutJob = null
        val pending = pendingHandoff
        pendingHandoff = null
        expectedRemoteHandoff = null
        appQueueSnapshot = null
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
        AndroidPlaybackBridge.onCastMediaSessionState?.invoke(null)
    }

    private fun syncRemotePlayback() {
        val client = remoteMediaClient() ?: return
        val previous = mutableState.value
        val isPlaying = client.isPlaying
        val isBuffering = client.isBuffering
        val status = client.mediaStatus
        val queueItems = status?.queueItems.orEmpty()
        val remoteTrack = client.currentItem?.media?.toTrack() ?: client.mediaInfo?.toTrack()
        val remoteCastUrl = client.currentItem?.media?.contentId ?: client.mediaInfo?.contentId
        val knownQueue = appQueueSnapshot?.queue?.takeIf { it.isNotEmpty() }
            ?: pendingHandoff?.queue?.takeIf { it.isNotEmpty() }
            ?: previous.queue
        val remoteQueueEntries = queueItems.mapNotNull { item ->
            val track = item.media?.toTrack() ?: return@mapNotNull null
            RemoteQueueEntry(
                track = knownQueue.firstOrNull { it.matchesCastMedia(track, item.media?.contentId) } ?: track,
                castUrl = item.media?.contentId,
            )
        }
        val remoteQueue = remoteQueueEntries.map { it.track }
        val currentItemId = status?.currentItemId ?: client.currentItem?.itemId ?: MediaQueueItem.INVALID_ITEM_ID
        val remoteQueueIndex = currentItemId.takeIf { it != MediaQueueItem.INVALID_ITEM_ID }?.let { itemId ->
            queueItems.indexOfFirst { it.itemId == itemId }.takeIf { it >= 0 }
        }
        val currentQueueItem = remoteQueueIndex?.let { queueItems.getOrNull(it) }
        val expectedHandoff = pendingHandoff ?: expectedRemoteHandoff
        if (expectedHandoff != null) {
            if (!remotePlaybackMatches(expectedHandoff, remoteTrack, remoteCastUrl, currentQueueItem)) return
            if (expectedRemoteHandoff?.requestId == expectedHandoff.requestId) {
                expectedRemoteHandoff = null
            }
        }
        val currentQueueItemKnownIndex = currentQueueItem?.media?.let { media ->
            val track = media.toTrack()
            knownQueue.indexOfFirst { it.matchesCastMedia(track, media.contentId) }.takeIf { index -> index >= 0 }
        }
        val remoteMediaKnownIndex = remoteTrack?.let { track ->
            knownQueue.indexOfFirst { it.matchesCastMedia(track, remoteCastUrl) }.takeIf { index -> index >= 0 }
        }
        val knownQueueTrackIndex = currentQueueItemKnownIndex ?: remoteMediaKnownIndex
        val remoteQueueMatchesKnown = remoteQueueEntries.isNotEmpty() &&
            knownQueue.isNotEmpty() &&
            remoteQueueEntries.all { entry ->
                knownQueue.any { it.matchesCastMedia(entry.track, entry.castUrl) }
            }
        val preservingKnownQueue = knownQueueTrackIndex != null || remoteQueueMatchesKnown
        val queue = when {
            preservingKnownQueue -> knownQueue
            remoteQueue.isNotEmpty() -> remoteQueue
            remoteTrack != null -> listOf(remoteTrack)
            else -> knownQueue
        }
        val currentIndex = when {
            queue.isEmpty() -> previous.currentIndex
            preservingKnownQueue -> knownQueueTrackIndex
                ?: remoteTrack?.let { track ->
                    queue.indexOfFirst { it.matchesCastMedia(track, remoteCastUrl) }.takeIf { index -> index >= 0 }
                }
                ?: appQueueSnapshot?.currentIndex?.takeIf { it in queue.indices }
                ?: previous.currentIndex.takeIf { it in queue.indices }
                ?: 0
            remoteQueue.isNotEmpty() -> remoteQueueIndex
                ?: remoteTrack?.let { track ->
                    queue.indexOfFirst { it.matchesCastMedia(track, remoteCastUrl) }.takeIf { index -> index >= 0 }
                }
                ?: previous.currentIndex.takeIf { it in queue.indices }
                ?: 0
            else -> 0
        }
        if (queue.isNotEmpty() && currentIndex in queue.indices) {
            rememberAppQueue(queue, currentIndex)
        } else if (remoteQueue.isNotEmpty() || remoteTrack != null) {
            appQueueSnapshot = null
        }
        if (status?.playerState == MediaStatus.PLAYER_STATE_IDLE &&
            status.idleReason == MediaStatus.IDLE_REASON_FINISHED &&
            pendingHandoff == null &&
            currentIndex in queue.indices &&
            currentIndex < queue.lastIndex
        ) {
            loadQueue(queue, currentIndex + 1)
            return
        }
        val positionMs = client.approximateStreamPosition.coerceAtLeast(0L)
        val durationMs = client.streamDuration.coerceAtLeast(0L).takeIf { duration -> duration > 0L }
            ?: remoteTrack?.durationMs?.takeIf { it > 0L }
            ?: previous.durationMs
        if (previous.isPlaying != isPlaying ||
            previous.isBuffering != isBuffering ||
            previous.queue != queue ||
            previous.currentIndex != currentIndex ||
            previous.durationMs != durationMs ||
            kotlin.math.abs(previous.positionMs - positionMs) > 750L
        ) {
            mutableState.update {
                it.copy(
                    queue = queue,
                    currentIndex = currentIndex,
                    isPlaying = isPlaying,
                    isBuffering = isBuffering,
                    positionMs = positionMs,
                    durationMs = durationMs,
                )
            }
        }
        publishCastMediaSessionState()
        val localPlayer = audioPlayer
        if (client.isPlaying && localPlayer?.state?.value?.isPlaying == true) {
            suspendLocalPlayback()
        }
        if (AndroidPlaybackBridge.isServicePlaybackActive()) {
            suspendLocalPlayback()
        }
    }

    private fun publishCastMediaSessionState() {
        val state = mutableState.value
        val track = state.currentTrack ?: return
        AndroidPlaybackBridge.onCastMediaSessionState?.invoke(
            CastMediaSessionState(
                track = track,
                isPlaying = state.isPlaying,
                isBuffering = state.isBuffering,
                positionMs = state.positionMs,
                durationMs = state.durationMs.takeIf { it > 0L } ?: track.durationMs,
            ),
        )
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
        context.sessionManager.currentCastSession?.let { session ->
            connect(session, castLocalQueueIfReceiverEmpty = false)
        }
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
        if (expectedRemoteHandoff?.requestId == requestId) {
            expectedRemoteHandoff = null
        }
        appQueueSnapshot = null
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
        val support = canLoadQueue(current.queue)
        if (!support.isSupported) {
            mutableState.update { it.copy(message = support.message) }
            return
        }
        loadQueue(current.queue, index, current.positionMs)
    }

    private fun remotePlaybackMatches(
        handoff: PendingCastHandoff,
        remoteTrack: Track?,
        remoteCastUrl: String?,
        currentQueueItem: MediaQueueItem?,
    ): Boolean {
        val expectedTrack = handoff.queue.getOrNull(handoff.index) ?: return true
        val media = currentQueueItem?.media
        if (media != null && expectedTrack.matchesCastMedia(media.toTrack(), media.contentId)) return true
        return remoteTrack?.let { expectedTrack.matchesCastMedia(it, remoteCastUrl) } == true
    }

    private fun rememberAppQueue(queue: List<Track>, currentIndex: Int) {
        appQueueSnapshot = AppQueueSnapshot(
            queue = queue,
            currentIndex = currentIndex,
        )
    }

    private fun Track.toMediaInfo(): MediaInfo {
        val descriptor = toCastMediaDescriptor()
        val metadata = MediaMetadata(MediaMetadata.MEDIA_TYPE_MUSIC_TRACK).apply {
            putString(MediaMetadata.KEY_TITLE, descriptor.title)
            putString(MediaMetadata.KEY_ARTIST, descriptor.artist)
            putString(MediaMetadata.KEY_ALBUM_TITLE, descriptor.album)
            descriptor.thumbUrl?.let { addImage(com.google.android.gms.common.images.WebImage(android.net.Uri.parse(it))) }
        }
        PhoebeLog.d("AndroidCastController") {
            "loading cast media id=${descriptor.trackId} codec=${descriptor.audioCodec} contentType=${descriptor.contentType} transcode=${descriptor.transcodesOriginal}"
        }
        return MediaInfo.Builder(descriptor.castUrl)
            .setStreamType(MediaInfo.STREAM_TYPE_BUFFERED)
            .setContentType(descriptor.contentType)
            .setMetadata(metadata)
            .setStreamDuration(descriptor.durationMs)
            .setCustomData(descriptor.toCastCustomData())
            .build()
    }

    private fun Track.toMediaQueueItem(startPositionMs: Long = 0L): MediaQueueItem =
        MediaQueueItem.Builder(toMediaInfo())
            .setAutoplay(true)
            .apply {
                if (startPositionMs > 0L) {
                    setStartTime(startPositionMs / 1_000.0)
                }
            }
            .build()

    private fun buildCastLoadRequest(queue: List<Track>, startIndex: Int, startPositionMs: Long): CastLoadRequest {
        val tail = queue.drop(startIndex)
        var itemCount = tail.size.coerceAtMost(MaxCastReceiverQueueItems)
        var best: CastLoadRequest? = null
        while (itemCount >= 1) {
            val request = buildMediaLoadRequest(
                queue = tail.take(itemCount),
                startPositionMs = startPositionMs,
            )
            val bytes = request.estimatedByteSize()
            val castRequest = CastLoadRequest(
                requestData = request,
                receiverQueueSize = itemCount,
                estimatedBytes = bytes,
            )
            best = castRequest
            if (bytes <= MaxCastLoadMessageBytes || itemCount == 1) return castRequest
            itemCount = (itemCount / 2).coerceAtLeast(1)
        }
        return requireNotNull(best)
    }

    private fun buildMediaLoadRequest(queue: List<Track>, startPositionMs: Long): MediaLoadRequestData {
        val queueData = MediaQueueData.Builder()
            .setItems(queue.mapIndexed { index, track ->
                track.toMediaQueueItem(startPositionMs.takeIf { index == 0 } ?: 0L)
            })
            .setStartIndex(0)
            .setStartTime(startPositionMs)
            .setRepeatMode(MediaStatus.REPEAT_MODE_REPEAT_OFF)
            .build()
        return MediaLoadRequestData.Builder()
            .setQueueData(queueData)
            .setAutoplay(true)
            .setCurrentTime(startPositionMs)
            .build()
    }

    private fun MediaLoadRequestData.estimatedByteSize(): Int =
        toJson().toString().toByteArray(Charsets.UTF_8).size

    private fun CastMediaDescriptor.toCastCustomData(): JSONObject =
        JSONObject().apply {
            put(CastMediaCustomDataKeys.TrackId, trackId)
            put(CastMediaCustomDataKeys.Title, title)
            put(CastMediaCustomDataKeys.Artist, artist)
            put(CastMediaCustomDataKeys.Album, album)
            put(CastMediaCustomDataKeys.DurationMs, durationMs)
            put(CastMediaCustomDataKeys.StreamUrl, streamUrl)
            put(CastMediaCustomDataKeys.CastUrl, castUrl)
            put(CastMediaCustomDataKeys.DownloadUrl, downloadUrl)
            thumbUrl?.let { put(CastMediaCustomDataKeys.ThumbUrl, it) }
            filepath?.let { put(CastMediaCustomDataKeys.Filepath, it) }
            audioCodec?.let { put(CastMediaCustomDataKeys.AudioCodec, it) }
        }

    private fun MediaInfo.toTrack(): Track {
        val data = customData
        val remoteMetadata = metadata
        val title = data?.optString(CastMediaCustomDataKeys.Title).takeUnless { it.isNullOrBlank() }
            ?: remoteMetadata?.getString(MediaMetadata.KEY_TITLE).orEmpty()
        val artist = data?.optString(CastMediaCustomDataKeys.Artist).takeUnless { it.isNullOrBlank() }
            ?: remoteMetadata?.getString(MediaMetadata.KEY_ARTIST).orEmpty()
        val album = data?.optString(CastMediaCustomDataKeys.Album).takeUnless { it.isNullOrBlank() }
            ?: remoteMetadata?.getString(MediaMetadata.KEY_ALBUM_TITLE).orEmpty()
        val streamUrl = data?.optString(CastMediaCustomDataKeys.StreamUrl).takeUnless { it.isNullOrBlank() }
            ?: contentId.orEmpty()
        val thumbUrl = data?.optString(CastMediaCustomDataKeys.ThumbUrl).takeUnless { it.isNullOrBlank() }
            ?: remoteMetadata?.images?.firstOrNull()?.url?.toString()
        val durationMs = data?.optLong(CastMediaCustomDataKeys.DurationMs, 0L)?.takeIf { it > 0L }
            ?: streamDuration.takeIf { it > 0L }
            ?: 0L
        return castTrackFromMediaFields(
            trackId = data?.optString(CastMediaCustomDataKeys.TrackId),
            title = title,
            artist = artist,
            album = album,
            durationMs = durationMs,
            streamUrl = streamUrl,
            castUrl = data?.optString(CastMediaCustomDataKeys.CastUrl) ?: contentId,
            downloadUrl = data?.optString(CastMediaCustomDataKeys.DownloadUrl),
            thumbUrl = thumbUrl,
            filepath = data?.optString(CastMediaCustomDataKeys.Filepath),
            audioCodec = data?.optString(CastMediaCustomDataKeys.AudioCodec),
        )
    }

    private companion object {
        const val LOAD_TIMEOUT_MS = 30_000L
        const val MaxCastLoadMessageBytes = 450_000
        const val MaxCastReceiverQueueItems = 80
    }
}
