package com.phoebe.app.ui

import com.phoebe.app.data.JellyfinQuickConnectResult
import com.phoebe.app.domain.Album
import com.phoebe.app.domain.AppSettings
import com.phoebe.app.domain.AppScreen
import com.phoebe.app.domain.Artist
import com.phoebe.app.domain.ArtistRadioAvailability
import com.phoebe.app.domain.CatalogSnapshot
import com.phoebe.app.domain.CollectionEntry
import com.phoebe.app.domain.EqualizerProfile
import com.phoebe.app.domain.HomeSection
import com.phoebe.app.domain.JellyfinLibraryPageKind
import com.phoebe.app.domain.JellyfinSyncMode
import com.phoebe.app.domain.LibraryColumnVisibility
import com.phoebe.app.domain.LibrarySortBy
import com.phoebe.app.domain.LibraryUiPreferences
import com.phoebe.app.domain.LyricsLoadState
import com.phoebe.app.domain.MediaProviderType
import com.phoebe.app.domain.MediaSourcesState
import com.phoebe.app.domain.MusicLibrary
import com.phoebe.app.domain.PersonalMixPreferences
import com.phoebe.app.domain.PlexRadioStation
import com.phoebe.app.domain.PlexServer
import com.phoebe.app.domain.PlexSession
import com.phoebe.app.domain.PlayerState
import com.phoebe.app.domain.Playlist
import com.phoebe.app.domain.ShellPlaybackState
import com.phoebe.app.domain.Track
import com.phoebe.app.domain.defaultCollectionEntries
import com.phoebe.app.player.CastState

internal data class DesktopShellState(
    val screen: AppScreen,
    val routes: List<PhoebeRoute> = emptyList(),
    val catalog: CatalogSnapshot,
    val catalogRefreshing: Boolean,
    val session: PlexSession?,
    val mediaSources: MediaSourcesState,
    val section: BrowseSection,
    val selectedPlaylistId: String?,
    val showQueue: Boolean,
    val compact: Boolean,
    val busy: Boolean,
)

internal data class PlaybackUiState(
    val shellPlayback: ShellPlaybackState,
    val player: PlayerState = PlayerState(),
    val track: Track?,
    val upNext: List<Track>,
    val currentIndex: Int,
    val lyricsTrack: Track? = null,
    val lyricsState: LyricsLoadState = LyricsLoadState.Idle,
    val castState: CastState = CastState(),
    val remotePlaybackTarget: String? = null,
    val equalizerProfile: EqualizerProfile = EqualizerProfile.Default,
    val persistEqualizerSettings: Boolean = false,
    val equalizerRemoteUnavailable: Boolean = false,
)

internal data class PlaybackActions(
    val onToggle: () -> Unit,
    val onPrevious: () -> Unit,
    val onNext: () -> Unit,
    val onShuffle: () -> Unit,
    val onRepeat: () -> Unit,
    val onVolume: (Float) -> Unit,
    val onSeek: (Long) -> Unit,
    val onCast: () -> Unit = {},
    val onLyrics: () -> Unit = {},
    val onEqualizerEnabled: (Boolean) -> Unit = {},
    val onEqualizerBandCount: (Int) -> Unit = {},
    val onEqualizerGain: (Int, Float) -> Unit = { _, _ -> },
    val onEqualizerReset: () -> Unit = {},
    val onPersistEqualizerSettings: (Boolean) -> Unit = {},
    val onPlayQueue: (Int) -> Unit,
    val onClearQueue: () -> Unit,
    val onMoveUpNext: (Int, Int) -> Unit,
    val onRemoveUpNext: (Int) -> Unit,
    val onRetryLyrics: () -> Unit = {},
)

internal data class BrowseUiState(
    val homeUiState: HomeUiState,
    val playHistory: PlayHistorySnapshot,
    val resolvedTracksById: Map<String, Track> = emptyMap(),
    val searchQuery: String,
    val libraryFilter: LibraryFilterTab,
    val libraryUi: LibraryUiPreferences,
    val supportedCollectionEntries: Set<CollectionEntry> = defaultCollectionEntries.toSet(),
    val decadeMixNotice: String? = null,
    val radioStations: List<PlexRadioStation> = emptyList(),
    val artistRadioAvailability: Map<String, ArtistRadioAvailability> = emptyMap(),
    val radioStartingIds: Set<String> = emptySet(),
)

internal data class BrowseActions(
    val onNavigate: (BrowseSection) -> Unit,
    val onSearchQuery: (String) -> Unit,
    val onLibraryFilter: (LibraryFilterTab) -> Unit,
    val onPlaylist: (Playlist) -> Unit,
    val onArtist: (Artist) -> Unit,
    val onAlbum: (Album) -> Unit,
    val onSong: (Track) -> Unit,
    val onOpenLyrics: (Track) -> Unit = {},
    val onRecentSongs: () -> Unit,
    val onRecentArtists: () -> Unit,
    val onRecentAlbums: () -> Unit,
    val onFavoritePlaylists: () -> Unit = {},
    val onFavoriteArtists: () -> Unit = {},
    val onFavoriteAlbums: () -> Unit = {},
    val onRecentlyPlayed: () -> Unit,
    val onMostPlayed: () -> Unit,
    val onCollections: (CollectionEntry) -> Unit,
    val onCollectionValue: (CollectionEntry, String) -> Unit,
    val onRefreshRandomArtists: () -> Unit,
    val onRefreshRandomAlbums: () -> Unit,
    val onPrefetchHomeArtist: (Artist) -> Unit = {},
    val onPrefetchHomeAlbum: (Album) -> Unit = {},
    val onPlayDecadeMix: (Int) -> Unit = {},
    val onClearDecadeMixNotice: () -> Unit = {},
    val onPlayRadioStation: (PlexRadioStation) -> Unit = {},
    val onPlayPersonalMix: () -> Unit = {},
    val onPopDetail: () -> Unit,
    val onPlayTracks: (List<Track>, Int) -> Unit,
    val onAddToUpNext: (Track) -> Unit,
    val onDownload: (Track) -> Unit,
    val onDownloadArtist: (Artist) -> Unit,
    val onProbeArtistRadio: (Artist) -> Unit = {},
    val onPlayArtistRadio: (Artist) -> Unit,
    val onDownloadAlbum: (Album) -> Unit,
    val onDownloadPlaylist: (Playlist) -> Unit,
    val onLibrarySortBy: (LibrarySortBy) -> Unit,
    val onLibraryAscending: (Boolean) -> Unit,
    val onLibraryColumns: (LibraryColumnVisibility) -> Unit,
)

internal data class AuthSetupState(
    val appMessage: String,
    val pinCode: String?,
    val authInProgress: Boolean = false,
    val serversLoading: Boolean = false,
    val jellyfinServers: List<PlexServer> = emptyList(),
    val jellyfinDiscoveryLoading: Boolean = false,
    val jellyfinQuickConnect: JellyfinQuickConnectResult? = null,
    val servers: List<PlexServer>,
    val libraries: List<MusicLibrary>,
    val librariesLoading: Boolean = false,
)

internal data class AuthSetupActions(
    val onStartSignIn: () -> Unit,
    val onFinishSignIn: () -> Unit,
    val onSignInJellyfin: (String, String, String) -> Unit,
    val onSignInProvider: (MediaProviderType, String, String, String, JellyfinSyncMode?) -> Unit = { _, _, _, _, _ -> },
    val onDiscoverJellyfinServers: () -> Unit = {},
    val onStartJellyfinQuickConnect: (String) -> Unit = {},
    val onFinishJellyfinQuickConnect: () -> Unit = {},
    val onSignOut: () -> Unit,
    val onAddLocalFolder: (String?) -> Unit,
    val onRemoveLocalFolder: (String) -> Unit,
    val onToggleLocalFolder: (String, Boolean) -> Unit,
    val onRefreshLibrary: () -> Unit,
    val onJellyfinPage: (JellyfinLibraryPageKind, Int) -> Unit = { _, _ -> },
    val onSelectServer: (PlexServer) -> Unit,
    val onSelectLibrary: (MusicLibrary, JellyfinSyncMode?) -> Unit,
    val onCancelPlexSetup: () -> Unit,
    val onBackToServerPicker: () -> Unit,
    val onRetryServers: () -> Unit,
)

internal data class SettingsUiState(
    val appSettings: AppSettings,
    val downloadDirectory: String?,
    val downloadCount: Int,
    val defaultDownloadDirectoryLabel: String,
    val useLightAppearance: Boolean,
    val appearanceTintId: String,
    val homeScreenLayoutMode: HomeScreenLayoutMode = HomeScreenLayoutMode.Default,
    val settingsInitialCategory: SettingsCategory = SettingsCategory.AudioPlayback,
)

internal data class SettingsActions(
    val onHomeSections: (List<HomeSection>) -> Unit,
    val onPersonalMix: (PersonalMixPreferences) -> Unit,
    val onGridColumns: (Int) -> Unit,
    val onExportFavoritePlaylists: () -> Unit,
    val onImportFavoritePlaylists: () -> Unit,
    val onCrossfadeSeconds: (Int) -> Unit,
    val onScanLibraryOnLaunch: (Boolean) -> Unit,
    val onNotifyWhenDownloadFinishes: (Boolean) -> Unit,
    val onPersistEqualizerSettings: (Boolean) -> Unit = {},
    val onDownloadDirectory: (String?) -> Unit,
    val onDeleteAllDownloads: () -> Unit,
    val onUseLightAppearanceChange: (Boolean) -> Unit,
    val onAppearanceTintChange: (String) -> Unit,
    val onHomeScreenLayoutModeChange: (HomeScreenLayoutMode) -> Unit = {},
)
