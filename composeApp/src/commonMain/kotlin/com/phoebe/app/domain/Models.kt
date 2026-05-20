package com.phoebe.app.domain

import kotlinx.serialization.Serializable

@Serializable
enum class MediaProviderType {
    Plex,
    Jellyfin,
    Emby,
    Navidrome,
    MusicAssistant,
}

@Serializable
enum class JellyfinSyncMode {
    Quick,
    Full,
}

enum class JellyfinLibraryPageKind {
    Artists,
    Albums,
    Tracks,
}

@Serializable
data class PlexSession(
    val token: String,
    val userName: String = "Plex listener",
    val selectedServer: PlexServer? = null,
    val selectedLibrary: MusicLibrary? = null,
    val providerType: MediaProviderType = MediaProviderType.Plex,
    val userId: String? = null,
    val jellyfinSyncMode: JellyfinSyncMode = JellyfinSyncMode.Quick,
)

typealias ProviderSession = PlexSession
typealias ProviderServer = PlexServer
typealias ProviderLibrary = MusicLibrary

@Serializable
data class ProviderAuthState(
    val providerType: MediaProviderType,
    val serverUrl: String,
    val userName: String = "",
    val token: String = "",
    val userId: String? = null,
)

val MediaProviderType.catalogPrefix: String
    get() = when (this) {
        MediaProviderType.Plex -> "plex"
        MediaProviderType.Jellyfin -> "jellyfin"
        MediaProviderType.Emby -> "emby"
        MediaProviderType.Navidrome -> "navidrome"
        MediaProviderType.MusicAssistant -> "music-assistant"
    }

val MediaProviderType.displayName: String
    get() = when (this) {
        MediaProviderType.Plex -> "Plex"
        MediaProviderType.Jellyfin -> "Jellyfin"
        MediaProviderType.Emby -> "Emby"
        MediaProviderType.Navidrome -> "Subsonic (Navidrome, etc)"
        MediaProviderType.MusicAssistant -> "Music Assistant"
    }

fun MediaProviderType.usesServerTokenAsAuth(): Boolean =
    this == MediaProviderType.Plex

fun MediaProviderType.isEmbyFamily(): Boolean =
    this == MediaProviderType.Jellyfin || this == MediaProviderType.Emby

/** Plex playlists (create/add) require token, server, and music library. */
fun PlexSession?.supportsPlexPlaylists(): Boolean {
    val s = this ?: return false
    return s.providerType == MediaProviderType.Plex &&
        s.token.isNotBlank() &&
        s.selectedServer != null &&
        s.selectedLibrary != null
}

fun PlexSession?.supportsRemotePlaylists(): Boolean {
    val s = this ?: return false
    return s.token.isNotBlank() && s.selectedServer != null && s.selectedLibrary != null
}

/** Plex ratings require token and a selected server; they apply to metadata rating keys. */
fun PlexSession?.supportsPlexRatings(): Boolean {
    val s = this ?: return false
    return s.providerType == MediaProviderType.Plex && s.token.isNotBlank() && s.selectedServer != null
}

fun PlexSession?.supportsRemoteRatings(): Boolean {
    val s = this ?: return false
    return s.providerType != MediaProviderType.MusicAssistant && s.token.isNotBlank() && s.selectedServer != null
}

fun PlexSession?.isJellyfin(): Boolean = this?.providerType == MediaProviderType.Jellyfin

fun PlexSession?.isEmby(): Boolean = this?.providerType == MediaProviderType.Emby

fun PlexSession?.isEmbyFamily(): Boolean = this?.providerType?.isEmbyFamily() == true

fun PlexSession?.isNavidrome(): Boolean = this?.providerType == MediaProviderType.Navidrome

fun PlexSession?.isMusicAssistant(): Boolean = this?.providerType == MediaProviderType.MusicAssistant

fun PlexSession?.isPlex(): Boolean = this?.providerType == MediaProviderType.Plex

fun PlexSession?.providerLabel(): String = when (this?.providerType) {
    null -> "Plex"
    else -> this.providerType.displayName
}

data class ProviderFeatureSet(
    val collectionFacetsByTarget: Map<CollectionTarget, Set<CollectionFacet>>,
) {
    fun supports(entry: CollectionEntry): Boolean =
        collectionFacetsByTarget[entry.target]?.contains(entry.facet) == true

    fun collectionEntries(): List<CollectionEntry> =
        CollectionTarget.entries.flatMap { target ->
            CollectionFacet.entries
                .filter { facet -> collectionFacetsByTarget[target]?.contains(facet) == true }
                .map { facet -> CollectionEntry(target, facet) }
        }
}

val MediaProviderType.featureSet: ProviderFeatureSet
    get() = when (this) {
        MediaProviderType.Plex -> ProviderFeatureSet(
            collectionFacetsByTarget = CollectionTarget.entries.associateWith {
                setOf(CollectionFacet.Mood, CollectionFacet.Style, CollectionFacet.Genre)
            },
        )
        MediaProviderType.Jellyfin,
        MediaProviderType.Emby,
        MediaProviderType.Navidrome,
        MediaProviderType.MusicAssistant,
        -> ProviderFeatureSet(
            collectionFacetsByTarget = CollectionTarget.entries.associateWith {
                setOf(CollectionFacet.Genre)
            },
        )
    }

fun PlexSession?.providerFeatureSet(): ProviderFeatureSet =
    this?.providerType?.featureSet ?: MediaProviderType.Plex.featureSet

fun PlexSession?.supportsCollectionEntry(entry: CollectionEntry): Boolean =
    providerFeatureSet().supports(entry)

fun PlexSession?.supportedCollectionEntries(): List<CollectionEntry> =
    providerFeatureSet().collectionEntries()

val defaultCollectionEntries: List<CollectionEntry>
    get() = MediaProviderType.Plex.featureSet.collectionEntries()

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
    val personalMix: PersonalMixPreferences = PersonalMixPreferences(),
)

@Serializable
data class AppSettings(
    val crossfadeSeconds: Int = 0,
    val scanLibraryOnLaunch: Boolean = false,
    val notifyWhenDownloadFinishes: Boolean = false,
) {
    fun normalized(): AppSettings =
        copy(crossfadeSeconds = crossfadeSeconds.coerceIn(MinCrossfadeSeconds, MaxCrossfadeSeconds))

    companion object {
        val Default = AppSettings()
        const val MinCrossfadeSeconds = 0
        const val MaxCrossfadeSeconds = 12
    }
}

@Serializable
data class PersonalMixPreferences(
    val limit: Int = 50,
    val heavyRotationWeight: Int = 25,
    val recentWeight: Int = 30,
    val mostPlayedWeight: Int = 25,
    val similarWeight: Int = 15,
    val discoveryWeight: Int = 5,
) {
    companion object {
        val Default = PersonalMixPreferences()
        const val MinLimit = 10
        const val MaxLimit = 100
    }

    fun normalized(): PersonalMixPreferences =
        normalizeMixWeights(
            copy(
                limit = limit.coerceIn(MinLimit, MaxLimit),
                heavyRotationWeight = heavyRotationWeight.coerceAtLeast(0),
                recentWeight = recentWeight.coerceAtLeast(0),
                mostPlayedWeight = mostPlayedWeight.coerceAtLeast(0),
                similarWeight = similarWeight.coerceAtLeast(0),
                discoveryWeight = discoveryWeight.coerceAtLeast(0),
            ),
        )
}

private fun normalizeMixWeights(preferences: PersonalMixPreferences): PersonalMixPreferences {
    val weights = listOf(
        preferences.heavyRotationWeight,
        preferences.recentWeight,
        preferences.mostPlayedWeight,
        preferences.similarWeight,
        preferences.discoveryWeight,
    )
    val total = weights.sum()
    if (total <= 100) return preferences

    val scaled = weights.map { weight -> (weight.toDouble() * 100.0) / total.toDouble() }
    val base = scaled.map { it.toInt() }.toMutableList()
    var remaining = 100 - base.sum()
    scaled.indices
        .sortedWith(compareByDescending<Int> { scaled[it] - base[it] }.thenBy { it })
        .forEach { index ->
            if (remaining > 0) {
                base[index]++
                remaining--
            }
        }
    return preferences.copy(
        limit = preferences.limit.coerceIn(PersonalMixPreferences.MinLimit, PersonalMixPreferences.MaxLimit),
        heavyRotationWeight = base[0],
        recentWeight = base[1],
        mostPlayedWeight = base[2],
        similarWeight = base[3],
        discoveryWeight = base[4],
    )
}

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
    val remotePageInfo: CatalogPageInfo = CatalogPageInfo(),
)

@Serializable
data class CatalogPageInfo(
    val pageSize: Int = 100,
    val artistTotal: Int? = null,
    val albumTotal: Int? = null,
    val trackTotal: Int? = null,
    val loadedArtistPages: Set<Int> = emptySet(),
    val loadedAlbumPages: Set<Int> = emptySet(),
    val loadedTrackPages: Set<Int> = emptySet(),
) {
    val hasAny: Boolean
        get() = artistTotal != null || albumTotal != null || trackTotal != null ||
            loadedArtistPages.isNotEmpty() || loadedAlbumPages.isNotEmpty() || loadedTrackPages.isNotEmpty()

    /** True when Quick sync has not yet loaded every remote page (reconcile must not drop unloaded tracks). */
    fun hasUnloadedRemotePages(): Boolean {
        val size = pageSize.coerceAtLeast(1)
        fun hasMore(total: Int?, loadedPages: Set<Int>): Boolean {
            if (total == null || loadedPages.isEmpty()) return false
            val loadedCount = loadedPages.size
            val expectedPages = (total + size - 1) / size
            return loadedCount < expectedPages
        }
        return hasMore(artistTotal, loadedArtistPages) ||
            hasMore(albumTotal, loadedAlbumPages) ||
            hasMore(trackTotal, loadedTrackPages)
    }
}

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
    val detail: String? = null,
    val loadedAlbums: Int = 0,
    val loadedTracks: Int = 0,
    val totalTracks: Int? = null,
    val totalPlaylists: Int? = null,
    val warmedPlaylists: Int = 0,
    val progress: Float? = null,
    val blocking: Boolean = false,
) {
    val isActive: Boolean
        get() = phase != CatalogSyncPhase.Idle &&
            phase != CatalogSyncPhase.Complete &&
            phase != CatalogSyncPhase.Failed &&
            phase != CatalogSyncPhase.RestoringCache

    /** True when playlist track lists are being fetched in the foreground sync path. */
    val isRefreshingPlaylists: Boolean
        get() = phase == CatalogSyncPhase.RefreshingPlaylists

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

val AppScreen.telemetryName: String
    get() = when (this) {
        AppScreen.SignIn -> "sign_in"
        AppScreen.ServerPicker -> "server_picker"
        AppScreen.LibraryPicker -> "library_picker"
        AppScreen.Home -> "home"
        is AppScreen.Collections -> "collections"
        is AppScreen.CollectionItems -> "collection_items"
        is AppScreen.AlbumDetail -> "album_detail"
        is AppScreen.ArtistDetail -> "artist_detail"
        is AppScreen.SongDetail -> "song_detail"
        is AppScreen.Lyrics -> "lyrics"
        is AppScreen.RecentlyAdded -> "recently_added"
        is AppScreen.PlayHistory -> "play_history"
        AppScreen.FavoritePlaylists -> "favorite_playlists"
        AppScreen.FavoriteArtists -> "favorite_artists"
        AppScreen.FavoriteAlbums -> "favorite_albums"
        is AppScreen.PlaylistDetail -> "playlist_detail"
        AppScreen.Player -> "player"
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

/** Ranked play-history row from SQL top-N queries (ordering + warm hints). */
data class MostPlayedEntry(
    val trackId: String,
    val playCount: Long,
    val lastPlayedMs: Long,
    val artist: String,
    val album: String,
)

/** Recently played track ranked by last play time. */
data class RecentlyPlayedEntry(
    val trackId: String,
    val lastPlayedMs: Long,
    val artist: String,
    val album: String,
)

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

/** Playback fields exposed to browse chrome without subscribing to transport ticks. */
data class ShellPlaybackState(
    val currentTrack: Track? = null,
    val isPlaying: Boolean = false,
    val isBuffering: Boolean = false,
)

/** Queue snapshot for up-next / skip UI; ignores position-only player updates. */
data class PlayerQueueSnapshot(
    val queue: List<Track> = emptyList(),
    val currentIndex: Int = -1,
) {
    val currentTrack: Track? get() = queue.getOrNull(currentIndex)
    val upNext: List<Track>
        get() = if (currentIndex < 0) emptyList() else queue.drop(currentIndex + 1)
}

data class PlayerState(
    val queue: List<Track> = emptyList(),
    val currentIndex: Int = -1,
    val isPlaying: Boolean = false,
    /** True while the platform player is preparing the next track after a switch. */
    val isBuffering: Boolean = false,
    val positionMs: Long = 0L,
    val bufferedPositionMs: Long = 0L,
    val durationMs: Long = 0L,
    val playbackErrorSerial: Int = 0,
    val playbackErrorMessage: String? = null,
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

fun Track.isJellyfinLibraryTrack(): Boolean = id.startsWith("jellyfin:")

fun Track.isEmbyLibraryTrack(): Boolean = id.startsWith("emby:")

fun Track.isNavidromeLibraryTrack(): Boolean = id.startsWith("navidrome:")

fun Track.isMusicAssistantLibraryTrack(): Boolean = id.startsWith("music-assistant:")

fun Track.remoteProviderPrefix(): String? =
    id.substringBefore(':', missingDelimiterValue = "").takeIf { prefix ->
        MediaProviderType.entries.any { it.catalogPrefix == prefix }
    }

fun Track.isRemoteLibraryTrack(): Boolean = remoteProviderPrefix() != null

fun Track.belongsToProvider(providerType: MediaProviderType): Boolean =
    id.startsWith("${providerType.catalogPrefix}:")

const val LOCAL_PLAYLIST_ID_PREFIX = "local:playlist:"
const val LIKED_SONGS_PLAYLIST_TITLE = "Liked Songs"
const val PENDING_LIKED_SONGS_PLAYLIST_ID = "plex:liked-songs-pending"

/** User-created playlist stored only in Phoebe (not synced to Plex). */
fun Playlist.isLocalPlaylist(): Boolean = id.startsWith(LOCAL_PLAYLIST_ID_PREFIX)

fun Playlist.isLikedSongsPlaylist(): Boolean =
    !isLocalPlaylist() && title.equals(LIKED_SONGS_PLAYLIST_TITLE, ignoreCase = false)

/** Local playlists accept on-device audio files only. */
fun Track.canAddToLocalPlaylist(): Boolean = isLocalMediaPlayback()

/** Remote playlists accept anything with a provider identity, including downloaded remote songs. */
fun Track.canAddToPlexPlaylist(): Boolean = isRemoteLibraryTrack()

/** Liked Songs syncs by Plex identity, so downloaded Plex songs are still eligible. */
fun Track.canTogglePlexLike(): Boolean = isRemoteLibraryTrack()

fun Playlist.remoteProviderPrefix(): String? =
    id.substringBefore(':', missingDelimiterValue = "").takeIf { prefix ->
        MediaProviderType.entries.any { it.catalogPrefix == prefix }
    }

fun Playlist.isRemoteProviderPlaylist(): Boolean = remoteProviderPrefix() != null

fun Playlist.belongsToProvider(providerType: MediaProviderType): Boolean =
    id.startsWith("${providerType.catalogPrefix}:")
