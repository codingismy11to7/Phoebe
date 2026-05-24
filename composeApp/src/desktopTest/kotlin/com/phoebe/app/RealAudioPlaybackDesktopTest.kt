package com.phoebe.app

import com.phoebe.app.domain.Track
import com.phoebe.app.player.DesktopAudioPlayer
import com.phoebe.app.player.PlaybackDiagnostics
import com.phoebe.app.player.PlaybackEnginePath
import java.io.File
import java.util.Collections
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test

class RealAudioPlaybackDesktopTest {
    @Test
    fun mp3AndM4aStartThroughJavaFxAndReportSpectrum() {
        assumeRealAudioTestsEnabled()

        listOf("wikimedia-example.mp3", "wikimedia-example.m4a").forEach { fixture ->
            val diagnostics = RecordingPlaybackDiagnostics()
            val player = DesktopAudioPlayer(diagnostics)
            try {
                val track = fixtureTrack(fixture, durationMs = 10_000)

                player.play(listOf(track), 0)

                assertTrue(
                    waitUntil {
                        diagnostics.hasEngine(PlaybackEnginePath.JavaFxMediaPlayer) &&
                            player.state.value.isPlaying
                    },
                    "JavaFX media playback did not start for $fixture; engines=${diagnostics.engineEvents()} errors=${diagnostics.errorEvents()}",
                )
                assertTrue(waitUntil { diagnostics.hasEnergy(PlaybackEnginePath.JavaFxMediaPlayer) })
                assertTrue(waitUntil { player.state.value.positionMs > 0L })
                assertTrue(diagnostics.hasPlayingEvent(PlaybackEnginePath.JavaFxMediaPlayer))
            } finally {
                player.releaseForTests()
            }
        }
    }

    @Test
    fun wavFlacAndOggStartThroughSampledPlaybackAndReportPcmRms() {
        assumeRealAudioTestsEnabled()

        listOf("wikimedia-example.wav", "wikimedia-example.flac", "wikimedia-example.ogg").forEach { fixture ->
            val diagnostics = RecordingPlaybackDiagnostics()
            val player = DesktopAudioPlayer(diagnostics)
            try {
                val track = fixtureTrack(fixture, durationMs = 10_000)

                player.play(listOf(track), 0)

                assertTrue(
                    waitUntil {
                        diagnostics.hasEngine(PlaybackEnginePath.SampledClip) &&
                            player.state.value.isPlaying
                    },
                    "Sampled audio playback did not start for $fixture; engines=${diagnostics.engineEvents()} errors=${diagnostics.errorEvents()}",
                )
                assertTrue(diagnostics.hasEnergy(PlaybackEnginePath.SampledClip))
                assertTrue(waitUntil { player.state.value.positionMs > 0L })
                assertTrue(diagnostics.hasPlayingEvent(PlaybackEnginePath.SampledClip))
            } finally {
                player.releaseForTests()
            }
        }
    }

    @Test
    fun mp3CrossfadeRampsVolumesAndCommitsToSecondTrack() {
        assumeRealAudioTestsEnabled()
        val diagnostics = RecordingPlaybackDiagnostics()
        val player = DesktopAudioPlayer(diagnostics)
        try {
            val first = fixtureTrack("mdn-t-rex-roar-cc0.mp3", durationMs = 2_500, id = "first-mp3")
            val second = fixtureTrack("wikimedia-example.mp3", durationMs = 10_000, id = "second-mp3")

            player.setCrossfadeDurationMs(12_000)
            player.play(listOf(first, second), 0)

            assertTrue(
                waitUntil {
                    diagnostics.hasEngine(PlaybackEnginePath.JavaFxMediaPlayer) &&
                        player.state.value.isPlaying
                },
                "JavaFX media playback did not start for crossfade; engines=${diagnostics.engineEvents()} errors=${diagnostics.errorEvents()}",
            )
            assertTrue(waitUntil(timeoutMs = 20_000) {
                player.state.value.currentTrack?.id == second.id &&
                    diagnostics.hasCommitted(PlaybackEnginePath.JavaFxMediaPlayer, second.id)
            })

            val volumes = diagnostics.volumeSteps(PlaybackEnginePath.JavaFxMediaPlayer)
            assertTrue(volumes.size >= 4, "Expected several crossfade volume samples")
            assertTrue(volumes.zipWithNext().all { (left, right) -> left.outgoingVolume >= right.outgoingVolume })
            assertTrue(volumes.zipWithNext().all { (left, right) -> left.incomingVolume <= right.incomingVolume })
            assertEquals(second, player.state.value.currentTrack)
        } finally {
            player.releaseForTests()
        }
    }

    private fun assumeRealAudioTestsEnabled() {
        assumeTrue("Real audio playback tests are disabled", System.getProperty("phoebe.realAudioTests").toBoolean())
    }

    private fun fixtureTrack(
        name: String,
        durationMs: Long,
        id: String = name,
    ): Track {
        val url = javaClass.classLoader.getResource("test-audio/$name")
            ?: error("Missing test audio fixture: $name")
        val file = File(url.toURI())
        return Track(
            id = id,
            title = name,
            artist = "Fixture",
            album = "Real Audio Tests",
            durationMs = durationMs,
            streamUrl = file.toURI().toString(),
            downloadUrl = "",
            localUri = file.toURI().toString(),
            filepath = file.absolutePath,
            audioCodec = file.extension,
        )
    }

    private fun waitUntil(
        timeoutMs: Long = 15_000L,
        condition: () -> Boolean,
    ): Boolean {
        val deadline = System.nanoTime() + timeoutMs * 1_000_000L
        while (System.nanoTime() < deadline) {
            if (condition()) return true
            Thread.sleep(100L)
        }
        return condition()
    }

    private data class VolumeSample(
        val outgoingVolume: Float,
        val incomingVolume: Float,
    )

    private class RecordingPlaybackDiagnostics : PlaybackDiagnostics {
        private val engines = Collections.synchronizedList(mutableListOf<PlaybackEnginePath>())
        private val energyByEngine = Collections.synchronizedMap(mutableMapOf<PlaybackEnginePath, Double>())
        private val playingEngines = Collections.synchronizedSet(mutableSetOf<PlaybackEnginePath>())
        private val committed = Collections.synchronizedList(mutableListOf<Pair<PlaybackEnginePath, String>>())
        private val volumes = Collections.synchronizedList(mutableListOf<Pair<PlaybackEnginePath, VolumeSample>>())
        private val errors = Collections.synchronizedList(mutableListOf<Pair<PlaybackEnginePath, String?>>())

        override fun engineSelected(engine: PlaybackEnginePath) {
            engines += engine
        }

        override fun platformPlaying(engine: PlaybackEnginePath, positionMs: Long, durationMs: Long) {
            playingEngines += engine
        }

        override fun decodedAudioEnergy(engine: PlaybackEnginePath, rms: Double) {
            energyByEngine[engine] = maxOf(energyByEngine[engine] ?: 0.0, rms)
        }

        override fun crossfadeVolume(
            engine: PlaybackEnginePath,
            step: Int,
            outgoingVolume: Float,
            incomingVolume: Float,
        ) {
            volumes += engine to VolumeSample(outgoingVolume, incomingVolume)
        }

        override fun crossfadeCommitted(engine: PlaybackEnginePath, incomingTrackId: String) {
            committed += engine to incomingTrackId
        }

        override fun playbackError(engine: PlaybackEnginePath, message: String?) {
            errors += engine to message
        }

        fun hasEngine(engine: PlaybackEnginePath): Boolean = engine in engines

        fun hasEnergy(engine: PlaybackEnginePath): Boolean = (energyByEngine[engine] ?: 0.0) > 0.000001

        fun hasPlayingEvent(engine: PlaybackEnginePath): Boolean = engine in playingEngines

        fun hasCommitted(engine: PlaybackEnginePath, trackId: String): Boolean = engine to trackId in committed

        fun engineEvents(): List<PlaybackEnginePath> = engines.toList()

        fun errorEvents(): List<Pair<PlaybackEnginePath, String?>> = errors.toList()

        fun volumeSteps(engine: PlaybackEnginePath): List<VolumeSample> =
            volumes.filter { it.first == engine }.map { it.second }
    }
}
