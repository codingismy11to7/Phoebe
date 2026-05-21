package com.phoebe.app.player

import kotlinx.browser.document
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.w3c.dom.HTMLAudioElement

actual fun createAudioPlayer(): AudioPlayer = WebAudioPlayer()

@OptIn(ExperimentalWasmJsInterop::class)
private class WebAudioPlayer : SimpleAudioPlayer() {
    override val useProgressTicker: Boolean = false

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private var currentUri: String? = null
    private var retryJob: Job? = null
    private var retryGeneration = -1
    private var retryCount = 0

    private val audio = (document.createElement("audio") as HTMLAudioElement).apply {
        preload = "auto"
    }

    override fun stopCurrentPlaybackImmediately() {
        retryJob?.cancel()
        audio.pause()
    }

    override fun playUri(uri: String) {
        if (uri.isBlank()) {
            markPlaybackFailed()
            return
        }
        val generation = activePlayGeneration
        currentUri = uri
        retryGeneration = generation
        retryCount = 0
        retryJob?.cancel()
        audio.volume = effectiveOutputVolume().toDouble().coerceIn(0.0, 1.0)
        audio.currentTime = 0.0
        audio.src = uri
        audio.load()
        audio.onplaying = {
            retryCount = 0
            syncFromAudio(generation, isBuffering = false)
            markPlaybackReady(generation = generation)
        }
        audio.onwaiting = {
            syncFromAudio(generation, isBuffering = true)
        }
        audio.onstalled = {
            syncFromAudio(generation, isBuffering = true)
            scheduleRetry(generation, reload = false)
        }
        audio.oncanplay = {
            syncFromAudio(generation, isBuffering = audio.paused && playWhenReady)
        }
        audio.ontimeupdate = {
            syncFromAudio(generation, isBuffering = false)
        }
        audio.onended = {
            if (isPlayRequestCurrent(generation)) {
                next()
            }
        }
        audio.onerror = { _, _, _, _, _ ->
            scheduleRetry(generation, reload = true)
            null
        }
        if (playWhenReady) {
            audio.play()
        }
    }

    override fun pause() {
        retryJob?.cancel()
        audio.pause()
    }

    override fun resume() {
        audio.play()
    }

    override fun seek(positionMs: Long) {
        audio.currentTime = positionMs / 1000.0
    }

    override fun setOutputVolume(volume: Float) {
        audio.volume = volume.toDouble().coerceIn(0.0, 1.0)
    }

    private fun syncFromAudio(generation: Int, isBuffering: Boolean) {
        if (!isPlayRequestCurrent(generation)) return
        val durationMs = if (audio.duration.isFinite() && audio.duration > 0.0) {
            (audio.duration * 1000.0).toLong()
        } else {
            state.value.durationMs
        }
        val positionMs = (audio.currentTime * 1000.0).toLong().coerceAtLeast(0L)
        applyPlatformPlayback(
            positionMs = positionMs,
            durationMs = durationMs,
            isPlaying = !audio.paused && !isBuffering,
            isBuffering = isBuffering,
            bufferedPositionMs = bufferedPositionMs(positionMs),
            generation = generation,
        )
    }

    private fun bufferedPositionMs(positionMs: Long): Long {
        var bufferedMs = positionMs
        val ranges = audio.buffered
        for (index in 0 until ranges.length) {
            val start = ranges.start(index) * 1000.0
            val end = ranges.end(index) * 1000.0
            if (positionMs.toDouble() + 250.0 >= start) {
                bufferedMs = maxOf(bufferedMs, end.toLong())
            }
        }
        return bufferedMs
    }

    private fun scheduleRetry(generation: Int, reload: Boolean) {
        if (!isPlayRequestCurrent(generation) || !playWhenReady) return
        if (retryGeneration != generation) {
            retryGeneration = generation
            retryCount = 0
        }
        if (retryCount >= MaxStreamRetryCount) {
            markPlaybackFailed(generation)
            return
        }
        retryCount++
        val positionSeconds = audio.currentTime
        syncFromAudio(generation, isBuffering = true)
        retryJob?.cancel()
        retryJob = scope.launch {
            delay(StreamRetryBaseDelayMs * retryCount)
            if (!isPlayRequestCurrent(generation) || !playWhenReady) return@launch
            if (reload) {
                val uri = currentUri ?: return@launch
                audio.src = uri
                audio.load()
                audio.currentTime = positionSeconds
            }
            audio.play()
        }
    }

    private companion object {
        const val MaxStreamRetryCount = 5
        const val StreamRetryBaseDelayMs = 1_000L
    }
}
