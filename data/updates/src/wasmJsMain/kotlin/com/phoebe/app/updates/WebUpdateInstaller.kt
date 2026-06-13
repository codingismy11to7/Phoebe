package com.phoebe.app.updates

import kotlinx.browser.window

actual fun createPlatformUpdateInstaller(): PlatformUpdateInstaller = WebUpdateInstaller()

private class WebUpdateInstaller : PlatformUpdateInstaller {
    override val platform: UpdatePlatform = UpdatePlatform.Web

    override suspend fun install(
        update: AvailableUpdate,
        onProgress: (UpdateInstallProgress) -> Unit,
        confirmInstall: suspend (AvailableUpdate) -> Boolean,
    ): UpdateInstallResult {
        val path = window.location.pathname.ifBlank { "/" }
        window.location.assign("$path?phoebeVersion=${update.versionName}")
        return UpdateInstallResult.Started("Phoebe is reloading the latest web build.")
    }
}
