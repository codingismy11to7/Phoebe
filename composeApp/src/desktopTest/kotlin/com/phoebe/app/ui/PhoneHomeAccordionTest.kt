package com.phoebe.app.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.v2.runDesktopComposeUiTest
import androidx.compose.ui.unit.dp
import com.phoebe.app.domain.HomeSection
import com.phoebe.app.domain.PlexRadioStation
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class PhoneHomeAccordionTest {
    @OptIn(ExperimentalTestApi::class, ExperimentalComposeUiApi::class)
    @Test
    fun phoneHomeAccordionsStartCollapsedAndOpenOneAtATime() = runDesktopComposeUiTest(width = 430, height = 932) {
        setContent {
            PhoebeTheme {
                Box(Modifier.size(430.dp, 932.dp)) {
                    MobileHomeScreen(
                        state = HomeUiState(),
                        listState = rememberLazyListState(),
                        radioStations = listOf(
                            PlexRadioStation(
                                id = "radio-library",
                                title = "Library Radio",
                                subtitle = "Shuffle the library",
                                key = "radio-library",
                            ),
                        ),
                        homeSections = listOf(
                            HomeSection.Mixes,
                            HomeSection.Collections,
                            HomeSection.Played,
                        ),
                        onArtist = {},
                        onAlbum = {},
                        onPlaylist = {},
                        onRecentSongs = {},
                        onRecentArtists = {},
                        onRecentAlbums = {},
                        onFavoritePlaylists = {},
                        onFavoriteArtists = {},
                        onFavoriteAlbums = {},
                        onCollections = {},
                        onRecentlyPlayed = {},
                        onMostPlayed = {},
                        onRefreshArtists = {},
                        onRefreshAlbums = {},
                        onPlayTracks = { _, _ -> },
                        onAddToUpNext = {},
                        onDownload = {},
                    )
                }
            }
        }

        fun assertTextExists(text: String) {
            assertTrue(
                onAllNodesWithText(text).fetchSemanticsNodes().isNotEmpty(),
                "Expected text '$text' to exist.",
            )
        }

        fun assertTextDoesNotExist(text: String) {
            assertFalse(
                onAllNodesWithText(text).fetchSemanticsNodes().isNotEmpty(),
                "Expected text '$text' not to exist.",
            )
        }

        waitForIdle()
        assertTextExists("Mixes")
        assertTextExists("Collections")
        assertTextExists("Listening History")
        assertTextDoesNotExist("Personal")
        assertTextDoesNotExist("Artist Mood")
        assertTextDoesNotExist("Recently Played")

        onNodeWithText("Mixes").performClick()
        mainClock.advanceTimeBy(260)
        waitForIdle()
        assertTextExists("Personal")
        assertTextExists("Decade")
        assertTextExists("Library Radio")

        onNodeWithText("Collections").performClick()
        mainClock.advanceTimeBy(260)
        waitForIdle()
        assertTextDoesNotExist("Personal")
        assertTextExists("Artist Mood")

        onNodeWithText("Listening History").performClick()
        mainClock.advanceTimeBy(260)
        waitForIdle()
        assertTextDoesNotExist("Artist Mood")
        assertTextExists("Recently Played")
        assertTextExists("Most Played")
    }
}
