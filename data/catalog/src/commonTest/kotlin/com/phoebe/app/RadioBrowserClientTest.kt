package com.phoebe.app

import com.phoebe.app.data.RadioBrowserClient
import com.phoebe.app.data.RadioBrowserClickDto
import com.phoebe.app.data.RadioBrowserStationDto
import com.phoebe.app.domain.RadioStationSearchQuery
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import io.ktor.serialization.kotlinx.json.json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlinx.serialization.json.Json
import kotlinx.coroutines.test.runTest

class RadioBrowserClientTest {
    @Test
    fun clickResponseAcceptsBooleanOk() {
        val response = Json.decodeFromString<RadioBrowserClickDto>(
            """
            {
              "ok": true,
              "message": "retrieved station url",
              "stationuuid": "360bb528-cea3-4e8e-84c6-3970c55bda71",
              "name": "Funk the Planet",
              "url": "https://streaming.live365.com/a01484"
            }
            """.trimIndent(),
        )

        assertTrue(response.ok)
        assertEquals("https://streaming.live365.com/a01484", response.url)
    }

    @Test
    fun stationResponseMapsGeoFields() {
        val station = Json.decodeFromString<RadioBrowserStationDto>(
            """
            {
              "stationuuid": "kexp",
              "name": "KEXP",
              "url": "https://stream.example/kexp",
              "homepage": "https://kexp.org",
              "favicon": "https://kexp.org/favicon.ico",
              "countrycode": "US",
              "state": "Washington",
              "geo_lat": 47.608,
              "geo_long": -122.335,
              "codec": "MP3",
              "bitrate": 128
            }
            """.trimIndent(),
        ).toRadioStation()

        assertEquals("Washington", station?.state)
        assertEquals(47.608, station?.geoLat)
        assertEquals(-122.335, station?.geoLong)
        assertTrue(station?.hasGeoLocation == true)
    }

    @Test
    fun globeSearchRequestsGeoInfo() = runTest {
        var capturedHasGeoInfo: String? = null
        val client = RadioBrowserClient(
            HttpClient(
                MockEngine { request ->
                    capturedHasGeoInfo = request.url.parameters["has_geo_info"]
                    respond(
                        content = "[]",
                        status = HttpStatusCode.OK,
                        headers = headersOf(HttpHeaders.ContentType, "application/json"),
                    )
                },
            ) {
                install(ContentNegotiation) {
                    json(Json { ignoreUnknownKeys = true })
                }
            },
        )

        client.search(RadioStationSearchQuery(text = "jazz"), requireGeoInfo = true)

        assertEquals("true", capturedHasGeoInfo)
    }
}
