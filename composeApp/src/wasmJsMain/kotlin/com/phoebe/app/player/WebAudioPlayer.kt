package com.phoebe.app.player

import com.phoebe.app.domain.EqualizerProfile
import com.phoebe.app.sources.resolveWebLocalAudioUri
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
    private var sourcePreparedForWebEqualizer = false
    private var pendingSeekAfterLoadSeconds: Double? = null
    private var pendingPlayAfterLoad = false
    private var audioUsesCors = true
    private var corsFallbackAttempted = false
    private var equalizerUnavailableForCurrentStream = false
    private var equalizerUnavailableNoticeShown = false
    private var prefetchGeneration = -1
    private var prefetchStartedGeneration = -1
    private var prefetchedBufferFraction = 0.0

    private var audio = createAudioElement(useCors = true)

    override fun stopCurrentPlaybackImmediately() {
        retryJob?.cancel()
        cancelWebAudioPrefetch()
        resetWebAudioPrefetch()
        clearPendingReloadRestore()
        audio.pause()
    }

    override fun playUri(uri: String) {
        if (uri.isBlank()) {
            markPlaybackFailed()
            return
        }
        val playbackUri = resolveWebLocalAudioUri(uri)
        val generation = activePlayGeneration
        currentUri = playbackUri
        retryGeneration = generation
        retryCount = 0
        corsFallbackAttempted = false
        equalizerUnavailableForCurrentStream = false
        equalizerUnavailableNoticeShown = false
        retryJob?.cancel()
        cancelWebAudioPrefetch()
        resetWebAudioPrefetch(generation)
        if (!audioUsesCors) {
            val previousAudio = audio
            audio = createAudioElement(useCors = true)
            audioUsesCors = true
            sourcePreparedForWebEqualizer = false
            disposeWebAudioElement(previousAudio)
        }
        audio.volume = effectiveOutputVolume().toDouble().coerceIn(0.0, 1.0)
        prepareAudioElementForCurrentEqualizer()
        setWebAudioCurrentTime(audio, 0.0)
        installAudioEventHandlers(generation)
        audio.src = playbackUri
        audio.load()
        applyCurrentEqualizer()
        if (playWhenReady) {
            playWebAudio(audio)
        }
    }

    override fun pause() {
        retryJob?.cancel()
        pendingPlayAfterLoad = false
        audio.pause()
    }

    override fun resume() {
        playWebAudio(audio)
    }

    override fun seek(positionMs: Long) {
        setWebAudioCurrentTime(audio, positionMs / 1000.0)
    }

    override fun setOutputVolume(volume: Float) {
        audio.volume = volume.toDouble().coerceIn(0.0, 1.0)
    }

    override fun applyEqualizer(profile: EqualizerProfile) {
        val normalized = profile.normalized()
        if (normalized.enabled && !sourcePreparedForWebEqualizer) {
            prepareAudioElementForCurrentEqualizer()
        }
        applyCurrentEqualizer()
    }

    private fun prepareAudioElementForCurrentEqualizer() {
        val enabled = equalizerProfile.normalized().enabled
        prepareWebEqualizerAudio(audio, enabled && audioUsesCors)
        sourcePreparedForWebEqualizer = (enabled && audioUsesCors) || isWebEqualizerAttached(audio)
    }

    private fun applyCurrentEqualizer() {
        val normalized = equalizerProfile.normalized()
        val effectiveProfile = if (GraphicEqualizerProcessor.isActive(normalized) && !sourcePreparedForWebEqualizer) {
            surfaceEqualizerUnavailableNoticeIfNeeded()
            normalized.copy(enabled = false)
        } else {
            normalized
        }
        applyWebEqualizer(audio, webEqualizerPayload(effectiveProfile))
    }

    private fun installAudioEventHandlers(generation: Int) {
        audio.onloadedmetadata = {
            restorePendingReloadPosition(generation)
            syncFromAudio(generation, isBuffering = audio.paused && playWhenReady)
        }
        audio.onloadeddata = {
            syncFromAudio(generation, isBuffering = audio.paused && playWhenReady)
        }
        audio.ondurationchange = {
            syncFromAudio(generation, isBuffering = audio.paused && playWhenReady)
        }
        audio.onplaying = {
            retryCount = 0
            startWebAudioPrefetchIfNeeded(currentUri, generation)
            syncFromAudio(generation, isBuffering = false)
            markPlaybackReady(generation = generation)
        }
        audio.onpause = {
            if (isPlayRequestCurrent(generation)) {
                syncFromAudio(generation, isBuffering = false)
            }
        }
        audio.onwaiting = {
            syncFromAudio(generation, isBuffering = true)
        }
        audio.onstalled = {
            syncFromAudio(generation, isBuffering = true)
            scheduleRetry(generation, reload = false)
        }
        audio.oncanplay = {
            restorePendingReloadPosition(generation)
            syncFromAudio(generation, isBuffering = audio.paused && playWhenReady)
        }
        audio.oncanplaythrough = {
            syncFromAudio(generation, isBuffering = audio.paused && playWhenReady)
        }
        installWebAudioProgressHandler(audio) {
            syncFromAudio(generation, isBuffering = audio.paused && playWhenReady)
        }
        audio.onsuspend = {
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
            clearPendingReloadRestore()
            if (!retryWithoutCors(generation)) {
                scheduleRetry(generation, reload = true)
            }
            null
        }
    }

    private fun restorePendingReloadPosition(generation: Int) {
        val positionSeconds = pendingSeekAfterLoadSeconds ?: return
        if (!isPlayRequestCurrent(generation)) {
            clearPendingReloadRestore()
            return
        }
        val boundedPositionSeconds = if (audio.duration.isFinite() && audio.duration > 0.0) {
            positionSeconds.coerceIn(0.0, audio.duration)
        } else {
            positionSeconds.coerceAtLeast(0.0)
        }
        if (!setWebAudioCurrentTime(audio, boundedPositionSeconds)) return
        pendingSeekAfterLoadSeconds = null
        if (pendingPlayAfterLoad && playWhenReady) {
            pendingPlayAfterLoad = false
            playWebAudio(audio)
        } else {
            pendingPlayAfterLoad = false
        }
    }

    private fun clearPendingReloadRestore() {
        pendingSeekAfterLoadSeconds = null
        pendingPlayAfterLoad = false
    }

    private fun retryWithoutCors(generation: Int): Boolean {
        val uri = currentUri ?: return false
        if (!audioUsesCors || corsFallbackAttempted || !isPlayRequestCurrent(generation)) return false
        corsFallbackAttempted = true
        val previousAudio = audio
        val positionSeconds = previousAudio.currentTime.takeIf { it.isFinite() && it > 0.0 } ?: 0.0
        audio = createAudioElement(useCors = false)
        audioUsesCors = false
        cancelWebAudioPrefetch()
        resetWebAudioPrefetch()
        sourcePreparedForWebEqualizer = false
        equalizerUnavailableForCurrentStream = true
        equalizerUnavailableNoticeShown = false
        audio.volume = previousAudio.volume
        installAudioEventHandlers(generation)
        if (positionSeconds > 0.0) {
            pendingSeekAfterLoadSeconds = positionSeconds
            pendingPlayAfterLoad = playWhenReady
        }
        audio.src = uri
        audio.load()
        applyCurrentEqualizer()
        disposeWebAudioElement(previousAudio)
        if (playWhenReady) {
            playWebAudio(audio)
        }
        return true
    }

    private fun surfaceEqualizerUnavailableNoticeIfNeeded(generation: Int = activePlayGeneration) {
        if (!equalizerUnavailableForCurrentStream || equalizerUnavailableNoticeShown) return
        if (!GraphicEqualizerProcessor.isActive(equalizerProfile)) return
        equalizerUnavailableNoticeShown = true
        val title = state.value.currentTrack?.title?.takeIf { it.isNotBlank() } ?: "this song"
        surfacePlaybackNotice(
            generation = generation,
            message = "Equalizer isn't available for $title in the browser because its stream blocks WebAudio access. Playback continues without EQ.",
        )
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
            bufferedPositionMs = bufferedPositionMs(positionMs, durationMs),
            generation = generation,
        )
    }

    private fun bufferedPositionMs(positionMs: Long, durationMs: Long): Long =
        webAudioBufferedPositionMs(
            positionMs = positionMs,
            durationMs = durationMs,
            bufferedRanges = audio.buffered.toWebAudioTimeRanges(),
            prefetchedPositionMs = prefetchedBufferedPositionMs(durationMs),
        )

    private fun startWebAudioPrefetch(uri: String, generation: Int) {
        if (!uri.startsWith("http://", ignoreCase = true) && !uri.startsWith("https://", ignoreCase = true)) return
        startWebAudioPrefetch(
            uri,
            { loadedBytes, totalBytes -> handleWebAudioPrefetchProgress(generation, loadedBytes, totalBytes) },
            { completed -> handleWebAudioPrefetchComplete(generation, completed) },
        )
    }

    private fun startWebAudioPrefetchIfNeeded(uri: String?, generation: Int) {
        if (uri.isNullOrBlank() || prefetchStartedGeneration == generation) return
        prefetchStartedGeneration = generation
        startWebAudioPrefetch(uri, generation)
    }

    private fun handleWebAudioPrefetchProgress(generation: Int, loadedBytes: Double, totalBytes: Double) {
        if (generation != prefetchGeneration || !isPlayRequestCurrent(generation)) return
        if (!loadedBytes.isFinite() || !totalBytes.isFinite() || totalBytes <= 0.0) return
        val fraction = (loadedBytes / totalBytes).coerceIn(0.0, 1.0)
        if (fraction <= prefetchedBufferFraction + WebAudioPrefetchUpdateThreshold && fraction < 1.0) return
        prefetchedBufferFraction = maxOf(prefetchedBufferFraction, fraction)
        updatePrefetchedBufferedPosition(generation)
    }

    private fun handleWebAudioPrefetchComplete(generation: Int, completed: Boolean) {
        if (!completed || generation != prefetchGeneration || !isPlayRequestCurrent(generation)) return
        prefetchedBufferFraction = 1.0
        updatePrefetchedBufferedPosition(generation)
    }

    private fun updatePrefetchedBufferedPosition(generation: Int) {
        val bufferedPositionMs = prefetchedBufferedPositionMs(currentDurationMs())
        if (bufferedPositionMs > 0L) {
            updateBufferedPosition(bufferedPositionMs, generation)
        }
    }

    private fun currentDurationMs(): Long {
        val stateDurationMs = state.value.durationMs
        if (stateDurationMs > 0L) return stateDurationMs
        return if (audio.duration.isFinite() && audio.duration > 0.0) {
            (audio.duration * 1000.0).toLong()
        } else {
            0L
        }
    }

    private fun prefetchedBufferedPositionMs(durationMs: Long): Long {
        if (durationMs <= 0L || prefetchedBufferFraction <= 0.0) return 0L
        return (durationMs * prefetchedBufferFraction).toLong().coerceIn(0L, durationMs)
    }

    private fun resetWebAudioPrefetch(generation: Int = -1) {
        prefetchGeneration = generation
        prefetchStartedGeneration = -1
        prefetchedBufferFraction = 0.0
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
                pendingSeekAfterLoadSeconds = positionSeconds
                pendingPlayAfterLoad = true
                prepareAudioElementForCurrentEqualizer()
                audio.src = uri
                audio.load()
                applyCurrentEqualizer()
                if (playWhenReady) {
                    playWebAudio(audio)
                }
            }
            if (!reload) {
                playWebAudio(audio)
            }
        }
    }

    private companion object {
        const val MaxStreamRetryCount = 5
        const val StreamRetryBaseDelayMs = 1_000L
    }
}

private fun webEqualizerPayload(profile: EqualizerProfile): String {
    val normalized = profile.normalized()
    val bands = normalized.bands.joinToString(
        prefix = "[",
        postfix = "]",
    ) { band -> band.frequencyHz.toString() }
    val gains = normalized.gainsDb.joinToString(
        prefix = "[",
        postfix = "]",
    ) { gain -> gain.toString() }
    return """{"enabled":${normalized.enabled},"bandCount":${normalized.bandCount},"bands":$bands,"gains":$gains}"""
}

private fun createAudioElement(useCors: Boolean): HTMLAudioElement =
    (document.createElement("audio") as HTMLAudioElement).apply {
        preload = "auto"
        if (useCors) {
            crossOrigin = "anonymous"
        }
    }

data class WebAudioTimeRange(
    val startMs: Long,
    val endMs: Long,
)

fun webAudioBufferedPositionMs(
    positionMs: Long,
    durationMs: Long,
    bufferedRanges: List<WebAudioTimeRange>,
    prefetchedPositionMs: Long = 0L,
): Long {
    val boundedPositionMs = positionMs.coerceAtLeast(0L)
    val playableEndMs = bufferedRanges
        .fold(boundedPositionMs) { currentEndMs, range ->
            if (boundedPositionMs + WebAudioRangeStartToleranceMs >= range.startMs) {
                maxOf(currentEndMs, range.endMs)
            } else {
                currentEndMs
            }
        }
        .coerceAtLeast(prefetchedPositionMs)
        .coerceAtLeast(boundedPositionMs)

    if (durationMs <= 0L) return playableEndMs
    val boundedPlayableEndMs = playableEndMs.coerceAtMost(durationMs)
    return if (durationMs - boundedPlayableEndMs <= WebAudioDurationEndToleranceMs) {
        durationMs
    } else {
        boundedPlayableEndMs
    }
}

private fun org.w3c.dom.TimeRanges.toWebAudioTimeRanges(): List<WebAudioTimeRange> {
    val timeRanges = mutableListOf<WebAudioTimeRange>()
    for (index in 0 until length) {
        val startMs = start(index) * 1000.0
        val endMs = end(index) * 1000.0
        if (startMs.isFinite() && endMs.isFinite() && endMs > startMs) {
            timeRanges += WebAudioTimeRange(
                startMs = startMs.toLong().coerceAtLeast(0L),
                endMs = endMs.toLong().coerceAtLeast(0L),
            )
        }
    }
    return timeRanges
}

private const val WebAudioRangeStartToleranceMs = 250L
private const val WebAudioDurationEndToleranceMs = 750L
private const val WebAudioPrefetchUpdateThreshold = 0.005

@OptIn(ExperimentalWasmJsInterop::class)
@JsFun("(audio, callback) => { audio.onprogress = () => callback(); }")
private external fun installWebAudioProgressHandler(audio: HTMLAudioElement, callback: () -> Unit)

@OptIn(ExperimentalWasmJsInterop::class)
@JsFun(
    """(uri, onProgress, onComplete) => {
        const previous = globalThis.__phoebeAudioPrefetchController;
        if (previous) {
            try { previous.abort(); } catch (_) {}
        }
        if (!uri || !/^https?:\/\//i.test(String(uri))) {
            try { onComplete(false); } catch (_) {}
            return;
        }
        const controller = new AbortController();
        globalThis.__phoebeAudioPrefetchController = controller;
        (async () => {
            let completed = false;
            try {
                const response = await fetch(uri, {
                    signal: controller.signal,
                    cache: "default",
                    credentials: "omit"
                });
                if (!response || !response.ok) {
                    throw new Error("prefetch failed: " + (response ? response.status : "no response"));
                }
                const total = Number(response.headers.get("Content-Length")) || 0;
                if (response.body && typeof response.body.getReader === "function") {
                    const reader = response.body.getReader();
                    let loaded = 0;
                    while (true) {
                        const chunk = await reader.read();
                        if (chunk.done) break;
                        loaded += chunk.value ? chunk.value.byteLength : 0;
                        if (total > 0) {
                            onProgress(loaded, total);
                        }
                    }
                    completed = true;
                } else {
                    const blob = await response.blob();
                    const size = blob?.size || total;
                    if (size > 0) {
                        onProgress(size, size);
                    }
                    completed = true;
                }
            } catch (error) {
                completed = false;
            } finally {
                if (globalThis.__phoebeAudioPrefetchController === controller) {
                    globalThis.__phoebeAudioPrefetchController = null;
                }
                try { onComplete(completed); } catch (_) {}
            }
        })();
    }""",
)
private external fun startWebAudioPrefetch(
    uri: String,
    onProgress: (Double, Double) -> Unit,
    onComplete: (Boolean) -> Unit,
)

@OptIn(ExperimentalWasmJsInterop::class)
@JsFun(
    """() => {
        const controller = globalThis.__phoebeAudioPrefetchController;
        if (controller) {
            try { controller.abort(); } catch (_) {}
            globalThis.__phoebeAudioPrefetchController = null;
        }
    }""",
)
private external fun cancelWebAudioPrefetch()

@OptIn(ExperimentalWasmJsInterop::class)
@JsFun(
    """(audio, payload) => {
        const profile = JSON.parse(payload);
        if (!audio) return;
        const active = profile.enabled && profile.gains?.some((gain) => Math.abs(gain) > 0.001);
        let eq = globalThis.__phoebeEqualizer;
        if (!active) {
            if (eq && eq.audio === audio) {
                try { eq.source.disconnect(); } catch (_) {}
                for (const node of eq.nodes || []) {
                    try { node.disconnect(); } catch (_) {}
                }
                eq.nodes = [];
                try { eq.source.connect(eq.context.destination); } catch (_) {}
            }
            return;
        }
        const Ctx = globalThis.AudioContext || globalThis.webkitAudioContext;
        if (!Ctx) return;
        if (!eq || eq.audio !== audio) {
            const context = eq?.context || new Ctx();
            let source;
            try {
                source = context.createMediaElementSource(audio);
            } catch (error) {
                // A media element can only have one source node. Reuse the previous one if it exists.
                if (!eq || eq.audio !== audio || !eq.source) return;
                source = eq.source;
            }
            eq = { audio, context, source, nodes: [] };
            globalThis.__phoebeEqualizer = eq;
        }
        eq.context.resume?.();
        try { eq.source.disconnect(); } catch (_) {}
        for (const node of eq.nodes || []) {
            try { node.disconnect(); } catch (_) {}
        }
        eq.nodes = [];
        let current = eq.source;
        const q = profile.bandCount === 31 ? 4.2 : profile.bandCount === 15 ? 2.1 : profile.bandCount === 5 ? 0.9 : 1.35;
        for (let i = 0; i < profile.bands.length; i++) {
            const gain = profile.gains[i] || 0;
            if (Math.abs(gain) <= 0.001) continue;
            const filter = eq.context.createBiquadFilter();
            filter.type = "peaking";
            filter.frequency.value = profile.bands[i];
            filter.Q.value = q;
            filter.gain.value = gain;
            current.connect(filter);
            current = filter;
            eq.nodes.push(filter);
        }
        current.connect(eq.context.destination);
    }""",
)
private external fun applyWebEqualizer(audio: HTMLAudioElement, payload: String)

@OptIn(ExperimentalWasmJsInterop::class)
@JsFun(
    """(audio, enabled) => {
        if (!audio) return;
        if (enabled) {
            audio.crossOrigin = "anonymous";
        }
    }""",
)
private external fun prepareWebEqualizerAudio(audio: HTMLAudioElement, enabled: Boolean)

@OptIn(ExperimentalWasmJsInterop::class)
@JsFun(
    """(audio) => {
        const eq = globalThis.__phoebeEqualizer;
        return !!eq && eq.audio === audio && !!eq.source;
    }""",
)
private external fun isWebEqualizerAttached(audio: HTMLAudioElement): Boolean

@OptIn(ExperimentalWasmJsInterop::class)
@JsFun(
    """(audio) => {
        if (!audio) return;
        try { audio.pause(); } catch (_) {}
        audio.onloadedmetadata = null;
        audio.onloadeddata = null;
        audio.ondurationchange = null;
        audio.onplaying = null;
        audio.onpause = null;
        audio.onwaiting = null;
        audio.onstalled = null;
        audio.oncanplay = null;
        audio.oncanplaythrough = null;
        audio.onprogress = null;
        audio.onsuspend = null;
        audio.ontimeupdate = null;
        audio.onended = null;
        audio.onerror = null;
        const eq = globalThis.__phoebeEqualizer;
        if (eq && eq.audio === audio) {
            try { eq.source.disconnect(); } catch (_) {}
            for (const node of eq.nodes || []) {
                try { node.disconnect(); } catch (_) {}
            }
            globalThis.__phoebeEqualizer = { context: eq.context, audio: null, source: null, nodes: [] };
        }
    }""",
)
private external fun disposeWebAudioElement(audio: HTMLAudioElement)

@OptIn(ExperimentalWasmJsInterop::class)
@JsFun(
    """(audio, seconds) => {
        try {
            if (!audio || !Number.isFinite(seconds)) return false;
            audio.currentTime = Math.max(0, seconds);
            return true;
        } catch (error) {
            console.warn("Phoebe web audio seek failed.", error);
            return false;
        }
    }""",
)
private external fun setWebAudioCurrentTime(audio: HTMLAudioElement, seconds: Double): Boolean

@OptIn(ExperimentalWasmJsInterop::class)
@JsFun(
    """(audio) => {
        try {
            const Ctx = globalThis.AudioContext || globalThis.webkitAudioContext;
            if (Ctx) {
                let eq = globalThis.__phoebeEqualizer;
                if (!eq || !eq.context) {
                    eq = { context: new Ctx(), audio: null, source: null, nodes: [] };
                    globalThis.__phoebeEqualizer = eq;
                }
                eq.context?.resume?.();
            }
            const activeEq = globalThis.__phoebeEqualizer;
            if (activeEq && activeEq.audio === audio) {
                activeEq.context?.resume?.();
            }
            const playResult = audio.play();
            if (playResult && typeof playResult.catch === "function") {
                playResult.catch((error) => {
                    console.warn("Phoebe web audio playback was blocked or failed.", error);
                });
            }
        } catch (error) {
            console.warn("Phoebe web audio playback failed.", error);
        }
    }""",
)
private external fun playWebAudio(audio: HTMLAudioElement)
