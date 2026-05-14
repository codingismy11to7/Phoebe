package com.phoebe.app

import com.phoebe.app.player.SimpleAudioPlayer

/** Lightweight player used by multiplatform E2E tests to assert the resolved playback URI. */
internal class RecordingAudioPlayer : SimpleAudioPlayer() {
    var lastUri: String? = null

    override fun playUri(uri: String) {
        lastUri = uri
        markPlaybackReady()
    }
}
