package com.phoebe.app.testing

import com.phoebe.app.data.ProviderCapabilities
import com.phoebe.app.domain.MediaProviderType

enum class SmokeTestLayer {
    CommonMock,
    DesktopIntegration,
    AndroidInstrumented,
    WebE2e,
    PackagePlayback,
}

enum class ProviderSmokeFeature {
    SignIn,
    Libraries,
    CatalogSync,
    AlbumTracks,
    PlaylistList,
    PlaylistCreate,
    PlaylistAddTracks,
    SetFavorite,
    RateItem,
    StreamUrl,
    PlaybackReport,
    MetadataEdit,
    LocalFolderIndex,
    LocalPlaylistExport,
    PackagePlayback,
    ProviderScreenPlayAction,
    ChromecastRemoteStream,
    EqualizerProfile,
    PlaybackUrlRefresh,
}

enum class SmokeSource {
    Plex,
    Jellyfin,
    Emby,
    Navidrome,
    MusicAssistant,
    LocalFolders,
    ;

    val providerType: MediaProviderType?
        get() = when (this) {
            Plex -> MediaProviderType.Plex
            Jellyfin -> MediaProviderType.Jellyfin
            Emby -> MediaProviderType.Emby
            Navidrome -> MediaProviderType.Navidrome
            MusicAssistant -> MediaProviderType.MusicAssistant
            LocalFolders -> null
        }
}

object ProviderSmokeCoverage {
    fun supported(source: SmokeSource, feature: ProviderSmokeFeature): Boolean =
        when (feature) {
            ProviderSmokeFeature.SignIn -> source != SmokeSource.LocalFolders
            ProviderSmokeFeature.Libraries -> source != SmokeSource.LocalFolders
            ProviderSmokeFeature.CatalogSync -> true
            ProviderSmokeFeature.AlbumTracks -> source != SmokeSource.LocalFolders
            ProviderSmokeFeature.PlaylistList -> source != SmokeSource.LocalFolders
            ProviderSmokeFeature.PlaylistCreate -> when (source) {
                SmokeSource.Emby -> false
                else -> true
            }
            ProviderSmokeFeature.PlaylistAddTracks -> when (source) {
                SmokeSource.Emby, SmokeSource.LocalFolders -> false
                else -> true
            }
            ProviderSmokeFeature.SetFavorite -> source != SmokeSource.LocalFolders
            ProviderSmokeFeature.RateItem -> when (source) {
                SmokeSource.MusicAssistant, SmokeSource.LocalFolders -> false
                else -> true
            }
            ProviderSmokeFeature.StreamUrl -> when (source) {
                SmokeSource.MusicAssistant -> false
                else -> true
            }
            ProviderSmokeFeature.PlaybackReport -> when (source) {
                SmokeSource.LocalFolders, SmokeSource.MusicAssistant -> false
                else -> true
            }
            ProviderSmokeFeature.MetadataEdit -> when (source) {
                SmokeSource.Plex, SmokeSource.Jellyfin, SmokeSource.Emby -> true
                else -> false
            }
            ProviderSmokeFeature.LocalFolderIndex -> source == SmokeSource.LocalFolders
            ProviderSmokeFeature.LocalPlaylistExport -> source == SmokeSource.LocalFolders
            ProviderSmokeFeature.PackagePlayback -> true
            ProviderSmokeFeature.ProviderScreenPlayAction -> source != SmokeSource.LocalFolders
            ProviderSmokeFeature.ChromecastRemoteStream -> source != SmokeSource.LocalFolders && source != SmokeSource.MusicAssistant
            ProviderSmokeFeature.EqualizerProfile -> source == SmokeSource.LocalFolders
            ProviderSmokeFeature.PlaybackUrlRefresh -> source != SmokeSource.LocalFolders && source != SmokeSource.MusicAssistant
        }

    fun implemented(source: SmokeSource, feature: ProviderSmokeFeature, layer: SmokeTestLayer): Boolean =
        IMPLEMENTED[Triple(source, feature, layer)] == true

    fun requiresCommonMock(source: SmokeSource, feature: ProviderSmokeFeature): Boolean =
        requiresLayer(SmokeTestLayer.CommonMock, source, feature)

    fun requiresAndroidInstrumented(source: SmokeSource, feature: ProviderSmokeFeature): Boolean =
        requiresLayer(SmokeTestLayer.AndroidInstrumented, source, feature)

    fun requiresWebE2e(source: SmokeSource, feature: ProviderSmokeFeature): Boolean =
        requiresLayer(SmokeTestLayer.WebE2e, source, feature)

    fun requiresDesktopIntegration(source: SmokeSource, feature: ProviderSmokeFeature): Boolean =
        requiresLayer(SmokeTestLayer.DesktopIntegration, source, feature)

    fun missingCommonMockCoverage(): List<Pair<SmokeSource, ProviderSmokeFeature>> =
        missingForLayer(SmokeTestLayer.CommonMock, ::requiresCommonMock)

    fun missingAndroidInstrumentedCoverage(): List<Pair<SmokeSource, ProviderSmokeFeature>> =
        missingForLayer(SmokeTestLayer.AndroidInstrumented, ::requiresAndroidInstrumented)

    fun missingWebE2eCoverage(): List<Pair<SmokeSource, ProviderSmokeFeature>> =
        missingForLayer(SmokeTestLayer.WebE2e, ::requiresWebE2e)

    fun missingDesktopIntegrationCoverage(): List<Pair<SmokeSource, ProviderSmokeFeature>> =
        missingForLayer(SmokeTestLayer.DesktopIntegration, ::requiresDesktopIntegration)

    private fun missingForLayer(
        layer: SmokeTestLayer,
        requires: (SmokeSource, ProviderSmokeFeature) -> Boolean,
    ): List<Pair<SmokeSource, ProviderSmokeFeature>> =
        SmokeSource.entries.flatMap { source ->
            ProviderSmokeFeature.entries.filter { feature ->
                requires(source, feature) && !implemented(source, feature, layer)
            }.map { source to it }
        }

    private fun requiresLayer(
        layer: SmokeTestLayer,
        source: SmokeSource,
        feature: ProviderSmokeFeature,
    ): Boolean {
        if (!supported(source, feature)) return false
        return when (layer) {
            SmokeTestLayer.CommonMock -> requiresCommonMockInternal(source, feature)
            SmokeTestLayer.AndroidInstrumented -> requiresAndroidInstrumentedInternal(source, feature)
            SmokeTestLayer.WebE2e -> requiresWebE2eInternal(source, feature)
            SmokeTestLayer.DesktopIntegration -> requiresDesktopIntegrationInternal(source, feature)
            SmokeTestLayer.PackagePlayback -> feature == ProviderSmokeFeature.PackagePlayback
        }
    }

    private fun requiresCommonMockInternal(source: SmokeSource, feature: ProviderSmokeFeature): Boolean {
        if (source == SmokeSource.LocalFolders) return false
        if (feature in GLOBAL_COMMON_FEATURES) return false
        if (feature == ProviderSmokeFeature.MetadataEdit) return false
        return when (source) {
            SmokeSource.Plex -> feature in setOf(
                ProviderSmokeFeature.AlbumTracks,
                ProviderSmokeFeature.PlaylistCreate,
                ProviderSmokeFeature.PlaylistAddTracks,
            )
            else -> feature in REMOTE_ADAPTER_FEATURES
        }
    }

    private fun requiresAndroidInstrumentedInternal(source: SmokeSource, feature: ProviderSmokeFeature): Boolean =
        when (feature) {
            ProviderSmokeFeature.CatalogSync, ProviderSmokeFeature.LocalFolderIndex -> true
            ProviderSmokeFeature.PlaylistCreate, ProviderSmokeFeature.PlaylistAddTracks ->
                source == SmokeSource.Plex || source == SmokeSource.LocalFolders
            else -> false
        }

    private fun requiresWebE2eInternal(source: SmokeSource, feature: ProviderSmokeFeature): Boolean =
        when (feature) {
            ProviderSmokeFeature.LocalFolderIndex,
            ProviderSmokeFeature.LocalPlaylistExport,
            -> source == SmokeSource.LocalFolders
            ProviderSmokeFeature.CatalogSync -> true
            ProviderSmokeFeature.ChromecastRemoteStream -> source != SmokeSource.LocalFolders && source != SmokeSource.MusicAssistant
            ProviderSmokeFeature.SignIn,
            ProviderSmokeFeature.Libraries,
            ProviderSmokeFeature.AlbumTracks,
            ProviderSmokeFeature.PlaylistList,
            ProviderSmokeFeature.PlaylistCreate,
            ProviderSmokeFeature.PlaylistAddTracks,
            ProviderSmokeFeature.SetFavorite,
            ProviderSmokeFeature.RateItem,
            ProviderSmokeFeature.StreamUrl,
            ProviderSmokeFeature.PlaybackReport,
            -> source != SmokeSource.LocalFolders
            else -> false
        }

    private fun requiresDesktopIntegrationInternal(source: SmokeSource, feature: ProviderSmokeFeature): Boolean =
        when (feature) {
            ProviderSmokeFeature.CatalogSync -> true
            ProviderSmokeFeature.PlaylistCreate, ProviderSmokeFeature.PlaylistAddTracks ->
                source == SmokeSource.Plex || source == SmokeSource.LocalFolders
            ProviderSmokeFeature.LocalFolderIndex, ProviderSmokeFeature.LocalPlaylistExport ->
                source == SmokeSource.LocalFolders
            ProviderSmokeFeature.MetadataEdit -> source == SmokeSource.Jellyfin
            ProviderSmokeFeature.ProviderScreenPlayAction -> source != SmokeSource.LocalFolders
            ProviderSmokeFeature.EqualizerProfile -> true
            ProviderSmokeFeature.PlaybackUrlRefresh -> source != SmokeSource.LocalFolders && source != SmokeSource.MusicAssistant
            else -> false
        }

    fun adapterCapabilities(source: SmokeSource): ProviderCapabilities? =
        when (source) {
            SmokeSource.Plex -> ProviderCapabilities(
                quickConnect = false,
                serverDiscovery = true,
                pagedCatalog = true,
                metadataEdit = true,
                libraryRadio = true,
                itemRadio = true,
            )
            SmokeSource.Jellyfin -> ProviderCapabilities(quickConnect = true, pagedCatalog = true, metadataEdit = true, itemRadio = true)
            SmokeSource.Emby -> ProviderCapabilities(pagedCatalog = true, metadataEdit = true, itemRadio = true)
            SmokeSource.Navidrome -> ProviderCapabilities(pagedCatalog = true, itemRadio = true)
            SmokeSource.MusicAssistant -> ProviderCapabilities(
                ratings = false,
                nativeStreaming = false,
                remotePlayerControl = true,
                libraryRadio = true,
            )
            SmokeSource.LocalFolders -> null
        }

    private val REMOTE_ADAPTER_FEATURES = setOf(
        ProviderSmokeFeature.SignIn,
        ProviderSmokeFeature.Libraries,
        ProviderSmokeFeature.CatalogSync,
        ProviderSmokeFeature.AlbumTracks,
        ProviderSmokeFeature.PlaylistList,
        ProviderSmokeFeature.PlaylistCreate,
        ProviderSmokeFeature.PlaylistAddTracks,
        ProviderSmokeFeature.SetFavorite,
        ProviderSmokeFeature.RateItem,
        ProviderSmokeFeature.StreamUrl,
        ProviderSmokeFeature.PlaybackReport,
    )

    private val GLOBAL_COMMON_FEATURES = setOf(
        ProviderSmokeFeature.PackagePlayback,
        ProviderSmokeFeature.LocalFolderIndex,
        ProviderSmokeFeature.LocalPlaylistExport,
        ProviderSmokeFeature.ProviderScreenPlayAction,
        ProviderSmokeFeature.ChromecastRemoteStream,
        ProviderSmokeFeature.EqualizerProfile,
        ProviderSmokeFeature.PlaybackUrlRefresh,
    )

    private val IMPLEMENTED: Map<Triple<SmokeSource, ProviderSmokeFeature, SmokeTestLayer>, Boolean> = buildMap {
        fun mark(source: SmokeSource, feature: ProviderSmokeFeature, vararg layers: SmokeTestLayer) {
            layers.forEach { layer -> put(Triple(source, feature, layer), true) }
        }

        SmokeSource.entries.forEach { source ->
            mark(source, ProviderSmokeFeature.PackagePlayback, SmokeTestLayer.PackagePlayback)
        }

        mark(SmokeSource.LocalFolders, ProviderSmokeFeature.CatalogSync, SmokeTestLayer.DesktopIntegration, SmokeTestLayer.AndroidInstrumented, SmokeTestLayer.WebE2e)
        mark(SmokeSource.LocalFolders, ProviderSmokeFeature.LocalFolderIndex, SmokeTestLayer.DesktopIntegration, SmokeTestLayer.AndroidInstrumented, SmokeTestLayer.WebE2e)
        mark(SmokeSource.LocalFolders, ProviderSmokeFeature.LocalPlaylistExport, SmokeTestLayer.DesktopIntegration, SmokeTestLayer.WebE2e)
        mark(SmokeSource.LocalFolders, ProviderSmokeFeature.PlaylistCreate, SmokeTestLayer.DesktopIntegration, SmokeTestLayer.AndroidInstrumented)

        listOf(
            SmokeSource.Plex,
            SmokeSource.Jellyfin,
            SmokeSource.Emby,
            SmokeSource.Navidrome,
            SmokeSource.MusicAssistant,
        ).forEach { source ->
            mark(source, ProviderSmokeFeature.SignIn, SmokeTestLayer.CommonMock, SmokeTestLayer.WebE2e)
            mark(source, ProviderSmokeFeature.Libraries, SmokeTestLayer.CommonMock, SmokeTestLayer.WebE2e)
            mark(source, ProviderSmokeFeature.CatalogSync, SmokeTestLayer.CommonMock, SmokeTestLayer.AndroidInstrumented, SmokeTestLayer.DesktopIntegration, SmokeTestLayer.WebE2e)
            mark(source, ProviderSmokeFeature.AlbumTracks, SmokeTestLayer.CommonMock, SmokeTestLayer.WebE2e)
            mark(source, ProviderSmokeFeature.PlaylistList, SmokeTestLayer.CommonMock, SmokeTestLayer.WebE2e)
            if (source != SmokeSource.Emby) {
                mark(source, ProviderSmokeFeature.PlaylistCreate, SmokeTestLayer.CommonMock, SmokeTestLayer.WebE2e)
                mark(source, ProviderSmokeFeature.PlaylistAddTracks, SmokeTestLayer.CommonMock, SmokeTestLayer.WebE2e)
            }
            mark(source, ProviderSmokeFeature.SetFavorite, SmokeTestLayer.CommonMock, SmokeTestLayer.WebE2e)
            if (source != SmokeSource.MusicAssistant) {
                mark(source, ProviderSmokeFeature.RateItem, SmokeTestLayer.CommonMock, SmokeTestLayer.WebE2e)
                mark(source, ProviderSmokeFeature.StreamUrl, SmokeTestLayer.CommonMock, SmokeTestLayer.WebE2e)
            }
            if (source != SmokeSource.MusicAssistant) {
                mark(source, ProviderSmokeFeature.PlaybackReport, SmokeTestLayer.CommonMock, SmokeTestLayer.WebE2e)
            }
            mark(source, ProviderSmokeFeature.ProviderScreenPlayAction, SmokeTestLayer.DesktopIntegration)
        }

        mark(SmokeSource.Plex, ProviderSmokeFeature.AlbumTracks, SmokeTestLayer.DesktopIntegration)
        mark(SmokeSource.Plex, ProviderSmokeFeature.PlaylistCreate, SmokeTestLayer.DesktopIntegration, SmokeTestLayer.AndroidInstrumented)
        mark(SmokeSource.Plex, ProviderSmokeFeature.PlaylistAddTracks, SmokeTestLayer.DesktopIntegration, SmokeTestLayer.AndroidInstrumented)

        mark(SmokeSource.Jellyfin, ProviderSmokeFeature.MetadataEdit, SmokeTestLayer.CommonMock, SmokeTestLayer.DesktopIntegration)

        mark(SmokeSource.LocalFolders, ProviderSmokeFeature.EqualizerProfile, SmokeTestLayer.CommonMock, SmokeTestLayer.DesktopIntegration)

        listOf(SmokeSource.Plex, SmokeSource.Jellyfin, SmokeSource.Emby, SmokeSource.Navidrome).forEach { source ->
            mark(source, ProviderSmokeFeature.PlaybackUrlRefresh, SmokeTestLayer.CommonMock, SmokeTestLayer.DesktopIntegration)
        }

        SmokeSource.entries.filter { it != SmokeSource.LocalFolders && it != SmokeSource.MusicAssistant }.forEach { source ->
            mark(source, ProviderSmokeFeature.ChromecastRemoteStream, SmokeTestLayer.WebE2e)
        }
    }
}
