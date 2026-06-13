package com.phoebe.app.data

import com.phoebe.app.domain.Album
import com.phoebe.app.domain.Artist
import com.phoebe.app.domain.PlexSession
import com.phoebe.app.domain.Playlist
import com.phoebe.app.domain.Track
import com.phoebe.app.platform.DownloadNotifier
import com.phoebe.app.platform.PlatformStorage
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn

data class DownloadServiceResult(
    val batch: DownloadBatchResult,
    val message: String,
)

data class DownloadDirectoryResult(
    val uri: String?,
    val message: String,
)

@SingleIn(AppScope::class)
@Inject
class DownloadService(
    private val catalogRepository: CatalogRepository,
    private val appSettingsRepository: AppSettingsRepository,
    private val downloadNotifier: DownloadNotifier,
    private val platformStorage: PlatformStorage,
) {
    suspend fun download(track: Track): DownloadServiceResult {
        val result = catalogRepository.download(track)
        return DownloadServiceResult(result, downloadMessage(result, singular = "song", plural = "songs"))
    }

    suspend fun download(session: PlexSession?, album: Album): DownloadServiceResult {
        val result = catalogRepository.downloadAlbum(session, album)
        return DownloadServiceResult(
            result,
            downloadMessage(result, singular = "song from ${album.title}", plural = "songs from ${album.title}"),
        )
    }

    suspend fun download(session: PlexSession?, artist: Artist): DownloadServiceResult {
        val result = catalogRepository.downloadArtist(session, artist)
        return DownloadServiceResult(
            result,
            downloadMessage(result, singular = "song by ${artist.title}", plural = "songs by ${artist.title}"),
        )
    }

    suspend fun download(session: PlexSession?, playlist: Playlist): DownloadServiceResult {
        catalogRepository.previewQueuedDownloadsForPlaylist(playlist)
        val result = catalogRepository.downloadPlaylist(session, playlist)
        return DownloadServiceResult(
            result,
            downloadMessage(result, singular = "song from ${playlist.title}", plural = "songs from ${playlist.title}"),
        )
    }

    suspend fun notifyDownloadFinishedIfNeeded(result: DownloadBatchResult) {
        if (!appSettingsRepository.settings.value.notifyWhenDownloadFinishes || result.completed <= 0) return
        val body = if (result.completed == 1) {
            "Downloaded 1 song."
        } else {
            "Downloaded ${result.completed} songs."
        }
        downloadNotifier.notifyDownloadFinished("Download complete", body)
    }

    suspend fun setDownloadDirectory(uri: String?): DownloadDirectoryResult {
        platformStorage.writeDownloadDirectory(uri)
        val current = platformStorage.readDownloadDirectory()
        val message = if (current == null) {
            "Downloads will use ${platformStorage.defaultDownloadDirectoryLabel()}."
        } else {
            "Download location updated."
        }
        return DownloadDirectoryResult(current, message)
    }

    suspend fun deleteAllDownloads(): String {
        val deleted = catalogRepository.deleteAllDownloads()
        return if (deleted == 0) {
            "No downloads to delete."
        } else {
            "Deleted $deleted downloaded ${if (deleted == 1) "song" else "songs"}."
        }
    }

    suspend fun tracksForPlaylist(session: PlexSession?, playlist: Playlist): List<Track> =
        catalogRepository.tracksForPlaylist(session, playlist)

    suspend fun deleteDownloadsForTracks(tracks: List<Track>): String {
        val deleted = catalogRepository.deleteDownloadsForTracks(tracks)
        return if (deleted == 0) {
            "No downloaded songs to delete."
        } else {
            "Deleted $deleted downloaded ${if (deleted == 1) "song" else "songs"}."
        }
    }

    suspend fun cancelDownloadsForTracks(tracks: List<Track>): String {
        catalogRepository.cancelDownloadsForTracks(tracks)
        val deleted = catalogRepository.deleteDownloadsForTracks(tracks)
        return if (deleted == 0) {
            "Cancelled download."
        } else {
            "Cancelled download and deleted $deleted downloaded ${if (deleted == 1) "song" else "songs"}."
        }
    }

    private fun downloadMessage(result: DownloadBatchResult, singular: String, plural: String): String =
        when {
            result.total == 0 -> "Nothing to download yet."
            result.failed == 0 && result.completed == result.total -> {
                val noun = if (result.completed == 1) singular else plural
                "Downloaded ${result.completed} $noun."
            }
            result.failed == 0 && result.completed > 0 && result.skipped > 0 -> {
                val noun = if (result.completed == 1) singular else plural
                "Downloaded ${result.completed} $noun. ${result.skipped} unavailable."
            }
            result.failed == 0 && result.skipped > 0 -> "No downloadable songs found."
            result.completed > 0 -> {
                val percent = ((result.completed.toFloat() / result.total.toFloat()) * 100f).toInt().coerceIn(0, 100)
                val skipped = result.skipped.takeIf { it > 0 }?.let { " $it unavailable." }.orEmpty()
                "Downloaded ${result.completed} of ${result.total} songs ($percent%). " +
                    "${result.failed} failed.$skipped${result.downloadFailureDetailMessage()}"
            }
            else -> "Couldn't download those songs. 0% downloaded.${result.downloadFailureDetailMessage()}"
        }

    private fun DownloadBatchResult.downloadFailureDetailMessage(): String {
        val topReason = failureReasons.firstOrNull() ?: return ""
        val count = topReason.count.takeIf { it > 1 }?.let { " ($it)" }.orEmpty()
        val sample = failedSamples.firstOrNull()
            ?.title
            ?.takeIf { it.isNotBlank() }
            ?.let { " Example: ${it.compactDownloadMessageDetail(52)}." }
            .orEmpty()
        return " Top reason: ${topReason.reason.compactDownloadMessageDetail(96)}$count.$sample"
    }

    private fun String.compactDownloadMessageDetail(maxLength: Int): String {
        val compact = replace(Regex("\\s+"), " ").trim()
        return if (compact.length <= maxLength) compact else compact.take(maxLength - 1).trimEnd() + "…"
    }
}
