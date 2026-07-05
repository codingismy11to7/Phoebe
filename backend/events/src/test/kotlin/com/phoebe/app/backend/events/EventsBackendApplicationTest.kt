package com.phoebe.app.backend.events

import com.phoebe.app.domain.ArtistEventsResponse
import com.phoebe.app.domain.EventDataProvider
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation as ClientContentNegotiation
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.testing.testApplication
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class EventsBackendApplicationTest {
    private val json = Json {
        ignoreUnknownKeys = true
        explicitNulls = false
    }

    @Test
    fun healthReturnsOk() = testApplication {
        application {
            eventsModule(config = testConfig(), httpClient = mockProviderClient("{}"))
        }

        val response = client.get("/health")

        assertEquals(HttpStatusCode.OK, response.status)
        assertTrue(response.bodyAsText().contains("phoebe-events"))
    }

    @Test
    fun artistEventsNormalizesTicketmasterAndKeepsRawPayload() = testApplication {
        var providerUrl = ""
        var providerQuery = ""
        val providerClient = mockProviderClient(ticketmasterPayload()) { url, query ->
            providerUrl = url
            providerQuery = query
        }
        application {
            eventsModule(config = testConfig(ticketmasterApiKey = "tm-key"), httpClient = providerClient)
        }
        val routeClient = createClient {
            install(ClientContentNegotiation) {
                json(json)
            }
        }

        val response = routeClient.get("/v1/artist-events?provider=ticketmaster&artist=Phoebe%20Bridgers&limit=5")

        assertEquals(HttpStatusCode.OK, response.status)
        assertTrue(providerUrl.contains("ticketmaster.com/discovery/v2/events.json"))
        assertTrue(providerQuery.contains("apikey=tm-key"))
        assertTrue(providerQuery.contains("keyword=Phoebe+Bridgers") || providerQuery.contains("keyword=Phoebe%20Bridgers"))
        val body = json.decodeFromString<ArtistEventsResponse>(response.bodyAsText())
        assertEquals(EventDataProvider.Ticketmaster, body.provider)
        assertEquals("Phoebe Bridgers", body.artist)
        assertEquals(1, body.events.size)
        val event = body.events.single()
        assertEquals("tm-1", event.id)
        assertEquals("Phoebe Bridgers", event.title)
        assertEquals("onsale", event.status)
        assertEquals("2026-08-21", event.date.localDate)
        assertEquals("The Anthem", event.venue?.name)
        assertEquals("USD", event.price?.currency)
        assertEquals("\$40", event.price?.display)
        assertNotNull(event.raw)
    }

    @Test
    fun artistEventsReturnsBadGatewayWhenTicketmasterReturnsError() = testApplication {
        application {
            eventsModule(
                config = testConfig(ticketmasterApiKey = "tm-key"),
                httpClient = mockProviderClient("""{"fault":"nope"}""", status = HttpStatusCode.Unauthorized),
            )
        }

        val response = client.get("/v1/artist-events?provider=ticketmaster&artist=Phoebe&limit=1")

        assertEquals(HttpStatusCode.BadGateway, response.status)
        assertTrue(response.bodyAsText().contains("Ticketmaster API returned HTTP 401"))
    }

    @Test
    fun artistEventsReturnsBadGatewayWhenSeatGeekReturnsError() = testApplication {
        application {
            eventsModule(
                config = testConfig(seatGeekClientId = "sg-id"),
                httpClient = mockProviderClient("""{"error":"rate limited"}""", status = HttpStatusCode.TooManyRequests),
            )
        }

        val response = client.get("/v1/artist-events?provider=seatgeek&artist=Phoebe&limit=1")

        assertEquals(HttpStatusCode.BadGateway, response.status)
        assertTrue(response.bodyAsText().contains("SeatGeek API returned HTTP 429"))
    }

    @Test
    fun artistEventsReturnsServiceUnavailableWhenCredentialsAreMissing() = testApplication {
        application {
            eventsModule(config = testConfig(ticketmasterApiKey = null), httpClient = mockProviderClient("{}"))
        }

        val response = client.get("/v1/artist-events?provider=ticketmaster&artist=Phoebe&limit=1")

        assertEquals(HttpStatusCode.ServiceUnavailable, response.status)
        assertTrue(response.bodyAsText().contains("TICKETMASTER_API_KEY"))
    }

    @Test
    fun corsAllowsConfiguredOriginWithPort() = testApplication {
        application {
            eventsModule(
                config = testConfig(allowedOrigins = listOf("http://localhost:3000")),
                httpClient = mockProviderClient("{}"),
            )
        }

        val response = client.get("/health") {
            header(HttpHeaders.Origin, "http://localhost:3000")
        }

        assertEquals(HttpStatusCode.OK, response.status)
        assertEquals("http://localhost:3000", response.headers[HttpHeaders.AccessControlAllowOrigin])
    }

    private fun mockProviderClient(
        payload: String,
        status: HttpStatusCode = HttpStatusCode.OK,
        onRequest: (url: String, query: String) -> Unit = { _, _ -> },
    ): HttpClient =
        HttpClient(
            MockEngine { request ->
                onRequest(request.url.toString(), request.url.encodedQuery)
                respond(
                    content = payload,
                    status = status,
                    headers = headersOf(HttpHeaders.ContentType, ContentType.Application.Json.toString()),
                )
            },
        ) {
            install(ClientContentNegotiation) {
                json(json)
            }
        }

    private fun testConfig(
        ticketmasterApiKey: String? = "tm-key",
        seatGeekClientId: String? = "sg-id",
        allowedOrigins: List<String> = emptyList(),
    ): EventsBackendConfig =
        EventsBackendConfig(
            ticketmasterApiKey = ticketmasterApiKey,
            seatGeekClientId = seatGeekClientId,
            allowedOrigins = allowedOrigins,
            cacheTtlMinutes = 240,
        )

    private fun ticketmasterPayload(): String =
        """
        {
          "_embedded": {
            "events": [
              {
                "id": "tm-1",
                "name": "Phoebe Bridgers",
                "url": "https://tickets.example/tm-1",
                "images": [
                  { "url": "https://images.example/tm-1.jpg", "width": 1200, "height": 675, "ratio": "16_9" }
                ],
                "dates": {
                  "start": {
                    "localDate": "2026-08-21",
                    "localTime": "20:00:00",
                    "dateTime": "2026-08-22T01:00:00Z",
                    "timezone": "America/New_York"
                  },
                  "status": { "code": "onsale" }
                },
                "priceRanges": [
                  { "min": 40.0, "max": 40.0, "currency": "USD" }
                ],
                "_embedded": {
                  "venues": [
                    {
                      "name": "The Anthem",
                      "city": { "name": "Washington" },
                      "state": { "stateCode": "DC" },
                      "country": { "countryCode": "US" },
                      "address": { "line1": "901 Wharf St SW" },
                      "location": { "latitude": "38.880", "longitude": "-77.026" }
                    }
                  ]
                }
              }
            ]
          }
        }
        """.trimIndent()
}
