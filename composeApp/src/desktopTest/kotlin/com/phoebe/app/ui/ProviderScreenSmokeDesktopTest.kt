package com.phoebe.app.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.v2.runDesktopComposeUiTest
import androidx.compose.ui.unit.dp
import com.phoebe.app.domain.Track
import com.phoebe.app.domain.catalogPrefix
import com.phoebe.app.testing.SmokeSource
import com.phoebe.app.ui.PlaybackTestTags
import kotlin.test.Test

class ProviderScreenSmokeDesktopTest {
    @OptIn(ExperimentalTestApi::class, ExperimentalComposeUiApi::class)
    @Test
    fun providerTrackRowsExposePlayActionsForEachRemoteSource() = runDesktopComposeUiTest(width = 900, height = 640) {
        SmokeSource.entries.filter { it != SmokeSource.LocalFolders }.forEach { source ->
            val prefix = source.providerType?.catalogPrefix ?: "plex"
            val track = Track(
                id = "$prefix:smoke-track",
                title = "${source.name} Smoke Track",
                artist = "Smoke Artist",
                album = "Smoke Album",
                durationMs = 180_000,
                streamUrl = "https://example.test/$prefix/smoke-track.mp3",
                downloadUrl = "https://example.test/$prefix/smoke-track.mp3?download=1",
            )

            setContent {
                PhoebeTheme {
                    Box(Modifier.size(900.dp, 640.dp)) {
                        TrackList(
                            tracks = listOf(track),
                            empty = "No songs",
                            catalogRefreshing = false,
                            onPlayTracks = { _, _ -> },
                            onAddToUpNext = {},
                            onDownload = {},
                        )
                    }
                }
            }

            waitForIdle()
            onNodeWithTag(PlaybackTestTags.playTrack(track.id)).assertIsDisplayed()
        }
    }
}
