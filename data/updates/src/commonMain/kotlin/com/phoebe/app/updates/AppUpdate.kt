package com.phoebe.app.updates

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

enum class UpdatePlatform {
    Android,
    Ios,
    MacOs,
    Windows,
    LinuxDeb,
    LinuxFlatpak,
    Web,
    Other,
}

data class ReleaseAsset(
    val name: String,
    val downloadUrl: String,
    val sizeBytes: Long,
    val digest: String?,
) {
    val sha256Digest: String?
        get() = digest.normalizedSha256Digest()
}

data class AvailableUpdate(
    val versionName: String,
    val releaseName: String?,
    val releaseNotes: String?,
    val releasePageUrl: String,
    val asset: ReleaseAsset?,
)

enum class UpdateInstallPhase {
    Downloading,
    ReadyToInstall,
    Installing,
}

data class UpdateInstallProgress(
    val phase: UpdateInstallPhase,
    val message: String,
    val fraction: Float? = null,
)

sealed interface AppUpdateState {
    data object Idle : AppUpdateState
    data object Checking : AppUpdateState
    data object Current : AppUpdateState
    data class Available(val update: AvailableUpdate) : AppUpdateState
    data class Installing(
        val update: AvailableUpdate,
        val message: String,
        val progress: Float? = null,
    ) : AppUpdateState
    data class Failed(val message: String, val lastKnownUpdate: AvailableUpdate? = null) : AppUpdateState
}

sealed interface UpdateInstallResult {
    val message: String

    data class Started(
        override val message: String,
    ) : UpdateInstallResult

    data class OpenedReleasePage(
        override val message: String,
    ) : UpdateInstallResult

    data class RequiresUserAction(
        override val message: String,
    ) : UpdateInstallResult
}

interface PlatformUpdateInstaller {
    val platform: UpdatePlatform
    suspend fun install(
        update: AvailableUpdate,
        onProgress: (UpdateInstallProgress) -> Unit = {},
        confirmInstall: suspend (AvailableUpdate) -> Boolean = { true },
    ): UpdateInstallResult
}

expect fun createPlatformUpdateInstaller(): PlatformUpdateInstaller

@Serializable
internal data class GitHubReleaseDto(
    @SerialName("tag_name")
    val tagName: String,
    @SerialName("html_url")
    val htmlUrl: String,
    val name: String? = null,
    val body: String? = null,
    val draft: Boolean = false,
    val prerelease: Boolean = false,
    val assets: List<GitHubAssetDto> = emptyList(),
)

@Serializable
internal data class GitHubAssetDto(
    val name: String,
    @SerialName("browser_download_url")
    val browserDownloadUrl: String,
    val size: Long = 0L,
    val digest: String? = null,
    val state: String? = null,
) {
    fun toReleaseAsset(): ReleaseAsset =
        ReleaseAsset(
            name = name,
            downloadUrl = browserDownloadUrl,
            sizeBytes = size,
            digest = digest,
        )
}

internal data class SemanticVersion(
    val major: Int,
    val minor: Int,
    val patch: Int,
) : Comparable<SemanticVersion> {
    override fun compareTo(other: SemanticVersion): Int =
        compareValuesBy(this, other, SemanticVersion::major, SemanticVersion::minor, SemanticVersion::patch)

    override fun toString(): String = "$major.$minor.$patch"

    companion object {
        private val VersionPattern = Regex("""(?:release/|v)?([0-9]+)\.([0-9]+)\.([0-9]+)(?:[-+].*)?""")

        fun parse(value: String?): SemanticVersion? {
            val match = VersionPattern.matchEntire(value?.trim().orEmpty()) ?: return null
            return SemanticVersion(
                major = match.groupValues[1].toIntOrNull() ?: return null,
                minor = match.groupValues[2].toIntOrNull() ?: return null,
                patch = match.groupValues[3].toIntOrNull() ?: return null,
            )
        }
    }
}

internal fun selectUpdateAsset(
    platform: UpdatePlatform,
    assets: List<GitHubAssetDto>,
): ReleaseAsset? {
    val uploadedAssets = assets.filter { asset ->
        asset.browserDownloadUrl.isNotBlank() && asset.state?.equals("uploaded", ignoreCase = true) != false
    }
    fun firstWithExtension(vararg extensions: String): ReleaseAsset? =
        extensions.firstNotNullOfOrNull { extension ->
            uploadedAssets.firstOrNull { asset -> asset.name.endsWith(extension, ignoreCase = true) }
        }?.toReleaseAsset()

    return when (platform) {
        UpdatePlatform.Android -> firstWithExtension(".apk")
        UpdatePlatform.MacOs -> firstWithExtension(".pkg", ".dmg")
        UpdatePlatform.Windows -> firstWithExtension(".msi")
        UpdatePlatform.LinuxFlatpak -> firstWithExtension(".flatpak")
        UpdatePlatform.LinuxDeb -> firstWithExtension(".deb")
        UpdatePlatform.Ios,
        UpdatePlatform.Web,
        UpdatePlatform.Other,
        -> null
    }
}

internal fun String?.normalizedSha256Digest(): String? {
    val normalized = this?.trim()?.lowercase()?.removePrefix("sha256:") ?: return null
    return normalized.takeIf { it.length == 64 && it.all { char -> char in '0'..'9' || char in 'a'..'f' } }
}
