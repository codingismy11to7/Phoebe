package com.phoebe.app.player

import javazoom.spi.vorbis.sampled.file.VorbisAudioFileReader
import com.phoebe.app.domain.Track
import com.phoebe.app.platform.PhoebeLog
import javafx.application.Platform
import javafx.scene.media.Media
import javafx.scene.media.MediaPlayer
import java.io.ByteArrayInputStream
import java.io.File
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.nio.file.Files
import java.nio.file.StandardCopyOption
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
import javax.sound.sampled.FloatControl
import org.jflac.sound.spi.FlacAudioFileReader

actual fun createAudioPlayer(): AudioPlayer = DesktopAudioPlayer()

/**
 * Desktop playback uses JavaFX [MediaPlayer] for streams and formats it handles well (**MP3**, **M4A**, etc.).
 * JavaFX does **not** decode FLAC, Ogg Vorbis, or many WAV variants reliably, so local **WAV / AIFF / FLAC / Ogg / Opus**
 * use [javax.sound.sampled] plus mp3spi / vorbisspi / jflac on the classpath (MP3 SPI is kept for non-JavaFX paths if needed).
 */
private class DesktopAudioPlayer : SimpleAudioPlayer() {
    private var player: MediaPlayer? = null
    private var sampledClip: Clip? = null
    private var remoteSampledFile: File? = null
    private val httpClient = HttpClient.newBuilder()
        .followRedirects(HttpClient.Redirect.NORMAL)
        .build()
    private val playbackExecutor = Executors.newSingleThreadExecutor { r ->
        Thread(r, "Phoebe-desktop-playback").apply { isDaemon = true }
    }

    override fun playUri(uri: String) {
        playUri(uri, preferredSampledExtension = null)
    }

    override fun playTrack(track: Track) {
        val uri = track.localUri ?: track.streamUrl
        playUri(uri, preferredSampledExtension = sampledPlaybackExtensionFromTrack(track))
    }

    override fun stopCurrentPlaybackImmediately() {
        runCatching { sampledClip?.stop() }
        JavaFxRuntime.runLater {
            runCatching { player?.pause() }
        }
    }

    private fun playUri(uri: String, preferredSampledExtension: String?) {
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
                val file = uriToLocalFile(uri)
                if (file != null && preferSampledPlayback(file)) {
                    val clip = openAndStartSampledClip(file)
                    if (clip != null) {
                        if (!isPlayRequestCurrent(generation)) {
                            runCatching { clip.stop(); clip.close() }
                            return@execute
                        }
                        sampledClip = clip
                        applyVolumesFromState()
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
                        applyVolumesFromState()
                        markPlaybackReady(generation = generation)
                        return@execute
                    }
                    disposeSampled()
                }
                if (!isPlayRequestCurrent(generation)) return@execute
                playJavaFxSync(uri)
                if (!isPlayRequestCurrent(generation)) return@execute
                applyVolumesFromState()
                markPlaybackReady(generation = generation)
            }.onFailure { error ->
                if (!isPlayRequestCurrent(generation)) return@execute
                logPlaybackFailure(error)
                markPlaybackFailed(generation = generation)
            }
        }
    }

    override fun pause() {
        playbackExecutor.execute {
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

    private fun applyVolumesFromState() {
        val v = effectiveOutputVolume()
        JavaFxRuntime.runLater { player?.volume = v.toDouble() }
        applySampledVolume(v)
    }

    private fun applySampledVolume(volume: Float) {
        val clip = sampledClip ?: return
        applyVolumeToClip(clip, volume)
    }

    private fun applyVolumeToClip(clip: Clip, volume: Float) {
        val v = volume.coerceIn(0f, 1f)
        runCatching {
            val control = clip.getControl(FloatControl.Type.MASTER_GAIN) as FloatControl
            val min = control.minimum
            val max = control.maximum
            control.value = min + (max - min) * v
        }.onFailure {
            runCatching {
                val control = clip.getControl(FloatControl.Type.VOLUME) as FloatControl
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

    private fun sampledPlaybackExtensionFromUri(uri: String): String? {
        val path = runCatching { URI(uri).path }.getOrNull()
            ?: uri.substringBefore('?').substringBefore('#')
        return sampledPlaybackExtensionFromSuffix(path.substringAfterLast('.', missingDelimiterValue = ""))
    }

    private fun sampledPlaybackExtensionFromTrack(track: Track): String? {
        sampledPlaybackExtensionFromSuffix(track.audioCodec.orEmpty())?.let { return it }
        sampledPlaybackExtensionFromUri(track.localUri ?: track.streamUrl)?.let { return it }
        return sampledPlaybackExtensionFromSuffix(track.filepath.orEmpty().substringAfterLast('.', missingDelimiterValue = ""))
    }

    private fun sampledPlaybackExtensionFromSuffix(extension: String): String? {
        return when (extension.lowercase()) {
            // Local MP3/M4A: JavaFX decodes reliably; mp3spi + Clip can mis-handle some MP3s (noise/static).
            "wav", "wave", "aif", "aiff", "flac", "ogg", "opus" -> extension.lowercase()
            else -> null
        }
    }

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
        return runCatching {
            val clip = AudioSystem.getClip()
            try {
                clip.open(prepared)
            } catch (e: Throwable) {
                runCatching { prepared.close() }
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
            null
        }
    }

    private fun downloadRemoteAudio(uri: String, extension: String): File {
        val request = HttpRequest.newBuilder(URI(uri)).GET().build()
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
        val suffix = ".$extension"
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
        runCatching {
            sampledClip?.stop()
            sampledClip?.close()
        }
        sampledClip = null
        remoteSampledFile?.let { temp ->
            runCatching { temp.delete() }
        }
        remoteSampledFile = null
    }

    private fun disposeJavaFxBlocking() {
        val latch = CountDownLatch(1)
        JavaFxRuntime.runLater {
            runCatching {
                player?.stop()
                player?.dispose()
            }
            player = null
            latch.countDown()
        }
        latch.await(30, TimeUnit.SECONDS)
    }

    private fun disposeAllOnPlaybackThread() {
        disposeSampled()
        disposeJavaFxBlocking()
    }

    private fun playJavaFxSync(uri: String) {
        val latch = CountDownLatch(1)
        var err: Throwable? = null
        JavaFxRuntime.runLater {
            runCatching {
                val media = Media(uri)
                player = MediaPlayer(media).also { mediaPlayer ->
                    mediaPlayer.volume = effectiveOutputVolume().toDouble().coerceIn(0.0, 1.0)
                    mediaPlayer.setOnError {
                        PhoebeLog.d("DesktopAudioPlayer") { "playback error: ${mediaPlayer.error?.message}" }
                    }
                    val generation = activePlayGeneration
                    mediaPlayer.setOnEndOfMedia {
                        if (isPlayRequestCurrent(generation)) {
                            next()
                        }
                    }
                    media.setOnError {
                        PhoebeLog.d("DesktopAudioPlayer") { "media error: ${media.error?.message}" }
                    }
                    mediaPlayer.play()
                }
            }.onFailure { err = it }
            latch.countDown()
        }
        latch.await(30, TimeUnit.SECONDS)
        err?.let(::logPlaybackFailure)
    }

    private fun logPlaybackFailure(error: Throwable) {
        val message = error.message ?: error::class.simpleName.orEmpty()
        PhoebeLog.d("DesktopAudioPlayer") { "playback error: $message" }
    }
}

private object JavaFxRuntime {
    private val started = AtomicBoolean(false)
    private val ready = CompletableFuture<Unit>()

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
