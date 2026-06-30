package com.phoebe.app

import com.phoebe.app.data.PlexClient
import com.phoebe.app.data.RadioBrowserClient
import com.phoebe.app.data.SubsonicClient
import com.phoebe.app.domain.RadioMapViewport
import com.phoebe.app.domain.RadioStationSearchQuery
import com.phoebe.app.platform.PlatformStorage
import com.phoebe.app.testing.newInMemoryPhoebeDatabase
import com.phoebe.app.testing.testHttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import kotlinx.coroutines.runBlocking
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class RadioRepositoryTest {
    @Test
    fun loadGlobeCalculatesOffsetForPagesAndResetsOnNewQuery() = runBlocking {
        val (database, driver) = newInMemoryPhoebeDatabase()
        
        val capturedLimits = mutableListOf<String?>()
        val capturedOffsets = mutableListOf<String?>()
        val capturedNames = mutableListOf<String?>()
        
        val engine = MockEngine { request ->
            if (request.url.encodedPath.contains("/json/stations/search")) {
                capturedLimits.add(request.url.parameters["limit"])
                capturedOffsets.add(request.url.parameters["offset"])
                capturedNames.add(request.url.parameters["name"])
            }
            respond(
                content = "[]",
                status = HttpStatusCode.OK,
                headers = headersOf(HttpHeaders.ContentType, "application/json")
            )
        }
        
        val httpClient = testHttpClient(engine)
        val sessionRepository = testSessionRepository(
            plexClient = PlexClient(httpClient),
            database = database,
            storage = PlatformStorage(),
            httpClient = httpClient,
        )
        val radioBrowserClient = RadioBrowserClient(httpClient)
        val subsonicClient = SubsonicClient(httpClient)
        
        val repository = testRadioRepository(
            database = database,
            radioBrowserClient = radioBrowserClient,
            subsonicClient = subsonicClient,
            sessionRepository = sessionRepository,
        )
        
        try {
            val query1 = RadioStationSearchQuery(text = "jazz")
            
            // 1. Load Page 0
            repository.loadGlobe(query1, page = 0)
            assertEquals("1000", capturedLimits[0])
            assertEquals(null, capturedOffsets[0]) // offset parameter not added/omitted for 0
            assertEquals("jazz", capturedNames[0])
            
            // 2. Load Page 1 (same query)
            repository.loadGlobe(query1, page = 1)
            assertTrue(capturedLimits.contains("1000"))
            assertTrue(capturedOffsets.contains("1000"))
            
            val initialSize = capturedLimits.size
            
            // 3. Load with new query
            val query2 = RadioStationSearchQuery(text = "rock")
            repository.loadGlobe(query2, page = 2) // even if caller asks for page 2, query change must reset to page 0
            
            // The last requests should have offset = null/0 because page reset to 0
            val lastGeoIndex = initialSize
            assertEquals("rock", capturedNames[lastGeoIndex])
            assertEquals(null, capturedOffsets[lastGeoIndex])
            assertEquals(0, repository.state.value.globePageIndex)
            
        } finally {
            driver.close()
        }
    }

    @Test
    fun loadGlobeAppendsNextPageAndReplacesOnNewQuery() = runBlocking {
        val (database, driver) = newInMemoryPhoebeDatabase()

        val engine = MockEngine { request ->
            val hasGeoInfo = request.url.parameters["has_geo_info"] == "true"
            val name = request.url.parameters["name"].orEmpty()
            val offset = request.url.parameters["offset"]?.toIntOrNull() ?: 0
            val stationIndex = if (offset >= 1000) 1 else 0
            val content = if (hasGeoInfo) {
                """
                [{
                  "stationuuid": "$name-geo-$stationIndex",
                  "name": "$name station $stationIndex",
                  "url": "https://example.com/$name-$stationIndex",
                  "geo_lat": ${41.0 + stationIndex},
                  "geo_long": ${-87.0 - stationIndex},
                  "countrycode": "US"
                }]
                """.trimIndent()
            } else {
                "[]"
            }
            respond(
                content = content,
                status = HttpStatusCode.OK,
                headers = headersOf(HttpHeaders.ContentType, "application/json")
            )
        }

        val httpClient = testHttpClient(engine)
        val sessionRepository = testSessionRepository(
            plexClient = PlexClient(httpClient),
            database = database,
            storage = PlatformStorage(),
            httpClient = httpClient,
        )
        val repository = testRadioRepository(
            database = database,
            radioBrowserClient = RadioBrowserClient(httpClient),
            subsonicClient = SubsonicClient(httpClient),
            sessionRepository = sessionRepository,
        )

        try {
            repository.loadGlobe(RadioStationSearchQuery(text = "jazz"), page = 0)
            assertEquals(listOf("jazz-geo-0"), repository.state.value.globeStations.map { it.id })

            repository.loadGlobe(RadioStationSearchQuery(text = "jazz"), page = 1)
            assertEquals(listOf("jazz-geo-0", "jazz-geo-1"), repository.state.value.globeStations.map { it.id })
            assertEquals(1, repository.state.value.globePageIndex)

            repository.loadGlobe(RadioStationSearchQuery(text = "rock"), page = 1)
            assertEquals(listOf("rock-geo-0"), repository.state.value.globeStations.map { it.id })
            assertEquals(0, repository.state.value.globePageIndex)
        } finally {
            driver.close()
        }
    }

    @Test
    fun loadGlobeAutoPrefetchesUntilBalancedCapThenLeavesManualLoadMore() = runBlocking {
        val (database, driver) = newInMemoryPhoebeDatabase()

        val geoOffsets = mutableListOf<Int>()
        val engine = MockEngine { request ->
            val hasGeoInfo = request.url.parameters["has_geo_info"] == "true"
            val offset = request.url.parameters["offset"]?.toIntOrNull() ?: 0
            if (hasGeoInfo) geoOffsets.add(offset)
            val content = if (hasGeoInfo) {
                val page = offset / 1000
                (0 until 1000).joinToString(prefix = "[", postfix = "]") { index ->
                    val id = "geo-${page}-$index"
                    """
                    {
                      "stationuuid": "$id",
                      "name": "$id",
                      "url": "https://example.com/$id",
                      "geo_lat": ${10.0 + (index % 80) / 10.0},
                      "geo_long": ${20.0 + (index % 120) / 10.0},
                      "countrycode": "US"
                    }
                    """.trimIndent()
                }
            } else {
                "[]"
            }
            respond(
                content = content,
                status = HttpStatusCode.OK,
                headers = headersOf(HttpHeaders.ContentType, "application/json")
            )
        }

        val httpClient = testHttpClient(engine)
        val sessionRepository = testSessionRepository(
            plexClient = PlexClient(httpClient),
            database = database,
            storage = PlatformStorage(),
            httpClient = httpClient,
        )
        val repository = testRadioRepository(
            database = database,
            radioBrowserClient = RadioBrowserClient(httpClient),
            subsonicClient = SubsonicClient(httpClient),
            sessionRepository = sessionRepository,
        )

        try {
            repository.loadGlobe(RadioStationSearchQuery(), page = 0)

            assertEquals(listOf(0, 1000, 2000, 3000, 4000), geoOffsets)
            assertEquals(5000, repository.state.value.globeLoadedStationCount)
            assertEquals(4, repository.state.value.globePageIndex)
            assertEquals(true, repository.state.value.canLoadNextGlobePage)
            assertEquals(false, repository.state.value.globeAutoPrefetching)
        } finally {
            driver.close()
        }
    }

    @Test
    fun loadGlobeCountryDrilldownAppendsAndDedupes() = runBlocking {
        val (database, driver) = newInMemoryPhoebeDatabase()

        val engine = MockEngine { request ->
            val country = request.url.parameters["countrycode"].orEmpty()
            val hasGeoInfo = request.url.parameters["has_geo_info"] == "true"
            val content = when {
                country == "DE" && hasGeoInfo -> """
                    [
                      {
                        "stationuuid": "global-de",
                        "name": "Global DE",
                        "url": "https://example.com/global-de",
                        "geo_lat": 51.0,
                        "geo_long": 10.0,
                        "countrycode": "DE"
                      },
                      {
                        "stationuuid": "country-de",
                        "name": "Country DE",
                        "url": "https://example.com/country-de",
                        "geo_lat": 52.0,
                        "geo_long": 11.0,
                        "countrycode": "DE"
                      }
                    ]
                """.trimIndent()
                country.isBlank() && hasGeoInfo -> """
                    [{
                      "stationuuid": "global-de",
                      "name": "Global DE",
                      "url": "https://example.com/global-de",
                      "geo_lat": 51.0,
                      "geo_long": 10.0,
                      "countrycode": "DE"
                    }]
                """.trimIndent()
                else -> "[]"
            }
            respond(
                content = content,
                status = HttpStatusCode.OK,
                headers = headersOf(HttpHeaders.ContentType, "application/json")
            )
        }

        val httpClient = testHttpClient(engine)
        val sessionRepository = testSessionRepository(
            plexClient = PlexClient(httpClient),
            database = database,
            storage = PlatformStorage(),
            httpClient = httpClient,
        )
        val repository = testRadioRepository(
            database = database,
            radioBrowserClient = RadioBrowserClient(httpClient),
            subsonicClient = SubsonicClient(httpClient),
            sessionRepository = sessionRepository,
        )

        try {
            repository.loadGlobe(RadioStationSearchQuery(), page = 0, autoPrefetch = false)
            repository.loadGlobe(RadioStationSearchQuery(), page = 0, countryCode = "DE", autoPrefetch = false)

            assertEquals(listOf("global-de", "country-de"), repository.state.value.globeStations.map { it.id })
            assertEquals("DE", repository.state.value.globeMapScope.countryCode)
            assertEquals(2, repository.state.value.globeLoadedStationCount)
        } finally {
            driver.close()
        }
    }

    @Test
    fun loadGlobeViewportWithoutInferredCountryDoesNotReloadGlobalPage() = runBlocking {
        val (database, driver) = newInMemoryPhoebeDatabase()

        var searchRequests = 0
        val engine = MockEngine { request ->
            if (request.url.encodedPath.contains("/json/stations/search")) {
                searchRequests++
            }
            respond(
                content = "[]",
                status = HttpStatusCode.OK,
                headers = headersOf(HttpHeaders.ContentType, "application/json")
            )
        }

        val httpClient = testHttpClient(engine)
        val sessionRepository = testSessionRepository(
            plexClient = PlexClient(httpClient),
            database = database,
            storage = PlatformStorage(),
            httpClient = httpClient,
        )
        val repository = testRadioRepository(
            database = database,
            radioBrowserClient = RadioBrowserClient(httpClient),
            subsonicClient = SubsonicClient(httpClient),
            sessionRepository = sessionRepository,
        )

        try {
            repository.loadGlobe(RadioStationSearchQuery(), page = 0, autoPrefetch = false)
            assertEquals(1, searchRequests)

            repository.loadGlobe(
                query = RadioStationSearchQuery(),
                viewport = RadioMapViewport(
                    north = 55.0,
                    south = 45.0,
                    east = 15.0,
                    west = 5.0,
                    zoom = 6.0,
                ),
                autoPrefetch = false,
            )

            assertEquals(1, searchRequests)
            assertEquals(emptyList(), repository.state.value.globeStations)
        } finally {
            driver.close()
        }
    }

    @Test
    fun loadGlobeViewportLoadsInferredCountryOnlyOncePerScope() = runBlocking {
        val (database, driver) = newInMemoryPhoebeDatabase()

        val capturedCountries = mutableListOf<String?>()
        val engine = MockEngine { request ->
            if (request.url.encodedPath.contains("/json/stations/search") &&
                request.url.parameters["has_geo_info"] == "true"
            ) {
                capturedCountries.add(request.url.parameters["countrycode"])
            }
            val country = request.url.parameters["countrycode"].orEmpty()
            val hasGeoInfo = request.url.parameters["has_geo_info"] == "true"
            val content = when {
                country == "DE" && hasGeoInfo -> """
                    [{
                      "stationuuid": "de-extra",
                      "name": "DE Extra",
                      "url": "https://example.com/de-extra",
                      "geo_lat": 51.5,
                      "geo_long": 10.5,
                      "countrycode": "DE"
                    }]
                """.trimIndent()
                country.isBlank() && hasGeoInfo -> """
                    [{
                      "stationuuid": "de-seed",
                      "name": "DE Seed",
                      "url": "https://example.com/de-seed",
                      "geo_lat": 51.0,
                      "geo_long": 10.0,
                      "countrycode": "DE"
                    }]
                """.trimIndent()
                else -> "[]"
            }
            respond(
                content = content,
                status = HttpStatusCode.OK,
                headers = headersOf(HttpHeaders.ContentType, "application/json")
            )
        }

        val httpClient = testHttpClient(engine)
        val sessionRepository = testSessionRepository(
            plexClient = PlexClient(httpClient),
            database = database,
            storage = PlatformStorage(),
            httpClient = httpClient,
        )
        val repository = testRadioRepository(
            database = database,
            radioBrowserClient = RadioBrowserClient(httpClient),
            subsonicClient = SubsonicClient(httpClient),
            sessionRepository = sessionRepository,
        )
        val germanyViewport = RadioMapViewport(
            north = 55.0,
            south = 45.0,
            east = 15.0,
            west = 5.0,
            zoom = 6.0,
        )

        try {
            repository.loadGlobe(RadioStationSearchQuery(), page = 0, autoPrefetch = false)
            repository.loadGlobe(RadioStationSearchQuery(), viewport = germanyViewport, autoPrefetch = false)
            repository.loadGlobe(RadioStationSearchQuery(), viewport = germanyViewport, autoPrefetch = false)

            assertEquals(listOf(null, "DE"), capturedCountries)
            assertEquals(listOf("de-seed", "de-extra"), repository.state.value.globeStations.map { it.id })
            assertEquals("DE", repository.state.value.globeMapScope.countryCode)
        } finally {
            driver.close()
        }
    }
}
