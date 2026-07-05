package com.phoebe.app.backend.events

import com.phoebe.app.domain.ArtistEvent
import com.phoebe.app.domain.ArtistEventDate
import com.phoebe.app.domain.ArtistEventImage
import com.phoebe.app.domain.ArtistEventPrice
import com.phoebe.app.domain.ArtistEventVenue
import com.phoebe.app.domain.ArtistEventsResponse
import com.phoebe.app.domain.EventDataProvider
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.engine.cio.CIO
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation as ClientContentNegotiation
import io.ktor.client.request.get
import io.ktor.client.request.parameter
import io.ktor.client.statement.HttpResponse
import io.ktor.http.HttpStatusCode
import io.ktor.http.HttpHeaders
import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.application.Application
import io.ktor.server.application.ApplicationCall
import io.ktor.server.application.call
import io.ktor.server.application.install
import io.ktor.server.engine.embeddedServer
import io.ktor.server.netty.Netty
import io.ktor.server.plugins.BadRequestException
import io.ktor.server.plugins.contentnegotiation.ContentNegotiation
import io.ktor.server.plugins.cors.routing.CORS
import io.ktor.server.plugins.origin
import io.ktor.server.plugins.statuspages.StatusPages
import io.ktor.server.response.respond
import io.ktor.server.routing.get
import io.ktor.server.routing.routing
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
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

fun main() {
    val port = System.getenv("PORT")?.toIntOrNull() ?: 8088
    embeddedServer(Netty, host = "0.0.0.0", port = port) {
        eventsModule()
    }.start(wait = true)
}

fun Application.eventsModule(
    config: EventsBackendConfig = EventsBackendConfig.fromEnv(),
    httpClient: HttpClient = createEventsHttpClient(),
    clockMs: () -> Long = System::currentTimeMillis,
) {
    val service = ArtistEventsService(
        adapters = mapOf(
            EventDataProvider.Ticketmaster to TicketmasterEventsAdapter(httpClient, config.ticketmasterApiKey),
            EventDataProvider.SeatGeek to SeatGeekEventsAdapter(httpClient, config.seatGeekClientId),
        ),
        cache = ArtistEventsCache(config.cacheTtlMinutes * 60_000L, clockMs),
    )
    val limiter = IpRateLimiter(clockMs = clockMs)

    install(ContentNegotiation) {
        json(eventsJson)
    }
    install(CORS) {
        allowHeader(HttpHeaders.ContentType)
        allowHeader(HttpHeaders.Accept)
        allowMethod(io.ktor.http.HttpMethod.Get)
        if (config.allowedOrigins.isEmpty()) {
            anyHost()
        } else {
            val allowedOrigins = config.allowedOrigins.mapNotNull(AllowedCorsOrigin::parse)
            allowOrigins { origin ->
                val requestOrigin = AllowedCorsOrigin.parse(origin)
                requestOrigin != null && allowedOrigins.any { allowed -> allowed.matches(requestOrigin) }
            }
            allowedOrigins
                .filter { it.port == null }
                .forEach { allowed ->
                    allowHost(allowed.host, schemes = allowed.schemes)
                }
        }
    }
    install(StatusPages) {
        exception<BadRequestException> { call, cause ->
            call.respond(HttpStatusCode.BadRequest, ErrorResponse(cause.message ?: "Bad request."))
        }
        exception<MissingProviderCredentialException> { call, cause ->
            call.respond(HttpStatusCode.ServiceUnavailable, ErrorResponse(cause.message ?: "Provider credentials are not configured."))
        }
        exception<ProviderApiException> { call, cause ->
            call.respond(HttpStatusCode.BadGateway, ErrorResponse(cause.message ?: "Provider API request failed."))
        }
        exception<Throwable> { call, cause ->
            call.respond(HttpStatusCode.InternalServerError, ErrorResponse(cause.message ?: "Events backend failed."))
        }
    }

    routing {
        get("/health") {
            call.respond(
                HealthResponse(
                    ok = true,
                    service = "phoebe-events",
                    cacheTtlMinutes = config.cacheTtlMinutes,
                ),
            )
        }
        get("/v1/artist-events") {
            val remote = call.request.origin.remoteHost.ifBlank { "unknown" }
            if (!limiter.tryAcquire(remote)) {
                call.respond(HttpStatusCode.TooManyRequests, ErrorResponse("Too many requests."))
                return@get
            }
            val provider = call.providerParameter()
            val artist = call.request.queryParameters["artist"]
                ?.trim()
                ?.takeIf { it.isNotBlank() }
                ?: throw BadRequestException("artist is required.")
            val limit = call.request.queryParameters["limit"]?.toIntOrNull()?.coerceIn(1, 50) ?: 50
            call.respond(service.artistEvents(provider, artist, limit))
        }
    }
}

fun createEventsHttpClient(): HttpClient = HttpClient(CIO) {
    install(HttpTimeout) {
        requestTimeoutMillis = 20_000
        connectTimeoutMillis = 8_000
        socketTimeoutMillis = 20_000
    }
    install(ClientContentNegotiation) {
        json(eventsJson)
    }
}

private fun ApplicationCall.providerParameter(): EventDataProvider =
    when (request.queryParameters["provider"]?.trim()?.lowercase(Locale.US)) {
        "ticketmaster" -> EventDataProvider.Ticketmaster
        "seatgeek" -> EventDataProvider.SeatGeek
        else -> throw BadRequestException("provider must be ticketmaster or seatgeek.")
    }

data class EventsBackendConfig(
    val ticketmasterApiKey: String?,
    val seatGeekClientId: String?,
    val allowedOrigins: List<String>,
    val cacheTtlMinutes: Long,
) {
    companion object {
        fun fromEnv(): EventsBackendConfig =
            EventsBackendConfig(
                ticketmasterApiKey = System.getenv("TICKETMASTER_API_KEY")?.takeIf { it.isNotBlank() },
                seatGeekClientId = System.getenv("SEATGEEK_CLIENT_ID")?.takeIf { it.isNotBlank() },
                allowedOrigins = System.getenv("ALLOWED_ORIGINS")
                    .orEmpty()
                    .split(',')
                    .map { it.trim() }
                    .filter { it.isNotBlank() },
                cacheTtlMinutes = System.getenv("EVENTS_CACHE_TTL_MINUTES")?.toLongOrNull()?.coerceIn(1, 24 * 60) ?: 240,
            )
    }
}

private data class AllowedCorsOrigin(
    val host: String,
    val schemes: List<String>,
    val port: Int?,
) {
    fun matches(origin: AllowedCorsOrigin): Boolean =
        host.equals(origin.host, ignoreCase = true) &&
            (port == null || port == origin.port) &&
            schemes.any { scheme -> origin.schemes.any { it.equals(scheme, ignoreCase = true) } }

    companion object {
        fun parse(origin: String): AllowedCorsOrigin? {
            val trimmed = origin.trim().trimEnd('/')
            if (trimmed.isBlank()) return null
            val explicitScheme = trimmed.substringBefore("://", missingDelimiterValue = "")
                .takeIf { it == "http" || it == "https" }
            val withoutScheme = trimmed.removePrefix("https://").removePrefix("http://")
            val hostPort = withoutScheme.substringBefore('/')
            val host = hostPort.substringBefore(':').takeIf { it.isNotBlank() } ?: return null
            val port = hostPort.substringAfter(':', missingDelimiterValue = "").toIntOrNull()
            return AllowedCorsOrigin(
                host = host,
                schemes = explicitScheme?.let(::listOf) ?: listOf("https", "http"),
                port = port,
            )
        }
    }
}

class ArtistEventsService(
    private val adapters: Map<EventDataProvider, ArtistEventsAdapter>,
    private val cache: ArtistEventsCache,
) {
    suspend fun artistEvents(provider: EventDataProvider, artist: String, limit: Int): ArtistEventsResponse {
        val key = "${provider.name}:${artist.trim().lowercase(Locale.US)}:$limit"
        cache.get(key)?.let { return it }
        val adapter = adapters[provider] ?: error("Unsupported provider.")
        val response = ArtistEventsResponse(
            provider = provider,
            artist = artist,
            events = adapter.searchArtistEvents(artist, limit),
        )
        cache.put(key, response)
        return response
    }
}

interface ArtistEventsAdapter {
    suspend fun searchArtistEvents(artist: String, limit: Int): List<ArtistEvent>
}

class MissingProviderCredentialException(message: String) : RuntimeException(message)

class ProviderApiException(message: String) : RuntimeException(message)

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

class IpRateLimiter(
    private val maxRequests: Int = 120,
    private val windowMs: Long = 60_000,
    private val clockMs: () -> Long,
) {
    private val hits = ConcurrentHashMap<String, MutableList<Long>>()
    private val lastCleanupMs = AtomicLong(0L)

    fun tryAcquire(key: String): Boolean {
        val now = clockMs()
        cleanupExpiredBucketsIfNeeded(now)
        val bucket = hits.getOrPut(key) { mutableListOf() }
        synchronized(bucket) {
            bucket.removeAll { now - it > windowMs }
            if (bucket.size >= maxRequests) return false
            bucket += now
            return true
        }
    }

    private fun cleanupExpiredBucketsIfNeeded(now: Long) {
        val intervalMs = windowMs.coerceAtMost(60_000L).coerceAtLeast(1_000L)
        val last = lastCleanupMs.get()
        if (now - last < intervalMs || !lastCleanupMs.compareAndSet(last, now)) return
        hits.forEach { (key, bucket) ->
            synchronized(bucket) {
                bucket.removeAll { now - it > windowMs }
                if (bucket.isEmpty()) {
                    hits.remove(key, bucket)
                }
            }
        }
    }
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
        response.requireSuccess("Ticketmaster")
        val payload: JsonObject = response.body()
        val events = payload["_embedded"]
            ?.jsonObject
            ?.get("events")
            ?.jsonArray
            .orEmpty()
        return events.mapNotNull { raw ->
            val dto = runCatching { eventsJson.decodeFromJsonElement(TicketmasterEvent.serializer(), raw) }.getOrNull()
                ?: return@mapNotNull null
            dto.toArtistEvent(raw)
        }
    }
}

class SeatGeekEventsAdapter(
    private val httpClient: HttpClient,
    private val clientId: String?,
) : ArtistEventsAdapter {
    override suspend fun searchArtistEvents(artist: String, limit: Int): List<ArtistEvent> {
        val id = clientId ?: throw MissingProviderCredentialException("SEATGEEK_CLIENT_ID is not configured.")
        val response = httpClient.get("https://api.seatgeek.com/2/events") {
            parameter("client_id", id)
            parameter("q", artist)
            parameter("type", "concert")
            parameter("per_page", limit.coerceIn(1, 50))
            parameter("sort", "datetime_utc.asc")
        }
        response.requireSuccess("SeatGeek")
        val payload: JsonObject = response.body()
        return payload["events"]
            ?.jsonArray
            .orEmpty()
            .mapNotNull { raw ->
                val dto = runCatching { eventsJson.decodeFromJsonElement(SeatGeekEvent.serializer(), raw) }.getOrNull()
                    ?: return@mapNotNull null
                dto.toArtistEvent(raw)
            }
    }
}

@Serializable
data class HealthResponse(
    val ok: Boolean,
    val service: String,
    val cacheTtlMinutes: Long,
)

@Serializable
data class ErrorResponse(
    val error: String,
)

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

@Serializable
private data class SeatGeekEvent(
    val id: Long,
    val title: String,
    val url: String? = null,
    val status: String? = null,
    val datetime_utc: String? = null,
    val datetime_local: String? = null,
    val venue: SeatGeekVenue? = null,
    val performers: List<SeatGeekPerformer> = emptyList(),
    val stats: SeatGeekStats? = null,
    val lowest_price: Double? = null,
    val average_price: Double? = null,
    val highest_price: Double? = null,
)

@Serializable
private data class SeatGeekVenue(
    val name: String? = null,
    val city: String? = null,
    val state: String? = null,
    val country: String? = null,
    val address: String? = null,
    val location: SeatGeekLocation? = null,
)

@Serializable
private data class SeatGeekLocation(
    val lat: Double? = null,
    val lon: Double? = null,
)

@Serializable
private data class SeatGeekPerformer(
    val image: String? = null,
    val images: JsonObject? = null,
)

@Serializable
private data class SeatGeekStats(
    val lowest_price: Double? = null,
    val average_price: Double? = null,
    val highest_price: Double? = null,
)

private fun SeatGeekEvent.toArtistEvent(raw: JsonElement): ArtistEvent {
    val min = lowest_price ?: stats?.lowest_price
    val max = highest_price ?: stats?.highest_price
    return ArtistEvent(
        id = id.toString(),
        provider = EventDataProvider.SeatGeek,
        title = title,
        url = url,
        status = status,
        date = ArtistEventDate(
            localDate = datetime_local?.substringBefore('T'),
            localTime = datetime_local?.substringAfter('T')?.takeIf { it != datetime_local },
            dateTimeUtc = datetime_utc,
        ),
        images = performers.flatMap { it.toImages() }.distinctBy { it.url },
        venue = venue?.let {
            ArtistEventVenue(
                name = it.name,
                city = it.city,
                region = it.state,
                country = it.country,
                address = it.address,
                latitude = it.location?.lat,
                longitude = it.location?.lon,
            )
        },
        price = ArtistEventPrice(
            min = min,
            max = max,
            currency = "USD",
            display = formatPriceRange(min, max, "USD"),
        ).takeIf { it.min != null || it.max != null },
        raw = raw,
    )
}

private fun SeatGeekPerformer.toImages(): List<ArtistEventImage> {
    val direct = image?.takeIf { it.isNotBlank() }?.let { ArtistEventImage(url = it) }
    val nested = images?.values
        ?.mapNotNull { element -> (element as? JsonPrimitive)?.contentOrNull?.takeIf { it.startsWith("http") } }
        ?.map { ArtistEventImage(url = it) }
        .orEmpty()
    return listOfNotNull(direct) + nested
}

private fun HttpResponse.requireSuccess(providerName: String) {
    if (status.value !in 200..299) {
        throw ProviderApiException("$providerName API returned HTTP ${status.value}.")
    }
}

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

private val eventsJson = Json {
    ignoreUnknownKeys = true
    explicitNulls = false
}
