package com.phoebe.app.domain

import kotlinx.serialization.Serializable

@Serializable
data class PlexSession(
    val token: String,
    val userName: String = "Plex listener",
    val selectedServer: PlexServer? = null,
    val selectedLibrary: MusicLibrary? = null,
)

/** Plex playlists (create/add) require token, server, and music library. */
fun PlexSession?.supportsPlexPlaylists(): Boolean {
    val s = this ?: return false
    return s.token.isNotBlank() && s.selectedServer != null && s.selectedLibrary != null
}

/** Plex ratings require token and a selected server; they apply to metadata rating keys. */
fun PlexSession?.supportsPlexRatings(): Boolean {
    val s = this ?: return false
    return s.token.isNotBlank() && s.selectedServer != null
}

@Serializable
data class PlexPin(
    val id: Long,
    val code: String,
    val authUrl: String,
)

@Serializable
data class PlexServer(
    val id: String,
    val name: String,
    val uri: String,
    val owned: Boolean,
    /** Advertised URLs plus synthesized plain-IP fallbacks from plex.direct hostnames. */
    val connectionUris: List<String> = emptyList(),
    /** URLs Plex.tv listed explicitly (never synthesized). */
    val advertisedConnectionUris: List<String> = emptyList(),
    /** Subset of [advertisedConnectionUris] Plex marked `local=true`. */
    val localConnectionUris: List<String> = emptyList(),
    /** Per-server token from plex.tv `/resources`; required for many PMS API calls. */
    val accessToken: String? = null,
    val httpsRequired: Boolean = false,
) {
    fun authToken(fallbackUserToken: String): String =
        accessToken?.takeIf { it.isNotBlank() } ?: fallbackUserToken
}

/** Plex Media Server auth token (per-server accessToken when Plex provides one). */
fun PlexSession?.serverAuthToken(): String? {
    val s = this ?: return null
    val user = s.token.takeIf { it.isNotBlank() } ?: return null
    return s.selectedServer?.authToken(user) ?: user
}

@Serializable
data class MusicLibrary(
    val key: String,
    val title: String,
)

@Serializable
data class Artist(
    val id: String,
    val title: String,
    val thumbUrl: String? = null,
    val albumCount: Int = 0,
    /** Tracks currently loaded in [CatalogSnapshot.tracksByParent] for this artist (subset until fully fetched). */
    val songCount: Int = 0,
    val dateAddedMs: Long? = null,
    val genre: String? = null,
    val mood: String? = null,
    val style: String? = null,
    /** User rating normalized to 0..5 stars. Plex stores this as 0..10. */
    val rating: Float? = null,
    val favorite: Boolean = false,
)

@Serializable
data class Album(
    val id: String,
    val title: String,
    val artist: String,
    val year: Int? = null,
    val thumbUrl: String? = null,
    val dateAddedMs: Long? = null,
    val genre: String? = null,
    val mood: String? = null,
    val style: String? = null,
    /** User rating normalized to 0..5 stars. Plex stores this as 0..10. */
    val rating: Float? = null,
    val favorite: Boolean = false,
)

@Serializable
data class Playlist(
    val id: String,
    val title: String,
    val trackCount: Int,
    val key: String? = null,
    val thumbUrl: String? = null,
    /** User rating normalized to 0..5 stars. Plex stores this as 0..10. */
    val rating: Float? = null,
    val favorite: Boolean = false,
)

@Serializable
data class PlexRadioStation(
    val id: String,
    val title: String,
    val subtitle: String,
    val key: String,
    val thumbUrl: String? = null,
    val category: PlexRadioStationCategory = PlexRadioStationCategory.Library,
)

@Serializable
enum class PlexRadioStationCategory {
    Library,
    Artist,
}

@Serializable
enum class ArtistRadioAvailability {
    Available,
    Unavailable,
}

@Serializable
data class Track(
    val id: String,
    val title: String,
    val artist: String,
    val album: String,
    val durationMs: Long,
    val streamUrl: String,
    val downloadUrl: String,
    val thumbUrl: String? = null,
    val localArtworkUri: String? = null,
    val localUri: String? = null,
    val year: Int? = null,
    val genre: String? = null,
    val mood: String? = null,
    val style: String? = null,
    /** Display path: server file path or local URI tail. */
    val filepath: String? = null,
    val audioCodec: String? = null,
    /** Kilobits per second when known. */
    val bitrateKbps: Int? = null,
    val dateAddedMs: Long? = null,
    /** User rating normalized to 0..5 stars. Plex stores this as 0..10. */
    val rating: Float? = null,
    /** Plex playlist entry id, present when this track was loaded from a playlist. */
    val playlistItemId: Long? = null,
    /** Plex album rating key for flat track index responses, if known. */
    val parentAlbumId: String? = null,
)

data class TrackMetadataUpdate(
    val trackId: String,
    val title: String,
    val artist: String,
    val album: String,
    val year: Int? = null,
    val genre: String? = null,
)

@Serializable
enum class LibrarySortBy {
    /** Preserve the album's source track order. */
    AlbumOrder,
    /** Preserve the source playlist's track order (e.g. Plex). */
    PlaylistOrder,
    Name,
    Artist,
    Album,
    Year,
    DateAdded,
}

@Serializable
data class LibraryColumnVisibility(
    val year: Boolean = false,
    val genre: Boolean = false,
    val filepath: Boolean = false,
    val audioCodec: Boolean = false,
    val bitrate: Boolean = false,
    val duration: Boolean = false,
    val sampleRate: Boolean = false,
    val fileType: Boolean = false,
    val dateAdded: Boolean = false,
    val rating: Boolean = false,
    val favorite: Boolean = false,
)

@Serializable
data class LibraryUiPreferences(
    val sortBy: LibrarySortBy = LibrarySortBy.Name,
    val ascending: Boolean = true,
    val columns: LibraryColumnVisibility = LibraryColumnVisibility(),
    val homeSections: List<HomeSection> = HomeSection.defaultOrder,
)

@Serializable
enum class HomeSection(val label: String) {
    Mixes("Mixes"),
    Collections("Collections"),
    /** Kept only to migrate older saved preferences into the split favorite sections. */
    Favorites("Favorites"),
    FavoritePlaylists("Favorite playlists"),
    FavoriteArtists("Favorite artists"),
    FavoriteAlbums("Favorite albums"),
    /** Kept only to migrate older saved preferences into the split recent sections. */
    Recents("Recents"),
    RecentSongs("Recent songs"),
    RecentArtists("Recent artists"),
    RecentAlbums("Recent albums"),
    Played("Listening history"),
    Random("Random picks");

    companion object {
        val defaultOrder: List<HomeSection> =
            listOf(
                Mixes,
                Collections,
                FavoritePlaylists,
                FavoriteArtists,
                FavoriteAlbums,
                RecentSongs,
                RecentArtists,
                RecentAlbums,
                Played,
                Random,
            )
    }
}

@Serializable
data class CatalogSnapshot(
    val artists: List<Artist> = emptyList(),
    val albums: List<Album> = emptyList(),
    val playlists: List<Playlist> = emptyList(),
    val tracksByParent: Map<String, List<Track>> = emptyMap(),
    val collectionValues: List<CatalogCollectionValue> = emptyList(),
    val collectionValueLoads: List<CatalogCollectionValueLoad> = emptyList(),
    val collectionTags: List<CatalogCollectionTag> = emptyList(),
    val downloads: List<DownloadItem> = emptyList(),
)

@Serializable
data class CatalogCollectionValueLoad(
    val target: String,
    val facet: String,
)

@Serializable
data class CatalogCollectionValue(
    val target: String,
    val facet: String,
    val value: String,
    val key: String,
    val fastKey: String? = null,
    val filterField: String? = null,
    val itemsLoaded: Boolean = false,
)

@Serializable
data class CatalogCollectionTag(
    val target: String,
    val facet: String,
    val itemId: String,
    val value: String,
)

enum class CatalogSyncPhase {
    Idle,
    RestoringCache,
    LoadingLibrary,
    LoadingSongs,
    RefreshingPlaylists,
    FinishingArtwork,
    Persisting,
    Complete,
    Failed,
}

data class CatalogSyncState(
    val phase: CatalogSyncPhase = CatalogSyncPhase.Idle,
    val message: String? = null,
    val loadedAlbums: Int = 0,
    val loadedTracks: Int = 0,
    val blocking: Boolean = false,
) {
    val isActive: Boolean
        get() = phase != CatalogSyncPhase.Idle &&
            phase != CatalogSyncPhase.Complete &&
            phase != CatalogSyncPhase.Failed

    val showGlobalProgress: Boolean
        get() = isActive && blocking
}

@Serializable
data class DownloadItem(
    val trackId: String,
    val title: String,
    val artist: String,
    val state: DownloadState,
    val progress: Float = 0f,
    val localUri: String? = null,
)

@Serializable
enum class DownloadState {
    Queued,
    Downloading,
    Complete,
    Failed,
}

enum class LibraryTab {
    Albums,
    Artists,
    Playlists,
    Downloads,
    Settings,
}

sealed interface AppScreen {
    data object SignIn : AppScreen
    data object ServerPicker : AppScreen
    data object LibraryPicker : AppScreen
    data object Home : AppScreen
    data class Collections(val entry: CollectionEntry = CollectionEntry(CollectionTarget.Artists, CollectionFacet.Genre)) : AppScreen
    data class CollectionItems(val entry: CollectionEntry, val value: String) : AppScreen
    data class AlbumDetail(val album: Album) : AppScreen
    data class ArtistDetail(val artist: Artist) : AppScreen
    data class SongDetail(val track: Track) : AppScreen
    data class Lyrics(val track: Track? = null) : AppScreen
    data class RecentlyAdded(val kind: RecentlyAddedKind) : AppScreen
    data class PlayHistory(val kind: PlayHistoryKind) : AppScreen
    data object FavoritePlaylists : AppScreen
    data object FavoriteArtists : AppScreen
    data object FavoriteAlbums : AppScreen
    data class PlaylistDetail(val playlist: Playlist) : AppScreen
    data object Player : AppScreen
}

data class LyricsLine(
    val startMs: Long?,
    val text: String,
)

enum class LyricsSource {
    LocalEmbedded,
    LocalSidecar,
    Lrclib,
    Cache,
}

data class LyricsDocument(
    val trackFingerprint: String,
    val lines: List<LyricsLine>,
    val source: LyricsSource,
    val synced: Boolean,
    val instrumental: Boolean = false,
) {
    val hasText: Boolean
        get() = lines.any { it.text.isNotBlank() }
}

sealed interface LyricsLoadState {
    data object Idle : LyricsLoadState
    data object Loading : LyricsLoadState
    data class Loaded(val document: LyricsDocument) : LyricsLoadState
    data object NotFound : LyricsLoadState
    data class Failed(val message: String) : LyricsLoadState
}

@Serializable
data class CollectionEntry(
    val target: CollectionTarget,
    val facet: CollectionFacet,
)

@Serializable
enum class CollectionTarget {
    Artists,
    Albums,
}

@Serializable
enum class CollectionFacet {
    Mood,
    Style,
    Genre,
}

@Serializable
enum class RecentlyAddedKind {
    Songs,
    Artists,
    Albums,
}

@Serializable
enum class PlayHistoryKind {
    RecentlyPlayed,
    MostPlayed,
}

/** Repeat cycle used by the audio player and surfaced in the UI. */
enum class RepeatMode {
    /** Don't repeat — playback stops at the end of the queue. */
    Off,
    /** Repeat the current track indefinitely. */
    One,
    /** Repeat the entire queue indefinitely. */
    All,
}

data class PlayerState(
    val queue: List<Track> = emptyList(),
    val currentIndex: Int = -1,
    val isPlaying: Boolean = false,
    /** True while the platform player is preparing the next track after a switch. */
    val isBuffering: Boolean = false,
    val positionMs: Long = 0L,
    val durationMs: Long = 0L,
    val shuffle: Boolean = false,
    val repeat: RepeatMode = RepeatMode.Off,
    val volume: Float = 0.7f,
) {
    val currentTrack: Track? get() = queue.getOrNull(currentIndex)

    /**
     * Tracks scheduled to play after the current one. Derived from the queue so that
     * playing an album/playlist automatically exposes its remaining tracks as Up Next,
     * and manually-queued tracks live in the same list.
     */
    val upNext: List<Track>
        get() = if (currentIndex < 0) emptyList() else queue.drop(currentIndex + 1)
}

/** True when the track is played from on-device storage (local folder or download), not stream-only. */
fun Track.isLocalMediaPlayback(): Boolean = !localUri.isNullOrBlank()

/** True when this row came from the Plex music library slice of the merged catalog. */
fun Track.isPlexLibraryTrack(): Boolean = id.startsWith("plex:")

const val LOCAL_PLAYLIST_ID_PREFIX = "local:playlist:"
const val LIKED_SONGS_PLAYLIST_TITLE = "Liked Songs"
const val PENDING_LIKED_SONGS_PLAYLIST_ID = "plex:liked-songs-pending"

/** User-created playlist stored only in Phoebe (not synced to Plex). */
fun Playlist.isLocalPlaylist(): Boolean = id.startsWith(LOCAL_PLAYLIST_ID_PREFIX)

fun Playlist.isLikedSongsPlaylist(): Boolean =
    !isLocalPlaylist() && title.equals(LIKED_SONGS_PLAYLIST_TITLE, ignoreCase = false)

/** Local playlists accept on-device audio files only. */
fun Track.canAddToLocalPlaylist(): Boolean = isLocalMediaPlayback()

/** Plex playlists accept anything with a Plex identity, including downloaded Plex songs. */
fun Track.canAddToPlexPlaylist(): Boolean = isPlexLibraryTrack()

/** Liked Songs syncs by Plex identity, so downloaded Plex songs are still eligible. */
fun Track.canTogglePlexLike(): Boolean = isPlexLibraryTrack()
