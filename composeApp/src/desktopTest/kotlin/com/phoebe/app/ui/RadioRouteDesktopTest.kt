package com.phoebe.app.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.printToLog
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.performTextClearance
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.swipeDown
import androidx.compose.ui.test.swipeUp
import androidx.compose.ui.test.v2.runDesktopComposeUiTest
import androidx.compose.ui.unit.dp
import com.phoebe.app.domain.RadioCountry
import com.phoebe.app.domain.RadioDirectoryState
import com.phoebe.app.domain.RadioStation
import com.phoebe.app.domain.RadioStationSearchQuery
import com.phoebe.app.domain.RadioStationSource
import com.phoebe.app.feature.library.LibrarySectionIndexMode
import com.phoebe.app.feature.radio.RadioRoute
import com.phoebe.app.feature.radio.RadioRouteActions
import com.phoebe.app.feature.radio.RadioRouteMode
import com.phoebe.app.feature.radio.RadioRouteState
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull

class RadioRouteDesktopTest {
    @OptIn(ExperimentalTestApi::class, ExperimentalComposeUiApi::class)
    @Test
    fun desktopRadioHomeShowsCountryEntryWithoutEmbeddingCountryRows() =
        runDesktopComposeUiTest(width = 900, height = 620) {
            var browseCountries = false
            var browseGlobe = false
            val countries = listOf(
                RadioCountry(name = "The United States Of America", code = "US", stationCount = 7349),
                RadioCountry(name = "Germany", code = "DE", stationCount = 5951),
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
                                onSearch = {},
                                onLoadMore = {},
                                onRefreshPopular = {},
                                onPlay = {},
                                onAddManualStation = { _, _ -> },
                                onUpdateManualStation = { _, _, _ -> },
                                onDeleteManualStation = {},
                                onBrowseCountries = { browseCountries = true },
                                onBrowseGlobe = { browseGlobe = true },
                            ),
                            contentPadding = PaddingValues(20.dp),
                        )
                    }
                }
            }

            waitForIdle()
            onNodeWithContentDescription("Library section index", useUnmergedTree = true).assertIsDisplayed()
            assertFalse(
                onAllNodesWithText("The United States Of America").fetchSemanticsNodes().isNotEmpty(),
                "Home radio should link to the country browser instead of embedding all country rows.",
            )
            onNode(hasText("Browse by country") and hasClickAction()).performClick()
            assertEquals(true, browseCountries)
            onNode(hasText("Browse on map") and hasClickAction()).performClick()
            assertEquals(true, browseGlobe)
            onNodeWithText("BBC Radio 6 Music").assertIsDisplayed()
        }

    @OptIn(ExperimentalTestApi::class, ExperimentalComposeUiApi::class)
    @Test
    fun desktopRadioMapInitialLoadDoesNotShowUnavailableFallback() =
        runDesktopComposeUiTest(width = 900, height = 620) {
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
                                onAddManualStation = { _, _ -> },
                                onUpdateManualStation = { _, _, _ -> },
                                onDeleteManualStation = {},
                            ),
                            contentPadding = PaddingValues(20.dp),
                            mode = RadioRouteMode.Map,
                        )
                    }
                }
            }

            waitForIdle()
            onNodeWithText("Loading radio map").assertIsDisplayed()
            assertFalse(onAllNodesWithText("Google Maps map").fetchSemanticsNodes().isNotEmpty())
            assertFalse(
                onAllNodesWithText("Google Maps map host is unavailable on this build; station locations are listed below.")
                    .fetchSemanticsNodes()
                    .isNotEmpty(),
            )
        }

    @OptIn(ExperimentalTestApi::class, ExperimentalComposeUiApi::class)
    @Test
    fun desktopRadioMapShowsFallbackAndSelectableStation() =
        runDesktopComposeUiTest(width = 900, height = 620) {
            val stations = listOf(
                RadioStation(
                    id = "kexp",
                    name = "KEXP",
                    streamUrl = "https://radio.example/kexp.mp3",
                    countryCode = "US",
                    state = "Washington",
                    geoLat = 47.608,
                    geoLong = -122.335,
                    source = RadioStationSource.RadioBrowser,
                ),
                RadioStation(
                    id = "de-fallback",
                    name = "Berlin Fallback",
                    streamUrl = "https://radio.example/de.mp3",
                    countryCode = "DE",
                    source = RadioStationSource.RadioBrowser,
                ),
            )
            var played: RadioStation? = null

            setContent {
                PhoebeTheme {
                    Box(Modifier.size(900.dp, 620.dp)) {
                        RadioRoute(
                            state = RadioRouteState(
                                directory = RadioDirectoryState(globeStations = stations),
                            ),
                            actions = RadioRouteActions(
                                onSearch = {},
                                onLoadMore = {},
                                onRefreshPopular = {},
                                onPlay = { played = it },
                                onAddManualStation = { _, _ -> },
                                onUpdateManualStation = { _, _, _ -> },
                                onDeleteManualStation = {},
                            ),
                            contentPadding = PaddingValues(20.dp),
                            mode = RadioRouteMode.Map,
                        )
                    }
                }
            }

            waitForIdle()
            onNodeWithText("Google Maps map").assertIsDisplayed()
            assertFalse(onAllNodesWithText("Search this area").fetchSemanticsNodes().isNotEmpty())
            onNode(hasText("KEXP") and hasClickAction()).performClick()
            assertEquals(
                false,
                onAllNodesWithText("Berlin Fallback").fetchSemanticsNodes().isNotEmpty(),
            )
            assertEquals("kexp", played?.id)
        }

    @OptIn(ExperimentalTestApi::class, ExperimentalComposeUiApi::class)
    @Test
    fun desktopRadioMapLoadingFallbackHidesClusterRows() =
        runDesktopComposeUiTest(width = 900, height = 620) {
            val stations = listOf(
                RadioStation(
                    id = "s1",
                    name = "Station 1",
                    streamUrl = "https://radio.example/1.mp3",
                    geoLat = 40.0,
                    geoLong = -100.0,
                    source = RadioStationSource.RadioBrowser,
                ),
                RadioStation(
                    id = "s2",
                    name = "Station 2",
                    streamUrl = "https://radio.example/2.mp3",
                    geoLat = 40.2,
                    geoLong = -99.8,
                    source = RadioStationSource.RadioBrowser,
                ),
            )

            setContent {
                PhoebeTheme {
                    Box(Modifier.size(900.dp, 620.dp)) {
                        RadioRoute(
                            state = RadioRouteState(
                                directory = RadioDirectoryState(
                                    globeStations = stations,
                                    globeLoading = true,
                                ),
                            ),
                            actions = RadioRouteActions(
                                onSearch = {},
                                onLoadMore = {},
                                onRefreshPopular = {},
                                onPlay = {},
                                onAddManualStation = { _, _ -> },
                                onUpdateManualStation = { _, _, _ -> },
                                onDeleteManualStation = {},
                            ),
                            contentPadding = PaddingValues(20.dp),
                            mode = RadioRouteMode.Map,
                        )
                    }
                }
            }

            waitForIdle()
            onNodeWithText("Loading station locations").assertIsDisplayed()
            assertFalse(onAllNodesWithText("Search this area").fetchSemanticsNodes().isNotEmpty())
            assertFalse(
                onAllNodesWithText("2 stations").fetchSemanticsNodes().isNotEmpty(),
                "Loading map fallback should not render cluster rows.",
            )
        }

    @OptIn(ExperimentalTestApi::class, ExperimentalComposeUiApi::class)
    @Test
    fun desktopRadioCountryIndexUsesSectionLabelBackButton() =
        runDesktopComposeUiTest(width = 900, height = 620) {
            var searchedQuery: RadioStationSearchQuery? = null
            var clearedCountry = false
            val countries = listOf(
                RadioCountry(name = "The United States Of America", code = "US", stationCount = 7349),
                RadioCountry(name = "Germany", code = "DE", stationCount = 5951),
                RadioCountry(name = "France", code = "FR", stationCount = 2640),
            )

            setContent {
                PhoebeTheme {
                    Box(Modifier.size(900.dp, 620.dp)) {
                        RadioRoute(
                            state = RadioRouteState(
                                directory = RadioDirectoryState(countries = countries),
                            ),
                            actions = RadioRouteActions(
                                onSearch = { searchedQuery = it },
                                onLoadMore = {},
                                onRefreshPopular = {},
                                onPlay = {},
                                onAddManualStation = { _, _ -> },
                                onUpdateManualStation = { _, _, _ -> },
                                onDeleteManualStation = {},
                                onClearCountry = { clearedCountry = true },
                            ),
                            contentPadding = PaddingValues(20.dp),
                            mode = RadioRouteMode.CountryIndex,
                        )
                    }
                }
            }

            waitForIdle()
            onNodeWithText("Browse by country").assertIsDisplayed()
            onNode(hasText("BROWSE BY COUNTRY") and hasClickAction()).assertIsDisplayed().performClick()
            assertEquals(true, clearedCountry)
            onNode(hasText("Radio") and hasClickAction()).assertDoesNotExist()
            onNode(hasText("The United States Of America") and hasClickAction()).performClick()
            assertEquals("US", searchedQuery?.countryCode)
        }

    @OptIn(ExperimentalTestApi::class, ExperimentalComposeUiApi::class)
    @Test
    fun desktopRadioCountryResultsExposeCountriesBreadcrumbOnly() =
        runDesktopComposeUiTest(width = 900, height = 620) {
            val countries = listOf(
                RadioCountry(name = "The United States Of America", code = "US", stationCount = 7349),
                RadioCountry(name = "Germany", code = "DE", stationCount = 5951),
            )
            val countryStation = RadioStation(
                id = "radio-browser:us-1",
                name = "KEXP",
                streamUrl = "https://radio.example/kexp.mp3",
                countryCode = "US",
                source = RadioStationSource.RadioBrowser,
            )
            var directory by mutableStateOf(
                RadioDirectoryState(
                    countries = countries,
                ),
            )

            setContent {
                PhoebeTheme {
                    Box(Modifier.size(900.dp, 620.dp)) {
                        RadioRoute(
                            state = RadioRouteState(directory = directory),
                            actions = RadioRouteActions(
                                onSearch = { query ->
                                    directory = if (query.isBlank) {
                                        RadioDirectoryState(countries = countries)
                                    } else {
                                        RadioDirectoryState(
                                            countries = countries,
                                            directoryStations = listOf(countryStation),
                                            searchQuery = query.normalized(),
                                        )
                                    }
                                },
                                onLoadMore = {},
                                onRefreshPopular = {},
                                onPlay = {},
                                onAddManualStation = { _, _ -> },
                                onUpdateManualStation = { _, _, _ -> },
                                onDeleteManualStation = {},
                                onBrowseCountries = { directory = RadioDirectoryState(countries = countries) },
                            ),
                            contentPadding = PaddingValues(20.dp),
                            mode = if (directory.searchQuery.countryCode.isBlank()) {
                                RadioRouteMode.CountryIndex
                            } else {
                                RadioRouteMode.CountryStations
                            },
                        )
                    }
                }
            }

            onNode(hasText("The United States Of America") and hasClickAction()).performClick()
            onNodeWithText("KEXP").assertIsDisplayed()
            onNodeWithText("RESULTS").assertDoesNotExist()
            onNode(hasText("Radio") and hasClickAction()).assertDoesNotExist()
            assertFalse(
                onAllNodesWithContentDescription("Library section index").fetchSemanticsNodes().isNotEmpty(),
                "Country results should not show desktop scrollbar section tracking.",
            )
            onNode(hasText("Countries") and hasClickAction()).assertIsDisplayed().performClick()
            waitForIdle()

            assertEquals(true, directory.searchQuery.isBlank)
        }

    @OptIn(ExperimentalTestApi::class, ExperimentalComposeUiApi::class)
    @Test
    fun radioSearchDebouncesTextInputClearsImmediatelyAndKeepsDesktopAddActionInHeader() =
        runDesktopComposeUiTest(width = 900, height = 620) {
            var searchedQuery: RadioStationSearchQuery? = null

            setContent {
                PhoebeTheme {
                    Box(Modifier.size(900.dp, 620.dp)) {
                        RadioRoute(
                            state = RadioRouteState(
                                directory = RadioDirectoryState(),
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
            onNodeWithText("Add").assertIsDisplayed()

            mainClock.autoAdvance = false
            onAllNodes(hasSetTextAction())[0].performTextInput("jazz")
            mainClock.advanceTimeBy(449)
            assertNull(searchedQuery)

            mainClock.advanceTimeBy(1)
            mainClock.autoAdvance = true
            waitUntil(timeoutMillis = 1_000) {
                searchedQuery?.text == "jazz"
            }

            assertEquals("jazz", searchedQuery?.text)

            searchedQuery = null
            onAllNodes(hasSetTextAction())[0].performTextClearance()
            waitUntil(timeoutMillis = 1_000) {
                searchedQuery?.text == ""
            }
            assertEquals("", searchedQuery?.text)
        }

    @OptIn(ExperimentalTestApi::class, ExperimentalComposeUiApi::class)
    @Test
    fun mobileRadioUsesFloatingAddAction() =
        runDesktopComposeUiTest(width = 390, height = 720) {
            setContent {
                PhoebeTheme {
                    Box(Modifier.size(390.dp, 720.dp)) {
                        RadioRoute(
                            state = RadioRouteState(
                                directory = RadioDirectoryState(),
                            ),
                            actions = RadioRouteActions(
                                onSearch = {},
                                onLoadMore = {},
                                onRefreshPopular = {},
                                onPlay = {},
                                onAddManualStation = { _, _ -> },
                                onUpdateManualStation = { _, _, _ -> },
                                onDeleteManualStation = {},
                            ),
                            contentPadding = PaddingValues(20.dp),
                            sectionIndexMode = LibrarySectionIndexMode.MobileScrollbar,
                        )
                    }
                }
            }

            waitForIdle()
            onNodeWithContentDescription("Add station").assertIsDisplayed().performClick()
            onNodeWithText("Add station").assertIsDisplayed()
        }

    @OptIn(ExperimentalTestApi::class, ExperimentalComposeUiApi::class)
    @Test
    fun mobileRadioFloatingAddActionFollowsScrollDirection() =
        runDesktopComposeUiTest(width = 390, height = 720) {
            val stations = (1..60).map { index ->
                RadioStation(
                    id = "recommended:$index",
                    name = "Station $index",
                    streamUrl = "https://radio.example/$index.mp3",
                    source = RadioStationSource.Recommended,
                )
            }

            setContent {
                PhoebeTheme {
                    Box(Modifier.size(390.dp, 720.dp)) {
                        RadioRoute(
                            state = RadioRouteState(
                                directory = RadioDirectoryState(recommendedStations = stations),
                            ),
                            actions = RadioRouteActions(
                                onSearch = {},
                                onLoadMore = {},
                                onRefreshPopular = {},
                                onPlay = {},
                                onAddManualStation = { _, _ -> },
                                onUpdateManualStation = { _, _, _ -> },
                                onDeleteManualStation = {},
                            ),
                            contentPadding = PaddingValues(20.dp),
                            sectionIndexMode = LibrarySectionIndexMode.MobileScrollbar,
                        )
                    }
                }
            }

            waitForIdle()
            onNodeWithContentDescription("Add station").assertIsDisplayed()

            onRoot().performTouchInput { swipeUp() }
            waitForIdle()
            assertFalse(
                onAllNodesWithContentDescription("Add station").fetchSemanticsNodes().isNotEmpty(),
                "Mobile add FAB should hide when scrolling down.",
            )

            onRoot().performTouchInput { swipeDown() }
            waitForIdle()
            onNodeWithContentDescription("Add station").assertIsDisplayed()
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
