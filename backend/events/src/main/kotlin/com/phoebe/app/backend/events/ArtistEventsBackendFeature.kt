package com.phoebe.app.backend.events

import com.phoebe.app.backend.MissingProviderCredentialException
import com.phoebe.app.backend.BackendSingleFlight
import com.phoebe.app.backend.PhoebeBackendEnvironment
import com.phoebe.app.backend.PhoebeBackendFeature
import com.phoebe.app.backend.normalizedBackendCacheKey
import com.phoebe.app.backend.phoebeBackendJson
import com.phoebe.app.backend.requireProviderSuccess
import com.phoebe.app.backend.optionalBackendQueryParameter
import com.phoebe.app.backend.requiredBackendQueryParameter
import com.phoebe.app.backend.tryAcquire
import com.phoebe.app.domain.ArtistEvent
import com.phoebe.app.domain.ArtistEventDate
import com.phoebe.app.domain.ArtistEventImage
import com.phoebe.app.domain.ArtistEventPrice
import com.phoebe.app.domain.ArtistEventVenue
import com.phoebe.app.domain.ArtistEventsResponse
import com.phoebe.app.domain.EventDataProvider
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.get
import io.ktor.client.request.parameter
import io.ktor.server.application.Application
import io.ktor.server.application.ApplicationCall
import io.ktor.server.application.call
import io.ktor.server.plugins.BadRequestException
import io.ktor.server.response.respond
import io.ktor.server.routing.get
import io.ktor.server.routing.routing
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.decodeFromJsonElement
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicLong

class ArtistEventsBackendFeature : PhoebeBackendFeature {
    override fun install(application: Application, environment: PhoebeBackendEnvironment) {
        val service = ArtistEventsService(
            adapters = mapOf(
                EventDataProvider.Ticketmaster to TicketmasterEventsAdapter(
                    environment.httpClient,
                    environment.config.ticketmasterApiKey,
                ),
            ),
            cache = ArtistEventsCache(environment.config.cacheTtlMinutes * 60_000L, environment.clockMs),
        )

        application.routing {
            get("/v1/artist-events") {
                if (!call.tryAcquire(environment, "artist-events")) return@get
                val provider = call.providerParameter()
                val artist = call.requiredBackendQueryParameter("artist")
                val limit = call.request.queryParameters["limit"]?.toIntOrNull()?.coerceIn(1, 50) ?: 50
                call.respond(service.artistEvents(provider, artist, limit))
            }
        }
    }
}

private fun ApplicationCall.providerParameter(): EventDataProvider =
    when (optionalBackendQueryParameter("provider")?.lowercase(Locale.US)) {
        "ticketmaster", null -> EventDataProvider.Ticketmaster
        else -> throw BadRequestException("provider must be ticketmaster.")
    }

class ArtistEventsService(
    private val adapters: Map<EventDataProvider, ArtistEventsAdapter>,
    private val cache: ArtistEventsCache,
    private val singleFlight: BackendSingleFlight = BackendSingleFlight(),
) {
    suspend fun artistEvents(provider: EventDataProvider, artist: String, limit: Int): ArtistEventsResponse {
        val key = "${provider.name}:${artist.normalizedBackendCacheKey()}:$limit"
        cache.get(key)?.let { return it }
        return singleFlight.run(key) {
            cache.get(key)?.let { return@run it }
            val adapter = adapters[provider] ?: error("Unsupported provider.")
            val response = ArtistEventsResponse(
                provider = provider,
                artist = artist,
                events = adapter.searchArtistEvents(artist, limit),
            )
            cache.put(key, response)
            response
        }
    }
}

interface ArtistEventsAdapter {
    suspend fun searchArtistEvents(artist: String, limit: Int): List<ArtistEvent>
}

class ArtistEventsCache(
    private val ttlMs: Long,
    private val clockMs: () -> Long,
    private val maxEntries: Int = 512,
) {
    private val entries = ConcurrentHashMap<String, CacheEntry>()
    private val lastCleanupMs = AtomicLong(0L)

    fun get(key: String): ArtistEventsResponse? {
        val now = clockMs()
        cleanupExpiredIfNeeded(now)
        val entry = entries[key] ?: return null
        return if (now - entry.createdAtMs <= ttlMs) {
            entry.response
        } else {
            entries.remove(key, entry)
            null
        }
    }

    fun put(key: String, response: ArtistEventsResponse) {
        val now = clockMs()
        cleanupExpiredIfNeeded(now)
        entries[key] = CacheEntry(now, response)
        trimToMaxEntries()
    }

    private fun cleanupExpiredIfNeeded(now: Long) {
        val intervalMs = ttlMs.coerceAtMost(60_000L).coerceAtLeast(1_000L)
        val last = lastCleanupMs.get()
        if (now - last < intervalMs || !lastCleanupMs.compareAndSet(last, now)) return
        entries.forEach { (key, entry) ->
            if (now - entry.createdAtMs > ttlMs) {
                entries.remove(key, entry)
            }
        }
    }

    private fun trimToMaxEntries() {
        val overflow = entries.size - maxEntries
        if (overflow <= 0) return
        entries.entries
            .sortedBy { it.value.createdAtMs }
            .take(overflow)
            .forEach { (key, entry) -> entries.remove(key, entry) }
    }

    private data class CacheEntry(
        val createdAtMs: Long,
        val response: ArtistEventsResponse,
    )
}

class TicketmasterEventsAdapter(
    private val httpClient: HttpClient,
    private val apiKey: String?,
) : ArtistEventsAdapter {
    override suspend fun searchArtistEvents(artist: String, limit: Int): List<ArtistEvent> {
        val key = apiKey ?: throw MissingProviderCredentialException("TICKETMASTER_API_KEY is not configured.")
        val response = httpClient.get("https://app.ticketmaster.com/discovery/v2/events.json") {
            parameter("apikey", key)
            parameter("keyword", artist)
            parameter("classificationName", "music")
            parameter("sort", "date,asc")
            parameter("size", limit.coerceIn(1, 50))
        }
        response.requireProviderSuccess("Ticketmaster")
        val payload: JsonObject = response.body()
        val events = payload["_embedded"]
            ?.jsonObject
            ?.get("events")
            ?.jsonArray
            .orEmpty()
        return events.mapNotNull { raw ->
            val dto = runCatching { phoebeBackendJson.decodeFromJsonElement(TicketmasterEvent.serializer(), raw) }.getOrNull()
                ?: return@mapNotNull null
            dto.toArtistEvent(raw)
        }
    }
}

@Serializable
private data class TicketmasterEvent(
    val id: String,
    val name: String,
    val url: String? = null,
    val images: List<TicketmasterImage> = emptyList(),
    val dates: TicketmasterDates? = null,
    val priceRanges: List<TicketmasterPriceRange> = emptyList(),
    @SerialName("_embedded") val embedded: TicketmasterEmbedded? = null,
)

@Serializable
private data class TicketmasterDates(
    val start: TicketmasterStartDate? = null,
    val timezone: String? = null,
    val status: TicketmasterStatus? = null,
)

@Serializable
private data class TicketmasterStartDate(
    val localDate: String? = null,
    val localTime: String? = null,
    val dateTime: String? = null,
)

@Serializable
private data class TicketmasterStatus(
    val code: String? = null,
)

@Serializable
private data class TicketmasterImage(
    val url: String,
    val width: Int? = null,
    val height: Int? = null,
    val ratio: String? = null,
    val attribution: String? = null,
)

@Serializable
private data class TicketmasterPriceRange(
    val min: Double? = null,
    val max: Double? = null,
    val currency: String? = null,
)

@Serializable
private data class TicketmasterEmbedded(
    val venues: List<TicketmasterVenue> = emptyList(),
)

@Serializable
private data class TicketmasterVenue(
    val name: String? = null,
    val city: TicketmasterNamedValue? = null,
    val state: TicketmasterNamedValue? = null,
    val country: TicketmasterNamedValue? = null,
    val address: TicketmasterAddress? = null,
    val location: TicketmasterLocation? = null,
)

@Serializable
private data class TicketmasterNamedValue(
    val name: String? = null,
    val stateCode: String? = null,
    val countryCode: String? = null,
)

@Serializable
private data class TicketmasterAddress(
    val line1: String? = null,
)

@Serializable
private data class TicketmasterLocation(
    val latitude: String? = null,
    val longitude: String? = null,
)

private fun TicketmasterEvent.toArtistEvent(raw: JsonElement): ArtistEvent {
    val firstPrice = priceRanges.firstOrNull()
    return ArtistEvent(
        id = id,
        provider = EventDataProvider.Ticketmaster,
        title = name,
        url = url,
        status = dates?.status?.code,
        date = ArtistEventDate(
            localDate = dates?.start?.localDate,
            localTime = dates?.start?.localTime,
            dateTimeUtc = dates?.start?.dateTime,
            timezone = dates?.timezone,
        ),
        images = images.map {
            ArtistEventImage(
                url = it.url,
                width = it.width,
                height = it.height,
                ratio = it.ratio,
                attribution = it.attribution,
            )
        },
        venue = embedded?.venues?.firstOrNull()?.toArtistEventVenue(),
        price = firstPrice?.let {
            ArtistEventPrice(
                min = it.min,
                max = it.max,
                currency = it.currency,
                display = formatPriceRange(it.min, it.max, it.currency),
            )
        },
        raw = raw,
    )
}

private fun TicketmasterVenue.toArtistEventVenue(): ArtistEventVenue =
    ArtistEventVenue(
        name = name,
        city = city?.name,
        region = state?.stateCode ?: state?.name,
        country = country?.countryCode ?: country?.name,
        address = address?.line1,
        latitude = location?.latitude?.toDoubleOrNull(),
        longitude = location?.longitude?.toDoubleOrNull(),
    )



private fun formatPriceRange(min: Double?, max: Double?, currency: String?): String? {
    val symbol = when (currency?.uppercase(Locale.US)) {
        "USD" -> "$"
        "EUR" -> "€"
        "GBP" -> "£"
        else -> currency?.plus(" ").orEmpty()
    }
    return when {
        min != null && max != null -> {
            if (min == max) "${symbol}${min.cleanPrice()}" else "${symbol}${min.cleanPrice()}-${symbol}${max.cleanPrice()}"
        }
        min != null -> "From ${symbol}${min.cleanPrice()}"
        max != null -> "Up to ${symbol}${max.cleanPrice()}"
        else -> null
    }
}

private fun Double.cleanPrice(): String =
    if (isFinite() && this % 1.0 == 0.0) toLong().toString() else "%.2f".format(Locale.US, this)
