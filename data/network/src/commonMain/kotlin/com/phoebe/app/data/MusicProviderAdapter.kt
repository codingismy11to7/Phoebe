package com.phoebe.app.data

import com.phoebe.app.domain.Album
import com.phoebe.app.domain.Artist
import com.phoebe.app.domain.CatalogSnapshot
import com.phoebe.app.domain.CollectionFacet
import com.phoebe.app.domain.MediaProviderType
import com.phoebe.app.domain.MusicLibrary
import com.phoebe.app.domain.PlexRadioStation
import com.phoebe.app.domain.PlexServer
import com.phoebe.app.domain.PlexSession
import com.phoebe.app.domain.Playlist
import com.phoebe.app.domain.Track
import com.phoebe.app.domain.TrackMetadataUpdate

data class ProviderCapabilities(
    val passwordSignIn: Boolean = true,
    val quickConnect: Boolean = false,
    val serverDiscovery: Boolean = false,
    val pagedCatalog: Boolean = false,
    val playlists: Boolean = true,
    val playlistMutation: Boolean = true,
    val favorites: Boolean = true,
    val ratings: Boolean = true,
    val metadataEdit: Boolean = false,
    val nativeStreaming: Boolean = true,
    val remotePlayerControl: Boolean = false,
    val libraryRadio: Boolean = false,
    val itemRadio: Boolean = false,
    val collectionFacets: Set<CollectionFacet> = setOf(CollectionFacet.Genre),
    val matrix: ProviderCapabilityMatrix = ProviderCapabilityMatrix(),
)

data class ProviderCapabilityMatrix(
    val signIn: CapabilityStatus = CapabilityStatus.Supported,
    val discovery: CapabilityStatus = CapabilityStatus.Unsupported("Server discovery is not available for this provider."),
    val catalogPaging: CapabilityStatus = CapabilityStatus.Unsupported("This provider loads the catalog as a single library snapshot."),
    val playlists: CapabilityStatus = CapabilityStatus.Supported,
    val playlistMutation: CapabilityStatus = CapabilityStatus.Supported,
    val favorites: CapabilityStatus = CapabilityStatus.Supported,
    val ratings: CapabilityStatus = CapabilityStatus.Supported,
    val metadataFields: Set<ProviderMetadataField> = setOf(
        ProviderMetadataField.Title,
        ProviderMetadataField.Artist,
        ProviderMetadataField.Album,
        ProviderMetadataField.Year,
        ProviderMetadataField.Genre,
    ),
    val radioAndMixes: CapabilityStatus = CapabilityStatus.Unsupported("Radio is not available for this provider."),
    val playHistoryImport: CapabilityStatus = CapabilityStatus.Unsupported("Play history import is not available for this provider."),
    val streaming: CapabilityStatus = CapabilityStatus.Supported,
    val downloads: CapabilityStatus = CapabilityStatus.Supported,
    val transcodeQuality: CapabilityStatus = CapabilityStatus.Unsupported("Only original quality downloads are available."),
    val internetRadio: CapabilityStatus = CapabilityStatus.Unsupported("Internet radio is managed locally by Phoebe."),
)

sealed interface CapabilityStatus {
    data object Supported : CapabilityStatus
    data class Unsupported(val reason: String) : CapabilityStatus
}

enum class ProviderMetadataField {
    Title,
    Artist,
    Album,
    AlbumArtist,
    Year,
    Genre,
    Mood,
    Style,
    TrackNumber,
    DiscNumber,
    Composer,
    Comments,
    Explicit,
    SortFields,
}

fun ProviderCapabilities.unavailableReason(action: ProviderAction): String? =
    when (action) {
        ProviderAction.ServerDiscovery -> matrix.discovery.reasonOrNull().takeIf { !serverDiscovery }
        ProviderAction.CatalogPaging -> matrix.catalogPaging.reasonOrNull().takeIf { !pagedCatalog }
        ProviderAction.Playlists -> matrix.playlists.reasonOrNull().takeIf { !playlists }
        ProviderAction.PlaylistMutation -> matrix.playlistMutation.reasonOrNull().takeIf { !playlistMutation }
        ProviderAction.Favorites -> matrix.favorites.reasonOrNull().takeIf { !favorites }
        ProviderAction.Ratings -> matrix.ratings.reasonOrNull().takeIf { !ratings }
        ProviderAction.MetadataEdit -> "Metadata edits stay local for this provider.".takeIf { !metadataEdit }
        ProviderAction.Radio -> matrix.radioAndMixes.reasonOrNull().takeIf { !libraryRadio && !itemRadio }
        ProviderAction.Streaming -> matrix.streaming.reasonOrNull().takeIf { !nativeStreaming }
        ProviderAction.RemoteControl -> "Remote control is not available for this provider.".takeIf { !remotePlayerControl }
        ProviderAction.Downloads -> matrix.downloads.reasonOrNull()
        ProviderAction.TranscodeQuality -> matrix.transcodeQuality.reasonOrNull()
        ProviderAction.InternetRadio -> matrix.internetRadio.reasonOrNull()
    }

enum class ProviderAction {
    ServerDiscovery,
    CatalogPaging,
    Playlists,
    PlaylistMutation,
    Favorites,
    Ratings,
    MetadataEdit,
    Radio,
    Streaming,
    RemoteControl,
    Downloads,
    TranscodeQuality,
    InternetRadio,
}

private fun CapabilityStatus.reasonOrNull(): String? =
    when (this) {
        CapabilityStatus.Supported -> null
        is CapabilityStatus.Unsupported -> reason
    }

data class ProviderItemPage<T>(
    val items: List<T>,
    val total: Int,
    val pageIndex: Int,
    val pageSize: Int,
)

enum class ProviderItemKind {
    Artist,
    Album,
    Track,
    Playlist,
    Unknown,
}

enum class JellyfinPlaybackEvent {
    Start,
    Progress,
    Stop,
}

interface MusicProviderAdapter {
    val providerType: MediaProviderType
    val capabilities: ProviderCapabilities

    suspend fun signIn(serverUrl: String, username: String, password: String): PlexSession

    suspend fun servers(session: PlexSession): List<PlexServer> =
        listOfNotNull(session.selectedServer)

    suspend fun libraries(session: PlexSession, server: PlexServer): List<MusicLibrary>

    suspend fun buildCatalog(session: PlexSession): CatalogSnapshot

    suspend fun quickCatalog(session: PlexSession): CatalogSnapshot? = null

    suspend fun albumPageCatalog(session: PlexSession, pageIndex: Int): Pair<CatalogSnapshot, ProviderItemPage<Album>>? = null

    suspend fun albumTracks(session: PlexSession, album: Album): List<Track> = emptyList()

    suspend fun playlistTracks(session: PlexSession, playlist: Playlist): List<Track> = emptyList()

    suspend fun createPlaylist(session: PlexSession, title: String, initialTracks: List<Track>): Playlist? = null

    suspend fun addTracksToPlaylist(session: PlexSession, playlist: Playlist, tracks: List<Track>) {}

    suspend fun replacePlaylistTracks(session: PlexSession, playlist: Playlist, tracks: List<Track>): Boolean = false

    suspend fun setFavorite(session: PlexSession, itemId: String, favorite: Boolean, kind: ProviderItemKind = ProviderItemKind.Unknown): Boolean = false

    suspend fun rateItem(session: PlexSession, itemId: String, rating: Float?): Boolean = false

    suspend fun editTrackMetadata(session: PlexSession, itemId: String, original: Track, update: TrackMetadataUpdate): Boolean = false

    suspend fun reportPlayback(session: PlexSession, track: Track, positionMs: Long, isPaused: Boolean, event: JellyfinPlaybackEvent) {}

    suspend fun markPlayed(session: PlexSession, track: Track, playedAtMs: Long) {}

    suspend fun radioStation(session: PlexSession, artist: Artist): PlexRadioStation? = null

    suspend fun playArtistRadio(session: PlexSession, artist: Artist): List<Track> = emptyList()

    suspend fun playRemote(session: PlexSession, tracks: List<Track>, index: Int): String? = null

    suspend fun streamUrl(session: PlexSession, track: Track): String? = null
}

class MusicProviderRegistry(
    adapters: List<MusicProviderAdapter>,
) {
    private val byType = adapters.associateBy { it.providerType }

    fun adapterFor(type: MediaProviderType): MusicProviderAdapter? = byType[type]

    fun adapterFor(session: PlexSession?): MusicProviderAdapter? =
        session?.providerType?.let(::adapterFor)
}
