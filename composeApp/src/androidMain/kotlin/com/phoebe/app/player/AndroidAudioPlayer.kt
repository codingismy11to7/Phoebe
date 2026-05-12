package com.phoebe.app.player

import androidx.media3.common.MediaItem
import androidx.media3.exoplayer.ExoPlayer
import com.phoebe.app.AndroidContextHolder

actual fun createAudioPlayer(): AudioPlayer = AndroidAudioPlayer()

private class AndroidAudioPlayer : SimpleAudioPlayer() {
    private val player by lazy { ExoPlayer.Builder(AndroidContextHolder.application).build() }

    override fun playUri(uri: String) {
        if (uri.isBlank()) return
        player.setMediaItem(MediaItem.fromUri(uri))
        player.prepare()
        player.play()
    }

    override fun pause() {
        player.pause()
    }

    override fun resume() {
        player.play()
    }

    override fun seek(positionMs: Long) {
        player.seekTo(positionMs)
    }

    override fun setOutputVolume(volume: Float) {
        player.volume = volume
    }
}
