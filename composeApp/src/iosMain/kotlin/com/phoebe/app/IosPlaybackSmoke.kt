package com.phoebe.app

import com.phoebe.app.domain.Track
import com.phoebe.app.player.AudioPlayer
import com.phoebe.app.player.PlaybackDiagnostics
import com.phoebe.app.player.PlaybackEnginePath
import com.phoebe.app.player.createIosAudioPlayerForSmoke
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import platform.Foundation.NSDate
import platform.Foundation.NSFileManager
import platform.Foundation.NSProcessInfo
import platform.Foundation.NSURL
import platform.Foundation.timeIntervalSince1970
import platform.posix.exit
import platform.posix.fflush
import platform.posix.stdout

fun runIosPlaybackSmokeIfRequested(): Boolean {
    val arguments = NSProcessInfo.processInfo.arguments.filterIsInstance<String>()
    val rawPath = arguments.firstOrNull { it.startsWith(PathPrefix) }?.removePrefix(PathPrefix)
        ?: return false
    val path = rawPath.asSmokeFilePath()
    val timeoutMs = arguments
        .firstOrNull { it.startsWith(TimeoutPrefix) }
        ?.removePrefix(TimeoutPrefix)
        ?.toLongOrNull()
        ?.takeIf { it > 0L }
        ?: DefaultTimeoutMs

    if (!NSFileManager.defaultManager.fileExistsAtPath(path)) {
        finishIosPlaybackSmoke(
            code = 2,
            line = "PHOEBE_PLAYBACK_SMOKE_FAILED reason=missing-file file=${path.asSmokeValue()} timeoutMs=$timeoutMs",
        )
    }

    val diagnostics = IosSmokeDiagnostics()
    val player = createIosAudioPlayerForSmoke(diagnostics)
    val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    scope.launch {
        try {
            diagnostics.markPlayRequested()
            player.play(listOf(path.toSmokeTrack()), 0)
            val deadline = nowMs() + timeoutMs
            while (nowMs() <= deadline) {
                val snapshot = diagnostics.snapshot()
                val firstAudioMs = snapshot.firstAudioMs
                if (firstAudioMs != null) {
                    finishIosPlaybackSmoke(
                        code = 0,
                        line = "PHOEBE_PLAYBACK_SMOKE_OK firstAudioMs=$firstAudioMs " +
                            "engines=${snapshot.engines.asSmokeValue()} errors=${snapshot.errors.asSmokeValue()} " +
                            "file=${path.asSmokeValue()}",
                    )
                }
                delay(100L)
            }

            val snapshot = diagnostics.snapshot()
            val state = player.state.value
            finishIosPlaybackSmoke(
                code = 3,
                line = "PHOEBE_PLAYBACK_SMOKE_FAILED reason=timeout timeoutMs=$timeoutMs " +
                    "engines=${snapshot.engines.asSmokeValue()} errors=${snapshot.errors.asSmokeValue()} " +
                    "buffering=${state.isBuffering} playing=${state.isPlaying} errorSerial=${state.playbackErrorSerial} " +
                    "file=${path.asSmokeValue()}",
            )
        } finally {
            player.stopPlayback()
            scope.cancel()
        }
    }
    return true
}

private data class IosSmokeSnapshot(
    val engines: List<PlaybackEnginePath>,
    val firstAudioMs: Long?,
    val errors: List<String>,
)

private class IosSmokeDiagnostics : PlaybackDiagnostics {
    private var playRequestedAtMs = nowMs()
    private val engines = mutableListOf<PlaybackEnginePath>()
    private var firstAudioMs: Long? = null
    private val errors = mutableListOf<String>()

    fun markPlayRequested() {
        playRequestedAtMs = nowMs()
        engines.clear()
        firstAudioMs = null
        errors.clear()
    }

    override fun engineSelected(engine: PlaybackEnginePath) {
        if (engine !in engines) engines += engine
    }

    override fun platformPlaying(engine: PlaybackEnginePath, positionMs: Long, durationMs: Long) {
        engineSelected(engine)
        if (firstAudioMs == null) {
            firstAudioMs = (nowMs() - playRequestedAtMs).coerceAtLeast(0L)
        }
    }

    override fun playbackError(engine: PlaybackEnginePath, message: String?) {
        engineSelected(engine)
        errors += "${engine.name}:${message ?: "unknown"}"
    }

    fun snapshot(): IosSmokeSnapshot =
        IosSmokeSnapshot(
            engines = engines.toList(),
            firstAudioMs = firstAudioMs,
            errors = errors.toList(),
        )
}

private fun String.toSmokeTrack(): Track {
    val uri = NSURL.fileURLWithPath(this).absoluteString ?: this
    return Track(
        id = "ios-playback-smoke",
        title = substringAfterLast('/'),
        artist = "Phoebe Smoke",
        album = "iOS Playback Smoke",
        durationMs = 60_000L,
        streamUrl = uri,
        downloadUrl = "",
        localUri = uri,
        filepath = this,
        audioCodec = substringAfterLast('.', missingDelimiterValue = ""),
    )
}

private fun String.asSmokeFilePath(): String =
    if (startsWith("file://")) {
        NSURL.URLWithString(this)?.path ?: removePrefix("file://")
    } else {
        this
    }

private fun List<Any>.asSmokeValue(): String =
    takeIf { it.isNotEmpty() }
        ?.joinToString(",") { it.toString().asSmokeValue() }
        ?: "none"

private fun String.asSmokeValue(): String =
    replace(Regex("\\s+"), "_")

private fun nowMs(): Long =
    (NSDate().timeIntervalSince1970 * 1000.0).toLong()

@OptIn(ExperimentalForeignApi::class)
private fun finishIosPlaybackSmoke(code: Int, line: String): Nothing {
    println(line)
    fflush(stdout)
    exit(code)
    throw IllegalStateException("exit returned")
}

private const val PathPrefix = "--phoebe-playback-smoke="
private const val TimeoutPrefix = "--phoebe-playback-smoke-timeout-ms="
private const val DefaultTimeoutMs = 30_000L
