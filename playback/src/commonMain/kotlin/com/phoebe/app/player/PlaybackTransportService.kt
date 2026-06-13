package com.phoebe.app.player

import com.phoebe.app.domain.RepeatMode
import com.phoebe.app.domain.Track
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn

@SingleIn(AppScope::class)
@Inject
class PlaybackTransportService(
    private val audioPlayer: AudioPlayer,
    private val castController: CastController,
    private val systemVolumeController: SystemVolumeController,
) {
    fun togglePlayPause() {
        if (castController.state.value.isPlaybackActive) {
            castController.togglePlayPause()
        } else {
            audioPlayer.togglePlayPause()
        }
    }

    fun clearQueue() {
        if (castController.state.value.isPlaybackActive) {
            castController.disconnect()
        } else {
            audioPlayer.clearQueue()
        }
    }

    fun stopPlayback() {
        castController.disconnect()
        audioPlayer.stopPlayback()
    }

    fun addToUpNext(track: Track) = audioPlayer.addToUpNext(track)

    fun appendToQueue(tracks: List<Track>) = audioPlayer.appendToQueue(tracks)

    fun moveUpNext(fromIndex: Int, toIndex: Int) = audioPlayer.moveUpNext(fromIndex, toIndex)

    fun removeUpNext(index: Int) = audioPlayer.removeUpNext(index)

    fun next() {
        if (castController.state.value.isPlaybackActive) {
            castController.next()
        } else {
            audioPlayer.next()
        }
    }

    fun previous() {
        if (castController.state.value.isPlaybackActive) {
            castController.previous()
        } else {
            audioPlayer.previous()
        }
    }

    fun seekTo(positionMs: Long) {
        if (castController.state.value.isPlaybackActive) {
            castController.seekTo(positionMs)
        } else {
            audioPlayer.seekTo(positionMs)
        }
    }

    fun toggleShuffle(currentShuffle: Boolean) = audioPlayer.setShuffle(!currentShuffle)

    fun cycleRepeat(currentRepeat: RepeatMode) {
        val next = when (currentRepeat) {
            RepeatMode.Off -> RepeatMode.One
            RepeatMode.One -> RepeatMode.All
            RepeatMode.All -> RepeatMode.Off
        }
        audioPlayer.setRepeat(next)
    }

    fun setVolume(volume: Float): Boolean {
        if (castController.state.value.isConnected && castController.setVolume(volume)) {
            return true
        }
        if (systemVolumeController.controlsPlayerOutput) {
            systemVolumeController.setVolume(volume)
        } else {
            audioPlayer.setVolume(volume)
        }
        return false
    }
}
