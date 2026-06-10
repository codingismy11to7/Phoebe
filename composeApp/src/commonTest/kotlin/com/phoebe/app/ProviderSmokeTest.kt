package com.phoebe.app

import com.phoebe.app.testing.ProviderSmokeCoverage
import com.phoebe.app.testing.ProviderSmokeHarness
import com.phoebe.app.testing.ProviderSmokeFeature
import com.phoebe.app.testing.SmokeSource
import com.phoebe.app.testing.SmokeTestLayer
import com.phoebe.app.testing.testHttpClient
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertTrue

class ProviderSmokeTest {
    @Test
    fun commonMockCoverageMatrixHasNoUnexpectedGaps() {
        assertLayerCoverage(SmokeTestLayer.CommonMock, ProviderSmokeCoverage::missingCommonMockCoverage)
    }

    @Test
    fun androidInstrumentedCoverageMatrixHasNoUnexpectedGaps() {
        assertLayerCoverage(SmokeTestLayer.AndroidInstrumented, ProviderSmokeCoverage::missingAndroidInstrumentedCoverage)
    }

    @Test
    fun webE2eCoverageMatrixHasNoUnexpectedGaps() {
        assertLayerCoverage(SmokeTestLayer.WebE2e, ProviderSmokeCoverage::missingWebE2eCoverage)
    }

    @Test
    fun desktopIntegrationCoverageMatrixHasNoUnexpectedGaps() {
        assertLayerCoverage(SmokeTestLayer.DesktopIntegration, ProviderSmokeCoverage::missingDesktopIntegrationCoverage)
    }

    @Test
    fun remoteProviderAdapterSmokeCoversAllRemoteSources() = runTest {
        listOf(
            SmokeSource.Plex,
            SmokeSource.Jellyfin,
            SmokeSource.Emby,
            SmokeSource.Navidrome,
            SmokeSource.MusicAssistant,
        ).forEach { source ->
            val http = testHttpClient(ProviderSmokeHarness.mockEngineFor(source))
            ProviderSmokeHarness.runSourceSmoke(source, http)
        }
    }

    @Test
    fun smokeSourcesDeclareExpectedAdapterCapabilities() {
        SmokeSource.entries.forEach { source ->
            val capabilities = ProviderSmokeCoverage.adapterCapabilities(source) ?: return@forEach
            when (source) {
                SmokeSource.Plex -> {
                    assertTrue(capabilities.serverDiscovery)
                    assertTrue(capabilities.metadataEdit)
                }
                SmokeSource.Jellyfin -> assertTrue(capabilities.quickConnect)
                SmokeSource.MusicAssistant -> {
                    assertTrue(!capabilities.ratings)
                    assertTrue(!capabilities.nativeStreaming)
                    assertTrue(capabilities.remotePlayerControl)
                }
                else -> Unit
            }
        }
    }

    private fun assertLayerCoverage(
        layer: SmokeTestLayer,
        missingProvider: () -> List<Pair<SmokeSource, ProviderSmokeFeature>>,
    ) {
        val missing = missingProvider()
        assertTrue(
            missing.isEmpty(),
            "Missing ${layer.name} smoke coverage:\n${missing.joinToString("\n") { (source, feature) -> "- $source / $feature" }}",
        )
    }
}
