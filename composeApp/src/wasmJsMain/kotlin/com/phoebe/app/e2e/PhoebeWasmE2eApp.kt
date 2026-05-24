@file:OptIn(ExperimentalWasmJsInterop::class)

package com.phoebe.app.e2e

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.phoebe.app.domain.LocalFolderMediaSourceConfig
import com.phoebe.app.domain.Track
import com.phoebe.app.player.AudioPlayer
import com.phoebe.app.player.PlaybackDiagnostics
import com.phoebe.app.player.PlaybackEnginePath
import com.phoebe.app.player.SimpleAudioPlayer
import com.phoebe.app.player.createCastController
import com.phoebe.app.player.createWebAudioPlayerForTests
import com.phoebe.app.sources.LocalFolderCatalogBuilder
import com.phoebe.app.playlists.PlaylistExportFormat
import com.phoebe.app.playlists.PlaylistExporter
import com.phoebe.app.sources.LocalLibraryIO
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.time.TimeSource

@Composable
fun PhoebeWasmE2eApp(e2eMode: String? = null) {
    if (e2eMode == "localPlaybackRegression") {
        PhoebeWasmLocalPlaybackRegressionApp()
        return
    }

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

@Composable
private fun PhoebeWasmLocalPlaybackRegressionApp() {
    var status by remember { mutableStateOf("preparing") }
    var details by remember { mutableStateOf("") }
    var preparedTrack by remember { mutableStateOf<Track?>(null) }
    val scope = rememberCoroutineScope()
    val diagnostics = remember { WasmPlaybackStartupProbe() }
    val player = remember { createWebAudioPlayerForTests(diagnostics) }

    DisposableEffect(Unit) {
        onDispose {
            removeWasmPlaybackRegressionButton()
            player.stopPlayback()
        }
    }

    LaunchedEffect(Unit) {
        val rootUri = seedPlayableWavBrowserLocalFolder()
        val snapshot = LocalFolderCatalogBuilder.build(
            LocalFolderMediaSourceConfig(
                id = "web-playback-regression",
                rootUri = rootUri,
                label = "Web Playback Regression",
                enabled = true,
            ),
        )
        val track = snapshot.tracksByParent.values.flatten().singleOrNull()
        if (track == null || track.localUri.isNullOrBlank() || !LocalLibraryIO.fileExists(track.localUri.orEmpty())) {
            val message = "web playback regression fixture was not indexed"
            status = "failed"
            details = message
            publishWasmE2eResults(false, message)
            return@LaunchedEffect
        }

        preparedTrack = track
        status = "ready"
        details = "Ready to play ${track.title}"
        installWasmPlaybackRegressionButton("Play ${track.title}") {
            val selected = preparedTrack
            if (selected == null) {
                publishWasmE2eResults(false, "playback regression clicked before fixture was ready")
                return@installWasmPlaybackRegressionButton
            }
            diagnostics.markPlayRequested()
            player.play(listOf(selected), 0)
            scope.launch {
                val result = awaitWasmPlaybackRegressionResult(player, diagnostics, selected)
                status = if (result.passed) "passed" else "failed"
                details = result.message
                publishWasmE2eResults(result.passed, result.message)
            }
        }
        publishWasmE2eReady(true, "web local playback regression ready")
    }

    MaterialTheme {
        Column(Modifier.fillMaxSize().padding(16.dp)) {
            Text("Phoebe wasm playback regression: $status")
            Text(details)
        }
    }
}

private data class WasmE2eResult(val passed: Boolean, val message: String)

private class WasmPlaybackStartupProbe : PlaybackDiagnostics {
    private val timeSource = TimeSource.Monotonic
    private var startedAt = timeSource.markNow()
    var firstPlatformPlayingMs: Long? = null
        private set
    var firstDecodedEnergyMs: Long? = null
        private set
    var errors: List<String> = emptyList()
        private set
    var engines: List<PlaybackEnginePath> = emptyList()
        private set

    val firstAudioMs: Long?
        get() = firstDecodedEnergyMs ?: firstPlatformPlayingMs

    fun markPlayRequested() {
        startedAt = timeSource.markNow()
        firstPlatformPlayingMs = null
        firstDecodedEnergyMs = null
        errors = emptyList()
        engines = emptyList()
    }

    override fun engineSelected(engine: PlaybackEnginePath) {
        if (engine !in engines) engines = engines + engine
    }

    override fun platformPlaying(engine: PlaybackEnginePath, positionMs: Long, durationMs: Long) {
        engineSelected(engine)
        if (firstPlatformPlayingMs == null) firstPlatformPlayingMs = elapsedMs()
    }

    override fun decodedAudioEnergy(engine: PlaybackEnginePath, rms: Double) {
        if (rms <= 0.000001 || !rms.isFinite()) return
        engineSelected(engine)
        if (firstDecodedEnergyMs == null) firstDecodedEnergyMs = elapsedMs()
    }

    override fun playbackError(engine: PlaybackEnginePath, message: String?) {
        engineSelected(engine)
        errors = errors + "${engine.name}: ${message ?: "unknown playback error"}"
    }

    private fun elapsedMs(): Long = startedAt.elapsedNow().inWholeMilliseconds.coerceAtLeast(0L)
}

private suspend fun awaitWasmPlaybackRegressionResult(
    player: AudioPlayer,
    diagnostics: WasmPlaybackStartupProbe,
    track: Track,
): WasmE2eResult {
    val started = TimeSource.Monotonic.markNow()
    while (started.elapsedNow().inWholeMilliseconds <= WebPlaybackStartupThresholdMs) {
        val firstAudioMs = diagnostics.firstAudioMs
        val state = player.state.value
        if (firstAudioMs != null &&
            state.currentTrack?.id == track.id &&
            state.isPlaying &&
            !state.isBuffering
        ) {
            return WasmE2eResult(
                true,
                "web local playback started ${track.title} in ${firstAudioMs}ms via ${diagnostics.engines.joinToString()}",
            )
        }
        if (diagnostics.errors.isNotEmpty()) {
            return WasmE2eResult(false, "web local playback failed: ${diagnostics.errors.joinToString()}")
        }
        delay(50)
    }
    return WasmE2eResult(
        false,
        "web local playback did not start within ${WebPlaybackStartupThresholdMs}ms; engines=${diagnostics.engines.joinToString()} state=${player.state.value}",
    )
}

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

private const val WebPlaybackStartupThresholdMs = 5_000L

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

@OptIn(ExperimentalWasmJsInterop::class)
@JsFun(
    """(ready, message) => {
        globalThis.phoebeE2eReady = !!ready;
        globalThis.phoebeE2eReadyMessage = String(message ?? "");
    }""",
)
private external fun publishWasmE2eReady(ready: Boolean, message: String)

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

@JsFun(
    """
    () => {
        const store = globalThis.__phoebeLocalFileStore ||
            (globalThis.__phoebeLocalFileStore = { folders: new Map(), files: new Map() });
        const id = "web-playback-regression-folder";
        const folderLabel = "Playback Regression";
        const rootUri = "phoebe-web-folder://" + id + "/" + encodeURIComponent(folderLabel);
        for (const key of Array.from(store.files.keys())) {
            if (String(key).startsWith("phoebe-web-file://" + id + "/")) {
                store.files.delete(key);
            }
        }
        const sampleRate = 44100;
        const seconds = 0.65;
        const sampleCount = Math.floor(sampleRate * seconds);
        const dataBytes = sampleCount * 2;
        const bytes = new Uint8Array(44 + dataBytes);
        const view = new DataView(bytes.buffer);
        const text = (offset, value) => {
            for (let i = 0; i < value.length; i++) bytes[offset + i] = value.charCodeAt(i);
        };
        text(0, "RIFF");
        view.setUint32(4, 36 + dataBytes, true);
        text(8, "WAVE");
        text(12, "fmt ");
        view.setUint32(16, 16, true);
        view.setUint16(20, 1, true);
        view.setUint16(22, 1, true);
        view.setUint32(24, sampleRate, true);
        view.setUint32(28, sampleRate * 2, true);
        view.setUint16(32, 2, true);
        view.setUint16(34, 16, true);
        text(36, "data");
        view.setUint32(40, dataBytes, true);
        for (let i = 0; i < sampleCount; i++) {
            const envelope = Math.min(1, i / 400) * Math.min(1, (sampleCount - i) / 400);
            const sample = Math.round(Math.sin((2 * Math.PI * 440 * i) / sampleRate) * 0.26 * envelope * 32767);
            view.setInt16(44 + i * 2, sample, true);
        }
        const relativePath = "alpha.wav";
        const file = new File([bytes], relativePath, { type: "audio/wav", lastModified: 1234 });
        const uri = "phoebe-web-file://" + id + "/" + relativePath;
        const stored = {
            file,
            folderId: id,
            folderLabel,
            uri,
            objectUrl: null,
            relativePath,
            name: relativePath,
            parentPath: "",
            ext: "wav"
        };
        store.files.set(uri, stored);
        store.folders.set(id, { id, rootUri, label: folderLabel, files: [stored], textFiles: new Map() });
        return rootUri;
    }
    """,
)
private external fun seedPlayableWavBrowserLocalFolder(): String

@JsFun(
    """
    (label, callback) => {
        const id = "phoebe-web-playback-regression-play";
        let button = document.getElementById(id);
        if (!button) {
            button = document.createElement("button");
            button.id = id;
            button.style.position = "fixed";
            button.style.top = "12px";
            button.style.left = "12px";
            button.style.zIndex = "2147483647";
            button.style.padding = "10px 14px";
            button.style.borderRadius = "8px";
            button.style.border = "1px solid #aaa";
            button.style.background = "#111";
            button.style.color = "#fff";
            button.style.font = "14px system-ui, sans-serif";
            document.body.appendChild(button);
        }
        button.textContent = String(label || "Play");
        button.disabled = false;
        button.onclick = () => callback();
    }
    """,
)
private external fun installWasmPlaybackRegressionButton(label: String, callback: () -> Unit)

@JsFun(
    """
    () => {
        const button = document.getElementById("phoebe-web-playback-regression-play");
        if (button) {
            button.onclick = null;
            try { button.remove(); } catch (_) {}
        }
    }
    """,
)
private external fun removeWasmPlaybackRegressionButton()
