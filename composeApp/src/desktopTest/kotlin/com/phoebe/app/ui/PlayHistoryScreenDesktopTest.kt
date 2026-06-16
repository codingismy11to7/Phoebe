package com.phoebe.app.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.v2.runDesktopComposeUiTest
import androidx.compose.ui.unit.dp
import com.phoebe.app.data.HomePlayedTrack
import com.phoebe.app.domain.LibraryUiPreferences
import com.phoebe.app.domain.PlayHistoryKind
import com.phoebe.app.domain.Track
import com.phoebe.app.feature.history.PlayHistoryScreen
import com.phoebe.app.feature.history.PlayHistoryUiState
import kotlin.test.Test

class PlayHistoryScreenDesktopTest {
    @OptIn(ExperimentalTestApi::class)
    @Test
    fun compactDesktopHistoryScreenKeepsDesktopTableHeaders() = runDesktopComposeUiTest(width = 430, height = 760) {
        setContent {
            PhoebeTheme {
                Box(Modifier.size(430.dp, 760.dp)) {
                    PlayHistoryScreen(
                        kind = PlayHistoryKind.RecentlyPlayed,
                        state = PlayHistoryUiState(
                            rows = listOf(
                                HomePlayedTrack(
                                    track = Track(
                                        id = "track-1",
                                        title = "Fixture Song",
                                        artist = "Fixture Artist",
                                        album = "Fixture Album",
                                        durationMs = 180_000L,
                                        streamUrl = "https://stream.example/fixture-song.mp3",
                                        downloadUrl = "",
                                    ),
                                    lastPlayedMs = 1_700_000_000_000L,
                                ),
                            ),
                        ),
                        libraryUi = LibraryUiPreferences(),
                        onLibraryColumns = {},
                        preferTableLayout = true,
                        onBack = {},
                        onPlayTracks = { _, _ -> },
                        onAddToUpNext = {},
                        onDownload = {},
                    )
                }
            }
        }

        onNodeWithText("ARTIST").assertIsDisplayed()
    }
}
