package com.phoebe.app.data

import com.phoebe.app.domain.Album
import com.phoebe.app.domain.Artist
import com.phoebe.app.domain.MusicLibrary
import com.phoebe.app.domain.PlexPin
import com.phoebe.app.domain.PlexServer
import com.phoebe.app.domain.Playlist
import com.phoebe.app.domain.Track
import com.phoebe.app.domain.TrackMetadataUpdate
import com.phoebe.app.platform.PhoebeLog
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.get
import io.ktor.client.request.delete
import io.ktor.client.request.header
import io.ktor.client.request.parameter
import io.ktor.client.request.post
import io.ktor.client.request.put
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpHeaders
import io.ktor.http.URLBuilder
import io.ktor.http.appendPathSegments
import io.ktor.http.encodeURLParameter
import io.ktor.http.isSuccess
import io.ktor.serialization.kotlinx.json.json
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

class PlexClient(
    private val httpClient: HttpClient = HttpClient {
        install(ContentNegotiation) {
            json(PlexJson)
        }
    },
) {
    /** Last base URL that accepted API calls for this server — usually plain LAN HTTP. */
    private val apiBaseCache = mutableMapOf<String, String>()
    suspend fun createPin(): PlexPin {
        val response: PlexPinResponse = httpClient.post("https://plex.tv/api/v2/pins") {
            plexHeaders()
            parameter("strong", true)
        }.body()
        return PlexPin(
            id = response.id,
            code = response.code,
            authUrl = "https://app.plex.tv/auth#?clientID=$ClientIdentifier&code=${response.code}&context%5Bdevice%5D%5Bproduct%5D=Phoebe",
        )
    }

    suspend fun pollPin(pinId: Long): String? {
        val response: PlexPinResponse = httpClient.get("https://plex.tv/api/v2/pins/$pinId") {
            plexHeaders()
        }.body()
        return response.authToken
    }

    suspend fun userName(token: String): String {
        val response: PlexUserResponse = httpClient.get("https://plex.tv/api/v2/user") {
            plexHeaders(token)
        }.body()
        return response.username ?: "Plex listener"
    }

    suspend fun servers(token: String): List<PlexServer> {
        val devices: List<PlexDeviceDto> = httpClient.get("https://plex.tv/api/v2/resources") {
            plexHeaders(token)
            parameter("includeHttps", 1)
            parameter("includeRelay", 1)
        }.body()

        return devices
            .filter { "server" in it.provides }
            .mapNotNull { device ->
                val connections = device.connections
                if (connections.isEmpty()) return@mapNotNull null
                val advertised = connections.map { it.uri.trimEnd('/') }.filter { it.isNotBlank() }.distinct()
                val local = connections.filter { it.local }.map { it.uri.trimEnd('/') }.distinct()
                val allUris = expandConnectionUris(advertised)
                val bestUri = bestReachableBaseUri(
                    advertisedUris = advertised,
                    localUris = local,
                    httpsRequired = device.httpsRequired,
                ) ?: return@mapNotNull null
                PlexServer(
                    id = device.clientIdentifier,
                    name = device.name,
                    uri = bestUri,
                    owned = device.owned,
                    connectionUris = allUris,
                    advertisedConnectionUris = advertised,
                    localConnectionUris = local,
                    accessToken = device.accessToken?.takeIf { it.isNotBlank() },
                    httpsRequired = device.httpsRequired,
                )
            }
    }

    suspend fun musicLibraries(server: PlexServer, token: String): List<MusicLibrary> {
        val response = plexGet<PlexMediaContainerResponse>(server, token, "/library/sections")
        return response.mediaContainer.directories
            .filter { it.type == "artist" }
            .map { MusicLibrary(key = it.key, title = it.title) }
    }

    suspend fun resolveFastestBase(server: PlexServer, token: String, timeoutMs: Long = 1_500L): String? = coroutineScope {
        val candidates = server.reachableBaseUris(apiBaseCache[server.id])
        if (candidates.isEmpty()) return@coroutineScope null
        val results = Channel<String>(capacity = candidates.size)
        candidates.forEach { base ->
            launch {
                val ok = withTimeoutOrNull(timeoutMs) {
                    runCatching {
                        val response = httpClient.get("$base/identity") {
                            plexServerAuth(token)
                            header(HttpHeaders.Accept, "application/json")
                        }
                        response.status.isSuccess()
                    }.getOrDefault(false)
                } == true
                if (ok) results.trySend(base)
            }
        }
        val winner = withTimeoutOrNull(timeoutMs + 250L) { results.receive() }
        if (winner != null) apiBaseCache[server.id] = winner
        winner
    }

    suspend fun artists(server: PlexServer, library: MusicLibrary, token: String): List<Artist> {
        val response = plexGet<PlexMediaContainerResponse>(server, token, "/library/sections/${library.key}/all")
        val fromDirectories = response.mediaContainer.directories.map {
            Artist(
                id = it.ratingKey ?: it.key.ratingKeyFromMetadataPath() ?: it.key,
                title = it.title,
                thumbUrl = it.thumb?.let { thumb -> server.assetUrl(thumb, token) },
                albumCount = it.leafCount ?: 0,
                dateAddedMs = it.addedAt?.times(1000L),
                genre = it.primaryGenreTag(),
                mood = it.primaryMoodTag(),
                style = it.primaryStyleTag(),
                rating = it.userRating.toStarRating(),
            )
        }
        val meta = response.mediaContainer.metadata
        val typesPresent = meta.any { !it.type.isNullOrBlank() }
        val fromMetadata = meta.mapNotNull { item ->
            if (typesPresent && item.type != null && item.type != "artist") {
                return@mapNotNull null
            }
            Artist(
                id = item.ratingKey,
                title = item.title,
                thumbUrl = item.thumb?.let { thumb -> server.assetUrl(thumb, token) },
                albumCount = item.leafCount ?: 0,
                dateAddedMs = item.addedAt?.times(1000L),
                genre = item.primaryGenreTag(),
                mood = item.primaryMoodTag(),
                style = item.primaryStyleTag(),
                rating = item.userRating.toStarRating(),
            )
        }
        return (fromDirectories + fromMetadata).distinctBy { it.id }
    }

    suspend fun albums(server: PlexServer, library: MusicLibrary, token: String): List<Album> {
        val response = plexGet<PlexMediaContainerResponse>(server, token, "/library/sections/${library.key}/albums")
        val fromDirectories = response.mediaContainer.directories.mapNotNull {
            val id = it.ratingKey ?: it.key.ratingKeyFromMetadataPath()
            id?.let { albumId ->
                Album(
                    id = albumId,
                    title = it.title,
                    artist = it.parentTitle ?: "Unknown artist",
                    year = it.year,
                    thumbUrl = it.thumb?.let { thumb -> server.assetUrl(thumb, token) },
                    dateAddedMs = it.addedAt?.times(1000L),
                    genre = it.primaryGenreTag(),
                    mood = it.primaryMoodTag(),
                    style = it.primaryStyleTag(),
                    rating = it.userRating.toStarRating(),
                )
            }
        }
        val fromMetadata = response.mediaContainer.metadata.map {
            Album(
                id = it.ratingKey,
                title = it.title,
                artist = it.parentTitle ?: "Unknown artist",
                year = it.year,
                thumbUrl = it.thumb?.let { thumb -> server.assetUrl(thumb, token) },
                dateAddedMs = it.addedAt?.times(1000L),
                genre = it.primaryGenreTag(),
                mood = it.primaryMoodTag(),
                style = it.primaryStyleTag(),
                rating = it.userRating.toStarRating(),
            )
        }
        return (fromDirectories + fromMetadata).distinctBy { it.id }
    }

    suspend fun playlists(server: PlexServer, token: String): List<Playlist> {
        val response = plexGet<PlexMediaContainerResponse>(server, token, "/playlists")
        return response.mediaContainer.metadata.map {
            Playlist(
                id = it.ratingKey,
                title = it.title,
                trackCount = it.leafCount ?: 0,
                key = it.key,
                thumbUrl = it.thumb?.let { thumb -> server.assetUrl(thumb, token) },
                rating = it.userRating.toStarRating(),
            )
        }
    }

    suspend fun playlistTracks(server: PlexServer, playlist: Playlist, token: String): List<Track> {
        val path = playlist.key?.takeIf { it.isNotBlank() }?.let {
            if (it.startsWith("/")) it else "/$it"
        } ?: "/playlists/${playlist.id}/items"
        val response = plexGet<PlexMediaContainerResponse>(server, token, path)
        return response.mediaContainer.metadata.mapNotNull { it.toTrack(server, token) }
    }

    suspend fun children(server: PlexServer, parentKey: String, token: String): List<Track> {
        val response = plexGet<PlexMediaContainerResponse>(server, token, "/library/metadata/$parentKey/children")
        return response.mediaContainer.metadata.mapNotNull { it.toTrack(server, token) }
    }

    suspend fun artistDetails(server: PlexServer, ratingKey: String, token: String): Artist? {
        val item = metadataDetails(server, ratingKey, token).firstOrNull() ?: return null
        return Artist(
            id = item.ratingKey,
            title = item.title,
            thumbUrl = item.thumb?.let { thumb -> server.assetUrl(thumb, token) },
            albumCount = item.leafCount ?: 0,
            dateAddedMs = item.addedAt?.times(1000L),
            genre = item.primaryGenreTag(),
            mood = item.primaryMoodTag(),
            style = item.primaryStyleTag(),
            rating = item.userRating.toStarRating(),
        )
    }

    suspend fun albumDetails(server: PlexServer, ratingKey: String, token: String): Album? {
        val item = metadataDetails(server, ratingKey, token).firstOrNull() ?: return null
        return Album(
            id = item.ratingKey,
            title = item.title,
            artist = item.parentTitle ?: "Unknown artist",
            year = item.year,
            thumbUrl = item.thumb?.let { thumb -> server.assetUrl(thumb, token) },
            dateAddedMs = item.addedAt?.times(1000L),
            genre = item.primaryGenreTag(),
            mood = item.primaryMoodTag(),
            style = item.primaryStyleTag(),
            rating = item.userRating.toStarRating(),
        )
    }

    suspend fun collectionFilterChoices(
        server: PlexServer,
        library: MusicLibrary,
        target: PlexCollectionTarget,
        facet: PlexCollectionFacet,
        token: String,
    ): List<PlexFilterChoice> {
        val field = facet.filterField
        val type = target.typeId
        val discoveredPath = discoverCollectionFilterPath(server, library, target, facet, token)
        val paths = listOfNotNull(
            discoveredPath,
            "/library/sections/${library.key}/${target.libtype}.$field?type=$type",
            "/library/sections/${library.key}/$field?type=$type",
            "/library/sections/${library.key}/$field",
        ).distinct()
        PhoebeLog.d("PlexCollections") {
            "filter paths target=${target.name} facet=${facet.name} discovered=$discoveredPath candidates=$paths"
        }
        for (path in paths) {
            val choiceField = path.filterFieldFromCollectionPath() ?: field
            val choices = runCatching {
                val body = plexGetRaw(server, token, path)
                val response = PlexJson.decodeFromString(PlexMediaContainerResponse.serializer(), body)
                PhoebeLog.d("PlexCollections") {
                    "filter choices raw path=$path dirs=${response.mediaContainer.directories.size} meta=${response.mediaContainer.metadata.size} bodyPrefix=${body.take(360)}"
                }
                response.mediaContainer.directories.mapNotNull { it.toFilterChoice(choiceField, library.key, type) }
            }.onFailure { error ->
                PhoebeLog.d("PlexCollections") {
                    "filter choices failed target=${target.name} facet=${facet.name} path=$path error=${error.message}"
                }
            }.getOrDefault(emptyList())
            PhoebeLog.d("PlexCollections") {
                "filter choices path=$path count=${choices.size} sample=${choices.take(5).map { "${it.title}:${it.key}:${it.fastKey}" }}"
            }
            if (choices.isNotEmpty()) return choices.distinctBy { it.key to it.title }
        }
        return emptyList()
    }

    suspend fun collectionFilterItems(
        server: PlexServer,
        library: MusicLibrary,
        target: PlexCollectionTarget,
        facet: PlexCollectionFacet,
        choice: PlexFilterChoice,
        token: String,
    ): List<String> {
        val field = choice.filterField?.takeIf { it.isNotBlank() } ?: facet.filterField
        val scopedFields = when (target) {
            PlexCollectionTarget.Artists -> listOf("artist.${facet.filterField}", "album.${facet.filterField}", "track.${facet.filterField}")
            PlexCollectionTarget.Albums -> listOf("album.${facet.filterField}", "track.${facet.filterField}")
        }
        val fields = (listOf(field) + scopedFields + facet.filterField).distinct()
        val normalizedKey = choice.key.filterChoiceValue()
        val values = listOf(normalizedKey, choice.key, choice.title)
            .map { it.trim() }
            .filter { it.isNotBlank() }
            .distinct()
        val typedPaths = fields.flatMap { candidateField ->
            values.flatMap { candidateValue ->
                val encoded = candidateValue.encodeURLParameter()
                buildList {
                    add("/library/sections/${library.key}/all?type=${target.typeId}&$candidateField=$encoded")
                    add("/library/sections/${library.key}/${target.libtype}s?$candidateField=$encoded")
                    if (target == PlexCollectionTarget.Albums && candidateField.isTrackCollectionField(facet)) {
                        add("/library/sections/${library.key}/all?type=10&$candidateField=$encoded")
                        if (candidateField.startsWith("track.")) {
                            add("/library/sections/${library.key}/all?type=10&${facet.filterField}=$encoded")
                        }
                    }
                }
            }
        }
        val paths = (
            typedPaths +
                listOfNotNull(choice.fastKey?.takeIf { it.isNotBlank() && it.isTypedCollectionFastKey(target) })
            ).distinct()
        for (path in paths) {
            val items = collectionFilterItemsAtPath(server, target, facet, choice, token, path)
            if (items.isNotEmpty()) return items
        }
        PhoebeLog.d("PlexCollections") {
            "filter items exhausted target=${target.name} facet=${facet.name} choice='${choice.title}' paths=$paths"
        }
        return emptyList()
    }

    private suspend fun collectionFilterItemsAtPath(
        server: PlexServer,
        target: PlexCollectionTarget,
        facet: PlexCollectionFacet,
        choice: PlexFilterChoice,
        token: String,
        path: String,
    ): List<String> {
        val response = runCatching {
            plexGet<PlexMediaContainerResponse>(server, token, path)
        }.onFailure { error ->
            PhoebeLog.d("PlexCollections") {
                "filter items failed target=${target.name} facet=${facet.name} choice='${choice.title}' path=$path error=${error.message}"
            }
        }.getOrNull() ?: return emptyList()
        val items = when (target) {
            PlexCollectionTarget.Artists -> response.mediaContainer.directories.mapNotNull { it.ratingKey ?: it.key.ratingKeyFromMetadataPath() ?: it.key.takeIf { key -> key.isNotBlank() } } +
                response.mediaContainer.metadata.mapNotNull { it.ratingKey.takeIf { key -> key.isNotBlank() } }
            PlexCollectionTarget.Albums -> response.mediaContainer.directories.mapNotNull { it.ratingKey ?: it.key.ratingKeyFromMetadataPath() } +
                response.mediaContainer.metadata.mapNotNull { item ->
                    when (item.type) {
                        "track" -> item.parentRatingKey
                        else -> item.ratingKey
                    }?.takeIf { key -> key.isNotBlank() }
                }
        }.distinct()
        PhoebeLog.d("PlexCollections") {
            "filter items path=$path target=${target.name} facet=${facet.name} choice='${choice.title}' dirs=${response.mediaContainer.directories.size} meta=${response.mediaContainer.metadata.size} items=${items.size} sample=$items"
        }
        return items
    }

    suspend fun playbackHistoryPage(
        server: PlexServer,
        token: String,
        library: MusicLibrary,
        minViewedAtMs: Long?,
        start: Int,
        size: Int,
    ): PlexPlaybackHistoryPage {
        val response: PlexMediaContainerResponse = withReachableBase(server) { base ->
            val response = httpClient.get("$base/status/sessions/history/all") {
                plexServerAuth(token)
                header(HttpHeaders.Accept, "application/json")
                header("X-Plex-Container-Start", start.toString())
                header("X-Plex-Container-Size", size.toString())
                parameter("X-Plex-Container-Start", start)
                parameter("X-Plex-Container-Size", size)
                parameter("librarySectionID", library.key)
                parameter("sort", "viewedAt:desc")
                minViewedAtMs?.let { parameter("viewedAt", "viewedAt>=${it / 1000L}") }
            }
            if (!response.status.isSuccess()) {
                val body = response.bodyAsText()
                error("Plex playback history failed (${response.status.value}) via $base: ${body.take(200)}")
            }
            response.body()
        }
        val container = response.mediaContainer
        return PlexPlaybackHistoryPage(
            entries = container.metadata.mapNotNull { it.toPlaybackHistoryEntry() },
            offset = container.offset ?: start,
            size = container.size,
            totalSize = container.totalSize,
        )
    }

    suspend fun libraryTracksPage(
        server: PlexServer,
        library: MusicLibrary,
        token: String,
        start: Int,
        size: Int,
    ): PlexTrackPage {
        val response: PlexMediaContainerResponse = withReachableBase(server) { base ->
            val response = httpClient.get("$base/library/sections/${library.key}/all") {
                plexServerAuth(token)
                header(HttpHeaders.Accept, "application/json")
                header("X-Plex-Container-Start", start.toString())
                header("X-Plex-Container-Size", size.toString())
                parameter("X-Plex-Container-Start", start)
                parameter("X-Plex-Container-Size", size)
                parameter("type", PlexTrackType)
            }
            if (!response.status.isSuccess()) {
                val body = response.bodyAsText()
                error("Plex track page failed (${response.status.value}) via $base: ${body.take(200)}")
            }
            response.body()
        }
        return response.toTrackPage(server, token, requestedStart = start, requestedSize = size)
    }

    suspend fun tracksForYearRange(
        server: PlexServer,
        library: MusicLibrary,
        token: String,
        startYear: Int,
        endYear: Int,
        start: Int = 0,
        size: Int = 500,
        limit: Int? = null,
    ): List<Track> {
        val response: PlexMediaContainerResponse = withReachableBase(server) { base ->
            val response = httpClient.get("$base/library/sections/${library.key}/all") {
                plexServerAuth(token)
                header(HttpHeaders.Accept, "application/json")
                header("X-Plex-Container-Start", start.toString())
                header("X-Plex-Container-Size", size.toString())
                parameter("X-Plex-Container-Start", start)
                parameter("X-Plex-Container-Size", size)
                parameter("type", PlexTrackType)
                parameter("year>=", startYear)
                parameter("year<=", endYear)
                limit?.let { parameter("limit", it) }
            }
            if (!response.status.isSuccess()) {
                val body = response.bodyAsText()
                error("Plex track year search failed (${response.status.value}) via $base: ${body.take(200)}")
            }
            response.body()
        }
        return response.mediaContainer.metadata.mapNotNull { it.toTrack(server, token) }
    }

    suspend fun tracksForYearRangePage(
        server: PlexServer,
        library: MusicLibrary,
        token: String,
        startYear: Int,
        endYear: Int,
        start: Int,
        size: Int,
        limit: Int? = null,
    ): PlexTrackPage {
        val response: PlexMediaContainerResponse = withReachableBase(server) { base ->
            val response = httpClient.get("$base/library/sections/${library.key}/all") {
                plexServerAuth(token)
                header(HttpHeaders.Accept, "application/json")
                header("X-Plex-Container-Start", start.toString())
                header("X-Plex-Container-Size", size.toString())
                parameter("X-Plex-Container-Start", start)
                parameter("X-Plex-Container-Size", size)
                parameter("type", PlexTrackType)
                parameter("year>=", startYear)
                parameter("year<=", endYear)
                limit?.let { parameter("limit", it) }
            }
            if (!response.status.isSuccess()) {
                val body = response.bodyAsText()
                error("Plex track year page failed (${response.status.value}) via $base: ${body.take(200)}")
            }
            response.body()
        }
        return response.toTrackPage(server, token, requestedStart = start, requestedSize = size)
    }

    /**
     * Fetches the server's *canonical* machine identifier via `/identity`. This is the value
     * Plex expects inside playlist URIs of the form `server://{X}/com.plexapp.plugins.library/…`.
     *
     * For owned servers `clientIdentifier` from `plex.tv/api/v2/resources` is usually the same
     * thing, but for relay / shared connections those two ids can diverge, in which case
     * playlist mutations silently no-op. Calling `/identity` is the only reliable way to be
     * sure we have the right id.
     */
    suspend fun machineIdentifier(server: PlexServer, token: String): String {
        val response: PlexMediaContainerResponse = plexGet(server, token, "/identity")
        return response.mediaContainer.machineIdentifier?.takeIf { it.isNotBlank() } ?: server.id
    }

    /**
     * Create a brand-new audio playlist on the Plex server. When [ratingKeys] is non-empty
     * the playlist is seeded with those items; otherwise an empty smart=0 playlist is created
     * scoped to [library] (Plex requires *some* `uri` parameter even for empty playlists, so
     * we point at the library section to satisfy that).
     *
     * [machineIdentifier] must be the value returned by [machineIdentifier]; we accept it as a
     * parameter so callers can cache it across multiple playlist mutations rather than hitting
     * `/identity` every time.
     *
     * Returns the freshly-created [Playlist] parsed from the server's response.
     */
    suspend fun createPlaylist(
        server: PlexServer,
        token: String,
        library: MusicLibrary,
        machineIdentifier: String,
        title: String,
        ratingKeys: List<String>,
    ): Playlist {
        val uri = if (ratingKeys.isNotEmpty()) {
            metadataUri(machineIdentifier, ratingKeys)
        } else {
            "server://$machineIdentifier/com.plexapp.plugins.library/library/sections/${library.key}"
        }
        val response = withReachableBase(server) { base ->
            httpClient.post("$base/playlists") {
                plexServerAuth(token)
                header(HttpHeaders.Accept, "application/json")
                parameter("type", "audio")
                parameter("title", title)
                parameter("smart", 0)
                parameter("uri", uri)
            }
        }
        val parsed = parsePlaylistResponse(response, "createPlaylist", title)
        val meta = parsed.mediaContainer.metadata.firstOrNull()
            ?: error("Plex returned an empty container when creating playlist '$title'")
        return Playlist(
            id = meta.ratingKey,
            title = meta.title,
            trackCount = meta.leafCount ?: ratingKeys.size,
            key = meta.key,
            thumbUrl = meta.thumb?.let { server.assetUrl(it, token) },
        )
    }

    /**
     * Append [ratingKeys] (Plex rating keys, i.e. the un-prefixed track ids) to an existing
     * Plex playlist. Returns the updated leaf count if the server reports one, otherwise
     * `null`.
     *
     * Throws if the server responds with a non-2xx, including the response body so it's
     * obvious why the sync failed. Callers can `runCatching` if they want best-effort.
     */
    suspend fun addTracksToPlaylist(
        server: PlexServer,
        token: String,
        machineIdentifier: String,
        playlistRatingKey: String,
        ratingKeys: List<String>,
    ): Int? {
        if (ratingKeys.isEmpty()) return null
        val response = withReachableBase(server) { base ->
            httpClient.put("$base/playlists/$playlistRatingKey/items") {
                plexServerAuth(token)
                header(HttpHeaders.Accept, "application/json")
                parameter("uri", metadataUri(machineIdentifier, ratingKeys))
            }
        }
        val parsed = parsePlaylistResponse(response, "addTracksToPlaylist", "playlist/$playlistRatingKey")
        return parsed.mediaContainer.leafCountAdded
            ?: parsed.mediaContainer.metadata.firstOrNull()?.leafCount
            ?: parsed.mediaContainer.size
    }

    suspend fun removePlaylistItems(
        server: PlexServer,
        token: String,
        playlistRatingKey: String,
        playlistItemIds: List<Long>,
    ) {
        if (playlistItemIds.isEmpty()) return
        withReachableBase(server) { base ->
            playlistItemIds.forEach { itemId ->
                val response = httpClient.delete("$base/playlists/$playlistRatingKey/items/$itemId") {
                    plexServerAuth(token)
                    header(HttpHeaders.Accept, "application/json")
                }
                if (!response.status.isSuccess()) {
                    val body = response.bodyAsText()
                    error("Plex remove playlist item failed (${response.status.value}) via $base: ${body.take(200)}")
                }
            }
        }
    }

    suspend fun rateItem(
        server: PlexServer,
        token: String,
        ratingKey: String,
        rating: Float?,
    ) {
        val plexRating = rating.toPlexRating()
        val response = withReachableBase(server) { base ->
            httpClient.put("$base/:/rate") {
                plexServerAuth(token)
                header(HttpHeaders.Accept, "application/json")
                parameter("identifier", LibraryIdentifier)
                parameter("key", ratingKey)
                parameter("rating", plexRating)
            }
        }
        val body = response.bodyAsText()
        if (!response.status.isSuccess()) {
            PhoebeLog.d("PlexClient") { "rateItem failed for '$ratingKey' → HTTP ${response.status.value}: $body" }
            error("Plex rating sync failed (${response.status.value}): $body")
        }
        PhoebeLog.v("PlexClient") { "rateItem ok for '$ratingKey' (${response.status.value}): ${body.take(200)}" }
    }

    /**
     * Report playback position to Plex so the server can mark items played and scrobble to
     * linked services (e.g. ListenBrainz). Clients must hit this on state changes and
     * periodically (~10s) while playing.
     *
     * Tries every known server connection (LAN before relay) because plex.direct
     * relay URLs often serve library media but return 401 for the timeline command path.
     */
    suspend fun reportTimeline(
        server: PlexServer,
        token: String,
        sessionIdentifier: String,
        ratingKey: String,
        timeMs: Long,
        durationMs: Long,
        state: PlexTimelineState,
        continuing: Boolean? = null,
        playQueueItemId: Long? = null,
    ) {
        val bases = server.reachableBaseUris(apiBaseCache[server.id])
        var lastStatus = 0
        var lastBody = ""
        var lastBase = server.uri
        for (base in bases) {
            lastBase = base
            val response = timelineHttpRequest(base, token, sessionIdentifier) {
                parameter("ratingKey", ratingKey)
                parameter("key", "/library/metadata/$ratingKey")
                parameter("identifier", LibraryIdentifier)
                parameter("time", timeMs.coerceAtLeast(0L))
                parameter("duration", durationMs.coerceAtLeast(0L))
                parameter("state", state.wireValue)
                continuing?.let { parameter("continuing", if (it) 1 else 0) }
                playQueueItemId?.let { parameter("playQueueItemID", it) }
            }
            if (response.status.isSuccess()) {
                apiBaseCache[server.id] = base
                return
            }
            lastStatus = response.status.value
            lastBody = response.bodyAsText()
            if (response.status.value != 401) break
        }
    }

    /**
     * Register an audio play queue with Plex — first-party clients do this before timeline
     * updates and many servers expect a playQueueItemID on each ping.
     */
    suspend fun createAudioPlayQueue(
        server: PlexServer,
        token: String,
        machineIdentifier: String,
        ratingKeys: List<String>,
        startRatingKey: String,
    ): PlexPlayQueue? {
        if (ratingKeys.isEmpty()) return null
        val uri = metadataUri(machineIdentifier, ratingKeys)
        val bases = server.reachableBaseUris(apiBaseCache[server.id])
        for (base in bases) {
            val response = httpClient.post("$base/playQueues") {
                plexTimelineAuth(token)
                parameter("type", "audio")
                parameter("uri", uri)
                parameter("key", startRatingKey)
                parameter("continuous", 1)
            }
            val body = response.bodyAsText()
            if (!response.status.isSuccess()) {
                if (response.status.value == 401) continue
                PhoebeLog.d("PlexClient") { "createAudioPlayQueue failed → HTTP ${response.status.value}: ${body.take(300)}" }
                return null
            }
            apiBaseCache[server.id] = base
            val parsed = runCatching {
                PlexJson.decodeFromString(PlexMediaContainerResponse.serializer(), body)
            }.getOrNull()
            val container = parsed?.mediaContainer
            val playQueueId = container?.playQueueId ?: return null
            val itemIds = buildMap {
                for (item in container.metadata) {
                    val id = item.playQueueItemId ?: continue
                    put(item.ratingKey, id)
                }
            }
            return PlexPlayQueue(playQueueId = playQueueId, itemIdByRatingKey = itemIds)
        }
        return null
    }

    private suspend fun timelineHttpRequest(
        base: String,
        token: String,
        sessionIdentifier: String,
        block: io.ktor.client.request.HttpRequestBuilder.() -> Unit,
    ): HttpResponse {
        val getResponse = httpClient.get("$base/:/timeline") {
            plexTimelineAuth(token)
            header("X-Plex-Session-Identifier", sessionIdentifier)
            block()
        }
        if (getResponse.status.isSuccess() || getResponse.status.value != 401) {
            return getResponse
        }
        return httpClient.post("$base/:/timeline") {
            plexTimelineAuth(token)
            header("X-Plex-Session-Identifier", sessionIdentifier)
            block()
        }
    }

    suspend fun editTrackMetadata(
        server: PlexServer,
        token: String,
        library: MusicLibrary,
        ratingKey: String,
        original: Track,
        update: TrackMetadataUpdate,
    ) {
        val response = withReachableBase(server) { base ->
            httpClient.put("$base/library/sections/${library.key}/all") {
                plexServerAuth(token)
                header(HttpHeaders.Accept, "application/json")
                parameter("type", 10)
                parameter("id", ratingKey)
                if (update.title != original.title) {
                    parameter("title.value", update.title)
                    parameter("title.locked", 1)
                }
                if (update.artist != original.artist) {
                    parameter("originalTitle.value", update.artist)
                    parameter("originalTitle.locked", 1)
                }
            }
        }
        val body = response.bodyAsText()
        if (!response.status.isSuccess()) {
            PhoebeLog.d("PlexClient") { "editTrackMetadata failed for '$ratingKey' → HTTP ${response.status.value}: $body" }
            error("Plex metadata sync failed (${response.status.value}): $body")
        }
        PhoebeLog.v("PlexClient") { "editTrackMetadata ok for '$ratingKey' (${response.status.value}): ${body.take(400)}" }
    }

    /**
     * Read the body once, log it, and only then deserialize. Plex returns somewhat ambiguous
     * shapes for playlist mutations (sometimes a full `Metadata` array, sometimes just stats
     * on the `MediaContainer`), and silent JSON failures had been hiding 4xx/5xx errors from
     * the user — printing the raw response gives us a chance to spot misformatted URIs,
     * wrong machine ids, etc.
     */
    private suspend fun parsePlaylistResponse(
        response: HttpResponse,
        op: String,
        context: String,
    ): PlexMediaContainerResponse {
        val body = response.bodyAsText()
        if (!response.status.isSuccess()) {
            PhoebeLog.d("PlexClient") { "$op failed for '$context' → HTTP ${response.status.value}: $body" }
            error("Plex $op failed (${response.status.value}): $body")
        }
        // Helpful for diagnosing "request succeeded but nothing happened" cases where Plex
        // returns 200 + leafCountAdded=0.
        PhoebeLog.v("PlexClient") { "$op ok for '$context' (${response.status.value}): ${body.take(400)}" }
        return PlexJson.decodeFromString(PlexMediaContainerResponse.serializer(), body)
    }

    /** Build a comma-joined `server://.../library/metadata/{key1,key2}` URI used by playlist mutations. */
    private fun metadataUri(machineIdentifier: String, ratingKeys: List<String>): String {
        val joined = ratingKeys.joinToString(",")
        return "server://$machineIdentifier/com.plexapp.plugins.library/library/metadata/$joined"
    }

    private suspend fun <T> withReachableBase(
        server: PlexServer,
        block: suspend (base: String) -> T,
    ): T {
        var lastError: Throwable? = null
        for (base in server.reachableBaseUris(apiBaseCache[server.id])) {
            val result = runCatching { block(base) }
            if (result.isSuccess) {
                apiBaseCache[server.id] = base
                return result.getOrThrow()
            }
            lastError = result.exceptionOrNull()
        }
        throw lastError ?: IllegalStateException("Could not reach Plex server '${server.name}'")
    }

    private suspend inline fun <reified T> plexGet(server: PlexServer, token: String, path: String): T =
        withReachableBase(server) { base ->
            val response = httpClient.get("$base$path") {
                plexServerAuth(token)
                header(HttpHeaders.Accept, "application/json")
            }
            if (!response.status.isSuccess()) {
                val body = response.bodyAsText()
                error("Plex GET $path failed (${response.status.value}) via $base: ${body.take(200)}")
            }
            response.body()
        }

    private suspend fun metadataDetails(server: PlexServer, ratingKey: String, token: String): List<PlexMetadataDto> {
        val response = plexGet<PlexMediaContainerResponse>(server, token, "/library/metadata/$ratingKey")
        return response.mediaContainer.metadata
    }

    private suspend fun discoverCollectionFilterPath(
        server: PlexServer,
        library: MusicLibrary,
        target: PlexCollectionTarget,
        facet: PlexCollectionFacet,
        token: String,
    ): String? {
        val scopedField = "${target.libtype}.${facet.filterField}"
        val wanted = setOf(scopedField, facet.filterField)
        val paths = listOf(
            "/library/sections/${library.key}/filters?type=${target.typeId}",
            "/library/sections/${library.key}?includeDetails=1",
            "/library/sections/${library.key}/all?includeMeta=1&includeAdvanced=1&X-Plex-Container-Start=0&X-Plex-Container-Size=0",
        )
        for (path in paths) {
            val body = runCatching { plexGetRaw(server, token, path) }.getOrNull()
            if (body == null) {
                PhoebeLog.d("PlexCollections") {
                    "discover target=${target.name} facet=${facet.name} path=$path failed"
                }
                continue
            }
            val root = runCatching { PlexJson.parseToJsonElement(body) }.getOrNull() ?: continue
            val mediaContainer = root.jsonObjectOrNull()?.get("MediaContainer") ?: root
            val typeObjects = mediaContainer.walkObjects().filter { obj ->
                obj.stringValue("type") == target.libtype && obj.containsKey("Filter")
            }.toList()
            val candidates = typeObjects.ifEmpty { mediaContainer.walkObjects().toList() }
            val filters = candidates
                .asSequence()
                .flatMap { it.walkObjects() }
                .filter { obj ->
                    obj.stringValue("filter") in wanted &&
                        obj.stringValue("key")?.startsWith("/library/sections/${library.key}/") == true
                }
                .toList()
            val discovered = (
                filters.firstOrNull { it.stringValue("filter") == scopedField } ?: filters.firstOrNull()
                )?.stringValue("key")
            PhoebeLog.d("PlexCollections") {
                val filters = candidates
                    .asSequence()
                    .flatMap { it.walkObjects() }
                    .mapNotNull { obj -> obj.stringValue("filter")?.let { filter -> "$filter -> ${obj.stringValue("key")}" } }
                    .take(24)
                    .toList()
                "discover target=${target.name} facet=${facet.name} path=$path typeObjects=${typeObjects.size} wanted=$wanted discovered=$discovered filters=$filters bodyPrefix=${body.take(240)}"
            }
            if (discovered != null) return discovered
        }
        return null
    }

    private suspend fun plexGetRaw(server: PlexServer, token: String, path: String): String =
        withReachableBase(server) { base ->
            val response = httpClient.get("$base$path") {
                plexServerAuth(token)
                header(HttpHeaders.Accept, "application/json")
            }
            val body = response.bodyAsText()
            if (!response.status.isSuccess()) {
                error("Plex GET $path failed (${response.status.value}) via $base: ${body.take(200)}")
            }
            body
        }

    private fun PlexMetadataDto.toTrack(server: PlexServer, token: String): Track? {
        val mediaItem = media.firstOrNull() ?: return null
        val part = mediaItem.parts.firstOrNull() ?: return null
        val streamUrl = server.assetUrl(part.key, token)
        val genre = primaryGenreTag()
        val mood = primaryMoodTag()
        val style = primaryStyleTag()
        val bitrateRaw = mediaItem.bitrate
        val bitrateKbps = bitrateRaw?.let { raw ->
            when {
                raw <= 0 -> null
                raw >= 500_000 -> raw / 1000
                raw >= 8000 -> raw / 1000
                else -> raw
            }
        }
        return Track(
            id = ratingKey,
            title = title,
            artist = grandparentTitle ?: parentTitle ?: "Unknown artist",
            album = parentTitle ?: "Unknown album",
            durationMs = duration ?: 0L,
            streamUrl = streamUrl,
            downloadUrl = "$streamUrl&download=1",
            thumbUrl = (thumb ?: parentThumb ?: grandparentThumb)?.let { server.assetUrl(it, token) },
            year = year ?: parentYear,
            genre = genre,
            mood = mood,
            style = style,
            filepath = part.file,
            audioCodec = mediaItem.audioCodec?.takeIf { it.isNotBlank() },
            bitrateKbps = bitrateKbps,
            dateAddedMs = addedAt?.times(1000L),
            rating = userRating.toStarRating(),
            playlistItemId = playlistItemId,
            parentAlbumId = parentRatingKey,
        )
    }

    private fun PlexMetadataDto.toPlaybackHistoryEntry(): PlexPlaybackHistoryEntry? {
        val key = historyKey?.takeIf { it.isNotBlank() } ?: return null
        val viewed = viewedAt ?: return null
        return PlexPlaybackHistoryEntry(
            ratingKey = ratingKey,
            historyKey = key,
            viewedAtMs = viewed * 1000L,
            type = type,
            librarySectionId = librarySectionID,
            title = title,
            artist = grandparentTitle ?: parentTitle ?: "Unknown Artist",
            album = parentTitle ?: "Unknown Album",
        )
    }

    private fun PlexMediaContainerResponse.toTrackPage(
        server: PlexServer,
        token: String,
        requestedStart: Int,
        requestedSize: Int,
    ): PlexTrackPage {
        val container = mediaContainer
        val tracks = container.metadata.mapNotNull { it.toTrack(server, token) }
        val offset = container.offset ?: requestedStart
        val totalSize = container.totalSize ?: responseHeaderTotalSizeFallback(offset, container.size, tracks.size, requestedSize)
        return PlexTrackPage(
            tracks = tracks,
            offset = offset,
            size = container.size.takeIf { it > 0 } ?: tracks.size,
            totalSize = totalSize,
        )
    }

    private fun responseHeaderTotalSizeFallback(offset: Int, containerSize: Int, trackSize: Int, requestedSize: Int): Int? {
        val actual = containerSize.takeIf { it > 0 } ?: trackSize
        return if (actual < requestedSize) offset + actual else null
    }

    private fun PlexServer.assetUrl(path: String, token: String): String {
        val base = apiBaseCache[id] ?: uri
        val builder = URLBuilder(base)
        builder.appendPathSegments(path.trimStart('/').split('/'))
        builder.parameters.append("X-Plex-Token", token)
        return builder.buildString()
    }

    /** Base URL that succeeded for API calls, used for media/thumbnail URLs. */
    fun mediaBaseUrl(server: PlexServer): String = apiBaseCache[server.id] ?: server.uri

    companion object {
        const val LibraryIdentifier = "com.plexapp.plugins.library"
        const val ClientIdentifier = "phoebe-compose-multiplatform"
        private const val PlexTrackType = 10
        val PlexJson = Json {
            ignoreUnknownKeys = true
            explicitNulls = false
        }
    }
}

private fun Double?.toStarRating(): Float? =
    this?.div(2.0)
        ?.toFloat()
        ?.coerceIn(0f, 5f)
        ?.let { (kotlin.math.round(it * 2f) / 2f).takeIf { rounded -> rounded > 0f } }

private fun PlexMetadataDto.primaryGenreTag(): String? = genreTags.primaryTag()

private fun PlexMetadataDto.primaryMoodTag(): String? = moodTags.primaryTag()

private fun PlexMetadataDto.primaryStyleTag(): String? = styleTags.primaryTag()

private fun PlexDirectoryDto.primaryGenreTag(): String? = genreTags.primaryTag()

private fun PlexDirectoryDto.primaryMoodTag(): String? = moodTags.primaryTag()

private fun PlexDirectoryDto.primaryStyleTag(): String? = styleTags.primaryTag()

private fun List<PlexGenreTagDto>?.primaryTag(): String? =
    this?.firstNotNullOfOrNull { tag ->
        tag.tag?.trim()?.takeIf { it.isNotBlank() }
    }

private fun PlexDirectoryDto.toFilterChoice(field: String, libraryKey: String, typeId: Int): PlexFilterChoice? {
    val cleanTitle = title.trim().takeIf { it.isNotBlank() } ?: return null
    val cleanKey = key.trim().takeIf { it.isNotBlank() } ?: cleanTitle
    val pathKey = cleanKey.takeIf { it.startsWith("/library/sections/") }
    val choiceKey = (pathKey?.queryValueFor(field) ?: cleanKey).filterChoiceValue()
    return PlexFilterChoice(
        key = choiceKey,
        title = cleanTitle,
        fastKey = fastKey ?: pathKey ?: "/library/sections/$libraryKey/all?type=$typeId&$field=${choiceKey.encodeURLParameter()}",
        filterField = field,
    )
}

private fun String.queryValueFor(field: String): String? {
    val query = substringAfter('?', missingDelimiterValue = "")
    if (query.isBlank()) return null
    val fieldSuffix = field.substringAfterLast('.')
    return query
        .split('&')
        .firstNotNullOfOrNull { part ->
            val name = part.substringBefore('=')
            val value = part.substringAfter('=', missingDelimiterValue = "")
            value.takeIf { it.isNotBlank() && (name == field || name == fieldSuffix) }
        }
}

private fun String.filterChoiceValue(): String =
    trim()
        .substringAfterLast('=')
        .substringBefore('&')
        .trim()

private fun String.filterFieldFromCollectionPath(): String? =
    substringAfterLast('/')
        .substringBefore('?')
        .takeIf { it.isNotBlank() && it != "all" }

private fun String.ratingKeyFromMetadataPath(): String? =
    trim('/')
        .split('/')
        .dropWhile { it != "metadata" }
        .drop(1)
        .firstOrNull()
        ?.takeIf { it.isNotBlank() }

private fun JsonElement.walkObjects(): Sequence<JsonObject> = sequence {
    when (val element = this@walkObjects) {
        is JsonObject -> {
            yield(element)
            element.values.forEach { child ->
                yieldAll(child.walkObjects())
            }
        }
        is JsonArray -> element.forEach { child ->
            yieldAll(child.walkObjects())
        }
        else -> Unit
    }
}

private fun JsonElement.jsonObjectOrNull(): JsonObject? = this as? JsonObject

private fun JsonObject.stringValue(key: String): String? =
    (this[key] as? JsonPrimitive)?.contentOrNull?.takeIf { it.isNotBlank() }

private fun String.isTrackCollectionField(facet: PlexCollectionFacet): Boolean =
    this == facet.filterField || this == "track.${facet.filterField}"

private fun String.isTypedCollectionFastKey(target: PlexCollectionTarget): Boolean =
    contains("type=${target.typeId}") || contains("/${target.libtype}s?")

enum class PlexCollectionTarget(val typeId: Int, val libtype: String) {
    Artists(8, "artist"),
    Albums(9, "album"),
}

enum class PlexCollectionFacet(val filterField: String) {
    Mood("mood"),
    Style("style"),
    Genre("genre"),
}

data class PlexFilterChoice(
    val key: String,
    val title: String,
    val fastKey: String?,
    val filterField: String? = null,
)

private fun Float?.toPlexRating(): Double =
    this?.coerceIn(0f, 5f)
        ?.let { kotlin.math.round(it * 2f) / 2f }
        ?.times(2f)
        ?.toDouble()
        ?: 0.0

data class PlexTrackPage(
    val tracks: List<Track>,
    val offset: Int,
    val size: Int,
    val totalSize: Int?,
) {
    val nextOffset: Int get() = offset + size
    val hasMore: Boolean
        get() = when {
            tracks.isEmpty() -> false
            totalSize != null -> nextOffset < totalSize
            else -> size > 0
        }
}

enum class PlexTimelineState(val wireValue: String) {
    Playing("playing"),
    Paused("paused"),
    Stopped("stopped"),
    Buffering("buffering"),
}

/** Plex accepts the token in a header and/or query param; relays reliably forward the query form. */
private fun io.ktor.client.request.HttpRequestBuilder.plexServerAuth(token: String) {
    header("X-Plex-Token", token)
    parameter("X-Plex-Token", token)
    plexHeaders()
}

private fun io.ktor.client.request.HttpRequestBuilder.plexTimelineAuth(token: String) {
    plexServerAuth(token)
    header("X-Plex-Device", "Phoebe")
    header("X-Plex-Device-Name", "Phoebe")
    header("X-Plex-Provides", "player")
}

private fun io.ktor.client.request.HttpRequestBuilder.plexHeaders(token: String? = null) {
    header("X-Plex-Product", "Phoebe")
    header("X-Plex-Version", "0.1.0")
    header("X-Plex-Client-Identifier", PlexClient.ClientIdentifier)
    header("X-Plex-Platform", "Compose Multiplatform")
    header(HttpHeaders.Accept, "application/json")
    token?.let { header("X-Plex-Token", it) }
}
