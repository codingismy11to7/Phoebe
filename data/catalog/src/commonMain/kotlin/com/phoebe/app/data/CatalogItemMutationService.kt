package com.phoebe.app.data

import com.phoebe.app.domain.Album
import com.phoebe.app.domain.Artist
import com.phoebe.app.domain.PlexSession
import com.phoebe.app.domain.Playlist
import com.phoebe.app.domain.Track
import com.phoebe.app.domain.TrackMetadataUpdate
import com.phoebe.app.domain.providerLabel
import com.phoebe.app.domain.supportsRemoteRatings
import com.phoebe.app.platform.PlatformStorage
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn

private const val FavoritePlaylistsExportPath = "exports/favorite-playlists.json"
private const val RadioStationsExportPath = "exports/radio-stations.json"

@SingleIn(AppScope::class)
@Inject
class CatalogItemMutationService(
    private val catalogRepository: CatalogRepository,
    private val radioRepository: RadioRepository,
    private val platformStorage: PlatformStorage,
) {
    suspend fun updateTrackMetadata(session: PlexSession?, update: TrackMetadataUpdate): String {
        val result = catalogRepository.updateTrackMetadata(session, update)
        val provider = session.providerLabel()
        return when {
            !result.savedLocally -> "Couldn't find that song in the library."
            result.plexAttempted && result.plexSynced -> "Metadata saved and synced to $provider."
            result.plexAttempted -> "Metadata saved locally, but $provider sync failed."
            else -> "Metadata saved."
        }
    }

    suspend fun rateTrack(session: PlexSession?, track: Track, rating: Float?): String =
        ratingMessage(session, catalogRepository.rateTrack(session, track, rating))

    suspend fun rateArtist(session: PlexSession?, artist: Artist, rating: Float?): String =
        ratingMessage(session, catalogRepository.rateArtist(session, artist, rating))

    suspend fun rateAlbum(session: PlexSession?, album: Album, rating: Float?): String =
        ratingMessage(session, catalogRepository.rateAlbum(session, album, rating))

    suspend fun ratePlaylist(session: PlexSession?, playlist: Playlist, rating: Float?): String =
        ratingMessage(session, catalogRepository.ratePlaylist(session, playlist, rating))

    suspend fun toggleFavoriteArtist(session: PlexSession?, artist: Artist): String =
        favoriteMessage("Artist", session, catalogRepository.toggleFavoriteArtist(session, artist))

    suspend fun toggleFavoriteAlbum(session: PlexSession?, album: Album): String =
        favoriteMessage("Album", session, catalogRepository.toggleFavoriteAlbum(session, album))

    suspend fun toggleFavoritePlaylist(session: PlexSession?, playlist: Playlist): String =
        favoriteMessage("Playlist", session, catalogRepository.toggleFavoritePlaylist(session, playlist))

    suspend fun exportFavoritePlaylists(): String {
        val export = catalogRepository.favoritePlaylistsExport()
        if (export.playlists.isEmpty()) return "No favorite playlists to export."
        return runCatching {
            platformStorage.writeText(
                FavoritePlaylistsExportPath,
                PlexClient.PlexJson.encodeToString(FavoritePlaylistsExport.serializer(), export),
            )
        }.fold(
            onSuccess = { "Exported ${export.playlists.size} favorite playlists." },
            onFailure = { it.message ?: "Couldn't export favorite playlists." },
        )
    }

    suspend fun importFavoritePlaylists(): String {
        val content = platformStorage.readText(FavoritePlaylistsExportPath)
        if (content.isNullOrBlank()) return "No favorite playlist export found."
        return runCatching {
            val export = PlexClient.PlexJson.decodeFromString(FavoritePlaylistsExport.serializer(), content)
            catalogRepository.importFavoritePlaylists(export)
        }.fold(
            onSuccess = { imported ->
                if (imported > 0) {
                    "Imported $imported favorite playlists."
                } else {
                    "No matching playlists found to import."
                }
            },
            onFailure = { it.message ?: "Couldn't import favorite playlists." },
        )
    }

    suspend fun exportRadioStations(): String {
        val export = radioRepository.exportRadioStations()
        if (export.stations.isEmpty()) return "No manual radio stations to export."
        return runCatching {
            platformStorage.writeText(
                RadioStationsExportPath,
                PlexClient.PlexJson.encodeToString(RadioStationsExport.serializer(), export),
            )
        }.fold(
            onSuccess = { "Exported ${export.stations.size} radio stations." },
            onFailure = { it.message ?: "Couldn't export radio stations." },
        )
    }

    suspend fun importRadioStations(): String {
        val content = platformStorage.readText(RadioStationsExportPath)
        if (content.isNullOrBlank()) return "No radio stations export found."
        return runCatching {
            val export = PlexClient.PlexJson.decodeFromString(RadioStationsExport.serializer(), content)
            radioRepository.importRadioStations(export)
        }.fold(
            onSuccess = { imported ->
                if (imported > 0) {
                    "Imported $imported radio stations."
                } else {
                    "No new radio stations to import."
                }
            },
            onFailure = { it.message ?: "Couldn't import radio stations." },
        )
    }

    private fun favoriteMessage(label: String, session: PlexSession?, result: FavoriteSyncResult): String {
        val provider = session.providerLabel()
        return when (result.favorite) {
            null -> "Couldn't find that item in the library."
            true -> when {
                result.plexAttempted && result.plexSynced -> "$label added to favorites and synced to $provider."
                result.plexAttempted -> "$label added to favorites, but $provider sync failed."
                else -> "$label added to favorites."
            }
            false -> when {
                result.plexAttempted && result.plexSynced -> "$label removed from favorites and synced to $provider."
                result.plexAttempted -> "$label removed from favorites, but $provider sync failed."
                else -> "$label removed from favorites."
            }
        }
    }

    private fun ratingMessage(session: PlexSession?, result: RatingSyncResult): String =
        when {
            !result.savedLocally -> "Couldn't find that item in the library."
            result.plexAttempted && result.plexSynced -> "Rating saved and synced to ${session.providerLabel()}."
            result.plexAttempted -> "Rating saved locally, but ${session.providerLabel()} sync failed."
            session.supportsRemoteRatings() -> "Rating saved."
            else -> "Rating saved locally."
        }
}
