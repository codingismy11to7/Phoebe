package com.phoebe.app.player

import javazoom.spi.vorbis.sampled.file.VorbisAudioFileReader
import com.phoebe.app.data.PlexClient
import com.phoebe.app.domain.EqualizerProfile
import com.phoebe.app.domain.Track
import com.phoebe.app.platform.PhoebeLog
import javafx.application.Platform
import javafx.scene.media.AudioSpectrumListener
import javafx.scene.media.Media
import javafx.scene.media.MediaPlayer
import java.io.BufferedInputStream
import java.io.ByteArrayInputStream
import java.io.File
import java.io.InputStream
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.time.Duration
import java.util.concurrent.CompletableFuture
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import javax.sound.sampled.LineEvent
import javax.sound.sampled.AudioFormat
import javax.sound.sampled.AudioInputStream
import javax.sound.sampled.AudioSystem
import javax.sound.sampled.Clip
import javax.sound.sampled.DataLine
import javax.sound.sampled.FloatControl
import javax.sound.sampled.SourceDataLine
import org.jflac.sound.spi.FlacAudioFileReader

actual fun createAudioPlayer(): AudioPlayer = DesktopAudioPlayer()

/**
 * Desktop playback uses a streaming Java Sound path for remote MP3 / FLAC / Ogg / WAV-style sources so startup
 * is not gated on JavaFX buffering or whole-file downloads. JavaFX [MediaPlayer] remains the fallback for formats
 * Java Sound does not decode well, such as AAC/M4A.
 */
internal class DesktopAudioPlayer(
    private val diagnostics: PlaybackDiagnostics = PlaybackDiagnostics.None,
) : SimpleAudioPlayer() {
    override val useProgressTicker: Boolean = false

    init {
        JavaFxRuntime.warmUp()
    }

    private var player: MediaPlayer? = null
    private var lastPlaybackUiSyncAtMs = 0L
    private var fadingOutPlayer: MediaPlayer? = null
    private var desktopCrossfadeGeneration = -1
    private var sampledClip: Clip? = null
    private var sampledStream: StreamingSampledPlayback? = null
    private var remoteSampledFile: File? = null
    private var prefetchedCrossfade: PrefetchedCrossfade? = null
    private var crossfadePrefetchFuture: CompletableFuture<PrefetchedCrossfade?>? = null
    private var fullyBufferedPlayback = false
    private val httpClient = HttpClient.newBuilder()
        .connectTimeout(Duration.ofSeconds(15))
        .followRedirects(HttpClient.Redirect.NORMAL)
        .build()
    private val playbackExecutor = Executors.newSingleThreadExecutor { r ->
        Thread(r, "Phoebe-desktop-playback").apply { isDaemon = true }
    }
    private val crossfadePrefetchExecutor = Executors.newSingleThreadExecutor { r ->
        Thread(r, "Phoebe-desktop-crossfade-prefetch").apply { isDaemon = true }
    }

    private data class PrefetchedCrossfade(
        val trackId: String,
        val file: File,
    )

    private class StreamingSampledPlayback(
        val line: SourceDataLine,
        val stream: AudioInputStream,
        val equalizerProcessor: GraphicEqualizerProcessor?,
    ) {
        val stopped = AtomicBoolean(false)
        @Volatile
        var paused = false
        @Volatile
        var reportedEnergy = false

        fun stop() {
            stopped.set(true)
            paused = false
            runCatching { line.stop() }
            runCatching { line.flush() }
            runCatching { line.close() }
            runCatching { stream.close() }
        }

        fun pause() {
            paused = true
            runCatching { line.stop() }
        }

        fun resume() {
            paused = false
            runCatching { line.start() }
        }
    }

    override fun playUri(uri: String) {
        playUri(uri, preferredSampledExtension = null, preferredStreamingExtension = null)
    }

    override fun playTrack(track: Track) {
        val uri = track.localUri ?: track.streamUrl
        playUri(
            uri = uri,
            preferredSampledExtension = sampledPlaybackExtensionFromTrack(track),
            preferredStreamingExtension = streamingSampledExtensionFromTrack(track),
        )
    }

    override fun playQueueOnPlatform(
        queue: List<Track>,
        startIndex: Int,
        track: Track,
        generation: Int,
    ) {
        super.playQueueOnPlatform(queue, startIndex, track, generation)
        scheduleCrossfadePrefetchAfterLoad(queue, startIndex, generation)
    }

    override fun skipToInQueueOnPlatform(
        queue: List<Track>,
        startIndex: Int,
        track: Track,
        generation: Int,
    ) {
        super.skipToInQueueOnPlatform(queue, startIndex, track, generation)
        scheduleCrossfadePrefetchAfterLoad(queue, startIndex, generation)
    }

    override fun stopCurrentPlaybackImmediately() {
        runCatching { sampledClip?.stop() }
        runCatching { sampledStream?.stop() }
        JavaFxRuntime.runLater {
            runCatching { player?.pause() }
            runCatching { fadingOutPlayer?.pause() }
        }
    }

    private fun playUri(
        uri: String,
        preferredSampledExtension: String?,
        preferredStreamingExtension: String?,
    ) {
        if (uri.isBlank()) {
            markPlaybackFailed()
            return
        }
        val generation = activePlayGeneration
        playbackExecutor.execute {
            if (!isPlayRequestCurrent(generation)) return@execute
            runCatching {
                disposeAllOnPlaybackThread()
                if (!isPlayRequestCurrent(generation)) return@execute
                var file = uriToLocalFile(uri)
                if (file == null) {
                    val streamingExtension = preferredStreamingExtension ?: streamingSampledExtensionFromUri(uri)
                    if (streamingExtension != null && tryStartSampledStream(uri, streamingExtension, generation)) {
                        return@execute
                    }
                    if (!isPlayRequestCurrent(generation)) return@execute
                }
                if (file == null && shouldEagerlyBufferRemotePlayback(uri, preferredSampledExtension)) {
                    val extension = preferredSampledExtension
                        ?: sampledPlaybackExtensionFromUri(uri)
                        ?: "mp3"
                    val downloaded = downloadRemoteAudio(uri, extension)
                    if (!isPlayRequestCurrent(generation)) {
                        runCatching { downloaded.delete() }
                        return@execute
                    }
                    remoteSampledFile = downloaded
                    file = downloaded
                }
                if (file != null && preferSampledPlayback(file)) {
                    val clip = openAndStartSampledClip(file)
                    if (clip != null) {
                        if (!isPlayRequestCurrent(generation)) {
                            runCatching { clip.stop(); clip.close() }
                            return@execute
                        }
                        sampledClip = clip
                        diagnostics.engineSelected(PlaybackEnginePath.SampledClip)
                        startSampledProgressProbe(clip, generation)
                        applyVolumesFromState()
                        updateBufferedPosition(trackDurationOrClipDuration(generation, clip), generation)
                        markPlaybackReady(generation = generation)
                        return@execute
                    }
                }
                val remoteExtension = preferredSampledExtension ?: sampledPlaybackExtensionFromUri(uri)
                if (file == null && remoteExtension != null) {
                    val downloaded = downloadRemoteAudio(uri, remoteExtension)
                    if (!isPlayRequestCurrent(generation)) {
                        runCatching { downloaded.delete() }
                        return@execute
                    }
                    remoteSampledFile = downloaded
                    val clip = openAndStartSampledClip(downloaded)
                    if (clip != null) {
                        if (!isPlayRequestCurrent(generation)) {
                            runCatching { clip.stop(); clip.close() }
                            runCatching { downloaded.delete() }
                            return@execute
                        }
                        sampledClip = clip
                        diagnostics.engineSelected(PlaybackEnginePath.SampledClip)
                        startSampledProgressProbe(clip, generation)
                        applyVolumesFromState()
                        updateBufferedPosition(trackDurationOrClipDuration(generation, clip), generation)
                        markPlaybackReady(generation = generation)
                        return@execute
                    }
                    disposeSampled()
                }
                if (!isPlayRequestCurrent(generation)) return@execute
                val playbackUri = file?.toURI()?.toString() ?: uri
                fullyBufferedPlayback = file != null
                val javaFxStarted = if (file == null && isRemoteUri(playbackUri)) {
                    playJavaFxSync(playbackUri, generation)
                } else {
                    playJavaFxWithRetries(playbackUri, generation)
                }
                if (!javaFxStarted) {
                    if (file != null && trySampledFallbackAfterJavaFxFailure(file, generation)) {
                        return@execute
                    }
                    if (file == null && tryBufferedRemotePlaybackFallback(uri, preferredSampledExtension, generation)) {
                        return@execute
                    }
                    diagnostics.playbackError(PlaybackEnginePath.JavaFxMediaPlayer, "JavaFX playback failed to start")
                    markPlaybackFailed(generation = generation)
                    return@execute
                }
                if (!isPlayRequestCurrent(generation)) return@execute
                applyVolumesFromState()
                markPlaybackReady(generation = generation)
            }.onFailure { error ->
                if (!isPlayRequestCurrent(generation)) return@execute
                logPlaybackFailure(error)
                diagnostics.playbackError(PlaybackEnginePath.JavaFxMediaPlayer, error.message)
                markPlaybackFailed(generation = generation)
            }
        }
    }

    override fun pause() {
        playbackExecutor.execute {
            val stream = sampledStream
            if (stream != null) {
                stream.pause()
                return@execute
            }
            val clip = sampledClip
            if (clip != null) {
                runCatching { clip.stop() }
            } else {
                JavaFxRuntime.runLater { player?.pause() }
            }
        }
    }

    override fun resume() {
        playbackExecutor.execute {
            val stream = sampledStream
            if (stream != null) {
                stream.resume()
                return@execute
            }
            val clip = sampledClip
            if (clip != null) {
                runCatching { clip.start() }
            } else {
                JavaFxRuntime.runLater { player?.play() }
            }
        }
    }

    override fun seek(positionMs: Long) {
        val generation = activePlayGeneration
        playbackExecutor.execute {
            if (!isPlayRequestCurrent(generation)) return@execute
            val stream = sampledStream
            if (stream != null) {
                runCatching { stream.line.flush() }
                return@execute
            }
            val clip = sampledClip
            if (clip != null) {
                runCatching {
                    val wasPlaying = clip.isActive
                    clip.stop()
                    clip.microsecondPosition = positionMs.coerceAtLeast(0L) * 1000L
                    if (wasPlaying) clip.start()
                }
            } else {
                JavaFxRuntime.runLater {
                    if (!isPlayRequestCurrent(generation)) return@runLater
                    player?.seek(javafx.util.Duration.millis(positionMs.toDouble()))
                }
            }
        }
    }

    override fun setOutputVolume(volume: Float) {
        val v = volume.coerceIn(0f, 1f)
        playbackExecutor.execute {
            JavaFxRuntime.runLater { player?.volume = v.toDouble() }
            applySampledVolume(v)
        }
    }

    override fun applyEqualizer(profile: EqualizerProfile) {
        val normalized = profile.normalized()
        playbackExecutor.execute {
            JavaFxRuntime.runLater {
                applyJavaFxEqualizer(player, normalized)
                applyJavaFxEqualizer(fadingOutPlayer, normalized)
            }
        }
    }

    override fun startCrossfadeOnPlatform(
        queue: List<Track>,
        targetIndex: Int,
        track: Track,
        durationMs: Long,
        baseVolume: Float,
        generation: Int,
    ): Boolean {
        val outgoing = player ?: return false
        if (sampledClip != null || sampledStream != null) return false
        val uri = track.localUri ?: track.streamUrl
        if (uri.isBlank()) return false
        val localFile = uriToLocalFile(uri)
        val prefetchedFile = if (localFile == null) {
            prefetchedCrossfade
            ?.takeIf { it.trackId == track.id && it.file.exists() }
            ?.file
        } else {
            null
        }
        val file = localFile ?: prefetchedFile ?: return false
        if (preferSampledPlayback(file)) return false
        if (desktopCrossfadeGeneration == generation) return true
        desktopCrossfadeGeneration = generation
        val playbackUri = file.toURI().toString()
        JavaFxRuntime.runLater {
            if (!isPlayRequestCurrent(generation) || player !== outgoing) {
                if (desktopCrossfadeGeneration == generation) desktopCrossfadeGeneration = -1
                return@runLater
            }
            outgoing.setOnEndOfMedia {}
            runCatching {
                val media = Media(playbackUri)
                val incoming = MediaPlayer(media)
                applyJavaFxEqualizer(incoming, equalizerProfile)
                var committed = false
                var failed = false
                fun fallbackToNormalPlayback() {
                    if (failed) return
                    failed = true
                    if (desktopCrossfadeGeneration == generation) desktopCrossfadeGeneration = -1
                    runCatching { incoming.dispose() }
                    if (isPlayRequestCurrent(generation)) {
                        play(queue, targetIndex)
                    }
                }
                incoming.volume = 0.0
                incoming.setOnError {
                    PhoebeLog.d("DesktopAudioPlayer") {
                        "crossfade playback error: ${incoming.error?.message ?: incoming.error?.type}"
                    }
                    diagnostics.playbackError(PlaybackEnginePath.JavaFxMediaPlayer, incoming.error?.message)
                    fallbackToNormalPlayback()
                }
                media.setOnError {
                    PhoebeLog.d("DesktopAudioPlayer") { "crossfade media error: ${media.error?.message}" }
                    diagnostics.playbackError(PlaybackEnginePath.JavaFxMediaPlayer, media.error?.message)
                    fallbackToNormalPlayback()
                }
                incoming.setOnReady {
                    if (!isPlayRequestCurrent(generation) || player !== outgoing) {
                        fallbackToNormalPlayback()
                        return@setOnReady
                    }
                    incoming.play()
                }
                incoming.setOnPlaying {
                    if (committed || !isPlayRequestCurrent(generation) || player !== outgoing) {
                        runCatching { incoming.dispose() }
                        if (desktopCrossfadeGeneration == generation) desktopCrossfadeGeneration = -1
                        return@setOnPlaying
                    }
                    incoming.setOnPlaying(null)
                    fadingOutPlayer = outgoing
                    committed = true
                    if (prefetchedCrossfade?.trackId == track.id) {
                        prefetchedCrossfade = null
                    }
                    diagnostics.crossfadeStarted(
                        engine = PlaybackEnginePath.JavaFxMediaPlayer,
                        outgoingTrackId = state.value.currentTrack?.id,
                        incomingTrackId = track.id,
                        durationMs = durationMs,
                    )
                    runDesktopCrossfade(
                        outgoing = outgoing,
                        incoming = incoming,
                        incomingTempFile = prefetchedFile,
                        queue = queue,
                        targetIndex = targetIndex,
                        durationMs = durationMs,
                        baseVolume = baseVolume,
                        generation = generation,
                    )
                }
                Thread({
                    Thread.sleep(JavaFxStartupTimeoutSeconds * 1_000L)
                    JavaFxRuntime.runLater {
                        if (!committed && !failed && isPlayRequestCurrent(generation) && player === outgoing) {
                            fallbackToNormalPlayback()
                        }
                    }
                }, "Phoebe-desktop-crossfade-timeout").apply { isDaemon = true }.start()
            }.onFailure { error ->
                if (desktopCrossfadeGeneration == generation) desktopCrossfadeGeneration = -1
                logPlaybackFailure(error)
                diagnostics.playbackError(PlaybackEnginePath.JavaFxMediaPlayer, error.message)
            }
        }
        return true
    }

    private fun runDesktopCrossfade(
        outgoing: MediaPlayer,
        incoming: MediaPlayer,
        incomingTempFile: File?,
        queue: List<Track>,
        targetIndex: Int,
        durationMs: Long,
        baseVolume: Float,
        generation: Int,
    ) {
        playbackExecutor.execute {
            val steps = 24
            val stepDelay = (durationMs / steps).coerceAtLeast(16L)
            repeat(steps) { index ->
                if (!isPlayRequestCurrent(generation)) return@execute
                val progress = (index + 1).toDouble() / steps.toDouble()
                val outgoingVolume = (baseVolume * (1.0 - progress)).toFloat().coerceIn(0f, 1f)
                val incomingVolume = (baseVolume * progress).toFloat().coerceIn(0f, 1f)
                diagnostics.crossfadeVolume(
                    engine = PlaybackEnginePath.JavaFxMediaPlayer,
                    step = index + 1,
                    outgoingVolume = outgoingVolume,
                    incomingVolume = incomingVolume,
                )
                JavaFxRuntime.runLater {
                    outgoing.volume = outgoingVolume.toDouble()
                    incoming.volume = incomingVolume.toDouble()
                }
                Thread.sleep(stepDelay)
            }
            JavaFxRuntime.runLater {
                runCatching {
                    outgoing.stop()
                    outgoing.dispose()
                }
                if (fadingOutPlayer === outgoing) fadingOutPlayer = null
                if (desktopCrossfadeGeneration == generation) desktopCrossfadeGeneration = -1
                if (isPlayRequestCurrent(generation)) {
                    player = incoming
                    incomingTempFile?.let { temp ->
                        remoteSampledFile?.takeIf { it != temp }?.delete()
                        remoteSampledFile = temp
                    }
                    incoming.volume = effectiveOutputVolume().toDouble().coerceIn(0.0, 1.0)
                    incoming.setOnEndOfMedia {
                        if (isPlayRequestCurrent(generation)) next()
                    }
                    incoming.bufferProgressTimeProperty().addListener { _, _, _ ->
                        if (player === incoming) syncJavaFxPlayback(incoming, generation, isBuffering = false)
                    }
                    incoming.currentTimeProperty().addListener { _, _, _ ->
                        if (player === incoming) syncJavaFxPlayback(incoming, generation, isBuffering = false)
                    }
                    adoptCrossfadeTarget(
                        queue = queue,
                        targetIndex = targetIndex,
                        positionMs = incoming.currentTime.toMillis().toLong().coerceAtLeast(0L),
                        generation = generation,
                    )
                    diagnostics.crossfadeCommitted(
                        engine = PlaybackEnginePath.JavaFxMediaPlayer,
                        incomingTrackId = queue[targetIndex].id,
                    )
                    syncJavaFxPlayback(incoming, generation, isBuffering = false)
                    prefetchCrossfadeCandidate(queue, targetIndex, generation)
                } else {
                    runCatching {
                        incoming.stop()
                        incoming.dispose()
                    }
                }
            }
        }
    }

    private fun applyVolumesFromState() {
        val v = effectiveOutputVolume()
        JavaFxRuntime.runLater { player?.volume = v.toDouble() }
        applySampledVolume(v)
    }

    private fun applyJavaFxEqualizer(mediaPlayer: MediaPlayer?, profile: EqualizerProfile) {
        val player = mediaPlayer ?: return
        val normalized = profile.normalized()
        val equalizer = player.audioEqualizer
        val active = javaFxEqualizerActive(normalized)
        if (!active) {
            equalizer.isEnabled = false
            if (player.status != MediaPlayer.Status.PLAYING && player.status != MediaPlayer.Status.STALLED) {
                equalizer.bands.clear()
            }
            return
        }
        val platformBands = EqualizerProfile.bandsForCount(31)
        val desiredBands = platformBands.map { band ->
            javafx.scene.media.EqualizerBand(
                band.frequencyHz.toDouble(),
                equalizerBandwidthHz(band.frequencyHz, normalized.bandCount).toDouble(),
                gainForJavaFxBand(normalized, band.frequencyHz).toDouble(),
            )
        }
        val currentBands = equalizer.bands
        val sameBandLayout = currentBands.size == desiredBands.size &&
            currentBands.indices.all { index ->
                kotlin.math.abs(currentBands[index].centerFrequency - desiredBands[index].centerFrequency) < 0.01
            }
        if (!sameBandLayout) {
            if (player.status == MediaPlayer.Status.PLAYING || player.status == MediaPlayer.Status.STALLED) {
                currentBands.forEach { band ->
                    band.gain = gainForJavaFxBand(normalized, band.centerFrequency.toFloat()).toDouble()
                }
                equalizer.isEnabled = true
                return
            }
            equalizer.isEnabled = false
            currentBands.setAll(desiredBands)
        } else {
            desiredBands.forEachIndexed { index, desired ->
                currentBands[index].bandwidth = desired.bandwidth
                currentBands[index].gain = desired.gain
            }
        }
        equalizer.isEnabled = true
    }

    private fun javaFxEqualizerActive(profile: EqualizerProfile): Boolean =
        profile.normalized().let { normalized ->
            normalized.enabled && EqualizerProfile.bandsForCount(31)
                .any { band -> gainForJavaFxBand(normalized, band.frequencyHz) != 0f }
        }

    private fun gainForJavaFxBand(profile: EqualizerProfile, centerFrequencyHz: Float): Float {
        if (!profile.enabled) return 0f
        val center = centerFrequencyHz.coerceAtLeast(1f)
        var closestIndex = -1
        var closestDistance = Float.MAX_VALUE
        profile.bands.forEachIndexed { index, band ->
            val distance = kotlin.math.abs(kotlin.math.ln(center / band.frequencyHz))
            if (distance < closestDistance) {
                closestDistance = distance
                closestIndex = index
            }
        }
        return if (closestIndex >= 0 && closestDistance <= JavaFxEqualizerBandMatchTolerance) {
            profile.gainsDb.getOrElse(closestIndex) { 0f }
        } else {
            0f
        }
    }

    private fun equalizerBandwidthHz(centerFrequencyHz: Float, bandCount: Int): Float {
        val multiplier = when (bandCount) {
            31 -> 0.23f
            15 -> 0.42f
            5 -> 0.95f
            else -> 0.62f
        }
        return (centerFrequencyHz * multiplier).coerceAtLeast(20f)
    }

    private fun applySampledVolume(volume: Float) {
        sampledClip?.let { clip ->
            applyVolumeToClip(clip, volume)
        }
        sampledStream?.let { stream ->
            applyVolumeToLine(stream.line, volume)
        }
    }

    private fun applyVolumeToClip(clip: Clip, volume: Float) {
        applyVolumeControl(clip, volume)
    }

    private fun applyVolumeToLine(line: SourceDataLine, volume: Float) {
        applyVolumeControl(line, volume)
    }

    private fun applyVolumeControl(line: javax.sound.sampled.Line, volume: Float) {
        val v = volume.coerceIn(0f, 1f)
        runCatching {
            val control = line.getControl(FloatControl.Type.MASTER_GAIN) as FloatControl
            val min = control.minimum
            val max = control.maximum
            control.value = min + (max - min) * v
        }.onFailure {
            runCatching {
                val control = line.getControl(FloatControl.Type.VOLUME) as FloatControl
                control.value = control.minimum + (control.maximum - control.minimum) * v
            }
        }
    }

    private fun uriToLocalFile(uri: String): File? = runCatching {
        when {
            uri.startsWith("file:", ignoreCase = true) -> File(URI(uri))
            uri.startsWith("/") && !uri.contains("://") -> File(uri)
            else -> null
        }
    }.getOrNull()

    private fun preferSampledPlayback(file: File): Boolean {
        return sampledPlaybackExtensionFromSuffix(file.extension) != null
    }

    private fun preferSampledFallbackAfterJavaFxFailure(file: File): Boolean {
        return when (file.extension.lowercase()) {
            "mp3", "mpeg" -> true
            else -> false
        }
    }

    private fun trySampledFallbackAfterJavaFxFailure(file: File, generation: Int): Boolean {
        if (!preferSampledFallbackAfterJavaFxFailure(file)) return false
        if (!isPlayRequestCurrent(generation)) return true
        disposeJavaFxBlocking()
        val clip = openAndStartSampledClip(file) ?: run {
            disposeSampled()
            return false
        }
        if (!isPlayRequestCurrent(generation)) {
            runCatching {
                clip.stop()
                clip.close()
            }
            return true
        }
        sampledClip = clip
        diagnostics.engineSelected(PlaybackEnginePath.SampledClip)
        startSampledProgressProbe(clip, generation)
        applyVolumesFromState()
        updateBufferedPosition(trackDurationOrClipDuration(generation, clip), generation)
        markPlaybackReady(generation = generation)
        return true
    }

    private fun tryBufferedRemotePlaybackFallback(
        uri: String,
        preferredSampledExtension: String?,
        generation: Int,
    ): Boolean {
        if (!isRemoteUri(uri) || !isPlayRequestCurrent(generation)) return false
        disposeJavaFxBlocking()
        val extension = preferredSampledExtension
            ?: sampledPlaybackExtensionFromUri(uri)
            ?: "mp3"
        val downloaded = runCatching { downloadRemoteAudio(uri, extension) }
            .getOrElse { error ->
                logPlaybackFailure(error)
                diagnostics.playbackError(PlaybackEnginePath.JavaFxMediaPlayer, error.message)
                return false
            }
        if (!isPlayRequestCurrent(generation)) {
            runCatching { downloaded.delete() }
            return true
        }
        remoteSampledFile = downloaded
        if (preferSampledPlayback(downloaded)) {
            val clip = openAndStartSampledClip(downloaded)
            if (clip != null) {
                if (!isPlayRequestCurrent(generation)) {
                    runCatching {
                        clip.stop()
                        clip.close()
                    }
                    return true
                }
                sampledClip = clip
                diagnostics.engineSelected(PlaybackEnginePath.SampledClip)
                startSampledProgressProbe(clip, generation)
                applyVolumesFromState()
                updateBufferedPosition(trackDurationOrClipDuration(generation, clip), generation)
                markPlaybackReady(generation = generation)
                return true
            }
            disposeSampled()
            return false
        }
        fullyBufferedPlayback = true
        if (playJavaFxWithRetries(downloaded.toURI().toString(), generation)) {
            if (!isPlayRequestCurrent(generation)) return true
            applyVolumesFromState()
            markPlaybackReady(generation = generation)
            return true
        }
        if (trySampledFallbackAfterJavaFxFailure(downloaded, generation)) {
            return true
        }
        disposeSampled()
        return false
    }

    private fun tryStartSampledStream(uri: String, extension: String, generation: Int): Boolean {
        if (!isRemoteUri(uri) || !isPlayRequestCurrent(generation)) return false
        val responseStream = runCatching { openRemoteAudioStream(uri) }
            .getOrElse { error ->
                logPlaybackFailure(error)
                diagnostics.playbackError(PlaybackEnginePath.SampledStream, error.message)
                return false
            }
        val pcmStream = runCatching {
            val raw = openStreamingRawAudioInputStream(responseStream, extension)
            preparePcmForSourceLine(raw)
        }.getOrElse { error ->
            runCatching { responseStream.close() }
            logPlaybackFailure(error)
            diagnostics.playbackError(PlaybackEnginePath.SampledStream, error.message)
            return false
        }
        val format = pcmStream.format
        val bufferSize = sourceLineBufferSize(format)
        val line = runCatching {
            val info = DataLine.Info(SourceDataLine::class.java, format)
            (AudioSystem.getLine(info) as SourceDataLine).also { it.open(format, bufferSize) }
        }.getOrElse { error ->
            runCatching { pcmStream.close() }
            logPlaybackFailure(error)
            diagnostics.playbackError(PlaybackEnginePath.SampledStream, error.message)
            return false
        }
        val firstBuffer = ByteArray(bufferSize)
        val firstRead = runCatching { pcmStream.read(firstBuffer, 0, firstBuffer.size) }
            .getOrElse { error ->
                runCatching { line.close() }
                runCatching { pcmStream.close() }
                logPlaybackFailure(error)
                diagnostics.playbackError(PlaybackEnginePath.SampledStream, error.message)
                return false
            }
        if (firstRead <= 0) {
            runCatching { line.close() }
            runCatching { pcmStream.close() }
            return false
        }
        if (!isPlayRequestCurrent(generation)) {
            runCatching { line.close() }
            runCatching { pcmStream.close() }
            return true
        }
        val playback = StreamingSampledPlayback(
            line = line,
            stream = pcmStream,
            equalizerProcessor = streamingEqualizerProcessor(format),
        )
        sampledStream = playback
        PhoebeLog.d("DesktopAudioPlayer") { "sampled stream playback extension=$extension format=$format" }
        diagnostics.engineSelected(PlaybackEnginePath.SampledStream)
        applyVolumeToLine(line, effectiveOutputVolume())
        line.start()
        writeSampledStreamBuffer(
            playback = playback,
            buffer = firstBuffer,
            length = firstRead,
            generation = generation,
        )
        markPlaybackReady(generation = generation)
        syncSampledStreamPlayback(playback, generation)
        startSampledStreamPump(playback, bufferSize, generation)
        return true
    }

    private fun openRemoteAudioStream(uri: String): InputStream {
        val request = HttpRequest.newBuilder(URI(uri))
            .GET()
            .timeout(Duration.ofSeconds(45))
            .header("User-Agent", "Phoebe/0.1.0 (https://github.com/phoebe)")
            .header("X-Plex-Client-Identifier", PlexClient.ClientIdentifier)
            .build()
        val response = httpClient.send(request, HttpResponse.BodyHandlers.ofInputStream())
        if (response.statusCode() !in 200..299) {
            response.body().close()
            error("Plex stream request failed (${response.statusCode()})")
        }
        val contentType = response.headers().firstValue("content-type").orElse("").lowercase()
        if (contentType.startsWith("text/") ||
            contentType.contains("html") ||
            contentType.contains("json") ||
            contentType.contains("xml")
        ) {
            response.body().close()
            error("Plex stream returned $contentType instead of audio")
        }
        return response.body()
    }

    private fun openStreamingRawAudioInputStream(input: InputStream, extension: String): AudioInputStream {
        val buffered = BufferedInputStream(input, RemoteAudioProbeBufferBytes)
        return when (extension.lowercase()) {
            "flac" -> FlacAudioFileReader().getAudioInputStream(buffered)
            "ogg" -> VorbisAudioFileReader().getAudioInputStream(buffered)
            "opus" -> runCatching { VorbisAudioFileReader().getAudioInputStream(buffered) }
                .getOrElse { AudioSystem.getAudioInputStream(buffered) }
            else -> AudioSystem.getAudioInputStream(buffered)
        }
    }

    private fun preparePcmForSourceLine(stream: AudioInputStream): AudioInputStream {
        val format = stream.format
        val enc = format.encoding
        val pcmReady = (enc == AudioFormat.Encoding.PCM_SIGNED || enc == AudioFormat.Encoding.PCM_UNSIGNED) &&
            format.frameSize > 0
        val channels = format.channels.takeIf { it > 0 } ?: 2
        val sampleRate = format.sampleRate.takeIf { it > 0f && !it.isNaN() } ?: 44100f
        val target = AudioFormat(
            AudioFormat.Encoding.PCM_SIGNED,
            sampleRate,
            16,
            channels,
            channels * 2,
            sampleRate,
            false,
        )
        return when {
            AudioSystem.isConversionSupported(target, format) -> AudioSystem.getAudioInputStream(target, stream)
            pcmReady -> stream
            else -> error("Unsupported streaming audio conversion: $format")
        }
    }

    private fun sourceLineBufferSize(format: AudioFormat): Int {
        val frameSize = format.frameSize.takeIf { it > 0 } ?: return StreamingPcmBufferBytes
        val sampleRate = format.sampleRate.takeIf { it > 0f && !it.isNaN() } ?: return StreamingPcmBufferBytes
        val frames = (sampleRate / 8f).toInt().coerceAtLeast(1)
        return (frames * frameSize)
            .coerceAtLeast(StreamingPcmBufferBytes)
            .coerceAtMost(MaxStreamingLineBufferBytes)
    }

    private fun startSampledStreamPump(
        playback: StreamingSampledPlayback,
        bufferSize: Int,
        generation: Int,
    ) {
        Thread({
            val buffer = ByteArray(bufferSize)
            try {
                while (isPlayRequestCurrent(generation) && sampledStream === playback && !playback.stopped.get()) {
                    waitIfSampledStreamPaused(playback)
                    val read = playback.stream.read(buffer, 0, buffer.size)
                    if (read <= 0) break
                    writeSampledStreamBuffer(playback, buffer, read, generation)
                    syncSampledStreamPlayback(playback, generation)
                }
                if (isPlayRequestCurrent(generation) && sampledStream === playback && !playback.stopped.get()) {
                    runCatching { playback.line.drain() }
                    next()
                }
            } catch (error: Throwable) {
                if (isPlayRequestCurrent(generation) && sampledStream === playback && !playback.stopped.get()) {
                    logPlaybackFailure(error)
                    diagnostics.playbackError(PlaybackEnginePath.SampledStream, error.message)
                    markPlaybackFailed(generation = generation)
                }
            } finally {
                if (sampledStream === playback) {
                    sampledStream = null
                }
                playback.stop()
            }
        }, "Phoebe-sampled-stream-playback").apply {
            isDaemon = true
            start()
        }
    }

    private fun waitIfSampledStreamPaused(playback: StreamingSampledPlayback) {
        while (playback.paused && !playback.stopped.get()) {
            Thread.sleep(25L)
        }
    }

    private fun writeSampledStreamBuffer(
        playback: StreamingSampledPlayback,
        buffer: ByteArray,
        length: Int,
        generation: Int,
    ) {
        if (!isPlayRequestCurrent(generation) || playback.stopped.get()) return
        applyEqualizerToPcmBuffer(
            buffer = buffer,
            length = length,
            format = playback.stream.format,
            processor = playback.equalizerProcessor,
        )
        playback.line.write(buffer, 0, length)
        if (!playback.reportedEnergy) {
            val rms = pcmRms(buffer.copyOf(length), playback.stream.format)
            if (rms > 0.0 && rms.isFinite()) {
                playback.reportedEnergy = true
                diagnostics.decodedAudioEnergy(PlaybackEnginePath.SampledStream, rms)
            }
        }
    }

    private fun streamingEqualizerProcessor(format: AudioFormat): GraphicEqualizerProcessor? {
        val profile = equalizerProfile.normalized()
        if (!GraphicEqualizerProcessor.isActive(profile) ||
            format.sampleSizeInBits != 16 ||
            format.frameSize <= 0 ||
            format.channels <= 0 ||
            format.sampleRate <= 0f ||
            (format.encoding != AudioFormat.Encoding.PCM_SIGNED && format.encoding != AudioFormat.Encoding.PCM_UNSIGNED)
        ) {
            return null
        }
        return GraphicEqualizerProcessor(
            sampleRateHz = format.sampleRate,
            channelCount = format.channels,
            profile = profile,
        )
    }

    private fun applyEqualizerToPcmBuffer(
        buffer: ByteArray,
        length: Int,
        format: AudioFormat,
        processor: GraphicEqualizerProcessor?,
    ) {
        if (processor == null) return
        var offset = 0
        while (offset + format.frameSize <= length) {
            repeat(format.channels) { channel ->
                val sampleOffset = offset + channel * 2
                val sample = readPcm16(buffer, sampleOffset, format.isBigEndian, format.encoding) / 32768f
                val processed = (processor.process(channel, sample) * 32767f)
                    .toInt()
                    .coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt())
                writePcm16(buffer, sampleOffset, processed, format.isBigEndian, format.encoding)
            }
            offset += format.frameSize
        }
    }

    private fun syncSampledStreamPlayback(playback: StreamingSampledPlayback, generation: Int) {
        if (!isPlayRequestCurrent(generation) || sampledStream !== playback) return
        val positionMs = (playback.line.microsecondPosition / 1_000L).coerceAtLeast(0L)
        val durationMs = state.value.durationMs
        diagnostics.playbackProgress(PlaybackEnginePath.SampledStream, positionMs, durationMs)
        if (playback.line.isActive || playback.line.isRunning) {
            diagnostics.platformPlaying(PlaybackEnginePath.SampledStream, positionMs, durationMs)
        }
        applyPlatformPlayback(
            positionMs = positionMs,
            durationMs = durationMs,
            isPlaying = !playback.paused && (playback.line.isActive || playback.line.isRunning),
            isBuffering = false,
            bufferedPositionMs = positionMs,
            generation = generation,
        )
    }

    private fun sampledPlaybackExtensionFromUri(uri: String): String? {
        return DesktopPlaybackStartupPolicy.sampledPlaybackExtensionFromUri(uri)
    }

    private fun sampledPlaybackExtensionFromTrack(track: Track): String? {
        sampledPlaybackExtensionFromSuffix(track.audioCodec.orEmpty())?.let { return it }
        sampledPlaybackExtensionFromUri(track.localUri ?: track.streamUrl)?.let { return it }
        return sampledPlaybackExtensionFromSuffix(track.filepath.orEmpty().substringAfterLast('.', missingDelimiterValue = ""))
    }

    private fun streamingSampledExtensionFromUri(uri: String): String? {
        return DesktopPlaybackStartupPolicy.streamingSampledExtensionFromUri(uri)
    }

    private fun streamingSampledExtensionFromTrack(track: Track): String? {
        DesktopPlaybackStartupPolicy.streamingSampledExtensionFromSuffix(track.audioCodec.orEmpty())?.let { return it }
        streamingSampledExtensionFromUri(track.localUri ?: track.streamUrl)?.let { return it }
        return DesktopPlaybackStartupPolicy.streamingSampledExtensionFromSuffix(
            track.filepath.orEmpty().substringAfterLast('.', missingDelimiterValue = ""),
        )
    }

    private fun sampledPlaybackExtensionFromSuffix(extension: String): String? {
        return DesktopPlaybackStartupPolicy.sampledPlaybackExtensionFromSuffix(extension)
    }

    /**
     * JavaFX cannot decode every format Phoebe supports. For sampled-only formats,
     * buffer the remote file first so startup reaches the Clip path directly. MP3/M4A
     * streams stay direct so first audio is not blocked by downloading the whole track.
     */
    private fun shouldEagerlyBufferRemotePlayback(uri: String, preferredSampledExtension: String?): Boolean =
        DesktopPlaybackStartupPolicy.shouldEagerlyBufferRemotePlayback(uri, preferredSampledExtension)

    private fun shouldPrefetchRemoteForCrossfade(uri: String): Boolean =
        DesktopPlaybackStartupPolicy.shouldPrefetchRemoteForCrossfade(uri)

    private fun scheduleCrossfadePrefetchAfterLoad(queue: List<Track>, currentIndex: Int, generation: Int) {
        playbackExecutor.execute {
            if (isPlayRequestCurrent(generation)) {
                prefetchCrossfadeCandidate(queue, currentIndex, generation)
            }
        }
    }

    private fun prefetchCrossfadeCandidate(queue: List<Track>, currentIndex: Int, generation: Int) {
        val nextTrack = queue.getOrNull(currentIndex + 1) ?: return clearCrossfadePrefetch()
        val uri = nextTrack.localUri ?: nextTrack.streamUrl
        if (!shouldPrefetchRemoteForCrossfade(uri)) return clearCrossfadePrefetch()
        if (prefetchedCrossfade?.trackId == nextTrack.id && prefetchedCrossfade?.file?.exists() == true) return
        crossfadePrefetchFuture?.cancel(true)
        crossfadePrefetchFuture = CompletableFuture.supplyAsync({
            if (!isPlayRequestCurrent(generation)) return@supplyAsync null
            runCatching {
                val extension = sampledPlaybackExtensionFromTrack(nextTrack)
                    ?: sampledPlaybackExtensionFromUri(uri)
                    ?: "mp3"
                val file = downloadRemoteAudioForCrossfade(uri, extension)
                PrefetchedCrossfade(nextTrack.id, file)
            }.getOrNull()
        }, crossfadePrefetchExecutor).whenComplete { prefetched, _ ->
            val current = state.value
            val stillNext = current.queue.getOrNull(current.currentIndex + 1)?.id == prefetched?.trackId
            if (!isPlayRequestCurrent(generation) || prefetched == null || !stillNext) {
                prefetched?.file?.delete()
                return@whenComplete
            }
            val previous = prefetchedCrossfade
            if (previous?.trackId != prefetched.trackId) {
                previous?.file?.delete()
            }
            prefetchedCrossfade = prefetched
        }
    }

    private fun clearCrossfadePrefetch() {
        crossfadePrefetchFuture?.cancel(true)
        crossfadePrefetchFuture = null
        prefetchedCrossfade?.file?.delete()
        prefetchedCrossfade = null
    }

    private fun downloadRemoteAudioForCrossfade(uri: String, extension: String): File =
        downloadRemoteAudio(uri, extension)

    /** Avoid SPI probe order issues (e.g. JFlac throwing on Ogg before Vorbis runs). */
    private fun openRawAudioInputStream(file: File): AudioInputStream {
        return when (file.extension.lowercase()) {
            "flac" -> FlacAudioFileReader().getAudioInputStream(file)
            "ogg" -> VorbisAudioFileReader().getAudioInputStream(file)
            "opus" -> runCatching { VorbisAudioFileReader().getAudioInputStream(file) }
                .getOrElse { AudioSystem.getAudioInputStream(file) }
            else -> AudioSystem.getAudioInputStream(file)
        }
    }

    /**
     * [Clip] only accepts PCM lines. Vorbis/MP3 SPIs return encoded formats until converted.
     * FLAC often decodes to **24-bit PCM** with unknown frame size; converting straight to **16-bit**
     * is not always registered, so we **re-open the file** and try **source bit depth, then 24, then 16**.
     */
    private fun decodeToPcmStream(file: File): AudioInputStream {
        val probe = openRawAudioInputStream(file)
        val format = probe.format
        val enc = format.encoding
        val pcmReady = (enc == AudioFormat.Encoding.PCM_SIGNED || enc == AudioFormat.Encoding.PCM_UNSIGNED) &&
            format.frameSize > 0
        if (pcmReady) {
            return probe
        }
        runCatching { probe.close() }

        val channels = format.channels.takeIf { it > 0 } ?: 2
        val sampleRate = format.sampleRate.takeIf { it > 0f && !it.isNaN() } ?: 44100f
        val bitCandidates = buildList {
            val sb = format.sampleSizeInBits
            if (sb > 0) add(sb.coerceIn(8, 32))
            add(24)
            add(16)
        }.distinct()

        var lastError: Throwable? = null
        for (bits in bitCandidates) {
            if (bits % 8 != 0) continue
            val bytesPerSample = bits / 8
            val frameSize = channels * bytesPerSample
            val target = AudioFormat(
                AudioFormat.Encoding.PCM_SIGNED,
                sampleRate,
                bits,
                channels,
                frameSize,
                sampleRate,
                false,
            )
            val raw = runCatching { openRawAudioInputStream(file) }.getOrElse { ex ->
                lastError = ex
                continue
            }
            try {
                return AudioSystem.getAudioInputStream(target, raw)
            } catch (e: Throwable) {
                lastError = e
                runCatching { raw.close() }
            }
        }
        throw lastError ?: IllegalStateException("Unsupported audio conversion")
    }

    private fun prepareStreamForClip(stream: AudioInputStream): AudioInputStream {
        val len = stream.frameLength
        if (len > 0L) {
            return stream
        }
        val format = stream.format
        val frameSize = format.frameSize.takeIf { it > 0 }
            ?: run {
                stream.close()
                error("Unsupported audio stream (unknown frame size)")
            }
        val bytes = stream.use { it.readAllBytes() }
        val frames = bytes.size.toLong() / frameSize
        return AudioInputStream(ByteArrayInputStream(bytes), format, frames)
    }

    /**
     * [Clip] on many JDKs only supports 16-bit (or 8-bit) PCM. FLAC often yields **24-bit** PCM; the SPI
     * may not offer a direct path to 16-bit, so we buffer PCM then downsample in software when needed.
     */
    private fun downsample24BitStereoLittleEndianTo16(input: ByteArray): ByteArray {
        require(input.size % 6 == 0) { "24-bit stereo PCM must be multiple of 6 bytes" }
        val frameCount = input.size / 6
        val out = ByteArray(frameCount * 4)
        var i = 0
        var o = 0
        repeat(frameCount) {
            fun sample24(b0: Int, b1: Int, b2: Int): Int {
                val u = (b0 or (b1 shl 8) or (b2 shl 16)) and 0xFFFFFF
                val s = (u shl 8) shr 8 // sign-extend 24-bit → Int
                return s shr 8 // drop 8 LSBs → 16-bit
            }
            val l = sample24(input[i].toInt() and 0xFF, input[i + 1].toInt() and 0xFF, input[i + 2].toInt() and 0xFF)
            val r = sample24(input[i + 3].toInt() and 0xFF, input[i + 4].toInt() and 0xFF, input[i + 5].toInt() and 0xFF)
            out[o++] = (l and 0xFF).toByte()
            out[o++] = ((l shr 8) and 0xFF).toByte()
            out[o++] = (r and 0xFF).toByte()
            out[o++] = ((r shr 8) and 0xFF).toByte()
            i += 6
        }
        return out
    }

    private fun bufferPcmForClipWithOptionalDownsampleTo16(stream: AudioInputStream): AudioInputStream {
        val bounded = prepareStreamForClip(stream)
        val fmt = bounded.format
        if (fmt.sampleSizeInBits <= 16) {
            return bounded
        }
        if (fmt.sampleSizeInBits == 24 &&
            fmt.channels == 2 &&
            !fmt.isBigEndian &&
            (fmt.encoding == AudioFormat.Encoding.PCM_SIGNED || fmt.encoding == AudioFormat.Encoding.PCM_UNSIGNED)
        ) {
            val pcmBytes = bounded.use { it.readAllBytes() }
            val out = downsample24BitStereoLittleEndianTo16(pcmBytes)
            val outFmt = AudioFormat(
                AudioFormat.Encoding.PCM_SIGNED,
                fmt.sampleRate,
                16,
                2,
                4,
                fmt.sampleRate,
                false,
            )
            val frames = out.size / 4L
            return AudioInputStream(ByteArrayInputStream(out), outFmt, frames)
        }
        runCatching { bounded.close() }
        error("Clip playback needs 16-bit PCM; unsupported format: $fmt")
    }

    private fun openAndStartSampledClip(file: File): Clip? {
        val pcmStream = try {
            decodeToPcmStream(file)
        } catch (e: Throwable) {
            logPlaybackFailure(e)
            return null
        }
        val prepared = try {
            bufferPcmForClipWithOptionalDownsampleTo16(pcmStream)
        } catch (e: Throwable) {
            runCatching { pcmStream.close() }
            logPlaybackFailure(e)
            return null
        }
        val playbackStream = applyEqualizerToPcmStream(prepared)
        return runCatching {
            val clip = AudioSystem.getClip()
            val diagnosticsStream = copyPcmStreamForDiagnostics(playbackStream)
            try {
                clip.open(diagnosticsStream)
            } catch (e: Throwable) {
                runCatching { diagnosticsStream.close() }
                throw e
            }
            applyVolumeToClip(clip, effectiveOutputVolume())
            val generation = activePlayGeneration
            clip.addLineListener { event ->
                if (event.type == LineEvent.Type.STOP &&
                    isPlayRequestCurrent(generation) &&
                    clip.microsecondLength > 0L &&
                    clip.microsecondPosition >= clip.microsecondLength
                ) {
                    next()
                }
            }
            clip.start()
            clip
        }.getOrElse { e ->
            logPlaybackFailure(e)
            diagnostics.playbackError(PlaybackEnginePath.SampledClip, e.message)
            null
        }
    }

    private fun applyEqualizerToPcmStream(stream: AudioInputStream): AudioInputStream {
        val profile = equalizerProfile.normalized()
        if (!GraphicEqualizerProcessor.isActive(profile)) return stream
        val format = stream.format
        if (format.sampleSizeInBits != 16 ||
            format.frameSize <= 0 ||
            format.channels <= 0 ||
            format.sampleRate <= 0f ||
            (format.encoding != AudioFormat.Encoding.PCM_SIGNED && format.encoding != AudioFormat.Encoding.PCM_UNSIGNED)
        ) {
            return stream
        }
        val input = stream.use { it.readAllBytes() }
        val output = input.copyOf()
        val processor = GraphicEqualizerProcessor(
            sampleRateHz = format.sampleRate,
            channelCount = format.channels,
            profile = profile,
        )
        var offset = 0
        while (offset + format.frameSize <= output.size) {
            repeat(format.channels) { channel ->
                val sampleOffset = offset + channel * 2
                val sample = readPcm16(output, sampleOffset, format.isBigEndian, format.encoding) / 32768f
                val processed = (processor.process(channel, sample) * 32767f)
                    .toInt()
                    .coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt())
                writePcm16(output, sampleOffset, processed, format.isBigEndian, format.encoding)
            }
            offset += format.frameSize
        }
        val frames = output.size.toLong() / format.frameSize.toLong()
        return AudioInputStream(ByteArrayInputStream(output), format, frames)
    }

    private fun downloadRemoteAudio(uri: String, extension: String): File {
        val request = HttpRequest.newBuilder(URI(uri))
            .GET()
            .timeout(Duration.ofSeconds(45))
            .header("User-Agent", "Phoebe/0.1.0 (https://github.com/phoebe)")
            .header("X-Plex-Client-Identifier", PlexClient.ClientIdentifier)
            .build()
        val response = httpClient.send(request, HttpResponse.BodyHandlers.ofInputStream())
        if (response.statusCode() !in 200..299) {
            response.body().close()
            error("Plex stream request failed (${response.statusCode()})")
        }
        val contentType = response.headers().firstValue("content-type").orElse("").lowercase()
        if (contentType.startsWith("text/") ||
            contentType.contains("html") ||
            contentType.contains("json") ||
            contentType.contains("xml")
        ) {
            response.body().close()
            error("Plex stream returned $contentType instead of audio")
        }
        val resolvedExtension = extensionFromContentType(contentType)
            ?: sampledPlaybackExtensionFromSuffix(extension)
            ?: extension.takeIf { it.isNotBlank() && it != "bin" }
            ?: "mp3"
        val suffix = ".$resolvedExtension"
        val temp = Files.createTempFile("phoebe-plex-stream-", suffix).toFile()
        temp.deleteOnExit()
        try {
            response.body().use { input ->
                Files.copy(input, temp.toPath(), StandardCopyOption.REPLACE_EXISTING)
            }
        } catch (e: Throwable) {
            temp.delete()
            throw e
        }
        return temp
    }

    private fun disposeSampled() {
        runCatching { sampledStream?.stop() }
        sampledStream = null
        runCatching {
            sampledClip?.stop()
            sampledClip?.close()
        }
        sampledClip = null
        remoteSampledFile?.let { temp ->
            runCatching { temp.delete() }
        }
        remoteSampledFile = null
        fullyBufferedPlayback = false
    }

    private fun disposeJavaFxBlocking() {
        if (player == null && fadingOutPlayer == null) return
        val latch = CountDownLatch(1)
        JavaFxRuntime.runLater {
            runCatching {
                player?.stop()
                player?.dispose()
            }
            player = null
            runCatching {
                fadingOutPlayer?.stop()
                fadingOutPlayer?.dispose()
            }
            fadingOutPlayer = null
            latch.countDown()
        }
        latch.await(30, TimeUnit.SECONDS)
    }

    private fun disposeAllOnPlaybackThread() {
        disposeJavaFxBlocking()
        disposeSampled()
        clearCrossfadePrefetch()
    }

    private fun playJavaFxSync(uri: String, generation: Int): Boolean {
        val latch = CountDownLatch(1)
        val failed = AtomicBoolean(false)
        JavaFxRuntime.runLater {
            runCatching {
                diagnostics.engineSelected(PlaybackEnginePath.JavaFxMediaPlayer)
                val media = Media(uri)
                val mediaPlayer = MediaPlayer(media)
                mediaPlayer.volume = effectiveOutputVolume().toDouble().coerceIn(0.0, 1.0)
                applyJavaFxEqualizer(mediaPlayer, equalizerProfile)
                mediaPlayer.audioSpectrumInterval = 0.05
                mediaPlayer.audioSpectrumNumBands = 64
                mediaPlayer.audioSpectrumThreshold = -80
                mediaPlayer.audioSpectrumListener = AudioSpectrumListener { _, _, magnitudes, _ ->
                    val maxMagnitude = magnitudes.maxOrNull() ?: return@AudioSpectrumListener
                    val rms = Math.pow(10.0, maxMagnitude.toDouble() / 20.0)
                    if (rms.isFinite() && rms > 0.0) {
                        diagnostics.decodedAudioEnergy(PlaybackEnginePath.JavaFxMediaPlayer, rms)
                    }
                }
                mediaPlayer.setOnError {
                    failed.set(true)
                    PhoebeLog.d("DesktopAudioPlayer") {
                        "playback error: ${mediaPlayer.error?.message ?: mediaPlayer.error?.type}"
                    }
                    diagnostics.playbackError(PlaybackEnginePath.JavaFxMediaPlayer, mediaPlayer.error?.message)
                    if (latch.count > 0L) latch.countDown()
                }
                mediaPlayer.setOnEndOfMedia {
                    if (isPlayRequestCurrent(generation)) {
                        next()
                    }
                }
                media.setOnError {
                    failed.set(true)
                    PhoebeLog.d("DesktopAudioPlayer") { "media error: ${media.error?.message}" }
                    diagnostics.playbackError(PlaybackEnginePath.JavaFxMediaPlayer, media.error?.message)
                    if (latch.count > 0L) latch.countDown()
                }
                mediaPlayer.setOnPlaying {
                    syncJavaFxPlayback(mediaPlayer, generation, isBuffering = false)
                    if (latch.count > 0L) latch.countDown()
                }
                mediaPlayer.setOnStalled {
                    syncJavaFxPlayback(mediaPlayer, generation, isBuffering = true)
                }
                mediaPlayer.setOnReady {
                    syncJavaFxPlayback(mediaPlayer, generation, isBuffering = false)
                    mediaPlayer.bufferProgressTimeProperty().addListener { _, _, _ ->
                        syncJavaFxPlayback(mediaPlayer, generation, isBuffering = false)
                    }
                    mediaPlayer.currentTimeProperty().addListener { _, _, _ ->
                        syncJavaFxPlayback(mediaPlayer, generation, isBuffering = false)
                    }
                    if (playWhenReady && isPlayRequestCurrent(generation)) {
                        mediaPlayer.play()
                    }
                }
                player = mediaPlayer
                if (playWhenReady && isPlayRequestCurrent(generation)) {
                    mediaPlayer.play()
                }
            }.onFailure { error ->
                failed.set(true)
                logPlaybackFailure(error)
                diagnostics.playbackError(PlaybackEnginePath.JavaFxMediaPlayer, error.message)
                if (latch.count > 0L) latch.countDown()
            }
        }
        val signaled = latch.await(JavaFxStartupTimeoutSeconds, TimeUnit.SECONDS)
        return signaled && !failed.get() && isPlayRequestCurrent(generation)
    }

    private fun playJavaFxWithRetries(uri: String, generation: Int): Boolean {
        var attempt = 0
        while (isPlayRequestCurrent(generation)) {
            if (playJavaFxSync(uri, generation)) return true
            if (!isRemoteUri(uri) || attempt >= MaxStreamRetryCount) return false
            disposeJavaFxBlocking()
            attempt++
            val current = state.value
            applyPlatformPlayback(
                positionMs = current.positionMs,
                durationMs = current.durationMs,
                isPlaying = false,
                isBuffering = true,
                bufferedPositionMs = current.bufferedPositionMs,
                generation = generation,
            )
            Thread.sleep(StreamRetryBaseDelayMs * attempt)
        }
        return false
    }

    private fun isRemoteUri(uri: String): Boolean =
        DesktopPlaybackStartupPolicy.isRemoteUri(uri)

    private fun syncJavaFxPlayback(mediaPlayer: MediaPlayer, generation: Int, isBuffering: Boolean) {
        if (!isPlayRequestCurrent(generation)) return
        val positionMs = mediaPlayer.currentTime.toMillis().toLong().coerceAtLeast(0L)
        val durationMs = mediaPlayer.media.duration.toMillis().toLong().coerceAtLeast(0L)
        val platformBufferedMs = mediaPlayer.bufferProgressTime.toMillis().toLong().coerceAtLeast(positionMs)
        val bufferedMs = if (fullyBufferedPlayback && durationMs > 0L) durationMs else platformBufferedMs
        val playing = mediaPlayer.status == MediaPlayer.Status.PLAYING
        val current = state.value
        val nowMs = System.currentTimeMillis()
        val playbackFlagsChanged = playing != current.isPlaying || isBuffering != current.isBuffering
        val bufferedAdvanced = bufferedMs > current.bufferedPositionMs + 500L
        if (!playbackFlagsChanged &&
            !bufferedAdvanced &&
            nowMs - lastPlaybackUiSyncAtMs < PlaybackUiSyncIntervalMs &&
            kotlin.math.abs(positionMs - current.positionMs) < PlaybackUiSyncIntervalMs
        ) {
            return
        }
        lastPlaybackUiSyncAtMs = nowMs
        diagnostics.playbackProgress(PlaybackEnginePath.JavaFxMediaPlayer, positionMs, durationMs)
        if (mediaPlayer.status == MediaPlayer.Status.PLAYING) {
            diagnostics.platformPlaying(PlaybackEnginePath.JavaFxMediaPlayer, positionMs, durationMs)
        }
        applyPlatformPlayback(
            positionMs = positionMs,
            durationMs = durationMs,
            isPlaying = playing,
            isBuffering = isBuffering,
            bufferedPositionMs = bufferedMs,
            generation = generation,
        )
    }

    private fun trackDurationOrClipDuration(generation: Int, clip: Clip): Long {
        val stateDuration = state.value.durationMs.takeIf { it > 0L }
        if (stateDuration != null && isPlayRequestCurrent(generation)) return stateDuration
        return (clip.microsecondLength / 1_000L).coerceAtLeast(0L)
    }

    private fun startSampledProgressProbe(clip: Clip, generation: Int) {
        Thread({
            while (isPlayRequestCurrent(generation) && sampledClip === clip && clip.isOpen) {
                val positionMs = (clip.microsecondPosition / 1_000L).coerceAtLeast(0L)
                val durationMs = (clip.microsecondLength / 1_000L).coerceAtLeast(0L)
                diagnostics.playbackProgress(PlaybackEnginePath.SampledClip, positionMs, durationMs)
                if (clip.isActive || clip.isRunning) {
                    diagnostics.platformPlaying(PlaybackEnginePath.SampledClip, positionMs, durationMs)
                }
                Thread.sleep(250L)
            }
        }, "Phoebe-sampled-playback-diagnostics").apply {
            isDaemon = true
            start()
        }
    }

    private fun copyPcmStreamForDiagnostics(stream: AudioInputStream): AudioInputStream {
        val format = stream.format
        val bytes = stream.use { it.readAllBytes() }
        val rms = pcmRms(bytes, format)
        if (rms > 0.0 && rms.isFinite()) {
            diagnostics.decodedAudioEnergy(PlaybackEnginePath.SampledClip, rms)
        }
        val frameSize = format.frameSize.takeIf { it > 0 } ?: 1
        return AudioInputStream(ByteArrayInputStream(bytes), format, bytes.size.toLong() / frameSize.toLong())
    }

    internal fun releaseForTests() {
        runCatching {
            playbackExecutor.submit {
                disposeAllOnPlaybackThread()
            }.get(30, TimeUnit.SECONDS)
        }
        playbackExecutor.shutdownNow()
        crossfadePrefetchExecutor.shutdownNow()
    }

    private fun extensionFromContentType(contentType: String): String? = when {
        contentType.contains("flac") -> "flac"
        contentType.contains("mpeg") || contentType.contains("mp3") -> "mp3"
        contentType.contains("mp4") || contentType.contains("m4a") || contentType.contains("aac") -> "m4a"
        contentType.contains("ogg") || contentType.contains("vorbis") -> "ogg"
        contentType.contains("opus") -> "opus"
        contentType.contains("wav") -> "wav"
        else -> null
    }

    private fun logPlaybackFailure(error: Throwable) {
        val message = error.message ?: error::class.simpleName.orEmpty()
        PhoebeLog.d("DesktopAudioPlayer") { "playback error: $message" }
    }

    private companion object {
        const val JavaFxEqualizerBandMatchTolerance = 0.045f
        const val MaxStreamRetryCount = 2
        const val StreamRetryBaseDelayMs = 1_000L
        const val JavaFxStartupTimeoutSeconds = 15L
        const val PlaybackUiSyncIntervalMs = 250L
        const val RemoteAudioProbeBufferBytes = 128 * 1024
        const val StreamingPcmBufferBytes = 16 * 1024
        const val MaxStreamingLineBufferBytes = 96 * 1024
    }
}

private fun pcmRms(bytes: ByteArray, format: AudioFormat): Double {
    val frameSize = format.frameSize.takeIf { it > 0 } ?: return 0.0
    val channels = format.channels.takeIf { it > 0 } ?: return 0.0
    val sampleBytes = ((format.sampleSizeInBits + 7) / 8).takeIf { it in 1..4 } ?: return 0.0
    var sumSquares = 0.0
    var sampleCount = 0L
    var frameOffset = 0
    while (frameOffset + frameSize <= bytes.size) {
        var sampleOffset = frameOffset
        repeat(channels) {
            if (sampleOffset + sampleBytes <= frameOffset + frameSize) {
                val normalized = normalizedPcmSample(bytes, sampleOffset, sampleBytes, format)
                sumSquares += normalized * normalized
                sampleCount++
            }
            sampleOffset += sampleBytes
        }
        frameOffset += frameSize
    }
    return if (sampleCount == 0L) 0.0 else Math.sqrt(sumSquares / sampleCount.toDouble())
}

private fun readPcm16(
    bytes: ByteArray,
    offset: Int,
    bigEndian: Boolean,
    encoding: AudioFormat.Encoding,
): Int {
    val b0 = bytes[offset].toInt() and 0xFF
    val b1 = bytes[offset + 1].toInt() and 0xFF
    val raw = if (bigEndian) (b0 shl 8) or b1 else b0 or (b1 shl 8)
    if (encoding == AudioFormat.Encoding.PCM_UNSIGNED) return raw - 32768
    return (raw shl 16) shr 16
}

private fun writePcm16(
    bytes: ByteArray,
    offset: Int,
    signedValue: Int,
    bigEndian: Boolean,
    encoding: AudioFormat.Encoding,
) {
    val raw = if (encoding == AudioFormat.Encoding.PCM_UNSIGNED) {
        (signedValue + 32768).coerceIn(0, 65535)
    } else {
        signedValue and 0xFFFF
    }
    if (bigEndian) {
        bytes[offset] = ((raw shr 8) and 0xFF).toByte()
        bytes[offset + 1] = (raw and 0xFF).toByte()
    } else {
        bytes[offset] = (raw and 0xFF).toByte()
        bytes[offset + 1] = ((raw shr 8) and 0xFF).toByte()
    }
}

private fun normalizedPcmSample(bytes: ByteArray, offset: Int, sampleBytes: Int, format: AudioFormat): Double {
    val unsigned = format.encoding == AudioFormat.Encoding.PCM_UNSIGNED
    val littleEndian = !format.isBigEndian
    var value = 0L
    repeat(sampleBytes) { index ->
        val sourceIndex = if (littleEndian) offset + index else offset + sampleBytes - 1 - index
        value = value or ((bytes[sourceIndex].toLong() and 0xFFL) shl (8 * index))
    }
    if (unsigned) {
        val midpoint = 1L shl (sampleBytes * 8 - 1)
        return (value - midpoint).toDouble() / midpoint.toDouble()
    }
    val shift = 64 - sampleBytes * 8
    val signed = (value shl shift) shr shift
    val denominator = (1L shl (sampleBytes * 8 - 1)).toDouble()
    return signed.toDouble() / denominator
}

internal object DesktopPlaybackStartupPolicy {
    fun isRemoteUri(uri: String): Boolean =
        uri.startsWith("http://", ignoreCase = true) || uri.startsWith("https://", ignoreCase = true)

    fun sampledPlaybackExtensionFromUri(uri: String): String? {
        val path = runCatching { URI(uri).path }.getOrNull()
            ?: uri.substringBefore('?').substringBefore('#')
        return sampledPlaybackExtensionFromSuffix(path.substringAfterLast('.', missingDelimiterValue = ""))
    }

    fun streamingSampledExtensionFromUri(uri: String): String? {
        val path = runCatching { URI(uri).path }.getOrNull()
            ?: uri.substringBefore('?').substringBefore('#')
        return streamingSampledExtensionFromSuffix(path.substringAfterLast('.', missingDelimiterValue = ""))
    }

    fun sampledPlaybackExtensionFromSuffix(extension: String): String? {
        return when (extension.lowercase()) {
            // Local MP3/M4A: JavaFX decodes reliably; mp3spi + Clip can mis-handle some MP3s (noise/static).
            "wav", "wave", "aif", "aiff", "flac", "ogg", "opus" -> extension.lowercase()
            else -> null
        }
    }

    fun streamingSampledExtensionFromSuffix(extension: String): String? {
        return when (extension.lowercase()) {
            "mp3", "mpeg", "mpga" -> "mp3"
            "wav", "wave" -> "wav"
            "aif", "aiff" -> "aiff"
            "flac", "ogg", "opus" -> extension.lowercase()
            else -> null
        }
    }

    fun shouldEagerlyBufferRemotePlayback(uri: String, preferredSampledExtension: String?): Boolean {
        if (!isRemoteUri(uri)) return false
        return (preferredSampledExtension ?: sampledPlaybackExtensionFromUri(uri)) != null
    }

    fun shouldPrefetchRemoteForCrossfade(uri: String): Boolean = isRemoteUri(uri)
}

private object JavaFxRuntime {
    private val started = AtomicBoolean(false)
    private val ready = CompletableFuture<Unit>()

    fun warmUp() {
        start()
    }

    fun runLater(block: () -> Unit) {
        start()
        ready.whenComplete { _, error ->
            if (error != null) {
                PhoebeLog.d("DesktopAudioPlayer") { "playback error: ${error.message ?: error::class.simpleName.orEmpty()}" }
                return@whenComplete
            }
            Platform.runLater(block)
        }
    }

    private fun start() {
        if (started.compareAndSet(false, true)) {
            Thread({
                runCatching {
                    Platform.startup {
                        ready.complete(Unit)
                    }
                }.onFailure { error ->
                    if (error is IllegalStateException) {
                        ready.complete(Unit)
                    } else {
                        ready.completeExceptionally(error)
                    }
                }
            }, "Phoebe-JavaFX-Startup").apply {
                isDaemon = true
                start()
            }
        }
    }
}
