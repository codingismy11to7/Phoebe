package com.phoebe.app.player

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

    override fun play(queue: List<Track>, startIndex: Int) {
        val index = startIndex.coerceIn(queue.indices)
        val track = queue.getOrNull(index)
        mutableState.value = mutableState.value.copy(
            queue = queue,
            currentIndex = if (track == null) -1 else index,
            isPlaying = track != null,
            positionMs = 0L,
            durationMs = track?.durationMs ?: 0L,
        )
        track?.let { playUri(it.localUri ?: it.streamUrl) }
        setOutputVolume(mutableState.value.volume)
        if (track != null) startProgressTicker()
    }

    override fun togglePlayPause() {
        val nextPlaying = !mutableState.value.isPlaying
        mutableState.value = mutableState.value.copy(isPlaying = nextPlaying)
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

    override fun addToUpNext(track: Track) {
        val state = mutableState.value
        val deduped = state.queue.filterNot { it.id == track.id }
        val insertAt = (state.currentIndex + 1).coerceIn(0, deduped.size)
        val newQueue = deduped.toMutableList().also { it.add(insertAt, track) }
        val newCurrent = if (state.currentIndex < 0) state.currentIndex
        else newQueue.indexOfFirst { it.id == state.currentTrack?.id }.takeIf { it >= 0 } ?: state.currentIndex
        mutableState.value = state.copy(queue = newQueue, currentIndex = newCurrent)
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
        val state = mutableState.value
        if (state.currentIndex < 0 || state.queue.isEmpty()) return
        when (state.repeat) {
            RepeatMode.One -> play(state.queue, state.currentIndex)
            RepeatMode.All -> {
                val target = if (state.currentIndex >= state.queue.lastIndex) 0 else state.currentIndex + 1
                play(state.queue, target)
            }
            RepeatMode.Off -> {
                val target = state.currentIndex + 1
                if (target <= state.queue.lastIndex) play(state.queue, target)
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
        setOutputVolume(coerced)
    }

    override fun updateReportedVolume(volume: Float) {
        val coerced = volume.coerceIn(0f, 1f)
        if (mutableState.value.volume != coerced) {
            mutableState.value = mutableState.value.copy(volume = coerced)
        }
    }

    protected abstract fun playUri(uri: String)
    protected open fun pause() = Unit
    protected open fun resume() = Unit
    protected open fun seek(positionMs: Long) = Unit
    protected open fun setOutputVolume(volume: Float) = Unit

    private fun startProgressTicker() {
        progressJob?.cancel()
        progressJob = scope.launch {
            while (true) {
                delay(1000)
                val current = mutableState.value
                if (!current.isPlaying) break
                val nextPosition = (current.positionMs + 1000L).coerceAtMost(current.durationMs)
                mutableState.value = current.copy(positionMs = nextPosition)
                if (nextPosition >= current.durationMs && current.durationMs > 0L) break
            }
        }
    }

    private fun stopProgressTicker() {
        progressJob?.cancel()
        progressJob = null
    }
}
