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
)

@Serializable
data class Album(
    val id: String,
    val title: String,
    val artist: String,
    val year: Int? = null,
    val thumbUrl: String? = null,
)

@Serializable
data class Playlist(
    val id: String,
    val title: String,
    val trackCount: Int,
    val key: String? = null,
    val thumbUrl: String? = null,
)

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
    val localUri: String? = null,
    val year: Int? = null,
    val genre: String? = null,
    /** Display path: server file path or local URI tail. */
    val filepath: String? = null,
    val audioCodec: String? = null,
    /** Kilobits per second when known. */
    val bitrateKbps: Int? = null,
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
    Name,
    Artist,
    Album,
    Year,
}

@Serializable
data class LibraryColumnVisibility(
    val year: Boolean = true,
    val genre: Boolean = true,
    val filepath: Boolean = false,
    val audioCodec: Boolean = false,
    val bitrate: Boolean = false,
    val duration: Boolean = true,
    val sampleRate: Boolean = true,
    val fileType: Boolean = true,
    val dateAdded: Boolean = true,
)

@Serializable
data class LibraryUiPreferences(
    val sortBy: LibrarySortBy = LibrarySortBy.Name,
    val ascending: Boolean = true,
    val columns: LibraryColumnVisibility = LibraryColumnVisibility(),
)

@Serializable
data class CatalogSnapshot(
    val artists: List<Artist> = emptyList(),
    val albums: List<Album> = emptyList(),
    val playlists: List<Playlist> = emptyList(),
    val tracksByParent: Map<String, List<Track>> = emptyMap(),
    val downloads: List<DownloadItem> = emptyList(),
)

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
    data class AlbumDetail(val album: Album) : AppScreen
    data class ArtistDetail(val artist: Artist) : AppScreen
    data class PlaylistDetail(val playlist: Playlist) : AppScreen
    data object Player : AppScreen
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
