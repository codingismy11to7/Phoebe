package com.phoebe.app

import android.app.Activity
import android.os.Bundle
import android.os.SystemClock
import android.util.Log
import com.phoebe.app.domain.Track
import com.phoebe.app.player.AndroidAudioPlayer
import com.phoebe.app.player.PlaybackDiagnostics
import com.phoebe.app.player.PlaybackEnginePath
import java.io.File
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class AndroidPlaybackSmokeActivity : Activity() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var player: AndroidAudioPlayer? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        AndroidContextHolder.application = application

        val path = intent.getStringExtra(ExtraPath).orEmpty()
        val timeoutMs = intent.getLongExtra(ExtraTimeoutMs, DefaultTimeoutMs).takeIf { it > 0L } ?: DefaultTimeoutMs
        val file = File(path)
        if (!file.isFile) {
            logSmoke("PHOEBE_PLAYBACK_SMOKE_FAILED reason=missing-file file=${path.asSmokeValue()} timeoutMs=$timeoutMs")
            finishAndRemoveTask()
            return
        }

        val diagnostics = AndroidSmokeDiagnostics()
        val smokePlayer = AndroidAudioPlayer(diagnostics)
        player = smokePlayer
        val track = file.toSmokeTrack()
        scope.launch {
            try {
                diagnostics.markPlayRequested()
                smokePlayer.play(listOf(track), 0)
                val deadline = SystemClock.elapsedRealtime() + timeoutMs
                while (SystemClock.elapsedRealtime() <= deadline) {
                    val snapshot = diagnostics.snapshot()
                    val firstAudioMs = snapshot.firstAudioMs
                    if (firstAudioMs != null) {
                        logSmoke(
                            "PHOEBE_PLAYBACK_SMOKE_OK firstAudioMs=$firstAudioMs " +
                                "engines=${snapshot.engines.asSmokeValue()} errors=${snapshot.errors.asSmokeValue()} " +
                                "file=${file.absolutePath.asSmokeValue()}",
                        )
                        return@launch
                    }
                    delay(100L)
                }

                val snapshot = diagnostics.snapshot()
                val state = smokePlayer.state.value
                logSmoke(
                    "PHOEBE_PLAYBACK_SMOKE_FAILED reason=timeout timeoutMs=$timeoutMs " +
                        "engines=${snapshot.engines.asSmokeValue()} errors=${snapshot.errors.asSmokeValue()} " +
                        "buffering=${state.isBuffering} playing=${state.isPlaying} errorSerial=${state.playbackErrorSerial} " +
                        "file=${file.absolutePath.asSmokeValue()}",
                )
            } finally {
                smokePlayer.releaseForTests()
                finishAndRemoveTask()
            }
        }
    }

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }

    private fun File.toSmokeTrack(): Track {
        val uri = toURI().toString()
        return Track(
            id = "android-playback-smoke",
            title = name,
            artist = "Phoebe Smoke",
            album = "Android Playback Smoke",
            durationMs = 60_000L,
            streamUrl = uri,
            downloadUrl = "",
            localUri = uri,
            filepath = absolutePath,
            audioCodec = extension,
        )
    }

    private fun logSmoke(message: String) {
        Log.i(LogTag, message)
    }

    private companion object {
        const val ExtraPath = "phoebe.playbackSmoke.path"
        const val ExtraTimeoutMs = "phoebe.playbackSmoke.timeoutMs"
        const val DefaultTimeoutMs = 30_000L
        const val LogTag = "PhoebePlaybackSmoke"
    }
}

private data class AndroidSmokeSnapshot(
    val engines: List<PlaybackEnginePath>,
    val firstAudioMs: Long?,
    val errors: List<String>,
)

private class AndroidSmokeDiagnostics : PlaybackDiagnostics {
    private val lock = Any()
    private var playRequestedAtMs = SystemClock.elapsedRealtime()
    private val engines = mutableListOf<PlaybackEnginePath>()
    private var firstAudioMs: Long? = null
    private val errors = mutableListOf<String>()

    fun markPlayRequested() {
        synchronized(lock) {
            playRequestedAtMs = SystemClock.elapsedRealtime()
            engines.clear()
            firstAudioMs = null
            errors.clear()
        }
    }

    override fun engineSelected(engine: PlaybackEnginePath) {
        synchronized(lock) {
            if (engine !in engines) engines += engine
        }
    }

    override fun platformPlaying(engine: PlaybackEnginePath, positionMs: Long, durationMs: Long) {
        engineSelected(engine)
        recordFirstAudio()
    }

    override fun decodedAudioEnergy(engine: PlaybackEnginePath, rms: Double) {
        if (rms <= 0.000001 || !rms.isFinite()) return
        engineSelected(engine)
        recordFirstAudio()
    }

    override fun playbackError(engine: PlaybackEnginePath, message: String?) {
        engineSelected(engine)
        synchronized(lock) {
            errors += "${engine.name}:${message ?: "unknown"}"
        }
    }

    fun snapshot(): AndroidSmokeSnapshot = synchronized(lock) {
        AndroidSmokeSnapshot(
            engines = engines.toList(),
            firstAudioMs = firstAudioMs,
            errors = errors.toList(),
        )
    }

    private fun recordFirstAudio() {
        synchronized(lock) {
            if (firstAudioMs == null) {
                firstAudioMs = (SystemClock.elapsedRealtime() - playRequestedAtMs).coerceAtLeast(0L)
            }
        }
    }
}

private fun List<Any>.asSmokeValue(): String =
    takeIf { it.isNotEmpty() }
        ?.joinToString(",") { it.toString().asSmokeValue() }
        ?: "none"

private fun String.asSmokeValue(): String =
    replace(Regex("\\s+"), "_")
