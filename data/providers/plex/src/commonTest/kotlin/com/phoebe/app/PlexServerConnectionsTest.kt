package com.phoebe.app

import com.phoebe.app.data.decodedIpFromPlexDirect
import com.phoebe.app.data.expandConnectionUris
import com.phoebe.app.data.reachableBaseUris
import com.phoebe.app.domain.PlexServer
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class PlexServerConnectionsTest {
    @Test
    fun decodedIpFromPlexDirectParsesDashedHost() {
        assertEquals(
            "172.105.8.66",
            decodedIpFromPlexDirect("https://172-105-8-66.abc.plex.direct:8443"),
        )
    }

    @Test
    fun expandConnectionUrisAddsPlainLanAnd8443() {
        val expanded = expandConnectionUris(
            listOf("https://172-105-8-66.abc.plex.direct:8443"),
        )
        assertTrue("http://172.105.8.66:32400" in expanded)
        assertTrue("https://172.105.8.66:8443" in expanded)
    }

    @Test
    fun reachableBaseUrisPrefersLocalAdvertisedLan() {
        val advertised = listOf(
            "https://172-105-8-66.abc.plex.direct:8443",
            "http://192.168.86.43:32400",
        )
        val server = PlexServer(
            id = "s1",
            name = "plex",
            uri = "http://192.168.86.43:32400",
            owned = true,
            connectionUris = expandConnectionUris(advertised),
            advertisedConnectionUris = advertised,
            localConnectionUris = listOf("http://192.168.86.43:32400"),
        )
        assertEquals("http://192.168.86.43:32400", server.reachableBaseUris().first())
    }

    @Test
    fun reachableBaseUrisAdvertisedBeforeSynthesizedIp() {
        val advertised = listOf("https://172-105-8-66.abc.plex.direct:8443")
        val server = PlexServer(
            id = "s1",
            name = "plex",
            uri = advertised.first(),
            owned = true,
            connectionUris = expandConnectionUris(advertised),
            advertisedConnectionUris = advertised,
        )
        val ordered = server.reachableBaseUris()
        assertEquals(advertised.first(), ordered.first())
        assertTrue(ordered.indexOf("http://172.105.8.66:32400") > 0)
    }

    @Test
    fun reachableBaseUrisPrefersPersistedPrimaryUri() {
        val advertised = listOf("https://172-105-8-66.abc.plex.direct:8443")
        val server = PlexServer(
            id = "s1",
            name = "plex",
            uri = "http://reachable.example:32400",
            owned = true,
            connectionUris = listOf("http://reachable.example:32400") + expandConnectionUris(advertised),
            advertisedConnectionUris = advertised,
        )
        assertEquals("http://reachable.example:32400", server.reachableBaseUris().first())
    }

    @Test
    fun authTokenPrefersServerAccessToken() {
        val server = PlexServer(
            id = "s1",
            name = "plex",
            uri = "http://localhost:32400",
            owned = true,
            accessToken = "server-specific",
        )
        assertEquals("server-specific", server.authToken("user-token"))
    }
}
