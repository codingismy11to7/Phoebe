package com.phoebe.app

import com.phoebe.app.player.IosPlaybackRuntime

actual fun installPlatformPlayback(dependencies: AppDependencies) {
    IosPlaybackRuntime.install(dependencies)
}

actual fun bindCarPlayPlayback(state: AppState) {
    com.phoebe.app.player.IosCarPlayBridge.bindPlayback { tracks, index ->
        state.playTracks(tracks, index)
    }
}

fun ensureIosPlaybackRuntime() {
    IosPlaybackRuntime.installFactory { AppDependencies.create() }
    IosPlaybackRuntime.ensureInstalled()
}
