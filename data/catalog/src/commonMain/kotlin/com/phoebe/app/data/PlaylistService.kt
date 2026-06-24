package com.phoebe.app.data

import com.phoebe.app.domain.CatalogSnapshot
import com.phoebe.app.domain.PlexSession
import com.phoebe.app.domain.Playlist
import com.phoebe.app.domain.Track
import com.phoebe.app.domain.canAddToLocalPlaylist
import com.phoebe.app.domain.canAddToPlexPlaylist
import com.phoebe.app.domain.displayName
import com.phoebe.app.domain.isLocalPlaylist
import com.phoebe.app.domain.isLikedSongsPlaylist
import com.phoebe.app.domain.isRemoteProviderPlaylist
import com.phoebe.app.domain.playlistEntryKey
import com.phoebe.app.domain.supportsRemotePlaylists
import com.phoebe.app.domain.supportsTrackRemoval
import com.phoebe.app.platform.PlatformStorage
import com.phoebe.app.playlists.PlaylistExportFormat
import com.phoebe.app.playlists.PlaylistExporter
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn

data class PlaylistCreateResult(
    val playlist: Playlist? = null,
    val message: String? = null,
)

@SingleIn(AppScope::class)
@Inject
class PlaylistService(
    private val catalogRepository: CatalogRepository,
    private val platformStorage: PlatformStorage,
) {
    suspend fun createPlaylist(
        session: PlexSession?,
        hasEnabledLocalFolders: Boolean,
        title: String,
        initialTracks: List<Track>,
    ): PlaylistCreateResult {
        val allLocalEligible = initialTracks.isNotEmpty() && initialTracks.all { it.canAddToLocalPlaylist() }
        val allPlexEligible = initialTracks.isNotEmpty() && initialTracks.all { it.canAddToPlexPlaylist() }
        val hasLocalOnlyTracks = initialTracks.any { it.canAddToLocalPlaylist() && !it.canAddToPlexPlaylist() }
        val hasPlexTracks = initialTracks.any { it.canAddToPlexPlaylist() }
        if (hasLocalOnlyTracks && hasPlexTracks) {
            return PlaylistCreateResult(message = "Can't mix local files and streaming songs in one playlist.")
        }
        val playlist = when {
            allPlexEligible && session.supportsRemotePlaylists() -> {
                if (initialTracks.any { !it.canAddToPlexPlaylist() }) {
                    return PlaylistCreateResult(message = "Only streaming library songs can be added to streaming playlists.")
                }
                catalogRepository.createPlaylist(session, title, initialTracks)
            }
            allLocalEligible || (initialTracks.isEmpty() && !session.supportsRemotePlaylists() && hasEnabledLocalFolders) -> {
                if (!hasEnabledLocalFolders) {
                    return PlaylistCreateResult(message = "Add a local music folder to create playlists.")
                }
                if (initialTracks.any { !it.canAddToLocalPlaylist() }) {
                    return PlaylistCreateResult(message = "Only local audio files can be added to local playlists.")
                }
                catalogRepository.createLocalPlaylist(title, initialTracks)
            }
            initialTracks.isEmpty() && session.supportsRemotePlaylists() -> {
                catalogRepository.createPlaylist(session, title, initialTracks)
            }
            else -> {
                return PlaylistCreateResult(message = "Sign in to your provider, or add a local music folder to use playlists.")
            }
        }
        return if (playlist != null) {
            PlaylistCreateResult(playlist = playlist)
        } else {
            PlaylistCreateResult(message = "Couldn't create playlist '$title'.")
        }
    }

    suspend fun addToPlaylist(
        session: PlexSession?,
        catalog: CatalogSnapshot,
        playlist: Playlist,
        track: Track,
    ): String {
        val validationMessage = validateCanAddToPlaylist(session, playlist, track)
        if (validationMessage != null) return validationMessage
        val before = catalog.tracksByParent[playlist.id].orEmpty()
        val trackKey = track.playlistEntryKey()
        val alreadyPresent = before.any { it.playlistEntryKey() == trackKey || it.id == track.id }
        catalogRepository.addTracksToPlaylist(session, playlist, listOf(track))
        val after = catalogRepository.catalog.value.tracksByParent[playlist.id].orEmpty()
        return when {
            alreadyPresent && after.size == before.size -> "${track.title} is already in ${playlist.title}."
            after.any { it.playlistEntryKey() == trackKey || it.id == track.id } || after.size > before.size ->
                "Added to ${playlist.title}."
            else -> "Couldn't add to ${playlist.title}."
        }
    }

    suspend fun movePlaylistTrack(session: PlexSession?, playlist: Playlist, fromIndex: Int, toIndex: Int): String? {
        val moved = catalogRepository.movePlaylistTrack(session, playlist, fromIndex, toIndex)
        return if (!moved && fromIndex != toIndex) "Couldn't reorder ${playlist.title}." else null
    }

    suspend fun removePlaylistTracks(session: PlexSession?, playlist: Playlist, tracks: List<Track>): String? {
        if (tracks.isEmpty()) return null
        if (!playlist.supportsTrackRemoval()) return "This playlist can't be edited in Phoebe."
        val removed = catalogRepository.removeTracksFromPlaylist(session, playlist, tracks)
        return if (removed) {
            val count = tracks.size
            "Removed $count ${if (count == 1) "song" else "songs"} from ${playlist.title}."
        } else {
            "Couldn't remove songs from ${playlist.title}."
        }
    }

    suspend fun deletePlaylist(session: PlexSession?, playlist: Playlist): String {
        if (playlist.isLikedSongsPlaylist()) return "Liked Songs can't be deleted."
        if (playlist.isSmartPlaylist()) return "Use smart playlist management to delete smart playlists."
        val deleted = catalogRepository.deletePlaylist(session, playlist)
        return if (deleted) {
            "Deleted ${playlist.title}."
        } else {
            "Couldn't delete ${playlist.title}."
        }
    }

    suspend fun saveSmartPlaylistToProvider(session: PlexSession?, playlist: Playlist): PlaylistCreateResult {
        if (!playlist.isSmartPlaylist()) return PlaylistCreateResult(message = "Only smart playlists can be saved to a provider.")
        if (!session.supportsRemotePlaylists()) {
            return PlaylistCreateResult(message = "Sign in and select a music library to save smart playlists.")
        }
        val created = catalogRepository.createProviderPlaylistFromSmartPlaylist(session, playlist)
        return if (created != null) {
            PlaylistCreateResult(playlist = created, message = "Saved ${playlist.title} to ${session?.providerType?.displayName}.")
        } else {
            PlaylistCreateResult(message = "No provider songs from ${playlist.title} could be saved.")
        }
    }

    suspend fun copyPlaylistIntoPlaylist(session: PlexSession?, source: Playlist, target: Playlist): String? {
        if (source.id == target.id) return null
        if (!source.id.startsWith("plex:") || !target.id.startsWith("plex:")) {
            return "Playlist copying supports Plex playlists only."
        }
        val copied = catalogRepository.copyPlexPlaylistIntoPlaylist(session, source, target)
        return if (copied > 0) {
            "Copied $copied songs to ${target.title}."
        } else {
            "No new songs to copy."
        }
    }

    suspend fun exportLocalPlaylist(session: PlexSession?, playlist: Playlist, format: PlaylistExportFormat): String {
        if (!playlist.isLocalPlaylist()) return "Only local playlists can be exported."
        val tracks = catalogRepository.tracksForPlaylist(session, playlist)
        if (tracks.isEmpty()) return "Nothing to export — playlist is empty."
        val content = PlaylistExporter.export(tracks, format)
        val fileName = PlaylistExporter.suggestedFileName(playlist.title, format)
        return runCatching {
            platformStorage.writeText("exports/$fileName", content)
        }.fold(
            onSuccess = { "Exported ${tracks.size} songs to $fileName." },
            onFailure = { it.message ?: "Couldn't export playlist." },
        )
    }

    private fun validateCanAddToPlaylist(session: PlexSession?, playlist: Playlist, track: Track): String? =
        if (playlist.isLocalPlaylist()) {
            if (!track.canAddToLocalPlaylist()) {
                "Only local audio files can be added to local playlists."
            } else {
                null
            }
        } else {
            when {
                !session.supportsRemotePlaylists() -> "Sign in and select a music library to use streaming playlists."
                !playlist.isRemoteProviderPlaylist() -> "This playlist can't be edited in Phoebe."
                !track.canAddToPlexPlaylist() -> "Only streaming library songs can be added to streaming playlists."
                else -> null
            }
        }
}
