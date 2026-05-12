package com.phoebe.app.data

import com.phoebe.app.domain.Album
import com.phoebe.app.domain.Artist
import com.phoebe.app.domain.MusicLibrary
import com.phoebe.app.domain.PlexPin
import com.phoebe.app.domain.PlexServer
import com.phoebe.app.domain.Playlist
import com.phoebe.app.domain.Track
import com.phoebe.app.domain.TrackMetadataUpdate
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.parameter
import io.ktor.client.request.post
import io.ktor.client.request.put
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpHeaders
import io.ktor.http.URLBuilder
import io.ktor.http.appendPathSegments
import io.ktor.http.isSuccess
import io.ktor.serialization.kotlinx.json.json
import kotlinx.serialization.json.Json

class PlexClient(
    private val httpClient: HttpClient = HttpClient {
        install(ContentNegotiation) {
            json(PlexJson)
        }
    },
) {
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
                val connection = device.connections.firstOrNull { !it.local } ?: device.connections.firstOrNull()
                connection?.let {
                    PlexServer(
                        id = device.clientIdentifier,
                        name = device.name,
                        uri = it.uri.trimEnd('/'),
                        owned = device.owned,
                    )
                }
            }
    }

    suspend fun musicLibraries(server: PlexServer, token: String): List<MusicLibrary> {
        val response = plexGet<PlexMediaContainerResponse>(server, token, "/library/sections")
        return response.mediaContainer.directories
            .filter { it.type == "artist" }
            .map { MusicLibrary(key = it.key, title = it.title) }
    }

    suspend fun artists(server: PlexServer, library: MusicLibrary, token: String): List<Artist> {
        val response = plexGet<PlexMediaContainerResponse>(server, token, "/library/sections/${library.key}/all")
        val fromDirectories = response.mediaContainer.directories.map {
            Artist(
                id = it.key,
                title = it.title,
                thumbUrl = it.thumb?.let { thumb -> server.assetUrl(thumb, token) },
                albumCount = it.leafCount ?: 0,
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
            )
        }
        return (fromDirectories + fromMetadata).distinctBy { it.id }
    }

    suspend fun albums(server: PlexServer, library: MusicLibrary, token: String): List<Album> {
        val response = plexGet<PlexMediaContainerResponse>(server, token, "/library/sections/${library.key}/albums")
        return response.mediaContainer.metadata.map {
            Album(
                id = it.ratingKey,
                title = it.title,
                artist = it.parentTitle ?: "Unknown artist",
                year = it.year,
                thumbUrl = it.thumb?.let { thumb -> server.assetUrl(thumb, token) },
            )
        }
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
        val response = httpClient.post(server.uri + "/playlists") {
            header("X-Plex-Token", token)
            header(HttpHeaders.Accept, "application/json")
            parameter("type", "audio")
            parameter("title", title)
            parameter("smart", 0)
            parameter("uri", uri)
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
        val response = httpClient.put(server.uri + "/playlists/$playlistRatingKey/items") {
            header("X-Plex-Token", token)
            header(HttpHeaders.Accept, "application/json")
            parameter("uri", metadataUri(machineIdentifier, ratingKeys))
        }
        val parsed = parsePlaylistResponse(response, "addTracksToPlaylist", "playlist/$playlistRatingKey")
        return parsed.mediaContainer.leafCountAdded
            ?: parsed.mediaContainer.metadata.firstOrNull()?.leafCount
            ?: parsed.mediaContainer.size
    }

    suspend fun editTrackMetadata(
        server: PlexServer,
        token: String,
        library: MusicLibrary,
        ratingKey: String,
        original: Track,
        update: TrackMetadataUpdate,
    ) {
        val response = httpClient.put(server.uri + "/library/sections/${library.key}/all") {
            header("X-Plex-Token", token)
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
        val body = response.bodyAsText()
        if (!response.status.isSuccess()) {
            println("[PlexClient] editTrackMetadata failed for '$ratingKey' → HTTP ${response.status.value}: $body")
            error("Plex metadata sync failed (${response.status.value}): $body")
        }
        println("[PlexClient] editTrackMetadata ok for '$ratingKey' (${response.status.value}): ${body.take(400)}")
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
            println("[PlexClient] $op failed for '$context' → HTTP ${response.status.value}: $body")
            error("Plex $op failed (${response.status.value}): $body")
        }
        // Helpful for diagnosing "request succeeded but nothing happened" cases where Plex
        // returns 200 + leafCountAdded=0.
        println("[PlexClient] $op ok for '$context' (${response.status.value}): ${body.take(400)}")
        return PlexJson.decodeFromString(PlexMediaContainerResponse.serializer(), body)
    }

    /** Build a comma-joined `server://.../library/metadata/{key1,key2}` URI used by playlist mutations. */
    private fun metadataUri(machineIdentifier: String, ratingKeys: List<String>): String {
        val joined = ratingKeys.joinToString(",")
        return "server://$machineIdentifier/com.plexapp.plugins.library/library/metadata/$joined"
    }

    private suspend inline fun <reified T> plexGet(server: PlexServer, token: String, path: String): T =
        httpClient.get(server.uri + path) {
            header("X-Plex-Token", token)
            header(HttpHeaders.Accept, "application/json")
        }.body()

    private fun PlexMetadataDto.toTrack(server: PlexServer, token: String): Track? {
        val mediaItem = media.firstOrNull() ?: return null
        val part = mediaItem.parts.firstOrNull() ?: return null
        val streamUrl = server.assetUrl(part.key, token)
        val genre = genreTags?.firstOrNull()?.tag?.takeIf { it.isNotBlank() }
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
            thumbUrl = thumb?.let { server.assetUrl(it, token) },
            year = year,
            genre = genre,
            filepath = part.file,
            audioCodec = mediaItem.audioCodec?.takeIf { it.isNotBlank() },
            bitrateKbps = bitrateKbps,
        )
    }

    private fun PlexServer.assetUrl(path: String, token: String): String {
        val builder = URLBuilder(uri)
        builder.appendPathSegments(path.trimStart('/').split('/'))
        builder.parameters.append("X-Plex-Token", token)
        return builder.buildString()
    }

    companion object {
        const val ClientIdentifier = "phoebe-compose-multiplatform"
        val PlexJson = Json {
            ignoreUnknownKeys = true
            explicitNulls = false
        }
    }
}

private fun io.ktor.client.request.HttpRequestBuilder.plexHeaders(token: String? = null) {
    header("X-Plex-Product", "Phoebe")
    header("X-Plex-Version", "0.1.0")
    header("X-Plex-Client-Identifier", PlexClient.ClientIdentifier)
    header("X-Plex-Platform", "Compose Multiplatform")
    header(HttpHeaders.Accept, "application/json")
    token?.let { header("X-Plex-Token", it) }
}
