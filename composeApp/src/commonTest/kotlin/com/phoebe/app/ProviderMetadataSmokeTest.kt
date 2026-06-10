package com.phoebe.app

import com.phoebe.app.data.JellyfinClient
import com.phoebe.app.data.JellyfinProviderAdapter
import com.phoebe.app.domain.Track
import com.phoebe.app.domain.TrackMetadataUpdate
import com.phoebe.app.testing.jellyfinSmokeMockEngine
import com.phoebe.app.testing.testHttpClient
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertTrue

class ProviderMetadataSmokeTest {
    @Test
    fun jellyfinAdapterMetadataEditSmoke() = runTest {
        val adapter = JellyfinProviderAdapter(JellyfinClient(testHttpClient(jellyfinSmokeMockEngine())))
        val config = com.phoebe.app.testing.ProviderSmokeHarness.remoteConfig(com.phoebe.app.testing.SmokeSource.Jellyfin)
        val signedIn = adapter.signIn(config.serverUrl, config.username, config.password)
        val session = signedIn.copy(
            selectedLibrary = adapter.libraries(signedIn, signedIn.selectedServer!!).first(),
        )
        val track = adapter.buildCatalog(session).tracksByParent.values.flatten().first()
        val original = track.copy(id = "jellyfin:track-1")
        val updated = TrackMetadataUpdate(
            trackId = original.id,
            title = "Renamed Song",
            artist = "Artist One",
            album = "Album One",
            year = 2021,
            genre = "Synthpop",
        )

        assertTrue(
            adapter.editTrackMetadata(session, original.id, original, updated),
        )
    }
}
