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
import com.phoebe.app.player.SimpleAudioPlayer
import com.phoebe.app.sources.LocalFolderCatalogBuilder
import com.phoebe.app.playlists.PlaylistExportFormat
import com.phoebe.app.playlists.PlaylistExporter
import com.phoebe.app.sources.LocalLibraryIO

@Composable
fun PhoebeWasmE2eApp(e2eMode: String? = null) {
    var status by remember { mutableStateOf("running") }
    var details by remember { mutableStateOf("") }

    LaunchedEffect(e2eMode) {
        val results = when (e2eMode) {
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
