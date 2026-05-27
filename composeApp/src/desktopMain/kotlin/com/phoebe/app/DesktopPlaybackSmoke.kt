package com.phoebe.app

import com.phoebe.app.domain.Track
import com.phoebe.app.player.DesktopAudioPlayer
import com.phoebe.app.player.PlaybackDiagnostics
import com.phoebe.app.player.PlaybackEnginePath
import java.io.File
import java.net.URI
import kotlin.system.exitProcess

private const val PlaybackSmokeArgPrefix = "--phoebe-playback-smoke="
private const val PlaybackSmokeTimeoutArgPrefix = "--phoebe-playback-smoke-timeout-ms="
private const val DefaultPlaybackSmokeTimeoutMs = 15_000L

internal fun runDesktopPlaybackSmokeIfRequested(args: Array<String>): Boolean {
    val fixtureRaw = args.firstOrNull { it.startsWith(PlaybackSmokeArgPrefix) }
        ?.removePrefix(PlaybackSmokeArgPrefix)
        ?: return false
    val timeoutMs = args.firstOrNull { it.startsWith(PlaybackSmokeTimeoutArgPrefix) }
        ?.removePrefix(PlaybackSmokeTimeoutArgPrefix)
        ?.toLongOrNull()
        ?.takeIf { it > 0L }
        ?: DefaultPlaybackSmokeTimeoutMs
    val status = runDesktopPlaybackSmoke(fixtureRaw, timeoutMs)
    exitProcess(status)
}

private fun runDesktopPlaybackSmoke(
    fixtureRaw: String,
    timeoutMs: Long,
): Int {
    val fixture = playbackSmokeFile(fixtureRaw)
    if (fixture == null || !fixture.isFile) {
        println("PHOEBE_PLAYBACK_SMOKE_FAILED reason=missing-file file=${singleLine(fixtureRaw)} timeoutMs=$timeoutMs")
        return 2
    }

    val diagnostics = PlaybackSmokeDiagnostics()
    val player = DesktopAudioPlayer(diagnostics)
    val track = fixture.toSmokeTrack()
    try {
        diagnostics.markPlayRequested()
        player.play(listOf(track), 0)
        val deadlineNs = System.nanoTime() + timeoutMs * 1_000_000L
        while (System.nanoTime() < deadlineNs) {
            val snapshot = diagnostics.snapshot()
            val firstAudioMs = snapshot.firstAudioMs
            if (firstAudioMs != null) {
                println(
                    "PHOEBE_PLAYBACK_SMOKE_OK firstAudioMs=$firstAudioMs " +
                        "engines=${snapshot.engines.asSmokeValue()} errors=${snapshot.errors.asSmokeValue()} " +
                        "file=${singleLine(fixture.absolutePath)}",
                )
                return 0
            }
            Thread.sleep(100L)
        }

        val snapshot = diagnostics.snapshot()
        val state = player.state.value
        println(
            "PHOEBE_PLAYBACK_SMOKE_FAILED reason=timeout timeoutMs=$timeoutMs " +
                "engines=${snapshot.engines.asSmokeValue()} errors=${snapshot.errors.asSmokeValue()} " +
                "buffering=${state.isBuffering} playing=${state.isPlaying} errorSerial=${state.playbackErrorSerial} " +
                "file=${singleLine(fixture.absolutePath)}",
        )
        return 3
    } finally {
        player.releaseForTests()
    }
}

private fun playbackSmokeFile(raw: String): File? {
    if (raw.isBlank()) return null
    return runCatching {
        if (raw.startsWith("file:", ignoreCase = true)) {
            File(URI(raw))
        } else {
            File(raw)
        }.absoluteFile
    }.getOrNull()
}

private fun File.toSmokeTrack(): Track {
    val uri = toURI().toString()
    return Track(
        id = "desktop-playback-smoke",
        title = name,
        artist = "Phoebe Smoke",
        album = "Desktop Playback Smoke",
        durationMs = 60_000L,
        streamUrl = uri,
        downloadUrl = "",
        localUri = uri,
        filepath = absolutePath,
        audioCodec = extension,
    )
}

private data class PlaybackSmokeSnapshot(
    val engines: List<PlaybackEnginePath>,
    val firstAudioMs: Long?,
    val errors: List<String>,
)

private class PlaybackSmokeDiagnostics : PlaybackDiagnostics {
    private val lock = Any()
    private var playRequestedAtNs = System.nanoTime()
    private val engines = mutableListOf<PlaybackEnginePath>()
    private var firstAudioMs: Long? = null
    private val errors = mutableListOf<String>()

    fun markPlayRequested() {
        synchronized(lock) {
            playRequestedAtNs = System.nanoTime()
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

    override fun platformPlaying(
        engine: PlaybackEnginePath,
        positionMs: Long,
        durationMs: Long,
    ) {
        engineSelected(engine)
        recordFirstAudio()
    }

    override fun decodedAudioEnergy(
        engine: PlaybackEnginePath,
        rms: Double,
    ) {
        if (rms <= 0.000001 || !rms.isFinite()) return
        engineSelected(engine)
        recordFirstAudio()
    }

    override fun playbackError(
        engine: PlaybackEnginePath,
        message: String?,
    ) {
        engineSelected(engine)
        synchronized(lock) {
            errors += "${engine.name}:${message ?: "unknown"}"
        }
    }

    fun snapshot(): PlaybackSmokeSnapshot = synchronized(lock) {
        PlaybackSmokeSnapshot(
            engines = engines.toList(),
            firstAudioMs = firstAudioMs,
            errors = errors.toList(),
        )
    }

    private fun recordFirstAudio() {
        synchronized(lock) {
            if (firstAudioMs == null) {
                firstAudioMs = ((System.nanoTime() - playRequestedAtNs) / 1_000_000L).coerceAtLeast(0L)
            }
        }
    }
}

private fun List<Any>.asSmokeValue(): String =
    takeIf { it.isNotEmpty() }
        ?.joinToString(",") { singleLine(it.toString()) }
        ?: "none"

private fun singleLine(value: String): String =
    value.replace(Regex("\\s+"), "_")
