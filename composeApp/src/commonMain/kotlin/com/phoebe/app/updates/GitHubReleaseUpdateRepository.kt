package com.phoebe.app.updates

import com.phoebe.app.platform.PhoebeBuildInfo
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.statement.HttpResponse
import io.ktor.http.HttpHeaders
import io.ktor.http.isSuccess

class GitHubReleaseUpdateRepository(
    private val httpClient: HttpClient,
    private val installer: PlatformUpdateInstaller,
    private val currentVersionName: String = PhoebeBuildInfo.versionName,
    private val githubOwner: String = PhoebeBuildInfo.githubOwner,
    private val githubRepo: String = PhoebeBuildInfo.githubRepo,
) {
    suspend fun checkForUpdate(): AvailableUpdate? {
        val currentVersion = SemanticVersion.parse(currentVersionName) ?: return null
        val response = latestReleaseResponse()
        if (!response.status.isSuccess()) {
            error("GitHub release check failed: HTTP ${response.status.value}")
        }
        val release = response.body<GitHubReleaseDto>()
        if (release.draft || release.prerelease) return null
        val latestVersion = SemanticVersion.parse(release.tagName) ?: return null
        if (latestVersion <= currentVersion) return null

        return AvailableUpdate(
            versionName = latestVersion.toString(),
            releaseName = release.name,
            releaseNotes = release.body,
            releasePageUrl = release.htmlUrl,
            asset = selectUpdateAsset(installer.platform, release.assets),
        )
    }

    private suspend fun latestReleaseResponse(): HttpResponse =
        httpClient.get("$GitHubApiBase/repos/$githubOwner/$githubRepo/releases/latest") {
            header(HttpHeaders.Accept, "application/vnd.github+json")
            header(HttpHeaders.UserAgent, "Phoebe/$currentVersionName")
            header("X-GitHub-Api-Version", "2022-11-28")
        }
}

private const val GitHubApiBase = "https://api.github.com"
