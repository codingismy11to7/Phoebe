package com.phoebe.app.updates

import com.phoebe.app.platform.openExternalUrl

actual fun createPlatformUpdateInstaller(): PlatformUpdateInstaller = IosUpdateInstaller()

private class IosUpdateInstaller : PlatformUpdateInstaller {
    override val platform: UpdatePlatform = UpdatePlatform.Ios

    override suspend fun install(
        update: AvailableUpdate,
        onProgress: (UpdateInstallProgress) -> Unit,
        confirmInstall: suspend (AvailableUpdate) -> Boolean,
    ): UpdateInstallResult {
        openExternalUrl(update.releasePageUrl)
        return UpdateInstallResult.OpenedReleasePage(
            "Phoebe opened the latest release page. iOS needs a supported distribution channel to install updates.",
        )
    }
}
