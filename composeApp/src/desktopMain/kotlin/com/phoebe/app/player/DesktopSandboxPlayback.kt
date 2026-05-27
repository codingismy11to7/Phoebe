package com.phoebe.app.player

import java.io.File

/**
 * Flatpak sandboxes break JavaFX Media ("Could not create player") while Java Sound +
 * SPI decoders still work through the PulseAudio socket.
 */
internal object DesktopSandboxPlayback {
    internal var flatpakSandboxOverride: (() -> Boolean)? = null

    fun isFlatpakSandbox(): Boolean =
        flatpakSandboxOverride?.invoke() ?: File("/.flatpak-info").exists()

    fun sampledPlaybackExtensionFromSuffix(extension: String): String? {
        if (isFlatpakSandbox()) {
            return when (extension.lowercase()) {
                "mp3", "mpeg", "mpga",
                "wav", "wave", "aif", "aiff", "flac", "ogg", "opus",
                -> extension.lowercase().let { if (it == "mpeg" || it == "mpga") "mp3" else it }
                else -> DesktopPlaybackStartupPolicy.sampledPlaybackExtensionFromSuffix(extension)
            }
        }
        return DesktopPlaybackStartupPolicy.sampledPlaybackExtensionFromSuffix(extension)
    }

    fun streamingSampledExtensionFromSuffix(extension: String): String? {
        if (isFlatpakSandbox()) {
            return when (extension.lowercase()) {
                "mp3", "mpeg", "mpga" -> "mp3"
                else -> DesktopPlaybackStartupPolicy.streamingSampledExtensionFromSuffix(extension)
            }
        }
        return DesktopPlaybackStartupPolicy.streamingSampledExtensionFromSuffix(extension)
    }

    fun shouldEagerlyBufferRemotePlayback(uri: String, preferredSampledExtension: String?): Boolean {
        if (!DesktopPlaybackStartupPolicy.isRemoteUri(uri)) return false
        if (isFlatpakSandbox()) {
            val extension = preferredSampledExtension
                ?: DesktopPlaybackStartupPolicy.sampledPlaybackExtensionFromUri(uri)
                ?: sampledPlaybackExtensionFromUri(uri)
            return extension != null
        }
        return DesktopPlaybackStartupPolicy.shouldEagerlyBufferRemotePlayback(uri, preferredSampledExtension)
    }

    private fun sampledPlaybackExtensionFromUri(uri: String): String? {
        val path = runCatching { java.net.URI(uri).path }.getOrNull()
            ?: uri.substringBefore('?').substringBefore('#')
        return sampledPlaybackExtensionFromSuffix(path.substringAfterLast('.', missingDelimiterValue = ""))
    }
}
