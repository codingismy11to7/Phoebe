package com.phoebe.app.updates

import com.phoebe.app.data.PhoebeDataJson
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.MockRequestHandleScope
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import io.ktor.serialization.kotlinx.json.json
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class GitHubReleaseUpdateRepositoryTest {
    @Test
    fun semanticVersionParsesReleaseTagsAndCompares() {
        assertEquals(SemanticVersion(1, 2, 3), SemanticVersion.parse("release/1.2.3"))
        assertEquals(SemanticVersion(1, 2, 3), SemanticVersion.parse("v1.2.3"))
        assertEquals(SemanticVersion(1, 2, 3), SemanticVersion.parse("1.2.3+45"))
        assertTrue(SemanticVersion.parse("1.2.4")!! > SemanticVersion.parse("1.2.3")!!)
        assertNull(SemanticVersion.parse("release/1.2"))
    }

    @Test
    fun normalizesOnlyGithubSha256Digests() {
        val digest = "A".repeat(64)
        assertEquals(digest.lowercase(), "sha256:$digest".normalizedSha256Digest())
        assertNull("sha512:$digest".normalizedSha256Digest())
        assertNull("sha256:not-a-digest".normalizedSha256Digest())
    }

    @Test
    fun selectsPlatformSpecificUploadedAssets() {
        val assets = listOf(
            GitHubAssetDto("Phoebe-1.2.3.dmg", "https://example.test/phoebe.dmg", state = "uploaded"),
            GitHubAssetDto("Phoebe-1.2.3.pkg", "https://example.test/phoebe.pkg", state = "uploaded"),
            GitHubAssetDto("Phoebe-1.2.3.msi", "https://example.test/phoebe.msi", state = "uploaded"),
            GitHubAssetDto("Phoebe-1.2.3.apk", "https://example.test/phoebe.apk", state = "uploaded"),
            GitHubAssetDto("Phoebe-1.2.3.flatpak", "https://example.test/phoebe.flatpak", state = "uploaded"),
            GitHubAssetDto("Phoebe-1.2.3.deb", "https://example.test/phoebe.deb", state = "uploaded"),
            GitHubAssetDto("Phoebe-1.2.3.zip", "https://example.test/phoebe.zip", state = "new"),
        )

        assertEquals("Phoebe-1.2.3.pkg", selectUpdateAsset(UpdatePlatform.MacOs, assets)?.name)
        assertEquals("Phoebe-1.2.3.msi", selectUpdateAsset(UpdatePlatform.Windows, assets)?.name)
        assertEquals("Phoebe-1.2.3.apk", selectUpdateAsset(UpdatePlatform.Android, assets)?.name)
        assertEquals("Phoebe-1.2.3.flatpak", selectUpdateAsset(UpdatePlatform.LinuxFlatpak, assets)?.name)
        assertEquals("Phoebe-1.2.3.deb", selectUpdateAsset(UpdatePlatform.LinuxDeb, assets)?.name)
        assertNull(selectUpdateAsset(UpdatePlatform.Ios, assets))
    }

    @Test
    fun latestStableReleaseReturnsAvailableUpdate() = runTest {
        val digest = "b".repeat(64)
        val repository = GitHubReleaseUpdateRepository(
            httpClient = testHttpClient(
                MockEngine { request ->
                    assertEquals("/repos/j-roskopf/Phoebe/releases/latest", request.url.encodedPath)
                    assertEquals("Phoebe/1.0.0", request.headers[HttpHeaders.UserAgent])
                    respondJson(
                        """
                        {
                          "tag_name": "release/1.2.3",
                          "html_url": "https://github.com/j-roskopf/Phoebe/releases/tag/release/1.2.3",
                          "name": "Phoebe 1.2.3",
                          "body": "Bug fixes",
                          "draft": false,
                          "prerelease": false,
                          "assets": [
                            {
                              "name": "Phoebe-1.2.3.apk",
                              "browser_download_url": "https://example.test/Phoebe-1.2.3.apk",
                              "size": 42,
                              "digest": "sha256:$digest",
                              "state": "uploaded"
                            }
                          ]
                        }
                        """.trimIndent(),
                    )
                },
            ),
            installer = FakeInstaller(UpdatePlatform.Android),
            currentVersionName = "1.0.0",
            githubOwner = "j-roskopf",
            githubRepo = "Phoebe",
        )

        val update = assertNotNull(repository.checkForUpdate())

        assertEquals("1.2.3", update.versionName)
        assertEquals("Phoebe 1.2.3", update.releaseName)
        assertEquals("Bug fixes", update.releaseNotes)
        assertEquals("Phoebe-1.2.3.apk", update.asset?.name)
        assertEquals(digest, update.asset?.sha256Digest)
    }

    @Test
    fun latestReleaseReturnsNullWhenCurrentOrPrerelease() = runTest {
        val currentRepository = repositoryForJson(
            currentVersionName = "1.2.3",
            json = """
                {
                  "tag_name": "release/1.2.3",
                  "html_url": "https://github.com/j-roskopf/Phoebe/releases/tag/release/1.2.3",
                  "draft": false,
                  "prerelease": false,
                  "assets": []
                }
            """.trimIndent(),
        )
        val prereleaseRepository = repositoryForJson(
            currentVersionName = "1.0.0",
            json = """
                {
                  "tag_name": "release/1.2.3",
                  "html_url": "https://github.com/j-roskopf/Phoebe/releases/tag/release/1.2.3",
                  "draft": false,
                  "prerelease": true,
                  "assets": []
                }
            """.trimIndent(),
        )

        assertNull(currentRepository.checkForUpdate())
        assertNull(prereleaseRepository.checkForUpdate())
    }

    @Test
    fun latestReleaseHttpFailureIsReported() = runTest {
        val repository = GitHubReleaseUpdateRepository(
            httpClient = testHttpClient(MockEngine { respond("rate limited", HttpStatusCode.Forbidden) }),
            installer = FakeInstaller(UpdatePlatform.Windows),
            currentVersionName = "1.0.0",
        )

        val error = assertFailsWith<IllegalStateException> {
            repository.checkForUpdate()
        }
        assertTrue(error.message.orEmpty().contains("HTTP 403"))
    }

    private fun repositoryForJson(
        currentVersionName: String,
        json: String,
    ): GitHubReleaseUpdateRepository =
        GitHubReleaseUpdateRepository(
            httpClient = testHttpClient(MockEngine { respondJson(json) }),
            installer = FakeInstaller(UpdatePlatform.MacOs),
            currentVersionName = currentVersionName,
        )
}

private class FakeInstaller(
    override val platform: UpdatePlatform,
) : PlatformUpdateInstaller {
    override suspend fun install(
        update: AvailableUpdate,
        onProgress: (UpdateInstallProgress) -> Unit,
        confirmInstall: suspend (AvailableUpdate) -> Boolean,
    ): UpdateInstallResult =
        UpdateInstallResult.Started("started")
}

private fun MockRequestHandleScope.respondJson(content: String) = respond(
    content = content,
    headers = headersOf(HttpHeaders.ContentType, "application/json"),
)

private fun testHttpClient(engine: MockEngine): HttpClient =
    HttpClient(engine) {
        install(ContentNegotiation) {
            json(PhoebeDataJson)
        }
    }
