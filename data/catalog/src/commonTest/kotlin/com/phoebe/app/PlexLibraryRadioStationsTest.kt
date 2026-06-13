package com.phoebe.app

import com.phoebe.app.data.defaultPlexRadioStations
import com.phoebe.app.data.mergePlexLibraryRadioStations
import com.phoebe.app.data.plexLibraryStationSlug
import com.phoebe.app.domain.MusicLibrary
import com.phoebe.app.domain.PlexRadioStation
import kotlin.test.Test
import kotlin.test.assertEquals

class PlexLibraryRadioStationsTest {
    @Test
    fun mergePlexLibraryRadioStationsFillsMissingDefaults() {
        val library = MusicLibrary("1", "Music")
        val defaults = defaultPlexRadioStations(library)
        val apiOnlyDeepCuts = listOf(
            PlexRadioStation(
                id = "deep-cuts",
                title = "Deep Cuts",
                subtitle = "Hidden gems",
                key = "/library/sections/1/stations/deepCuts",
            ),
        )

        val merged = mergePlexLibraryRadioStations(apiOnlyDeepCuts, defaults)

        assertEquals(
            listOf("library", "deepCuts", "timeTravel"),
            merged.map { it.key.plexLibraryStationSlug() },
        )
    }
}
