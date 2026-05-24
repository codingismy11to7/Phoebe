package com.phoebe.app.player

actual fun createCastController(audioPlayer: AudioPlayer): CastController =
    UnavailableCastController(
        unavailableMessage = "Chromecast is not supported in the desktop app yet. Use Phoebe in Chrome to cast from this computer.",
        surfaceInitialMessage = false,
    )
