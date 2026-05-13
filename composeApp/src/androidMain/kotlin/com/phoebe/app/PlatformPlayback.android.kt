package com.phoebe.app

import com.phoebe.app.player.AndroidPlaybackRuntime

actual fun installPlatformPlayback(dependencies: AppDependencies) {
    AndroidPlaybackRuntime.install(dependencies)
}
