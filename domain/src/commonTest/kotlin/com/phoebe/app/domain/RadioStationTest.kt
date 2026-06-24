package com.phoebe.app.domain

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class RadioStationTest {
    @Test
    fun faviconUrlOrFallbackKeepsExplicitFavicon() {
        val station = radioStation(
            faviconUrl = "https://cdn.example/icon.png",
            homepageUrl = "https://example.com/station",
        )

        assertEquals("https://cdn.example/icon.png", station.faviconUrlOrFallback)
    }

    @Test
    fun faviconUrlOrFallbackUsesHomepageOrigin() {
        val station = radioStation(homepageUrl = "https://example.com/station/about")

        assertEquals("https://example.com/favicon.ico", station.faviconUrlOrFallback)
    }

    @Test
    fun fallbackArtworkUrlUsesHomepageAppleTouchIcon() {
        val station = radioStation(
            faviconUrl = "https://cdn.example/icon.png",
            homepageUrl = "https://example.com/station/about",
        )

        assertEquals("https://example.com/apple-touch-icon.png", station.fallbackArtworkUrl)
    }

    @Test
    fun faviconUrlOrFallbackIgnoresUnsupportedHomepageScheme() {
        val station = radioStation(homepageUrl = "ftp://example.com/station")

        assertNull(station.faviconUrlOrFallback)
        assertNull(station.fallbackArtworkUrl)
    }

    private fun radioStation(
        homepageUrl: String? = null,
        faviconUrl: String? = null,
    ): RadioStation =
        RadioStation(
            id = "station",
            name = "Station",
            streamUrl = "https://stream.example/live",
            homepageUrl = homepageUrl,
            faviconUrl = faviconUrl,
        )
}
