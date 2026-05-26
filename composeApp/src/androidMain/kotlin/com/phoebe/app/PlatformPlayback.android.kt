package com.phoebe.app

import com.phoebe.app.player.AndroidPlaybackRuntime
import com.phoebe.app.platform.AndroidDownloadRuntime

actual fun installPlatformPlayback(dependencies: AppDependencies) {
    AndroidDownloadRuntime.install(dependencies)
    AndroidPlaybackRuntime.install(dependencies)
}
