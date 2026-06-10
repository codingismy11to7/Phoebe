@file:OptIn(ExperimentalWasmJsInterop::class)

package com.phoebe.app

import com.phoebe.app.domain.Track
import com.phoebe.app.player.SimpleAudioPlayer
import com.phoebe.app.player.createCastController
import com.phoebe.app.player.webCastLoadRequest
import com.phoebe.app.player.webCastQueueSupport
import kotlinx.coroutines.delay
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class WebCastControllerTest {
    @Test
    fun webQueueSupportAcceptsRemoteProviderStreams() {
        val tracks = listOf(
            remoteTrack(id = "jellyfin:track:1", streamUrl = "https://jellyfin.example/Audio/1/stream"),
            remoteTrack(id = "navidrome:track:2", streamUrl = "http://navidrome.local/rest/stream.view?id=2"),
        )

        val support = webCastQueueSupport(tracks)

        assertTrue(support.isSupported)
    }

    @Test
    fun webQueueSupportRejectsLocalAndBrowserOnlyUrls() {
        val downloadedRemote = remoteTrack(id = "plex:1").copy(localUri = "phoebe-web-file://folder/one.mp3")
        val localOnly = Track(
            id = "local_folder:track:1",
            title = "Local",
            artist = "Artist",
            album = "Album",
            durationMs = 60_000,
            streamUrl = "",
            downloadUrl = "",
            localUri = "phoebe-web-file://folder/local.mp3",
        )
        val blobStream = remoteTrack(id = "jellyfin:2", streamUrl = "blob:https://music.example/stream")

        assertTrue(webCastQueueSupport(listOf(downloadedRemote)).isSupported)
        assertFalse(webCastQueueSupport(listOf(localOnly)).isSupported)
        assertFalse(webCastQueueSupport(listOf(blobStream)).isSupported)
    }

    @Test
    fun webLoadRequestBuildsQueueMetadataAndPlexTranscodeUrl() {
        val plexFlac = remoteTrack(
            id = "plex:123",
            streamUrl = "https://plex.example/library/parts/1?X-Plex-Token=token",
            filepath = "/music/album/one.flac",
            audioCodec = "FLAC",
        )
        val jellyfinMp3 = remoteTrack(
            id = "jellyfin:456",
            streamUrl = "https://jellyfin.example/Audio/456/stream?static=true&api_key=token",
            filepath = "/music/album/two.mp3",
            audioCodec = "MP3",
        )

        val request = webCastLoadRequest(listOf(plexFlac, jellyfinMp3), startIndex = 1)

        assertNotNull(request)
        assertEquals(1, request.startIndex)
        assertEquals(listOf("plex:123", "jellyfin:456"), request.items.map { it.trackId })
        assertTrue(request.items[0].url.contains("/music/:/transcode/universal/start.mp3"))
        assertEquals("audio/mpeg", request.items[0].contentType)
        assertEquals(jellyfinMp3.streamUrl, request.items[1].url)
        assertEquals("audio/mpeg", request.items[1].contentType)
    }

    @Test
    fun loadFailureClearsBufferingAndSurfacesMessage() = runTest {
        installFailingCastMock()
        val controller = createCastController(NoopAudioPlayer())
        delay(1)

        controller.loadQueue(
            listOf(remoteTrack(id = "jellyfin:track:1", streamUrl = "https://jellyfin.example/Audio/1/stream")),
            startIndex = 0,
        )
        delay(100)

        assertTrue(controller.state.value.isAvailable)
        assertFalse(controller.state.value.isConnected, "failed load should disconnect Cast state")
        assertTrue(controller.state.value.queue.isEmpty())
        assertFalse(controller.state.value.isBuffering, "failed load should clear Cast buffering: ${controller.state.value}")
        assertEquals("Receiver rejected media.", controller.state.value.message)
    }

    @Test
    fun emptyRejoinedSessionDoesNotCaptureLocalPlaybackControls() = runTest {
        installEmptyConnectedCastMock()
        val player = NoopAudioPlayer()
        val controller = createCastController(player)
        delay(1)

        assertTrue(controller.state.value.isAvailable)
        assertFalse(controller.state.value.isConnected)

        val track = remoteTrack(id = "jellyfin:track:1", streamUrl = "https://jellyfin.example/Audio/1/stream.mp3")
        player.play(listOf(track), startIndex = 0)
        delay(1)

        assertTrue(player.state.value.isPlaying)
        assertTrue(player.state.value.queue.isNotEmpty())
        assertFalse(controller.state.value.isConnected)
        player.togglePlayPause()
        assertFalse(player.state.value.isPlaying)
    }

    @Test
    fun devicePickerHandoffsCurrentLocalQueueToChromecast() = runTest {
        installSuccessfulPickerCastMock()
        val player = NoopAudioPlayer()
        val track = remoteTrack(id = "jellyfin:track:1", streamUrl = "https://jellyfin.example/Audio/1/stream.mp3")
        player.play(listOf(track), startIndex = 0)
        delay(1)

        val controller = createCastController(player)
        delay(1)
        controller.showDevicePicker()
        delay(250)

        assertTrue(controller.state.value.isConnected, "controller should connect after picker: ${controller.state.value}")
        assertTrue(controller.state.value.isPlaying, "controller should report receiver playback")
        assertFalse(player.state.value.isPlaying, "local player should pause after cast load succeeds")
        assertEquals(track.streamUrl, lastCastContentId(), "receiver should load the local player's current track")
    }

    @Test
    fun devicePickerStopsLocalOutputEvenWhenLocalStateLooksIdle() = runTest {
        installSuccessfulPickerCastMock()
        val player = StaleStateAudioPlayer()
        val track = remoteTrack(id = "jellyfin:track:1", streamUrl = "https://jellyfin.example/Audio/1/stream.mp3")
        player.play(listOf(track), startIndex = 0)
        player.reportIdleWithoutStoppingOutput()
        delay(1)

        val controller = createCastController(player)
        delay(1)
        controller.showDevicePicker()
        delay(250)

        assertTrue(controller.state.value.isConnected, "controller should connect after picker: ${controller.state.value}")
        assertTrue(controller.state.value.isPlaying, "controller should report receiver playback")
        assertFalse(player.audible, "local browser output should be stopped by the cast handoff")
        assertFalse(player.state.value.isPlaying, "local player state should remain paused after handoff")
        assertEquals(track.streamUrl, lastCastContentId(), "receiver should load the local player's current track")
    }

    @Test
    fun castEndedStatusLoadsNextPhoebeQueueItem() = runTest {
        installSuccessfulPickerCastMock()
        val player = NoopAudioPlayer()
        val first = remoteTrack(id = "jellyfin:track:1", streamUrl = "https://jellyfin.example/Audio/1/stream.mp3")
        val second = remoteTrack(id = "jellyfin:track:2", streamUrl = "https://jellyfin.example/Audio/2/stream.mp3")
        player.play(listOf(first, second), startIndex = 0)
        val controller = createCastController(player)
        delay(1)

        controller.showDevicePicker()
        delay(250)
        assertEquals(first.streamUrl, lastCastContentId())

        markCurrentCastMediaEnded()
        delay(250)

        assertTrue(controller.state.value.isConnected)
        assertEquals(1, controller.state.value.currentIndex)
        assertEquals(second.streamUrl, lastCastContentId())
    }

    @Test
    fun receiverQueueNextKeepsLocalOutputSuspended() = runTest {
        installSuccessfulPickerCastMock()
        val player = StaleStateAudioPlayer()
        val first = remoteTrack(id = "jellyfin:track:1", streamUrl = "https://jellyfin.example/Audio/1/stream.mp3")
        val second = remoteTrack(id = "jellyfin:track:2", streamUrl = "https://jellyfin.example/Audio/2/stream.mp3")
        player.play(listOf(first, second), startIndex = 0)
        val controller = createCastController(player)
        delay(1)

        controller.showDevicePicker()
        delay(250)
        player.leakOutputWithoutState()

        controller.next()
        delay(250)

        assertTrue(controller.state.value.isConnected)
        assertEquals(1, controller.state.value.currentIndex)
        assertEquals(second.streamUrl, currentCastContentId())
        assertFalse(player.audible, "local browser output should stay stopped after receiver queue next")
        assertEquals(second, player.state.value.currentTrack)
        assertFalse(player.state.value.isPlaying)
    }

    @Test
    fun externalStopCastingClearsStaleSessionBeforeNextLocalPlay() = runTest {
        installSuccessfulPickerCastMock()
        val player = NoopAudioPlayer()
        val first = remoteTrack(id = "jellyfin:track:1", streamUrl = "https://jellyfin.example/Audio/1/stream.mp3")
        val second = remoteTrack(id = "jellyfin:track:2", streamUrl = "https://jellyfin.example/Audio/2/stream.mp3")
        player.play(listOf(first, second), startIndex = 0)
        val controller = createCastController(player)
        delay(1)

        controller.showDevicePicker()
        delay(250)
        assertTrue(controller.state.value.isConnected)

        stopCastExternally(notify = false)
        assertTrue(controller.state.value.isConnected, "test should simulate stale state before the listener catches up")

        controller.togglePlayPause()
        delay(1)

        assertFalse(controller.state.value.isConnected)
        assertEquals(null, controller.state.value.message)
        player.play(listOf(first, second), startIndex = 1)
        assertTrue(player.state.value.isPlaying)
        assertEquals(second, player.state.value.currentTrack)
    }

    @Test
    fun disconnectIgnoresStaleSdkSession() = runTest {
        installStaleDisconnectCastMock()
        val player = NoopAudioPlayer()
        val track = remoteTrack(id = "jellyfin:track:1", streamUrl = "https://jellyfin.example/Audio/1/stream.mp3")
        player.play(listOf(track), startIndex = 0)
        val controller = createCastController(player)
        delay(1)

        controller.showDevicePicker()
        delay(250)
        assertTrue(controller.state.value.isConnected)

        controller.disconnect()
        notifyCastStatus()
        delay(1)

        assertFalse(controller.state.value.isConnected, "ended Cast sessions should not recapture playback controls")
        assertFalse(controller.state.value.isBuffering)
        assertTrue(player.state.value.queue.isNotEmpty(), "local player should keep a prepared queue after disconnect")
    }

    @Test
    fun castHandoffDuringCrossfadeLoadsOutgoingTrackAndSuspendsLocalPlayback() = runTest {
        installSuccessfulPickerCastMock()
        val player = CrossfadeCapableAudioPlayer()
        val first = remoteTrack(id = "jellyfin:track:1", streamUrl = "https://jellyfin.example/Audio/1/stream.mp3")
        val second = remoteTrack(id = "jellyfin:track:2", streamUrl = "https://jellyfin.example/Audio/2/stream.mp3")
        player.setCrossfadeDurationMs(6_000)
        player.play(listOf(first, second), startIndex = 0)
        player.platformPlayback(positionMs = 55_000, durationMs = 60_000, bufferedPositionMs = 60_000)
        assertEquals(1, player.crossfadeStarts)
        assertEquals(first, player.state.value.currentTrack)

        val controller = createCastController(player)
        delay(1)
        controller.showDevicePicker()
        delay(250)

        assertTrue(controller.state.value.isConnected)
        assertEquals(first.streamUrl, lastCastContentId())
        assertFalse(player.state.value.isPlaying)
        assertEquals(first, player.state.value.currentTrack)
        assertTrue(player.stopCalls >= 1)
    }

    @Test
    fun castHandoffAfterCrossfadeCommitLoadsIncomingTrack() = runTest {
        installSuccessfulPickerCastMock()
        val player = CrossfadeCapableAudioPlayer()
        val first = remoteTrack(id = "jellyfin:track:1", streamUrl = "https://jellyfin.example/Audio/1/stream.mp3")
        val second = remoteTrack(id = "jellyfin:track:2", streamUrl = "https://jellyfin.example/Audio/2/stream.mp3")
        player.setCrossfadeDurationMs(6_000)
        player.play(listOf(first, second), startIndex = 0)
        player.platformPlayback(positionMs = 55_000, durationMs = 60_000, bufferedPositionMs = 60_000)
        player.commitCrossfade(positionMs = 6_000)
        assertEquals(second, player.state.value.currentTrack)

        val controller = createCastController(player)
        delay(1)
        controller.showDevicePicker()
        delay(250)

        assertTrue(controller.state.value.isConnected)
        assertEquals(second.streamUrl, lastCastContentId())
        assertFalse(player.state.value.isPlaying)
    }

    @Test
    fun disconnectAfterCastRestoresLocalPlaybackWithCrossfadeStillEnabled() = runTest {
        installSuccessfulPickerCastMock()
        val player = CrossfadeCapableAudioPlayer()
        val first = remoteTrack(id = "jellyfin:track:1", streamUrl = "https://jellyfin.example/Audio/1/stream.mp3")
        val second = remoteTrack(id = "jellyfin:track:2", streamUrl = "https://jellyfin.example/Audio/2/stream.mp3")
        player.setCrossfadeDurationMs(6_000)
        player.play(listOf(first, second), startIndex = 0)
        val controller = createCastController(player)
        delay(1)
        controller.showDevicePicker()
        delay(250)
        controller.seekTo(12_000L)
        delay(50)

        controller.disconnect()
        delay(50)

        assertFalse(controller.state.value.isConnected)
        assertEquals(first, player.state.value.currentTrack)
        assertEquals(12_000L, player.state.value.positionMs)
        player.togglePlayPause()

        player.platformPlayback(positionMs = 55_000, durationMs = 60_000, bufferedPositionMs = 60_000)
        assertEquals(1, player.crossfadeStarts)
    }

    @Test
    fun receiverTrackEndDoesNotStartLocalCrossfade() = runTest {
        installSuccessfulPickerCastMock()
        val player = CrossfadeCapableAudioPlayer()
        val first = remoteTrack(id = "jellyfin:track:1", streamUrl = "https://jellyfin.example/Audio/1/stream.mp3")
        val second = remoteTrack(id = "jellyfin:track:2", streamUrl = "https://jellyfin.example/Audio/2/stream.mp3")
        player.setCrossfadeDurationMs(6_000)
        player.play(listOf(first, second), startIndex = 0)
        val controller = createCastController(player)
        delay(1)
        controller.showDevicePicker()
        delay(250)
        val crossfadeStartsBeforeEnd = player.crossfadeStarts

        markCurrentCastMediaEnded()
        delay(250)

        assertTrue(controller.state.value.isConnected)
        assertEquals(1, controller.state.value.currentIndex)
        assertEquals(second.streamUrl, lastCastContentId())
        assertEquals(crossfadeStartsBeforeEnd, player.crossfadeStarts)
        assertFalse(player.state.value.isPlaying)
    }

    private fun remoteTrack(
        id: String,
        streamUrl: String = "https://stream.example/song.mp3",
        filepath: String? = "/music/song.mp3",
        audioCodec: String? = "MP3",
    ): Track =
        Track(
            id = id,
            title = "Song",
            artist = "Artist",
            album = "Album",
            durationMs = 60_000,
            streamUrl = streamUrl,
            downloadUrl = "",
            thumbUrl = "https://images.example/art.jpg",
            filepath = filepath,
            audioCodec = audioCodec,
        )

    private class NoopAudioPlayer : SimpleAudioPlayer() {
        override fun playUri(uri: String) {
            markPlaybackReady()
        }
    }

    private class StaleStateAudioPlayer : SimpleAudioPlayer() {
        var audible = false
            private set

        override fun playUri(uri: String) {
            audible = playWhenReady
            markPlaybackReady()
        }

        fun reportIdleWithoutStoppingOutput() {
            applyPlatformPlayback(
                positionMs = state.value.positionMs,
                durationMs = state.value.durationMs,
                isPlaying = false,
                isBuffering = false,
            )
        }

        fun leakOutputWithoutState() {
            audible = true
        }

        override fun stopCurrentPlaybackImmediately() {
            audible = false
        }

        override fun pause() {
            audible = false
        }
    }

    private class CrossfadeCapableAudioPlayer : SimpleAudioPlayer() {
        var crossfadeStarts = 0
        var stopCalls = 0
        private var pendingQueue: List<Track> = emptyList()
        private var pendingTargetIndex = -1
        private var pendingGeneration = -1

        override fun playUri(uri: String) {
            markPlaybackReady()
        }

        override fun stopCurrentPlaybackImmediately() {
            stopCalls++
        }

        override fun startCrossfadeOnPlatform(
            queue: List<Track>,
            targetIndex: Int,
            track: Track,
            durationMs: Long,
            baseVolume: Float,
            generation: Int,
        ): Boolean {
            crossfadeStarts++
            pendingQueue = queue
            pendingTargetIndex = targetIndex
            pendingGeneration = generation
            return true
        }

        fun platformPlayback(
            positionMs: Long,
            durationMs: Long,
            bufferedPositionMs: Long,
            isPlaying: Boolean = true,
        ) {
            applyPlatformPlayback(
                positionMs = positionMs,
                durationMs = durationMs,
                isPlaying = isPlaying,
                isBuffering = false,
                bufferedPositionMs = bufferedPositionMs,
            )
        }

        fun commitCrossfade(positionMs: Long) {
            adoptCrossfadeTarget(pendingQueue, pendingTargetIndex, positionMs, pendingGeneration)
        }
    }
}

@JsFun(
    """
    () => globalThis.__phoebeLastCastLoadRequest?.media?.contentId || null
    """,
)
private external fun lastCastContentId(): String?

@JsFun(
    """
    () => {
        const media = globalThis.__phoebeCastGetMedia?.();
        const currentItem = Array.isArray(media?.items)
            ? media.items.find((item) => item?.itemId === media.currentItemId)
            : null;
        return currentItem?.media?.contentId || media?.media?.contentId || null;
    }
    """,
)
private external fun currentCastContentId(): String?

@JsFun(
    """
    () => globalThis.__phoebeCastNotifyStatus?.()
    """,
)
private external fun notifyCastStatus()

@JsFun(
    """
    () => globalThis.__phoebeMarkCastMediaEnded?.()
    """,
)
private external fun markCurrentCastMediaEnded()

@JsFun(
    """
    (notify) => globalThis.__phoebeStopCastExternally?.(!!notify)
    """,
)
private external fun stopCastExternally(notify: Boolean)

@JsFun(
    """
    () => {
        delete globalThis.__phoebeCastState;
        delete globalThis.__phoebeCastReadStatus;
        delete globalThis.__phoebeCastNotifyStatus;
        delete globalThis.__phoebeCastErrorMessage;
        delete globalThis.__phoebeCastGetState;
        delete globalThis.__phoebeCastGetContext;
        delete globalThis.__phoebeCastGetSession;
        delete globalThis.__phoebeCastGetMedia;
        delete globalThis.__phoebeLastCastLoadRequest;
        delete globalThis.__phoebeMarkCastMediaEnded;
        delete globalThis.__phoebeStopCastExternally;
        let currentSession = null;
        let mediaSession = null;
        const mediaApi = {
            DEFAULT_MEDIA_RECEIVER_APP_ID: "CC1AD845",
            PlayerState: { PLAYING: "PLAYING", BUFFERING: "BUFFERING", PAUSED: "PAUSED", IDLE: "IDLE" },
            IdleReason: { FINISHED: "FINISHED" },
            StreamType: { BUFFERED: "BUFFERED" },
            RepeatMode: { REPEAT_OFF: "REPEAT_OFF" },
            MediaInfo: function(contentId, contentType) {
                this.contentId = contentId;
                this.contentType = contentType;
            },
            MusicTrackMediaMetadata: function() {},
            LoadRequest: function(media) {
                this.media = media;
            },
            QueueItem: function(media) {
                this.media = media;
            },
            QueueData: function(_id, _name, _containerMetadata, _repeatMode, items, startIndex, startTime) {
                this.items = Array.isArray(items) ? items : [];
                this.startIndex = Number(startIndex || 0);
                this.startTime = Number(startTime || 0);
            },
            SeekRequest: function() {}
        };
        const session = {
            getCastDevice: () => ({ friendlyName: "Office TV" }),
            getMediaSession: () => mediaSession,
            loadMedia: (request) => {
                globalThis.__phoebeLastCastLoadRequest = request;
                const queueItems = Array.isArray(request.queueData?.items) && request.queueData.items.length
                    ? request.queueData.items
                    : [{ media: request.media }];
                queueItems.forEach((item, index) => { item.itemId = index + 1; });
                const startIndex = Math.min(
                    Math.max(Number(request.queueData?.startIndex ?? 0), 0),
                    Math.max(queueItems.length - 1, 0)
                );
                const currentItem = queueItems[startIndex] || queueItems[0];
                mediaSession = {
                    playerState: mediaApi.PlayerState.PLAYING,
                    media: currentItem.media,
                    items: queueItems,
                    currentItemId: currentItem.itemId,
                    currentTime: Number(request.currentTime || 0),
                    duration: Number(currentItem.media?.duration || 0),
                    getEstimatedTime: function() { return this.currentTime; },
                    addUpdateListener: () => {},
                    removeUpdateListener: () => {},
                    play: (_request, success) => success?.(),
                    pause: (_request, success) => {
                        mediaSession.playerState = mediaApi.PlayerState.PAUSED;
                        success?.();
                    },
                    queueNext: (success, failure) => {
                        const currentIndex = mediaSession.items.findIndex((item) => item.itemId === mediaSession.currentItemId);
                        if (currentIndex < 0 || currentIndex >= mediaSession.items.length - 1) {
                            failure?.({ message: "No next queue item." });
                            return;
                        }
                        const nextItem = mediaSession.items[currentIndex + 1];
                        mediaSession.currentItemId = nextItem.itemId;
                        mediaSession.media = nextItem.media;
                        mediaSession.currentTime = 0;
                        mediaSession.duration = Number(nextItem.media?.duration || 0);
                        mediaSession.playerState = mediaApi.PlayerState.PLAYING;
                        success?.();
                    },
                    queuePrev: (success, failure) => {
                        const currentIndex = mediaSession.items.findIndex((item) => item.itemId === mediaSession.currentItemId);
                        if (currentIndex <= 0) {
                            failure?.({ message: "No previous queue item." });
                            return;
                        }
                        const previousItem = mediaSession.items[currentIndex - 1];
                        mediaSession.currentItemId = previousItem.itemId;
                        mediaSession.media = previousItem.media;
                        mediaSession.currentTime = 0;
                        mediaSession.duration = Number(previousItem.media?.duration || 0);
                        mediaSession.playerState = mediaApi.PlayerState.PLAYING;
                        success?.();
                    }
                };
                return mediaSession;
            },
            endSession: () => { currentSession = null; }
        };
        globalThis.__phoebeMarkCastMediaEnded = () => {
            if (!mediaSession) return;
            mediaSession.playerState = mediaApi.PlayerState.IDLE;
            mediaSession.idleReason = mediaApi.IdleReason.FINISHED;
            globalThis.__phoebeCastNotifyStatus?.();
        };
        globalThis.__phoebeStopCastExternally = (notify) => {
            currentSession = null;
            mediaSession = null;
            if (notify) globalThis.__phoebeCastNotifyStatus?.();
        };
        const context = {
            getCastState: () => currentSession ? "CONNECTED" : "NOT_CONNECTED",
            getCurrentSession: () => currentSession,
            requestSession: () => {
                currentSession = session;
                return session;
            },
            addEventListener: () => {}
        };
        globalThis.chrome = {
            cast: {
                AutoJoinPolicy: { ORIGIN_SCOPED: "ORIGIN_SCOPED" },
                media: mediaApi
            }
        };
        globalThis.cast = {
            framework: {
                CastState: { NO_DEVICES_AVAILABLE: "NO_DEVICES_AVAILABLE" },
                CastContextEventType: {
                    CAST_STATE_CHANGED: "CAST_STATE_CHANGED",
                    SESSION_STATE_CHANGED: "SESSION_STATE_CHANGED"
                },
                CastContext: {
                    getInstance: () => context
                }
            }
        };
    }
    """,
)
private external fun installSuccessfulPickerCastMock()

@JsFun(
    """
    () => {
        delete globalThis.__phoebeCastState;
        delete globalThis.__phoebeCastReadStatus;
        delete globalThis.__phoebeCastNotifyStatus;
        delete globalThis.__phoebeCastErrorMessage;
        delete globalThis.__phoebeCastGetState;
        delete globalThis.__phoebeCastGetContext;
        delete globalThis.__phoebeCastGetSession;
        delete globalThis.__phoebeCastGetMedia;
        delete globalThis.__phoebeLastCastLoadRequest;
        delete globalThis.__phoebeMarkCastMediaEnded;
        let mediaSession = null;
        const mediaApi = {
            DEFAULT_MEDIA_RECEIVER_APP_ID: "CC1AD845",
            PlayerState: { PLAYING: "PLAYING", BUFFERING: "BUFFERING", PAUSED: "PAUSED", IDLE: "IDLE" },
            IdleReason: { FINISHED: "FINISHED" },
            StreamType: { BUFFERED: "BUFFERED" },
            RepeatMode: { REPEAT_OFF: "REPEAT_OFF" },
            MediaInfo: function(contentId, contentType) {
                this.contentId = contentId;
                this.contentType = contentType;
            },
            MusicTrackMediaMetadata: function() {},
            LoadRequest: function(media) {
                this.media = media;
            },
            QueueItem: function(media) {
                this.media = media;
            },
            QueueData: function() {},
            SeekRequest: function() {}
        };
        const session = {
            getCastDevice: () => ({ friendlyName: "Office TV" }),
            getMediaSession: () => mediaSession,
            loadMedia: (request) => {
                globalThis.__phoebeLastCastLoadRequest = request;
                mediaSession = {
                    playerState: mediaApi.PlayerState.PLAYING,
                    media: request.media,
                    currentTime: Number(request.currentTime || 0),
                    duration: Number(request.media?.duration || 0),
                    getEstimatedTime: function() { return this.currentTime; },
                    addUpdateListener: () => {},
                    removeUpdateListener: () => {},
                    play: (_request, success) => success?.(),
                    pause: (_request, success) => {
                        mediaSession.playerState = mediaApi.PlayerState.PAUSED;
                        success?.();
                    }
                };
                return mediaSession;
            },
            endSession: () => {}
        };
        const context = {
            getCastState: () => "CONNECTED",
            getCurrentSession: () => session,
            requestSession: () => session,
            addEventListener: () => {}
        };
        globalThis.chrome = {
            cast: {
                AutoJoinPolicy: { ORIGIN_SCOPED: "ORIGIN_SCOPED" },
                media: mediaApi
            }
        };
        globalThis.cast = {
            framework: {
                CastState: { NO_DEVICES_AVAILABLE: "NO_DEVICES_AVAILABLE" },
                CastContextEventType: {
                    CAST_STATE_CHANGED: "CAST_STATE_CHANGED",
                    SESSION_STATE_CHANGED: "SESSION_STATE_CHANGED"
                },
                CastContext: {
                    getInstance: () => context
                }
            }
        };
    }
    """,
)
private external fun installStaleDisconnectCastMock()

@JsFun(
    """
    () => {
        delete globalThis.__phoebeCastState;
        delete globalThis.__phoebeCastReadStatus;
        delete globalThis.__phoebeCastNotifyStatus;
        delete globalThis.__phoebeCastErrorMessage;
        delete globalThis.__phoebeCastGetState;
        delete globalThis.__phoebeCastGetContext;
        delete globalThis.__phoebeCastGetSession;
        delete globalThis.__phoebeCastGetMedia;
        delete globalThis.__phoebeMarkCastMediaEnded;
        const mediaApi = {
            DEFAULT_MEDIA_RECEIVER_APP_ID: "CC1AD845",
            PlayerState: { PLAYING: "PLAYING", BUFFERING: "BUFFERING", PAUSED: "PAUSED", IDLE: "IDLE" },
            IdleReason: { FINISHED: "FINISHED" },
            StreamType: { BUFFERED: "BUFFERED" },
            RepeatMode: { REPEAT_OFF: "REPEAT_OFF" },
            MediaInfo: function(contentId, contentType) {
                this.contentId = contentId;
                this.contentType = contentType;
            },
            MusicTrackMediaMetadata: function() {},
            LoadRequest: function(media) {
                this.media = media;
            },
            QueueItem: function(media) {
                this.media = media;
            },
            QueueData: function() {},
            SeekRequest: function() {}
        };
        const session = {
            getCastDevice: () => ({ friendlyName: "Office TV" }),
            getMediaSession: () => null,
            loadMedia: () => { throw { message: "Receiver rejected media." }; },
            endSession: () => {}
        };
        const context = {
            getCastState: () => "CONNECTED",
            getCurrentSession: () => session,
            requestSession: () => session,
            addEventListener: () => {}
        };
        globalThis.chrome = {
            cast: {
                AutoJoinPolicy: { ORIGIN_SCOPED: "ORIGIN_SCOPED" },
                media: mediaApi
            }
        };
        globalThis.cast = {
            framework: {
                CastState: { NO_DEVICES_AVAILABLE: "NO_DEVICES_AVAILABLE" },
                CastContextEventType: {
                    CAST_STATE_CHANGED: "CAST_STATE_CHANGED",
                    SESSION_STATE_CHANGED: "SESSION_STATE_CHANGED"
                },
                CastContext: {
                    getInstance: () => context
                }
            }
        };
    }
    """,
)
private external fun installEmptyConnectedCastMock()

@JsFun(
    """
    () => {
        delete globalThis.__phoebeCastState;
        delete globalThis.__phoebeCastReadStatus;
        delete globalThis.__phoebeCastNotifyStatus;
        delete globalThis.__phoebeCastErrorMessage;
        delete globalThis.__phoebeCastGetState;
        delete globalThis.__phoebeCastGetContext;
        delete globalThis.__phoebeCastGetSession;
        delete globalThis.__phoebeCastGetMedia;
        delete globalThis.__phoebeMarkCastMediaEnded;
        let mediaSession = null;
        const mediaApi = {
            DEFAULT_MEDIA_RECEIVER_APP_ID: "CC1AD845",
            PlayerState: { PLAYING: "PLAYING", BUFFERING: "BUFFERING", PAUSED: "PAUSED", IDLE: "IDLE" },
            IdleReason: { FINISHED: "FINISHED" },
            StreamType: { BUFFERED: "BUFFERED" },
            RepeatMode: { REPEAT_OFF: "REPEAT_OFF" },
            MediaInfo: function(contentId, contentType) {
                this.contentId = contentId;
                this.contentType = contentType;
            },
            MusicTrackMediaMetadata: function() {},
            LoadRequest: function(media) {
                this.media = media;
            },
            QueueItem: function(media) {
                this.media = media;
            },
            QueueData: function() {},
            SeekRequest: function() {}
        };
        const session = {
            getCastDevice: () => ({ friendlyName: "Office TV" }),
            getMediaSession: () => mediaSession,
            loadMedia: (request) => {
                globalThis.__phoebeLastCastLoadRequest = request;
                throw { message: "Receiver rejected media." };
            },
            endSession: () => {}
        };
        const context = {
            getCastState: () => "CONNECTED",
            getCurrentSession: () => session,
            requestSession: () => session,
            addEventListener: () => {}
        };
        globalThis.chrome = {
            cast: {
                AutoJoinPolicy: { ORIGIN_SCOPED: "ORIGIN_SCOPED" },
                media: mediaApi
            }
        };
        globalThis.cast = {
            framework: {
                CastState: { NO_DEVICES_AVAILABLE: "NO_DEVICES_AVAILABLE" },
                CastContextEventType: {
                    CAST_STATE_CHANGED: "CAST_STATE_CHANGED",
                    SESSION_STATE_CHANGED: "SESSION_STATE_CHANGED"
                },
                CastContext: {
                    getInstance: () => context
                }
            }
        };
    }
    """,
)
private external fun installFailingCastMock()
