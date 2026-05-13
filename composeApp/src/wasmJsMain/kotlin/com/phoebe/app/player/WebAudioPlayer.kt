package com.phoebe.app.player

import kotlinx.browser.document
import org.w3c.dom.HTMLAudioElement

actual fun createAudioPlayer(): AudioPlayer = WebAudioPlayer()

@OptIn(ExperimentalWasmJsInterop::class)
private class WebAudioPlayer : SimpleAudioPlayer() {
    private val audio = (document.createElement("audio") as HTMLAudioElement).apply {
        preload = "auto"
    }

    override fun playUri(uri: String) {
        audio.volume = effectiveOutputVolume().toDouble().coerceIn(0.0, 1.0)
        audio.src = uri
        audio.play()
    }

    override fun pause() {
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
}
