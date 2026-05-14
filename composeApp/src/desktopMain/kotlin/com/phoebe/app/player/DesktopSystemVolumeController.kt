package com.phoebe.app.player

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.io.File
import java.util.concurrent.atomic.AtomicLong

actual fun createSystemVolumeController(): SystemVolumeController {
    val osName = System.getProperty("os.name")?.lowercase().orEmpty()
    return when {
        osName.startsWith("mac") -> MacSystemVolumeController()
        osName.contains("linux") -> LinuxSystemVolumeController()
        else -> NoOpSystemVolumeController()
    }
}

/**
 * macOS system volume bridge. Uses `osascript` for both reads and writes:
 *
 *  - reads run every 400 ms so hardware volume keys propagate to the UI
 *  - writes go straight through; subsequent polls reconcile any drift
 *
 * After a UI-driven write we ignore inbound polls for a short window so a slow
 * AppleScript response can't snap the slider back to a stale value.
 */
private class MacSystemVolumeController : SystemVolumeController {
    override val isSupported: Boolean = true
    override val controlsPlayerOutput: Boolean = false
    private val _volume = MutableStateFlow(readSystemVolume() ?: 0.7f)
    override val volume: StateFlow<Float> = _volume
    private var pollJob: Job? = null

    /** Wall-clock ms of the most recent UI write; while within `IGNORE_POLL_MS`, polls are skipped. */
    private val lastWriteAt = AtomicLong(0L)

    override fun start(scope: CoroutineScope) {
        if (pollJob != null) return
        pollJob = scope.launch(Dispatchers.IO) {
            while (isActive) {
                delay(POLL_MS)
                if (System.currentTimeMillis() - lastWriteAt.get() < IGNORE_POLL_MS) continue
                val current = readSystemVolume() ?: continue
                if (kotlin.math.abs(current - _volume.value) > 0.005f) {
                    _volume.value = current
                }
            }
        }
    }

    override fun setVolume(value: Float) {
        val clamped = value.coerceIn(0f, 1f)
        _volume.value = clamped
        lastWriteAt.set(System.currentTimeMillis())
        runCatching {
            val percent = (clamped * 100f).toInt().coerceIn(0, 100)
            ProcessBuilder("osascript", "-e", "set volume output volume $percent")
                .redirectErrorStream(true)
                .start()
                .waitFor()
        }
    }

    private fun readSystemVolume(): Float? = runCatching {
        val process = ProcessBuilder("osascript", "-e", "output volume of (get volume settings)")
            .redirectErrorStream(true)
            .start()
        process.waitFor()
        val raw = process.inputStream.bufferedReader().readText().trim()
        raw.toIntOrNull()?.let { it.coerceIn(0, 100) / 100f }
    }.getOrNull()

    companion object {
        const val POLL_MS = 400L
        const val IGNORE_POLL_MS = 600L
    }
}

/**
 * Linux desktop volume bridge via PulseAudio/PipeWire [pactl].
 *
 * Hardware volume keys change the default sink level; we poll that so the in-app
 * slider stays in sync. Dragging the slider writes back through pactl. Inside
 * Flatpak, direct writes are often denied, so we fall back to [flatpak-spawn] on
 * the host (requires the Flatpak talk permission in the manifest).
 */
private class LinuxSystemVolumeController : SystemVolumeController {
    override val isSupported: Boolean = readSinkVolume() != null
    override val controlsPlayerOutput: Boolean = false
    private val _volume = MutableStateFlow(readSinkVolume() ?: 0.7f)
    override val volume: StateFlow<Float> = _volume
    private var pollJob: Job? = null
    private val lastWriteAt = AtomicLong(0L)
    private val useFlatpakHostSpawn = File("/.flatpak-info").exists()

    override fun start(scope: CoroutineScope) {
        if (!isSupported || pollJob != null) return
        pollJob = scope.launch(Dispatchers.IO) {
            while (isActive) {
                delay(POLL_MS)
                if (System.currentTimeMillis() - lastWriteAt.get() < IGNORE_POLL_MS) continue
                val current = readSinkVolume() ?: continue
                if (kotlin.math.abs(current - _volume.value) > 0.005f) {
                    _volume.value = current
                }
            }
        }
    }

    override fun setVolume(value: Float) {
        if (!isSupported) return
        val clamped = value.coerceIn(0f, 1f)
        _volume.value = clamped
        lastWriteAt.set(System.currentTimeMillis())
        val percent = (clamped * 100f).toInt().coerceIn(0, 100)
        if (percent > 0) {
            runPactl("set-sink-mute", "@DEFAULT_SINK@", "0")
        }
        if (!runPactl("set-sink-volume", "@DEFAULT_SINK@", "$percent%")) {
            runPactlOnHost("set-sink-volume", "@DEFAULT_SINK@", "$percent%")
        }
    }

    private fun readSinkVolume(): Float? = runCatching {
        if (isSinkMuted()) return@runCatching 0f
        val output = runPactlOutput("get-sink-volume", "@DEFAULT_SINK@") ?: return@runCatching null
        PERCENT_PATTERN.find(output)?.groupValues?.get(1)?.toIntOrNull()?.coerceIn(0, 100)?.div(100f)
    }.getOrNull()

    private fun isSinkMuted(): Boolean = runCatching {
        val output = runPactlOutput("get-sink-mute", "@DEFAULT_SINK@") ?: return@runCatching false
        output.contains("yes", ignoreCase = true)
    }.getOrDefault(false)

    private fun runPactl(vararg args: String): Boolean = runCatching {
        val process = ProcessBuilder(listOf("pactl") + args)
            .redirectErrorStream(true)
            .start()
        val output = process.inputStream.bufferedReader().readText()
        process.waitFor() == 0 && !output.contains("Failure:", ignoreCase = true)
    }.getOrDefault(false)

    private fun runPactlOutput(vararg args: String): String? = runCatching {
        val process = ProcessBuilder(listOf("pactl") + args)
            .redirectErrorStream(true)
            .start()
        val output = process.inputStream.bufferedReader().readText()
        if (process.waitFor() != 0 || output.contains("Failure:", ignoreCase = true)) null else output
    }.getOrNull()

    private fun runPactlOnHost(vararg args: String): Boolean {
        if (!useFlatpakHostSpawn) return false
        return runCatching {
            val process = ProcessBuilder(listOf("flatpak-spawn", "--host", "pactl") + args)
                .redirectErrorStream(true)
                .start()
            val output = process.inputStream.bufferedReader().readText()
            process.waitFor() == 0 && !output.contains("Portal call failed", ignoreCase = true)
        }.getOrDefault(false)
    }

    companion object {
        private val PERCENT_PATTERN = Regex("""(\d+)%""")
        const val POLL_MS = 400L
        const val IGNORE_POLL_MS = 600L
    }
}

private class NoOpSystemVolumeController : SystemVolumeController {
    override val isSupported: Boolean = false
    override val volume: StateFlow<Float> = MutableStateFlow(0.7f)
    override fun start(scope: CoroutineScope) = Unit
    override fun setVolume(value: Float) = Unit
}
