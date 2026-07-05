package com.phoebe.app.data

import com.phoebe.app.domain.EventSettings
import com.phoebe.app.domain.EventsBackendTarget
import kotlin.test.Test
import kotlin.test.assertEquals

class EventsBackendTest {
    @Test
    fun releaseBuildAlwaysUsesProductionUrl() {
        val settings = EventSettings(
            backendTarget = EventsBackendTarget.Localhost,
            localBackendUrl = "http://localhost:8088",
        )

        assertEquals(
            "https://events.example.com",
            resolveEventsBackendBaseUrl(
                settings = settings,
                debugBuild = false,
                productionBackendUrl = "https://events.example.com/",
            ),
        )
    }

    @Test
    fun debugLocalhostUsesOverrideUrl() {
        val settings = EventSettings(
            backendTarget = EventsBackendTarget.Localhost,
            localBackendUrl = "http://192.168.1.25:8088/",
        )

        assertEquals(
            "http://192.168.1.25:8088",
            resolveEventsBackendBaseUrl(
                settings = settings,
                debugBuild = true,
                productionBackendUrl = "https://events.example.com",
            ),
        )
    }
}
