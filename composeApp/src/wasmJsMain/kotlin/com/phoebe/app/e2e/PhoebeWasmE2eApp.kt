@file:OptIn(ExperimentalWasmJsInterop::class)

package com.phoebe.app.e2e

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.phoebe.app.domain.LocalFolderMediaSourceConfig
import com.phoebe.app.domain.Track
import com.phoebe.app.player.SimpleAudioPlayer
import com.phoebe.app.player.createCastController
import com.phoebe.app.sources.LocalFolderCatalogBuilder
import com.phoebe.app.playlists.PlaylistExportFormat
import com.phoebe.app.playlists.PlaylistExporter
import com.phoebe.app.sources.LocalLibraryIO
import kotlinx.coroutines.delay

@Composable
fun PhoebeWasmE2eApp(e2eMode: String? = null) {
    var status by remember { mutableStateOf("running") }
    var details by remember { mutableStateOf("") }

    LaunchedEffect(e2eMode) {
        val results = when (e2eMode) {
            "castMock" -> runWasmCastMockE2eChecks()
            "localPlaylist" -> runWasmLocalPlaylistE2eChecks()
            else -> runWasmE2eChecks()
        }
        status = if (results.passed) "passed" else "failed"
        details = results.message
        publishWasmE2eResults(results.passed, results.message)
    }

    MaterialTheme {
        Column(Modifier.fillMaxSize().padding(16.dp)) {
            Text("Phoebe wasm E2E: $status")
            Text(details)
        }
    }
}

private data class WasmE2eResult(val passed: Boolean, val message: String)

private suspend fun runWasmE2eChecks(): WasmE2eResult {
    val snapshot = LocalFolderCatalogBuilder.build(
        LocalFolderMediaSourceConfig(
            id = "wasm-e2e",
            rootUri = "phoebe-test://music?files=alpha.mp3|nested/beta.mp3",
            label = "Wasm E2E",
            enabled = true,
        ),
    )
    val tracks = snapshot.tracksByParent.values.flatten()
    if (tracks.map { it.title }.sorted() != listOf("alpha", "beta")) {
        return WasmE2eResult(false, "expected alpha and beta tracks, got ${tracks.map { it.title }}")
    }

    val uri = tracks.first { it.title == "alpha" }.localUri
    if (uri == null || !LocalLibraryIO.fileExists(uri)) {
        return WasmE2eResult(false, "local file missing for alpha: $uri")
    }

    val player = WasmRecordingAudioPlayer()
    player.play(listOf(tracks.first { it.title == "alpha" }), 0)
    if (player.lastUri != uri || !player.state.value.isPlaying) {
        return WasmE2eResult(false, "playback did not start for $uri")
    }

    return WasmE2eResult(true, "local library indexed ${tracks.size} tracks and playback started")
}

private suspend fun runWasmLocalPlaylistE2eChecks(): WasmE2eResult {
    val snapshot = LocalFolderCatalogBuilder.build(
        LocalFolderMediaSourceConfig(
            id = "wasm-playlist-e2e",
            rootUri = "phoebe-test://music?files=alpha.mp3|beta.mp3",
            label = "Wasm Playlist E2E",
            enabled = true,
        ),
    )
    val tracks = snapshot.tracksByParent.values.flatten().sortedBy { it.title }
    if (tracks.map { it.title } != listOf("alpha", "beta")) {
        return WasmE2eResult(false, "expected alpha and beta tracks, got ${tracks.map { it.title }}")
    }
    if (tracks.any { it.localUri.isNullOrBlank() }) {
        return WasmE2eResult(false, "expected local URIs for wasm playlist tracks")
    }

    val m3u8 = PlaylistExporter.export(tracks, PlaylistExportFormat.M3U8)
    if (!m3u8.startsWith("#EXTM3U") || !m3u8.contains("alpha.mp3")) {
        return WasmE2eResult(false, "m3u8 export missing header or alpha path")
    }
    val text = PlaylistExporter.export(tracks, PlaylistExportFormat.Text)
    if (text.lines().size != 2) {
        return WasmE2eResult(false, "text export expected 2 lines, got ${text.lines().size}")
    }
    val csv = PlaylistExporter.export(tracks, PlaylistExportFormat.Csv)
    if (!csv.startsWith("title,artist,album,duration_ms,path")) {
        return WasmE2eResult(false, "csv export missing header row")
    }

    return WasmE2eResult(true, "local playlist export verified m3u8, text, and csv for ${tracks.size} mp3 tracks")
}

private suspend fun runWasmCastMockE2eChecks(): WasmE2eResult {
    installWasmCastE2eMock()
    val controller = createCastController(WasmRecordingAudioPlayer())
    delay(10)
    val track = Track(
        id = "jellyfin:cast-e2e",
        title = "Cast E2E",
        artist = "Phoebe",
        album = "Browser",
        durationMs = 60_000,
        streamUrl = "https://jellyfin.example/Audio/cast-e2e/stream",
        downloadUrl = "",
        thumbUrl = "https://images.example/cast-e2e.jpg",
        filepath = "/music/cast-e2e.mp3",
        audioCodec = "MP3",
    )
    if (!controller.state.value.isAvailable) {
        return WasmE2eResult(false, "mock Chromecast was not available")
    }
    val support = controller.canLoadQueue(listOf(track))
    if (!support.isSupported) {
        return WasmE2eResult(false, support.message ?: "remote track was not castable")
    }
    controller.showDevicePicker()
    delay(50)
    controller.loadQueue(listOf(track), 0)
    repeat(20) {
        val state = controller.state.value
        if (state.isPlaying && !state.isBuffering && state.currentTrack?.id == track.id) return@repeat
        delay(10)
    }
    val loadedUrl = wasmCastE2eLoadedContentId()
    val state = controller.state.value
    if (!state.isPlaying || state.currentTrack?.id != track.id || loadedUrl != track.streamUrl) {
        return WasmE2eResult(
            false,
            "cast state mismatch playing=${state.isPlaying} track=${state.currentTrack?.id} loaded=$loadedUrl",
        )
    }
    return WasmE2eResult(true, "mock Chromecast connected and loaded ${track.title}")
}

private class WasmRecordingAudioPlayer : SimpleAudioPlayer() {
    var lastUri: String? = null

    override fun playUri(uri: String) {
        lastUri = uri
        markPlaybackReady()
    }
}

@OptIn(ExperimentalWasmJsInterop::class)
@JsFun(
    """(passed, message) => {
        globalThis.phoebeE2eResults = { passed: !!passed, message: String(message ?? "") };
    }""",
)
private external fun publishWasmE2eResults(passed: Boolean, message: String)

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
        let connected = false;
        let mediaSession = null;
        let nextItemId = 1;
        const mediaApi = {
            DEFAULT_MEDIA_RECEIVER_APP_ID: "CC1AD845",
            PlayerState: { PLAYING: "PLAYING", BUFFERING: "BUFFERING", PAUSED: "PAUSED" },
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
                this.itemId = nextItemId++;
            },
            QueueData: function() {},
            SeekRequest: function() {}
        };
        const session = {
            getCastDevice: () => ({ friendlyName: "Mock TV" }),
            getMediaSession: () => mediaSession,
            loadMedia: (request) => {
                const items = request.queueData?.items || [{ media: request.media, itemId: nextItemId++ }];
                const index = Math.max(0, Math.min(Number(request.queueData?.startIndex || 0), items.length - 1));
                mediaSession = {
                    playerState: mediaApi.PlayerState.PLAYING,
                    media: items[index].media,
                    items,
                    currentItemId: items[index].itemId,
                    currentTime: Number(request.currentTime || 0),
                    duration: Number(items[index].media?.duration || 0),
                    getEstimatedTime: () => mediaSession.currentTime,
                    addUpdateListener: () => {},
                    removeUpdateListener: () => {},
                    play: (_request, success) => {
                        mediaSession.playerState = mediaApi.PlayerState.PLAYING;
                        success?.();
                    },
                    pause: (_request, success) => {
                        mediaSession.playerState = mediaApi.PlayerState.PAUSED;
                        success?.();
                    },
                    seek: (seekRequest, success) => {
                        mediaSession.currentTime = Number(seekRequest.currentTime || 0);
                        success?.();
                    },
                    queueNext: (success) => success?.(),
                    queuePrev: (success) => success?.()
                };
                globalThis.__phoebeCastE2eLoadedContentId = items[index].media?.contentId || "";
                return Promise.resolve();
            },
            endSession: () => {
                connected = false;
                mediaSession = null;
            }
        };
        const context = {
            getCastState: () => "CONNECTED",
            getCurrentSession: () => connected ? session : null,
            requestSession: () => {
                connected = true;
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
private external fun installWasmCastE2eMock()

@JsFun("() => String(globalThis.__phoebeCastE2eLoadedContentId || '')")
private external fun wasmCastE2eLoadedContentId(): String
