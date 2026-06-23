package com.phoebe.app.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.size
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.v2.runDesktopComposeUiTest
import androidx.compose.ui.unit.dp
import com.phoebe.app.domain.RadioCountry
import com.phoebe.app.domain.RadioDirectoryState
import com.phoebe.app.domain.RadioStation
import com.phoebe.app.domain.RadioStationSearchQuery
import com.phoebe.app.domain.RadioStationSource
import com.phoebe.app.feature.radio.RadioRoute
import com.phoebe.app.feature.radio.RadioRouteActions
import com.phoebe.app.feature.radio.RadioRouteState
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse

class RadioRouteDesktopTest {
    @OptIn(ExperimentalTestApi::class, ExperimentalComposeUiApi::class)
    @Test
    fun desktopRadioCountriesCollapseWithoutLeavingLazyItemsAndKeepSectionIndex() =
        runDesktopComposeUiTest(width = 900, height = 620) {
            var searchedQuery: RadioStationSearchQuery? = null
            val countries = listOf(
                RadioCountry(name = "The United States Of America", code = "US", stationCount = 7349),
                RadioCountry(name = "Germany", code = "DE", stationCount = 5951),
                RadioCountry(name = "France", code = "FR", stationCount = 2640),
                RadioCountry(name = "Japan", code = "JP", stationCount = 1238),
                RadioCountry(name = "Argentina", code = "AR", stationCount = 1124),
                RadioCountry(name = "Sweden", code = "SE", stationCount = 654),
                RadioCountry(name = "Norway", code = "NO", stationCount = 431),
            )
            val stations = listOf(
                RadioStation(
                    id = "recommended:bbc-6",
                    name = "BBC Radio 6 Music",
                    streamUrl = "https://radio.example/bbc6.mp3",
                    description = "Alternative music, new releases, and deep cuts",
                    category = "Recommended Streams",
                    source = RadioStationSource.Recommended,
                ),
                RadioStation(
                    id = "recommended:dublab",
                    name = "dublab",
                    streamUrl = "https://radio.example/dublab.mp3",
                    description = "Future roots music from a Los Angeles non-profit",
                    category = "Recommended Streams",
                    source = RadioStationSource.Recommended,
                ),
            )

            setContent {
                PhoebeTheme {
                    Box(Modifier.size(900.dp, 620.dp)) {
                        RadioRoute(
                            state = RadioRouteState(
                                directory = RadioDirectoryState(
                                    recommendedStations = stations,
                                    countries = countries,
                                ),
                            ),
                            actions = RadioRouteActions(
                                onSearch = { searchedQuery = it },
                                onLoadMore = {},
                                onRefreshPopular = {},
                                onPlay = {},
                                onAddManualStation = { _, _ -> },
                                onUpdateManualStation = { _, _, _ -> },
                                onDeleteManualStation = {},
                            ),
                            contentPadding = PaddingValues(20.dp),
                        )
                    }
                }
            }

            waitForIdle()
            onNodeWithContentDescription("Library section index", useUnmergedTree = true).assertIsDisplayed()
            onNode(hasText("The United States Of America") and hasClickAction()).performClick()
            assertEquals("US", searchedQuery?.countryCode)

            onNodeWithText("Hide").performClick()
            mainClock.advanceTimeBy(260)
            waitForIdle()

            assertFalse(
                onAllNodesWithText("The United States Of America").fetchSemanticsNodes().isNotEmpty(),
                "Collapsed country rows should be removed from the lazy list, not left as invisible spaced items.",
            )
            onNodeWithText("BBC Radio 6 Music").assertIsDisplayed()
        }

    @OptIn(ExperimentalTestApi::class, ExperimentalComposeUiApi::class)
    @Test
    fun manualStationDialogDisablesSaveUntilRequiredFieldsArePresent() =
        runDesktopComposeUiTest(width = 900, height = 620) {
            var addedStation: Pair<String, String>? = null

            setContent {
                PhoebeTheme {
                    Box(Modifier.size(900.dp, 620.dp)) {
                        RadioRoute(
                            state = RadioRouteState(
                                directory = RadioDirectoryState(),
                            ),
                            actions = RadioRouteActions(
                                onSearch = {},
                                onLoadMore = {},
                                onRefreshPopular = {},
                                onPlay = {},
                                onAddManualStation = { name, streamUrl -> addedStation = name to streamUrl },
                                onUpdateManualStation = { _, _, _ -> },
                                onDeleteManualStation = {},
                            ),
                            contentPadding = PaddingValues(20.dp),
                        )
                    }
                }
            }

            onNodeWithText("Add").performClick()
            onNodeWithText("Save").assertIsNotEnabled()

            val fields = onAllNodes(hasSetTextAction())
            fields[1].performTextInput("Kyoto Radio")
            onNodeWithText("Save").assertIsNotEnabled()
            fields[2].performTextInput("https://server.laradio.online:59009/live")

            onNodeWithText("Save").assertIsEnabled()
            onNodeWithText("Save").performClick()

            assertEquals("Kyoto Radio" to "https://server.laradio.online:59009/live", addedStation)
        }
}
