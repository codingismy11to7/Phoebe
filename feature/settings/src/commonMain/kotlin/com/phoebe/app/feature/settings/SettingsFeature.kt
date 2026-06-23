package com.phoebe.app.feature.settings

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.ui.Modifier
import com.phoebe.app.domain.AppSettings
import com.phoebe.app.domain.HomeSection
import com.phoebe.app.domain.LibraryUiPreferences
import com.phoebe.app.domain.MobileBottomTab
import com.phoebe.app.domain.NowPlayingVisualizerPreset
import com.phoebe.app.domain.PersonalMixPreferences
import com.phoebe.app.domain.PlexSession
import com.phoebe.app.platform.SecureCredentialAvailability
import com.phoebe.app.ui.HomeScreenLayoutMode

@Immutable
data class SettingsRouteState(
    val isLightMode: Boolean,
    val tintId: String,
    val downloadDirectory: String?,
    val downloadCount: Int,
    val appSettings: AppSettings,
    val libraryUi: LibraryUiPreferences,
    val defaultDownloadDirectoryLabel: String,
    val homeScreenLayoutMode: HomeScreenLayoutMode = HomeScreenLayoutMode.Default,
    val session: PlexSession? = null,
    val listenBrainzCredentialAvailability: SecureCredentialAvailability = SecureCredentialAvailability.Unavailable,
    val initialCategory: SettingsCategory = SettingsCategory.AudioPlayback,
)

@Immutable
class SettingsRouteActions(
    val onLightModeChange: (Boolean) -> Unit,
    val onTintChange: (String) -> Unit,
    val onDownloadDirectory: (String?) -> Unit,
    val onDeleteAllDownloads: () -> Unit,
    val onCrossfadeSeconds: (Int) -> Unit,
    val onScanLibraryOnLaunch: (Boolean) -> Unit,
    val onNotifyWhenDownloadFinishes: (Boolean) -> Unit,
    val onPersistEqualizerSettings: (Boolean) -> Unit = {},
    val onPersistVolumeSettings: (Boolean) -> Unit = {},
    val onVisualizerPreset: (NowPlayingVisualizerPreset) -> Unit = {},
    val onBlurredArtworkAppearance: (Boolean) -> Unit = {},
    val onHomeSections: (List<HomeSection>) -> Unit,
    val onMobileBottomTabs: (List<MobileBottomTab>) -> Unit = {},
    val onPersonalMix: (PersonalMixPreferences) -> Unit,
    val onAlbumGridItemSize: (Int) -> Unit,
    val onArtistGridItemSize: (Int) -> Unit,
    val onExportFavoritePlaylists: () -> Unit,
    val onImportFavoritePlaylists: () -> Unit,
    val onExportRadioStations: () -> Unit,
    val onImportRadioStations: () -> Unit,
    val onHomeScreenLayoutModeChange: (HomeScreenLayoutMode) -> Unit = {},
    val onConnectListenBrainz: (String) -> Unit = {},
    val onDisconnectListenBrainz: () -> Unit = {},
    val onListenBrainzSubmitNowPlaying: (Boolean) -> Unit = {},
    val onListenBrainzSubmitListens: (Boolean) -> Unit = {},
    val onListenBrainzSubmitCurrentTrackFeedback: (Boolean) -> Unit = {},
)

@Composable
fun SettingsDesktopRoute(
    state: SettingsRouteState,
    actions: SettingsRouteActions,
    modifier: Modifier = Modifier,
) {
    SettingsDesktopView(
        isLightMode = state.isLightMode,
        onLightModeChange = actions.onLightModeChange,
        tintId = state.tintId,
        onTintChange = actions.onTintChange,
        downloadDirectory = state.downloadDirectory,
        downloadCount = state.downloadCount,
        appSettings = state.appSettings,
        libraryUi = state.libraryUi,
        defaultDownloadDirectoryLabel = state.defaultDownloadDirectoryLabel,
        onDownloadDirectory = actions.onDownloadDirectory,
        onDeleteAllDownloads = actions.onDeleteAllDownloads,
        onCrossfadeSeconds = actions.onCrossfadeSeconds,
        onScanLibraryOnLaunch = actions.onScanLibraryOnLaunch,
        onNotifyWhenDownloadFinishes = actions.onNotifyWhenDownloadFinishes,
        onPersistEqualizerSettings = actions.onPersistEqualizerSettings,
        onPersistVolumeSettings = actions.onPersistVolumeSettings,
        onVisualizerPreset = actions.onVisualizerPreset,
        onBlurredArtworkAppearance = actions.onBlurredArtworkAppearance,
        onHomeSections = actions.onHomeSections,
        onMobileBottomTabs = actions.onMobileBottomTabs,
        onPersonalMix = actions.onPersonalMix,
        onAlbumGridItemSize = actions.onAlbumGridItemSize,
        onArtistGridItemSize = actions.onArtistGridItemSize,
        onExportFavoritePlaylists = actions.onExportFavoritePlaylists,
        onImportFavoritePlaylists = actions.onImportFavoritePlaylists,
        onExportRadioStations = actions.onExportRadioStations,
        onImportRadioStations = actions.onImportRadioStations,
        homeScreenLayoutMode = state.homeScreenLayoutMode,
        onHomeScreenLayoutModeChange = actions.onHomeScreenLayoutModeChange,
        session = state.session,
        listenBrainzCredentialAvailability = state.listenBrainzCredentialAvailability,
        onConnectListenBrainz = actions.onConnectListenBrainz,
        onDisconnectListenBrainz = actions.onDisconnectListenBrainz,
        onListenBrainzSubmitNowPlaying = actions.onListenBrainzSubmitNowPlaying,
        onListenBrainzSubmitListens = actions.onListenBrainzSubmitListens,
        onListenBrainzSubmitCurrentTrackFeedback = actions.onListenBrainzSubmitCurrentTrackFeedback,
        modifier = modifier,
        initialCategory = state.initialCategory,
    )
}

@Composable
fun SettingsMobileRoute(
    state: SettingsRouteState,
    actions: SettingsRouteActions,
    modifier: Modifier = Modifier,
) {
    SettingsMobileView(
        isLightMode = state.isLightMode,
        onLightModeChange = actions.onLightModeChange,
        tintId = state.tintId,
        onTintChange = actions.onTintChange,
        downloadDirectory = state.downloadDirectory,
        downloadCount = state.downloadCount,
        appSettings = state.appSettings,
        libraryUi = state.libraryUi,
        defaultDownloadDirectoryLabel = state.defaultDownloadDirectoryLabel,
        onDownloadDirectory = actions.onDownloadDirectory,
        onDeleteAllDownloads = actions.onDeleteAllDownloads,
        onCrossfadeSeconds = actions.onCrossfadeSeconds,
        onScanLibraryOnLaunch = actions.onScanLibraryOnLaunch,
        onNotifyWhenDownloadFinishes = actions.onNotifyWhenDownloadFinishes,
        onPersistEqualizerSettings = actions.onPersistEqualizerSettings,
        onPersistVolumeSettings = actions.onPersistVolumeSettings,
        onVisualizerPreset = actions.onVisualizerPreset,
        onBlurredArtworkAppearance = actions.onBlurredArtworkAppearance,
        onHomeSections = actions.onHomeSections,
        onMobileBottomTabs = actions.onMobileBottomTabs,
        onPersonalMix = actions.onPersonalMix,
        onAlbumGridItemSize = actions.onAlbumGridItemSize,
        onArtistGridItemSize = actions.onArtistGridItemSize,
        onExportFavoritePlaylists = actions.onExportFavoritePlaylists,
        onImportFavoritePlaylists = actions.onImportFavoritePlaylists,
        onExportRadioStations = actions.onExportRadioStations,
        onImportRadioStations = actions.onImportRadioStations,
        homeScreenLayoutMode = state.homeScreenLayoutMode,
        onHomeScreenLayoutModeChange = actions.onHomeScreenLayoutModeChange,
        session = state.session,
        listenBrainzCredentialAvailability = state.listenBrainzCredentialAvailability,
        onConnectListenBrainz = actions.onConnectListenBrainz,
        onDisconnectListenBrainz = actions.onDisconnectListenBrainz,
        onListenBrainzSubmitNowPlaying = actions.onListenBrainzSubmitNowPlaying,
        onListenBrainzSubmitListens = actions.onListenBrainzSubmitListens,
        onListenBrainzSubmitCurrentTrackFeedback = actions.onListenBrainzSubmitCurrentTrackFeedback,
        modifier = modifier,
    )
}
