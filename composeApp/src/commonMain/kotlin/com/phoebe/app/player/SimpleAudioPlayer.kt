package com.phoebe.app.player

import com.phoebe.app.domain.EqualizerProfile
import com.phoebe.app.domain.PlayerState
import com.phoebe.app.domain.RepeatMode
import com.phoebe.app.domain.Track
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

abstract class SimpleAudioPlayer : AudioPlayer {
    private val mutableState = MutableStateFlow(PlayerState())
    override val state: StateFlow<PlayerState> = mutableState
    private val scope = CoroutineScope(Dispatchers.Default)
    private var progressJob: Job? = null
    private var preferUnityOutputVolume = false
    private var systemVolumeScale = 1f
    private var playGeneration = 0
    private var crossfadeDurationMs = 0L
    private var crossfadeRequestKey: String? = null
    protected var equalizerProfile: EqualizerProfile = EqualizerProfile.Default.normalized()
        private set

    /** When false, a superseded or user-paused load must not start audible playback. */
    protected var playWhenReady = false
        private set

    protected fun cancelPlayIntent() {
        playWhenReady = false
    }

    protected fun adoptPlatformPlayIntent(playWhenReady: Boolean) {
        this.playWhenReady = playWhenReady
    }

    protected val activePlayGeneration: Int
        get() = playGeneration

    protected fun isPlayRequestCurrent(generation: Int): Boolean = generation == playGeneration

    override fun play(queue: List<Track>, startIndex: Int) {
        val previous = mutableState.value
        val index = startIndex.coerceIn(queue.indices)
        val track = queue.getOrNull(index)
        val sameQueue = tracksMatch(previous.queue, queue)

        if (sameQueue && track != null &&
            previous.currentIndex == index &&
            previous.currentTrack?.id == track.id
        ) {
            if (previous.isPlaying && !previous.isBuffering && playWhenReady) {
                return
            }
            crossfadeRequestKey = null
            playWhenReady = true
            mutableState.value = previous.copy(
                isPlaying = !previous.isBuffering,
                playbackErrorMessage = null,
            )
            resume()
            if (!previous.isBuffering) {
                startProgressTicker()
            }
            return
        }

        playGeneration++
        crossfadeRequestKey = null
        playWhenReady = true
        val generation = playGeneration
        stopProgressTicker()
        if (!sameQueue) {
            stopCurrentPlaybackImmediately()
        }
        mutableState.value = previous.copy(
            queue = queue,
            currentIndex = if (track == null) -1 else index,
            isPlaying = false,
            isBuffering = track != null,
            positionMs = 0L,
            bufferedPositionMs = 0L,
            durationMs = track?.durationMs ?: 0L,
            playbackErrorMessage = null,
        )
        setOutputVolume(effectiveOutputVolume())
        if (track != null) {
            if (sameQueue) {
                skipToInQueueOnPlatform(queue, index, track, generation)
            } else {
                playQueueOnPlatform(queue, index, track, generation)
            }
        }
    }

    override fun prepare(queue: List<Track>, startIndex: Int, positionMs: Long) {
        val previous = mutableState.value
        val index = startIndex.coerceIn(queue.indices)
        val track = queue.getOrNull(index)
        val generation = ++playGeneration
        crossfadeRequestKey = null
        playWhenReady = false
        stopProgressTicker()
        stopCurrentPlaybackImmediately()
        val boundedPositionMs = positionMs.coerceAtLeast(0L).let { position ->
            val duration = track?.durationMs ?: 0L
            if (duration > 0L) position.coerceAtMost(duration) else position
        }
        mutableState.value = previous.copy(
            queue = queue,
            currentIndex = if (track == null) -1 else index,
            isPlaying = false,
            isBuffering = track != null,
            positionMs = boundedPositionMs,
            bufferedPositionMs = boundedPositionMs,
            durationMs = track?.durationMs ?: 0L,
            playbackErrorMessage = null,
        )
        setOutputVolume(effectiveOutputVolume())
        if (track != null) {
            playQueueOnPlatform(queue, index, track, generation)
            if (boundedPositionMs > 0L) {
                seek(boundedPositionMs)
            }
        }
    }

    override fun suspendPlayback(queue: List<Track>, startIndex: Int, positionMs: Long) {
        val previous = mutableState.value
        val index = if (queue.isEmpty()) -1 else startIndex.coerceIn(queue.indices)
        val track = queue.getOrNull(index)
        playGeneration++
        crossfadeRequestKey = null
        playWhenReady = false
        stopProgressTicker()
        stopCurrentPlaybackImmediately()
        val boundedPositionMs = positionMs.coerceAtLeast(0L).let { position ->
            val duration = track?.durationMs ?: 0L
            if (duration > 0L) position.coerceAtMost(duration) else position
        }
        mutableState.value = previous.copy(
            queue = queue,
            currentIndex = if (track == null) -1 else index,
            isPlaying = false,
            isBuffering = false,
            positionMs = boundedPositionMs,
            bufferedPositionMs = boundedPositionMs,
            durationMs = track?.durationMs ?: 0L,
            playbackErrorMessage = null,
        )
        setOutputVolume(effectiveOutputVolume())
    }

    override fun togglePlayPause() {
        val state = mutableState.value
        if (state.isBuffering) {
            playWhenReady = false
            mutableState.value = state.copy(isPlaying = false, isBuffering = false)
            pause()
            stopCurrentPlaybackImmediately()
            return
        }
        val nextPlaying = !state.isPlaying
        playWhenReady = nextPlaying
        mutableState.value = state.copy(isPlaying = nextPlaying)
        if (nextPlaying) {
            resume()
            startProgressTicker()
        } else {
            pause()
            stopProgressTicker()
        }
    }

    override fun clearQueue() {
        val state = mutableState.value
        if (state.currentIndex < 0) {
            mutableState.value = state.copy(queue = emptyList())
            return
        }
        val keep = (state.currentIndex + 1).coerceAtMost(state.queue.size)
        mutableState.value = state.copy(queue = state.queue.subList(0, keep).toList())
    }

    override fun stopPlayback() {
        playGeneration++
        playWhenReady = false
        crossfadeRequestKey = null
        stopProgressTicker()
        stopCurrentPlaybackImmediately()
        val volume = mutableState.value.volume
        mutableState.value = PlayerState(volume = volume)
    }

    override fun addToUpNext(track: Track) {
        val state = mutableState.value
        val deduped = state.queue.filterNot { it.id == track.id }
        val insertAt = (state.currentIndex + 1).coerceIn(0, deduped.size)
        val newQueue = deduped.toMutableList().also { it.add(insertAt, track) }
        val newCurrent = if (state.currentIndex < 0) state.currentIndex
        else newQueue.indexOfFirst { it.id == state.currentTrack?.id }.takeIf { it >= 0 } ?: state.currentIndex
        mutableState.value = state.copy(queue = newQueue, currentIndex = newCurrent)
    }

    override fun appendToQueue(tracks: List<Track>) {
        if (tracks.isEmpty()) return
        val state = mutableState.value
        val existingIds = state.queue.map { it.id }.toMutableSet()
        val additions = tracks.filter { existingIds.add(it.id) }
        if (additions.isEmpty()) return
        mutableState.value = state.copy(queue = state.queue + additions)
    }

    override fun moveUpNext(fromIndex: Int, toIndex: Int) {
        val state = mutableState.value
        val base = state.currentIndex + 1
        val upNext = state.upNext
        if (fromIndex !in upNext.indices) return
        val target = toIndex.coerceIn(0, upNext.lastIndex)
        if (target == fromIndex) return
        val newQueue = state.queue.toMutableList()
        val moved = newQueue.removeAt(base + fromIndex)
        newQueue.add(base + target, moved)
        mutableState.value = state.copy(queue = newQueue)
    }

    override fun removeUpNext(index: Int) {
        val state = mutableState.value
        val base = state.currentIndex + 1
        if (index !in state.upNext.indices) return
        val newQueue = state.queue.toMutableList().also { it.removeAt(base + index) }
        mutableState.value = state.copy(queue = newQueue)
    }

    override fun next() {
        advanceNext(allowCrossfade = false)
    }

    private fun advanceNext(allowCrossfade: Boolean) {
        val state = mutableState.value
        if (state.currentIndex < 0 || state.queue.isEmpty()) return
        when (state.repeat) {
            RepeatMode.One -> {
                if (!allowCrossfade || !crossfadeToIndex(state.currentIndex)) play(state.queue, state.currentIndex)
            }
            RepeatMode.All -> {
                val target = if (state.currentIndex >= state.queue.lastIndex) 0 else state.currentIndex + 1
                if (!allowCrossfade || !crossfadeToIndex(target)) play(state.queue, target)
            }
            RepeatMode.Off -> {
                val target = state.currentIndex + 1
                if (target <= state.queue.lastIndex) {
                    if (!allowCrossfade || !crossfadeToIndex(target)) play(state.queue, target)
                } else {
                    playWhenReady = false
                    mutableState.value = state.copy(
                        isPlaying = false,
                        isBuffering = false,
                        positionMs = state.durationMs.takeIf { it > 0L } ?: state.positionMs,
                        bufferedPositionMs = state.durationMs.takeIf { it > 0L } ?: state.bufferedPositionMs,
                    )
                    stopProgressTicker()
                }
            }
        }
    }

    override fun previous() {
        val state = mutableState.value
        val previousIndex = (state.currentIndex - 1).coerceAtLeast(0)
        if (previousIndex >= 0) play(state.queue, previousIndex)
    }

    override fun seekTo(positionMs: Long) {
        mutableState.value = mutableState.value.copy(positionMs = positionMs)
        seek(positionMs)
    }

    override fun setShuffle(enabled: Boolean) {
        val state = mutableState.value
        if (enabled == state.shuffle) return
        if (!enabled) {
            mutableState.value = state.copy(shuffle = false)
            return
        }
        // Pre-shuffle just the upcoming portion of the queue so the current track
        // keeps playing and the user can see the new order in Up Next.
        if (state.currentIndex < 0 || state.currentIndex >= state.queue.lastIndex) {
            mutableState.value = state.copy(shuffle = true)
            return
        }
        val head = state.queue.subList(0, state.currentIndex + 1).toList()
        val tail = state.queue.subList(state.currentIndex + 1, state.queue.size).shuffled()
        mutableState.value = state.copy(shuffle = true, queue = head + tail)
    }

    override fun setRepeat(mode: RepeatMode) {
        mutableState.value = mutableState.value.copy(repeat = mode)
    }

    override fun setVolume(volume: Float) {
        val coerced = volume.coerceIn(0f, 1f)
        mutableState.value = mutableState.value.copy(volume = coerced)
        if (!preferUnityOutputVolume) {
            setOutputVolume(effectiveOutputVolume())
        }
    }

    override fun setCrossfadeDurationMs(durationMs: Long) {
        crossfadeDurationMs = durationMs.coerceIn(0L, MaxCrossfadeDurationMs)
    }

    override fun setEqualizer(profile: EqualizerProfile) {
        val normalized = profile.normalized()
        equalizerProfile = normalized
        applyEqualizer(normalized)
    }

    override fun setUnityOutputVolume() {
        preferUnityOutputVolume = true
        setOutputVolume(effectiveOutputVolume())
    }

    override fun setSystemVolumeScale(scale: Float) {
        systemVolumeScale = scale.coerceIn(0f, 1f)
        setOutputVolume(effectiveOutputVolume())
    }

    override fun updateReportedVolume(volume: Float) {
        val coerced = volume.coerceIn(0f, 1f)
        if (mutableState.value.volume != coerced) {
            mutableState.value = mutableState.value.copy(volume = coerced)
        }
    }

    /** Adopt queue state without touching platform output (Android Auto / MediaSession playlist). */
    protected fun adoptQueueState(queue: List<Track>, startIndex: Int, isPlaying: Boolean) {
        val index = startIndex.coerceIn(queue.indices)
        val track = queue.getOrNull(index)
        mutableState.value = mutableState.value.copy(
            queue = queue,
            currentIndex = if (track == null) -1 else index,
            isPlaying = isPlaying && track != null,
            isBuffering = false,
            positionMs = 0L,
            bufferedPositionMs = 0L,
            durationMs = track?.durationMs ?: 0L,
        )
        if (isPlaying && track != null && useProgressTicker) {
            startProgressTicker()
        } else {
            stopProgressTicker()
        }
    }

    /** When false, [applyPlatformPlayback] drives position instead of the 1s ticker (Android). */
    protected open val useProgressTicker: Boolean get() = true

    protected fun applyPlatformPlayback(
        positionMs: Long,
        durationMs: Long,
        isPlaying: Boolean,
        isBuffering: Boolean = false,
        bufferedPositionMs: Long = mutableState.value.bufferedPositionMs,
        generation: Int = playGeneration,
    ) {
        if (!isPlayRequestCurrent(generation)) return
        val current = mutableState.value
        val effectivePlaying = isPlaying && playWhenReady
        val effectiveDurationMs = if (durationMs > 0L) durationMs else current.durationMs
        val effectiveBufferedPositionMs = bufferedPositionMs
            .coerceAtLeast(positionMs)
            .coerceAtLeast(current.bufferedPositionMs)
            .let { buffered ->
                if (effectiveDurationMs > 0L) buffered.coerceAtMost(effectiveDurationMs) else buffered
            }
        val effectiveBuffering = isBuffering && playWhenReady
        if (current.positionMs == positionMs &&
            current.bufferedPositionMs == effectiveBufferedPositionMs &&
            current.durationMs == effectiveDurationMs &&
            current.isPlaying == effectivePlaying &&
            current.isBuffering == effectiveBuffering
        ) {
            return
        }
        mutableState.value = current.copy(
            positionMs = positionMs,
            bufferedPositionMs = effectiveBufferedPositionMs,
            durationMs = effectiveDurationMs,
            isPlaying = effectivePlaying,
            isBuffering = effectiveBuffering,
        )
        if (effectivePlaying && useProgressTicker) {
            startProgressTicker()
        } else {
            stopProgressTicker()
        }
        maybeStartCrossfade(generation)
    }

    /** Stop audible output immediately when leaving the current track (before the next loads). */
    protected open fun stopCurrentPlaybackImmediately() = Unit

    protected fun markPlaybackReady(isPlaying: Boolean = true, generation: Int = playGeneration) {
        if (!isPlayRequestCurrent(generation)) return
        val current = mutableState.value
        val effectivePlaying = isPlaying && playWhenReady
        mutableState.value = current.copy(
            isBuffering = false,
            isPlaying = effectivePlaying,
            bufferedPositionMs = current.bufferedPositionMs.coerceAtLeast(current.positionMs),
            playbackErrorMessage = null,
        )
        if (effectivePlaying && useProgressTicker) {
            startProgressTicker()
        }
    }

    protected fun updateBufferedPosition(bufferedPositionMs: Long, generation: Int = playGeneration) {
        if (!isPlayRequestCurrent(generation)) return
        val current = mutableState.value
        val boundedBufferedPositionMs = bufferedPositionMs
            .coerceAtLeast(current.positionMs)
            .let { buffered ->
                if (current.durationMs > 0L) buffered.coerceAtMost(current.durationMs) else buffered
            }
        if (boundedBufferedPositionMs != current.bufferedPositionMs) {
            mutableState.value = current.copy(bufferedPositionMs = boundedBufferedPositionMs)
        }
    }

    protected fun markPlaybackFailed(generation: Int = playGeneration, message: String? = null) {
        if (!isPlayRequestCurrent(generation)) return
        val current = mutableState.value
        mutableState.value = current.copy(
            isBuffering = false,
            isPlaying = false,
            playbackErrorSerial = current.playbackErrorSerial + 1,
            playbackErrorMessage = message,
        )
        stopProgressTicker()
    }

    protected fun surfacePlaybackNotice(generation: Int = playGeneration, message: String) {
        if (!isPlayRequestCurrent(generation)) return
        val current = mutableState.value
        mutableState.value = current.copy(
            playbackNoticeSerial = current.playbackNoticeSerial + 1,
            playbackNoticeMessage = message,
        )
    }

    protected abstract fun playUri(uri: String)

    /** Seek within an already-loaded queue without tearing down platform output. */
    protected open fun skipToInQueueOnPlatform(
        queue: List<Track>,
        startIndex: Int,
        track: Track,
        generation: Int,
    ) {
        playQueueOnPlatform(queue, startIndex, track, generation)
    }

    /** Push the active queue to the platform player; default plays only the current track. */
    protected open fun playQueueOnPlatform(
        queue: List<Track>,
        startIndex: Int,
        track: Track,
        generation: Int = activePlayGeneration,
    ) {
        playTrack(track)
    }

    private fun tracksMatch(left: List<Track>, right: List<Track>): Boolean {
        if (left.size != right.size) return false
        return left.indices.all { left[it].id == right[it].id }
    }

    protected open fun playTrack(track: Track) {
        playUri(track.localUri ?: track.streamUrl)
    }
    protected open fun pause() = Unit
    protected open fun resume() = Unit
    protected open fun seek(positionMs: Long) = Unit
    protected open fun setOutputVolume(volume: Float) = Unit
    protected open fun applyEqualizer(profile: EqualizerProfile) = Unit

    protected open fun startCrossfadeOnPlatform(
        queue: List<Track>,
        targetIndex: Int,
        track: Track,
        durationMs: Long,
        baseVolume: Float,
        generation: Int,
    ): Boolean = false

    protected fun adoptCrossfadeTarget(
        queue: List<Track>,
        targetIndex: Int,
        positionMs: Long,
        generation: Int,
    ) {
        if (!isPlayRequestCurrent(generation)) return
        val track = queue.getOrNull(targetIndex) ?: return
        val boundedPositionMs = positionMs.coerceAtLeast(0L).let { position ->
            if (track.durationMs > 0L) position.coerceAtMost(track.durationMs) else position
        }
        mutableState.value = mutableState.value.copy(
            queue = queue,
            currentIndex = targetIndex,
            isPlaying = playWhenReady,
            isBuffering = false,
            positionMs = boundedPositionMs,
            bufferedPositionMs = boundedPositionMs,
            durationMs = track.durationMs,
            playbackErrorMessage = null,
        )
        crossfadeRequestKey = null
    }

    protected fun effectiveOutputVolume(): Float {
        val playerLevel = if (preferUnityOutputVolume) 1f else mutableState.value.volume.coerceIn(0f, 1f)
        return (playerLevel * systemVolumeScale).coerceIn(0f, 1f)
    }

    private fun startProgressTicker() {
        if (!useProgressTicker) return
        progressJob?.cancel()
        progressJob = scope.launch {
            while (true) {
                delay(1000)
                val current = mutableState.value
                if (!current.isPlaying) break
                val nextPosition = (current.positionMs + 1000L).coerceAtMost(current.durationMs)
                mutableState.value = current.copy(positionMs = nextPosition)
                maybeStartCrossfade(activePlayGeneration)
                if (nextPosition >= current.durationMs && current.durationMs > 0L) break
            }
        }
    }

    private fun stopProgressTicker() {
        progressJob?.cancel()
        progressJob = null
    }

    private fun maybeStartCrossfade(generation: Int) {
        val duration = crossfadeDurationMs
        if (duration <= 0L || !isPlayRequestCurrent(generation)) return
        val current = mutableState.value
        if (!current.isPlaying || current.durationMs <= 0L || current.currentIndex !in current.queue.indices) return
        if (current.currentIndex >= current.queue.lastIndex && current.repeat != RepeatMode.All) return
        val remaining = current.durationMs - current.positionMs
        if (remaining > duration) return
        val target = when (current.repeat) {
            RepeatMode.One -> current.currentIndex
            RepeatMode.All -> if (current.currentIndex >= current.queue.lastIndex) 0 else current.currentIndex + 1
            RepeatMode.Off -> current.currentIndex + 1
        }
        if (target in current.queue.indices) {
            crossfadeToIndex(target)
        }
    }

    private fun crossfadeToIndex(targetIndex: Int): Boolean {
        val duration = crossfadeDurationMs
        val current = mutableState.value
        if (duration <= 0L || !current.isPlaying || targetIndex !in current.queue.indices) return false
        val generation = activePlayGeneration
        val requestKey = "$generation:$targetIndex"
        if (crossfadeRequestKey == requestKey) return true
        val baseVolume = effectiveOutputVolume()
        val targetTrack = current.queue[targetIndex]
        crossfadeRequestKey = requestKey
        val accepted = startCrossfadeOnPlatform(current.queue, targetIndex, targetTrack, duration, baseVolume, generation)
        if (accepted) {
            return true
        }
        crossfadeRequestKey = null
        return false
    }

    private companion object {
        const val MaxCrossfadeDurationMs = 12_000L
    }
}
