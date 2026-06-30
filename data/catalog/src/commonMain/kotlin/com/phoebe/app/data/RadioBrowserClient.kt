package com.phoebe.app.data

import com.phoebe.app.domain.RadioCountry
import com.phoebe.app.domain.RadioFilterOption
import com.phoebe.app.domain.RadioStation
import com.phoebe.app.domain.RadioStationSearchQuery
import com.phoebe.app.domain.RadioStationSource
import com.phoebe.app.platform.PhoebeLog
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.parameter
import io.ktor.http.HttpHeaders
import kotlinx.coroutines.CancellationException
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@SingleIn(AppScope::class)
@Inject
class RadioBrowserClient(
    private val httpClient: HttpClient,
) {
    private var currentBaseUrl: String? = null

    suspend fun countries(limit: Int = DefaultCountryLimit): List<RadioCountry> =
        withMirrorFallback { baseUrl ->
            httpClient.get("$baseUrl/json/countries") {
                radioBrowserHeaders()
                parameter("hidebroken", true)
                parameter("order", "stationcount")
                parameter("reverse", true)
            }.body<List<RadioBrowserCountryDto>>()
                .mapNotNull { it.toRadioCountry() }
                .sortedByDescending { it.stationCount }
                .take(limit)
        }

    suspend fun languages(limit: Int = DefaultFilterLimit): List<RadioFilterOption> =
        withMirrorFallback { baseUrl ->
            httpClient.get("$baseUrl/json/languages") {
                radioBrowserHeaders()
                parameter("hidebroken", true)
                parameter("order", "stationcount")
                parameter("reverse", true)
            }.body<List<RadioBrowserNamedCountDto>>()
                .mapNotNull { it.toFilterOption() }
                .sortedByDescending { it.stationCount }
                .take(limit)
        }

    suspend fun tags(limit: Int = DefaultFilterLimit): List<RadioFilterOption> =
        withMirrorFallback { baseUrl ->
            httpClient.get("$baseUrl/json/tags") {
                radioBrowserHeaders()
                parameter("hidebroken", true)
                parameter("order", "stationcount")
                parameter("reverse", true)
            }.body<List<RadioBrowserNamedCountDto>>()
                .mapNotNull { it.toFilterOption() }
                .sortedByDescending { it.stationCount }
                .take(limit)
        }

    suspend fun popularStations(limit: Int = DefaultLimit): List<RadioStation> =
        withMirrorFallback { baseUrl ->
            httpClient.get("$baseUrl/json/stations/topclick/$limit") {
                radioBrowserHeaders()
                parameter("hidebroken", true)
            }.body<List<RadioBrowserStationDto>>().mapNotNull { it.toRadioStation() }
        }

    suspend fun search(
        query: RadioStationSearchQuery,
        limit: Int = DefaultLimit,
        offset: Int = 0,
        requireGeoInfo: Boolean = false,
    ): List<RadioStation> {
        val normalized = query.normalized()
        return withMirrorFallback { baseUrl ->
            httpClient.get("$baseUrl/json/stations/search") {
                radioBrowserHeaders()
                parameter("hidebroken", true)
                if (requireGeoInfo) parameter("has_geo_info", true)
                parameter("limit", limit)
                if (offset > 0) parameter("offset", offset)
                parameter("order", "clickcount")
                parameter("reverse", true)
                normalized.text.takeIf { it.isNotBlank() }?.let { parameter("name", it) }
                normalized.countryCode.takeIf { it.isNotBlank() }?.let { parameter("countrycode", it) }
                normalized.language.takeIf { it.isNotBlank() }?.let { parameter("language", it) }
                normalized.tag.takeIf { it.isNotBlank() }?.let { parameter("tag", it) }
            }.body<List<RadioBrowserStationDto>>().mapNotNull { it.toRadioStation() }
        }
    }

    suspend fun stationByUuid(uuid: String): RadioStation? {
        val normalized = uuid.trim().takeIf { it.isNotBlank() } ?: return null
        return withMirrorFallback { baseUrl ->
            httpClient.get("$baseUrl/json/stations/byuuid/${normalized}") {
                radioBrowserHeaders()
                parameter("hidebroken", true)
            }.body<List<RadioBrowserStationDto>>().mapNotNull { it.toRadioStation() }.firstOrNull()
        }
    }

    suspend fun resolvePlaybackUrl(station: RadioStation): String =
        if (station.source != RadioStationSource.RadioBrowser) {
            station.streamUrl
        } else {
            try {
                withMirrorFallback { baseUrl ->
                    val response: RadioBrowserClickDto = httpClient.get("$baseUrl/json/url/${station.id}") {
                        radioBrowserHeaders()
                    }.body()
                    response.url.takeIf { it.isNotBlank() } ?: station.streamUrl
                }
            } catch (error: CancellationException) {
                throw error
            } catch (error: Throwable) {
                PhoebeLog.d("RadioBrowserClient") {
                    "Radio Browser click resolution failed for ${station.id}; falling back to listed stream URL: ${error.message}"
                }
                station.streamUrl
            }
        }

    private suspend fun <T> withMirrorFallback(block: suspend (String) -> T): T {
        val candidates = listOfNotNull(currentBaseUrl) + DefaultMirrors.filterNot { it == currentBaseUrl }
        var lastError: Throwable? = null
        for (baseUrl in candidates) {
            try {
                val result = block(baseUrl)
                currentBaseUrl = baseUrl
                return result
            } catch (error: CancellationException) {
                throw error
            } catch (error: Throwable) {
                lastError = error
                PhoebeLog.d("RadioBrowserClient") { "Radio Browser request failed for $baseUrl: ${error.message}" }
            }
        }
        throw lastError ?: IllegalStateException("Radio Browser request failed.")
    }

    private fun io.ktor.client.request.HttpRequestBuilder.radioBrowserHeaders() {
        header(HttpHeaders.UserAgent, UserAgent)
        header(HttpHeaders.Accept, "application/json")
    }

    companion object {
        private const val DefaultLimit = 60
        private const val DefaultCountryLimit = 80
        private const val DefaultFilterLimit = 80
        private const val UserAgent = "Phoebe/1.0 radio-browser"
        private val DefaultMirrors = listOf(
            "https://de1.api.radio-browser.info",
            "https://fi1.api.radio-browser.info",
            "https://nl1.api.radio-browser.info",
        )
    }
}

@Serializable
data class RadioBrowserCountryDto(
    val name: String = "",
    @SerialName("iso_3166_1")
    val code: String = "",
    val stationcount: Int = 0,
) {
    fun toRadioCountry(): RadioCountry? {
        val title = name.trim().takeIf { it.isNotBlank() } ?: return null
        val countryCode = code.trim().uppercase().takeIf { it.isNotBlank() } ?: return null
        return RadioCountry(
            name = title,
            code = countryCode,
            stationCount = stationcount.coerceAtLeast(0),
        )
    }
}

@Serializable
data class RadioBrowserNamedCountDto(
    val name: String = "",
    val stationcount: Int = 0,
) {
    fun toFilterOption(): RadioFilterOption? {
        val title = name.trim().takeIf { it.isNotBlank() } ?: return null
        return RadioFilterOption(
            name = title.replaceFirstChar { it.uppercase() },
            value = title.lowercase(),
            stationCount = stationcount.coerceAtLeast(0),
        )
    }
}

@Serializable
data class RadioBrowserStationDto(
    val stationuuid: String = "",
    val name: String = "",
    val url: String = "",
    @SerialName("url_resolved")
    val urlResolved: String = "",
    val homepage: String = "",
    val favicon: String = "",
    val tags: String = "",
    val countrycode: String = "",
    val state: String = "",
    @SerialName("geo_lat")
    val geoLat: Double? = null,
    @SerialName("geo_long")
    val geoLong: Double? = null,
    val language: String = "",
    val codec: String = "",
    val bitrate: Int = 0,
    val clickcount: Int = 0,
    val lastcheckok: Int = 0,
) {
    fun toRadioStation(): RadioStation? {
        val id = stationuuid.takeIf { it.isNotBlank() } ?: return null
        val title = name.trim().takeIf { it.isNotBlank() } ?: return null
        val stream = urlResolved.ifBlank { url }.trim().takeIf { it.isNotBlank() } ?: return null
        return RadioStation(
            id = id,
            name = title,
            streamUrl = stream,
            homepageUrl = homepage.takeIf { it.isNotBlank() },
            faviconUrl = favicon.takeIf { it.isNotBlank() },
            description = null,
            category = null,
            tags = tags.takeIf { it.isNotBlank() },
            countryCode = countrycode.takeIf { it.isNotBlank() },
            state = state.takeIf { it.isNotBlank() },
            geoLat = geoLat?.takeIf { it in -90.0..90.0 },
            geoLong = geoLong?.takeIf { it in -180.0..180.0 },
            language = language.takeIf { it.isNotBlank() },
            codec = codec.takeIf { it.isNotBlank() },
            bitrateKbps = bitrate.takeIf { it > 0 },
            clickCount = clickcount.coerceAtLeast(0),
            source = RadioStationSource.RadioBrowser,
        )
    }
}

@Serializable
data class RadioBrowserClickDto(
    val ok: Boolean = false,
    val message: String = "",
    val stationuuid: String = "",
    val name: String = "",
    val url: String = "",
)
