package com.phoebe.app.data

import com.phoebe.app.domain.Album
import com.phoebe.app.domain.Artist
import com.phoebe.app.domain.CatalogSnapshot
import com.phoebe.app.domain.MediaProviderType
import com.phoebe.app.domain.MusicLibrary
import com.phoebe.app.domain.Playlist
import com.phoebe.app.domain.PlexServer
import com.phoebe.app.domain.PlexSession
import com.phoebe.app.domain.Track
import com.phoebe.app.platform.PhoebeLog
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.bearerAuth
import io.ktor.client.request.get
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.encodeURLParameter
import io.ktor.http.contentType
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.decodeFromJsonElement
import kotlinx.serialization.json.floatOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

class MusicAssistantClient(
    private val httpClient: HttpClient,
) {
    private val json = Json { ignoreUnknownKeys = true; explicitNulls = false }

    suspend fun signIn(serverUrl: String, username: String, passwordOrToken: String): PlexSession {
        val base = normalizeBaseUrl(serverUrl)
        val trimmedUsername = username.trim()
        val trimmedSecret = passwordOrToken.trim()
        val enteredToken = when {
            trimmedUsername.equals("token", ignoreCase = true) -> trimmedSecret
            trimmedUsername.startsWith("token:", ignoreCase = true) -> trimmedUsername.substringAfter(':').trim()
            trimmedSecret.startsWith("token:", ignoreCase = true) -> trimmedSecret.substringAfter(':').trim()
            trimmedUsername.isBlank() -> trimmedSecret
            trimmedSecret.isBlank() -> trimmedUsername
            else -> null
        }
        val token = (enteredToken ?: login(base, trimmedUsername, trimmedSecret))
            .takeIf { it.isNotBlank() }
            ?: error("Music Assistant did not return an access token.")
        return PlexSession(
            token = token,
            userName = trimmedUsername.takeIf {
                enteredToken == null && it.isNotBlank()
            } ?: "Music Assistant listener",
            selectedServer = PlexServer(
                id = "music-assistant:${base.hashCode().toUInt().toString(16)}",
                name = base.removePrefix("https://").removePrefix("http://"),
                uri = base,
                owned = true,
            ),
            selectedLibrary = MusicLibrary(DefaultLibraryKey, "Music Assistant Library"),
            providerType = MediaProviderType.MusicAssistant,
        )
    }

    suspend fun libraries(server: PlexServer, token: String): List<MusicLibrary> {
        runCatching { info(server, token) }
        return listOf(MusicLibrary(DefaultLibraryKey, "Music Assistant Library"))
    }

    suspend fun buildCatalog(server: PlexServer, token: String): CatalogSnapshot {
        val artists = libraryItems<MaMediaItem>(server, token, "artists").map { it.toArtist(server) }
        val albums = libraryItems<MaMediaItem>(server, token, "albums").map { it.toAlbum(server) }
        val tracks = libraryItems<MaMediaItem>(server, token, "tracks").map { it.toTrack(server) }
        val playlists = libraryItems<MaMediaItem>(server, token, "playlists").map { it.toPlaylist(server) }
        val tracksByAlbum = tracks
            .groupBy { it.parentAlbumId?.takeIf { id -> id.isNotBlank() } ?: musicAssistantAlbumIdByTitle(albums, it) }
            .filterKeys { it.isNotBlank() }
        return CatalogSnapshot(
            artists = mergeMusicAssistantArtists(artists, albums, tracks),
            albums = albums,
            playlists = playlists,
            tracksByParent = tracksByAlbum,
        )
    }

    suspend fun albumTracks(server: PlexServer, token: String, album: Album): List<Track> {
        val rawId = album.id.removePrefix("music-assistant:")
        return commandList<MaMediaItem>(
            server = server,
            token = token,
            command = "music/albums/album_tracks",
            args = jsonObject(
                "item_id" to rawId,
                "provider_instance_id_or_domain" to "library",
                "in_library_only" to true,
            ),
        ).map { it.toTrack(server).copy(parentAlbumId = rawId) }
    }

    suspend fun playlistTracks(server: PlexServer, token: String, playlist: Playlist): List<Track> {
        val rawId = playlist.id.removePrefix("music-assistant:")
        return commandList<MaMediaItem>(
            server = server,
            token = token,
            command = "music/playlists/playlist_tracks",
            args = jsonObject(
                "item_id" to rawId,
                "provider_instance_id_or_domain" to "library",
                "provider_instance_or_domain" to "library",
                "force_refresh" to false,
            ),
        ).map { it.toTrack(server) }
    }

    suspend fun createPlaylist(server: PlexServer, token: String, title: String, itemUris: List<String>): Playlist {
        val result = command(
            server = server,
            token = token,
            command = "music/playlists/create_playlist",
            args = jsonObject("name" to title),
        )
        val item = result.asObjectOrNull()?.let { json.decodeFromJsonElement<MaMediaItem>(it) }
        val playlist = item?.toPlaylist(server) ?: Playlist(id = title, title = title, trackCount = itemUris.size)
        if (itemUris.isNotEmpty()) {
            addTracksToPlaylist(server, token, playlist.id, itemUris)
        }
        return playlist.copy(trackCount = itemUris.size)
    }

    suspend fun addTracksToPlaylist(server: PlexServer, token: String, playlistId: String, itemUris: List<String>) {
        val rawId = playlistId.removePrefix("music-assistant:")
        itemUris.filter { it.isNotBlank() }.forEach { uri ->
            command(
                server = server,
                token = token,
                command = "music/playlists/add_playlist_tracks",
                args = jsonObject(
                    "item_id" to rawId,
                    "provider_instance_id_or_domain" to "library",
                    "uri" to uri.removePrefix("music-assistant:"),
                ),
            )
        }
    }

    suspend fun setFavorite(server: PlexServer, token: String, itemUri: String, favorite: Boolean) {
        command(
            server = server,
            token = token,
            command = if (favorite) "music/favorites/add_item" else "music/favorites/remove_item",
            args = jsonObject("item" to itemUri.removePrefix("music-assistant:")),
        )
    }

    suspend fun playMedia(server: PlexServer, token: String, queueId: String, itemUri: String) {
        command(
            server = server,
            token = token,
            command = "player_queues/play_media",
            args = jsonObject(
                "queue_id" to queueId,
                "media" to itemUri.removePrefix("music-assistant:"),
            ),
        )
    }

    suspend fun playMediaOnDefaultQueue(server: PlexServer, token: String, itemUri: String): String {
        val queue = defaultPlaybackQueue(server, token) ?: error("No Music Assistant player queues are available.")
        playMedia(server, token, queue.queueId, itemUri)
        return queue.displayName ?: queue.queueId
    }

    suspend fun streamUrlForLocalPlayback(server: PlexServer, token: String, itemUri: String): String {
        val queue = localPlaybackQueue(server, token) ?: error("No Music Assistant player queues are available.")
        val sessionId = queue.sessionId?.takeIf { it.isNotBlank() }
            ?: error("Music Assistant local playback requires Phoebe to register as a Sendspin player.")
        val beforeIds = queueItems(server, token, queue.queueId).map { it.queueItemId }.toSet()
        command(
            server = server,
            token = token,
            command = "player_queues/play_media",
            args = jsonObject(
                "queue_id" to queue.queueId,
                "media" to itemUri.removePrefix("music-assistant:"),
                "option" to "add",
            ),
        )
        val queueItem = queueItems(server, token, queue.queueId)
            .lastOrNull { it.queueItemId !in beforeIds && it.uri == itemUri.removePrefix("music-assistant:") }
            ?: queueItems(server, token, queue.queueId).lastOrNull { it.uri == itemUri.removePrefix("music-assistant:") }
            ?: error("Music Assistant did not create a streamable queue item.")
        val streamBaseUrl = streamServerBaseUrl(server, token)
        return "$streamBaseUrl/single/$sessionId/${queue.queueId}/${queueItem.queueItemId}/${queue.queueId}.mp3"
    }

    private suspend fun defaultPlaybackQueue(server: PlexServer, token: String): MaPlayerQueue? {
        val queues = commandList<MaPlayerQueue>(
            server = server,
            token = token,
            command = "player_queues/all",
            args = JsonObject(emptyMap()),
        )
        return queues.firstOrNull { it.active == true && it.available != false }
            ?: queues.firstOrNull { it.available != false }
            ?: queues.firstOrNull()
    }

    private suspend fun localPlaybackQueue(server: PlexServer, token: String): MaPlayerQueue? {
        val queues = commandList<MaPlayerQueue>(
            server = server,
            token = token,
            command = "player_queues/all",
            args = JsonObject(emptyMap()),
        )
        return queues.firstOrNull { it.active != true && it.available != false }
            ?: queues.firstOrNull { it.available != false }
            ?: queues.firstOrNull()
    }

    private suspend fun queueItems(server: PlexServer, token: String, queueId: String): List<MaQueueItem> =
        commandList(
            server = server,
            token = token,
            command = "player_queues/items",
            args = jsonObject(
                "queue_id" to queueId,
                "limit" to 500,
                "offset" to 0,
            ),
        )

    private suspend fun streamServerBaseUrl(server: PlexServer, token: String): String {
        val configuredPublishIp = runCatching {
            command(
                server = server,
                token = token,
                command = "config/core/get_value",
                args = jsonObject(
                    "domain" to "streams",
                    "key" to "publish_ip",
                ),
            ).jsonPrimitive.contentOrNull
        }.getOrNull()?.takeIf { it.isNotBlank() }
        val serverHost = server.uri.substringAfter("://").substringBefore(":").substringBefore("/")
        val publishIp = configuredPublishIp
            ?.takeUnless { it.startsWith("172.") || it.startsWith("127.") || it == "0.0.0.0" }
            ?: serverHost
        val port = runCatching {
            command(
                server = server,
                token = token,
                command = "config/core/get_value",
                args = jsonObject(
                    "domain" to "streams",
                    "key" to "bind_port",
                    "default" to 8097,
                ),
            ).jsonPrimitive.intOrNull
        }.getOrNull() ?: 8097
        return "http://$publishIp:$port"
    }

    private suspend fun login(baseUrl: String, username: String, password: String): String {
        val response: JsonElement = httpClient.post("$baseUrl/auth/login") {
            contentType(ContentType.Application.Json)
            setBody(
                jsonObject(
                    "provider_id" to "builtin",
                    "credentials" to jsonObject(
                        "username" to username,
                        "password" to password,
                    ),
                    "device_name" to "Phoebe",
                ),
            )
        }.body()
        val obj = response.asObjectOrNull() ?: error("Music Assistant login returned an unexpected response.")
        if (obj["success"]?.jsonPrimitive?.booleanOrNull == false) {
            error(obj.string("error") ?: "Music Assistant rejected those credentials.")
        }
        return obj.string("access_token")
            ?: obj.string("token")
            ?: obj["session"]?.asObjectOrNull()?.string("access_token")
            ?: obj["session"]?.asObjectOrNull()?.string("token")
            ?: error("Music Assistant did not return an access token.")
    }

    private suspend fun info(server: PlexServer, token: String): JsonElement =
        runCatching {
            httpClient.get("${server.uri}/info") { bearerAuth(token) }.body<JsonElement>()
        }.getOrElse {
            command(server, token, "server/info", JsonObject(emptyMap()))
        }

    private suspend inline fun <reified T> libraryItems(server: PlexServer, token: String, mediaType: String): List<T> {
        val all = mutableListOf<T>()
        var offset = 0
        var previousPageSignature: String? = null
        PhoebeLog.d("MusicAssistantClient") { "loading $mediaType from Music Assistant" }
        while (true) {
            val page = commandPage<T>(
                server = server,
                token = token,
                command = "music/$mediaType/library_items",
                args = jsonObject(
                    "limit" to MaPageSize,
                    "offset" to offset,
                ),
            )
            PhoebeLog.d("MusicAssistantClient") {
                "loaded $mediaType page offset=$offset raw=${page.rawCount} decoded=${page.items.size} total=${all.size + page.items.size}"
            }
            if (page.rawCount == 0) break
            if (offset > 0 && page.signature == previousPageSignature) {
                PhoebeLog.d("MusicAssistantClient") {
                    "stopping $mediaType pagination because Music Assistant repeated the previous page at offset=$offset"
                }
                break
            }
            previousPageSignature = page.signature
            all += page.items
            if (page.rawCount < MaPageSize) break
            offset += page.rawCount
        }
        PhoebeLog.d("MusicAssistantClient") { "loaded $mediaType total=${all.size}" }
        return all
    }

    private suspend inline fun <reified T> commandList(
        server: PlexServer,
        token: String,
        command: String,
        args: JsonObject,
    ): List<T> = commandPage<T>(server, token, command, args).items

    private suspend inline fun <reified T> commandPage(
        server: PlexServer,
        token: String,
        command: String,
        args: JsonObject,
    ): MaCommandPage<T> {
        val element = command(server, token, command, args)
        val array = when (element) {
            is JsonArray -> element
            is JsonObject -> element["items"] as? JsonArray
                ?: element["result"] as? JsonArray
                ?: element["data"] as? JsonArray
            else -> null
        } ?: return MaCommandPage(emptyList(), rawCount = 0, signature = "")
        val items = array.mapNotNull { item ->
            runCatching { json.decodeFromJsonElement<T>(item) }.getOrNull()
        }
        return MaCommandPage(
            items = items,
            rawCount = array.size,
            signature = array.pageSignature(),
        )
    }

    private suspend fun command(
        server: PlexServer,
        token: String,
        command: String,
        args: JsonObject = JsonObject(emptyMap()),
    ): JsonElement {
        val response: JsonElement = httpClient.post("${server.uri}/api") {
            bearerAuth(token)
            contentType(ContentType.Application.Json)
            setBody(MaCommand(messageId = "phoebe-${messageCounter++}", command = command, args = args))
        }.body()
        val obj = response.asObjectOrNull()
        if (obj?.string("error") != null) error(obj.string("error") ?: "Music Assistant command failed.")
        return obj?.get("result") ?: obj?.get("data") ?: response
    }

    private companion object {
        const val DefaultLibraryKey = "music-assistant"
        const val MaPageSize = 500
        var messageCounter = 1
    }
}

private data class MaCommandPage<T>(
    val items: List<T>,
    val rawCount: Int,
    val signature: String,
)

@Serializable
private data class MaPlayerQueue(
    @SerialName("queue_id") val queueId: String,
    @SerialName("display_name") val displayName: String? = null,
    @SerialName("session_id") val sessionId: String? = null,
    val active: Boolean? = null,
    val available: Boolean? = null,
)

@Serializable
private data class MaQueueItem(
    @SerialName("queue_item_id") val queueItemId: String,
    val uri: String? = null,
)

private fun JsonArray.pageSignature(): String =
    when {
        isEmpty() -> ""
        size == 1 -> first().toString()
        else -> "${first()}|${last()}|$size"
    }

private fun normalizeBaseUrl(serverUrl: String): String {
    val trimmed = serverUrl.trim().trimEnd('/')
    return when {
        trimmed.startsWith("http://", ignoreCase = true) ||
            trimmed.startsWith("https://", ignoreCase = true) -> trimmed
        else -> "http://$trimmed"
    }
}

@Serializable
private data class MaCommand(
    @SerialName("message_id") val messageId: String,
    val command: String,
    val args: JsonObject = JsonObject(emptyMap()),
)

@Serializable
private data class MaMediaItem(
    @SerialName("item_id") val itemId: JsonElement? = null,
    val provider: String? = null,
    val name: String? = null,
    val uri: String? = null,
    val favorite: Boolean? = null,
    val duration: Float? = null,
    val year: Int? = null,
    @SerialName("date_added") val dateAdded: String? = null,
    @SerialName("timestamp_added") val timestampAdded: Long? = null,
    val image: MaImage? = null,
    val metadata: MaMetadata? = null,
    val artists: List<MaMediaItem>? = null,
    val album: MaMediaItem? = null,
    @SerialName("provider_mappings") val providerMappings: List<MaProviderMapping>? = null,
)

@Serializable
private data class MaMetadata(
    val images: List<MaImage>? = null,
    val genres: List<String>? = null,
)

@Serializable
private data class MaImage(
    val path: String? = null,
    val provider: String? = null,
    val type: String? = null,
    @SerialName("remotely_accessible") val remotelyAccessible: Boolean? = null,
)

@Serializable
private data class MaProviderMapping(
    @SerialName("item_id") val itemId: JsonElement? = null,
    @SerialName("provider_domain") val providerDomain: String? = null,
    @SerialName("provider_instance") val providerInstance: String? = null,
)

private fun MaMediaItem.toArtist(server: PlexServer): Artist =
    Artist(
        id = stableId(),
        title = name ?: "Unknown artist",
        thumbUrl = artworkUrl(server),
        dateAddedMs = addedAtMs(),
        favorite = favorite == true,
    )

private fun MaMediaItem.toAlbum(server: PlexServer): Album =
    Album(
        id = stableId(),
        title = name ?: "Unknown album",
        artist = artists.orEmpty().firstOrNull()?.name ?: "Unknown artist",
        year = year,
        genre = metadata?.genres.orEmpty().firstOrNull(),
        thumbUrl = artworkUrl(server),
        dateAddedMs = addedAtMs(),
        favorite = favorite == true,
    )

private fun MaMediaItem.toPlaylist(server: PlexServer): Playlist =
    Playlist(
        id = stableId(),
        title = name ?: "Playlist",
        trackCount = 0,
        key = uri,
        thumbUrl = artworkUrl(server),
        favorite = favorite == true,
    )

private fun MaMediaItem.toTrack(server: PlexServer): Track {
    val trackUri = uri ?: stableId()
    return Track(
        id = trackUri,
        title = name ?: "Untitled track",
        artist = artists.orEmpty().firstOrNull()?.name ?: "Unknown artist",
        album = album?.name ?: "Unknown album",
        durationMs = ((duration ?: 0f) * 1000).toLong(),
        streamUrl = "",
        downloadUrl = "",
        thumbUrl = artworkUrl(server),
        year = year,
        genre = metadata?.genres.orEmpty().firstOrNull(),
        parentAlbumId = album?.stableId(),
        filepath = trackUri,
        dateAddedMs = addedAtMs(),
    )
}

private fun MaMediaItem.stableId(): String =
    uri?.takeIf { it.isNotBlank() }
        ?: itemId?.stringValue()
        ?: providerMappings.orEmpty().firstNotNullOfOrNull { it.itemId?.stringValue() }
        ?: name.orEmpty()

private fun MaMediaItem.artworkUrl(server: PlexServer): String? {
    val artwork = image?.takeIf { !it.path.isNullOrBlank() }
        ?: metadata?.images.orEmpty().firstOrNull { it.type.equals("thumb", ignoreCase = true) && !it.path.isNullOrBlank() }
        ?: metadata?.images.orEmpty().firstOrNull { !it.path.isNullOrBlank() }
        ?: return null
    val path = artwork.path?.takeIf(String::isNotBlank) ?: return null
    return when {
        path.startsWith("http://") || path.startsWith("https://") -> path
        path.startsWith("/") -> "${server.uri}$path"
        else -> {
            val provider = artwork.provider
                ?.takeIf { it.isNotBlank() }
                ?.let { "provider=${it.encodeURLParameter()}&" }
                .orEmpty()
            val encodedPath = path.encodeURLParameter().encodeURLParameter()
            "${server.uri}/imageproxy?${provider}size=0&path=$encodedPath"
        }
    }
}

private fun MaMediaItem.addedAtMs(): Long? =
    timestampAdded?.let { it * 1000L } ?: dateAdded?.parseMusicAssistantTimestampMillis()

private fun musicAssistantAlbumIdByTitle(albums: List<Album>, track: Track): String =
    albums.firstOrNull {
        it.title.equals(track.album, ignoreCase = true) &&
            it.artist.equals(track.artist, ignoreCase = true)
    }?.id.orEmpty()

private fun mergeMusicAssistantArtists(
    explicitArtists: List<Artist>,
    albums: List<Album>,
    tracks: List<Track>,
): List<Artist> {
    val albumsByArtist = albums.groupBy { it.artist.normalizedArtistKey() }
    val tracksByArtist = tracks.groupBy { it.artist.normalizedArtistKey() }
    val explicitByKey = explicitArtists.associateBy { it.title.normalizedArtistKey() }
    val keys = (explicitByKey.keys + albumsByArtist.keys + tracksByArtist.keys)
        .filter { it.isNotBlank() && it != "unknown artist" }
    return keys.map { key ->
        val explicit = explicitByKey[key]
        val artistAlbums = albumsByArtist[key].orEmpty()
        val artistTracks = tracksByArtist[key].orEmpty()
        explicit?.copy(
            albumCount = explicit.albumCount.takeIf { it > 0 } ?: artistAlbums.size,
            songCount = explicit.songCount.takeIf { it > 0 } ?: artistTracks.distinctBy { it.id }.size,
            thumbUrl = explicit.thumbUrl ?: artistAlbums.firstNotNullOfOrNull { it.thumbUrl },
            genre = explicit.genre ?: artistAlbums.firstNotNullOfOrNull { it.genre },
            dateAddedMs = explicit.dateAddedMs ?: (artistAlbums.mapNotNull { it.dateAddedMs } + artistTracks.mapNotNull { it.dateAddedMs }).maxOrNull(),
        ) ?: Artist(
            id = artistAlbums.firstOrNull()?.artist ?: artistTracks.firstOrNull()?.artist ?: key,
            title = artistAlbums.firstOrNull()?.artist ?: artistTracks.firstOrNull()?.artist ?: key,
            thumbUrl = artistAlbums.firstNotNullOfOrNull { it.thumbUrl },
            albumCount = artistAlbums.size,
            songCount = artistTracks.distinctBy { it.id }.size,
            genre = artistAlbums.firstNotNullOfOrNull { it.genre },
            dateAddedMs = (artistAlbums.mapNotNull { it.dateAddedMs } + artistTracks.mapNotNull { it.dateAddedMs }).maxOrNull(),
        )
    }.sortedBy { it.title.lowercase() }
}

private fun String.normalizedArtistKey(): String = trim().lowercase()

private fun JsonElement.stringValue(): String? =
    jsonPrimitive.contentOrNull?.takeIf { it.isNotBlank() }

private fun jsonObject(vararg pairs: Pair<String, Any?>): JsonObject =
    JsonObject(
        pairs.mapNotNull { (key, value) ->
            key to when (value) {
                null -> JsonNull
                is JsonElement -> value
                is String -> JsonPrimitive(value)
                is Number -> JsonPrimitive(value)
                is Boolean -> JsonPrimitive(value)
                else -> JsonPrimitive(value.toString())
            }
        }.toMap(),
    )

private fun JsonElement.asObjectOrNull(): JsonObject? = this as? JsonObject

private fun JsonObject.string(key: String): String? =
    this[key]?.jsonPrimitive?.contentOrNull?.takeIf { it.isNotBlank() }

private fun String.parseMusicAssistantTimestampMillis(): Long? {
    val trimmed = trim()
    if (trimmed.length < 19) return null
    val year = trimmed.substringOrNull(0, 4)?.toIntOrNull() ?: return null
    val month = trimmed.substringOrNull(5, 7)?.toIntOrNull() ?: return null
    val day = trimmed.substringOrNull(8, 10)?.toIntOrNull() ?: return null
    val hour = trimmed.substringOrNull(11, 13)?.toIntOrNull() ?: return null
    val minute = trimmed.substringOrNull(14, 16)?.toIntOrNull() ?: return null
    val second = trimmed.substringOrNull(17, 19)?.toIntOrNull() ?: return null
    val offsetMinutes = when {
        trimmed.endsWith("Z") -> 0
        trimmed.length >= 25 && (trimmed[trimmed.length - 6] == '+' || trimmed[trimmed.length - 6] == '-') -> {
            val sign = if (trimmed[trimmed.length - 6] == '-') -1 else 1
            val offsetHour = trimmed.substringOrNull(trimmed.length - 5, trimmed.length - 3)?.toIntOrNull() ?: 0
            val offsetMinute = trimmed.substringOrNull(trimmed.length - 2, trimmed.length)?.toIntOrNull() ?: 0
            sign * (offsetHour * 60 + offsetMinute)
        }
        else -> 0
    }
    val epochDay = daysFromCivil(year, month, day)
    return (((epochDay * 24 + hour) * 60 + minute - offsetMinutes) * 60 + second) * 1000
}

private fun String.substringOrNull(startIndex: Int, endIndex: Int): String? =
    if (startIndex >= 0 && endIndex <= length && startIndex <= endIndex) substring(startIndex, endIndex) else null

private fun daysFromCivil(year: Int, month: Int, day: Int): Long {
    var y = year
    val m = month
    y -= if (m <= 2) 1 else 0
    val era = if (y >= 0) y else y - 399
    val eraDiv = era / 400
    val yoe = y - eraDiv * 400
    val mp = m + if (m > 2) -3 else 9
    val doy = (153 * mp + 2) / 5 + day - 1
    val doe = yoe * 365 + yoe / 4 - yoe / 100 + doy
    return eraDiv.toLong() * 146097L + doe - 719468L
}
