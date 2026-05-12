package com.phoebe.app.player

actual fun createAudioPlayer(): AudioPlayer = IosAudioPlayer()

private class IosAudioPlayer : SimpleAudioPlayer() {
    override fun playUri(uri: String) {
        // TODO: replace with AVPlayer once the iOS host app owns audio-session setup.
        uri.isNotBlank()
    }
}
