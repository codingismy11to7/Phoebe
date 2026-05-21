package com.phoebe.app.data

import com.phoebe.app.domain.CatalogSnapshot
import com.phoebe.app.domain.MusicLibrary
import com.phoebe.app.domain.PlexSession
import com.phoebe.app.domain.PlexServer
import com.phoebe.app.domain.Track
import com.phoebe.app.domain.isPlexLibraryTrack
import com.phoebe.app.domain.serverAuthToken
import com.phoebe.app.platform.PhoebeLog
import com.phoebe.app.platform.currentTimeMs
import kotlinx.coroutines.CancellationException

class PlexPlayHistorySyncer(
    private val plexClient: PlexClient,
    private val playHistoryRepository: PlayHistoryRepository,
    private val catalogRepository: CatalogRepository,
) {
    suspend fun sync(session: PlexSession?, catalog: CatalogSnapshot): PlexPlayHistorySyncResult {
        val server = session?.selectedServer ?: return PlexPlayHistorySyncResult.Skipped
        val library = session.selectedLibrary ?: return PlexPlayHistorySyncResult.Skipped
        val token = session.serverAuthToken() ?: return PlexPlayHistorySyncResult.Skipped
        val tracksById = catalog.tracksByParent.values
            .asSequence()
            .flatten()
            .filter { it.isPlexLibraryTrack() }
            .distinctBy { it.id }
            .associateBy { it.id }

        val latestImported = playHistoryRepository.maxImportedPlexPlayedAt(server.id)
        val minViewedAtMs = latestImported?.minus(IncrementalLookbackMs)?.coerceAtLeast(0L)
        val importedAtMs = currentTimeMs()
        var start = 0
        var imported = 0
        var seen = 0
        var historyFailed = false

        if (tracksById.isNotEmpty()) {
            runCatching {
                while (start < PageSize * MaxPages) {
                    val page = plexClient.playbackHistoryPage(
                        server = server,
                        token = token,
                        library = library,
                        minViewedAtMs = minViewedAtMs,
                        start = start,
                        size = PageSize,
                    )
                    seen += page.entries.size
                    for (entry in page.entries) {
                        if (entry.type != null && entry.type != PlexTrackTypeName) continue
                        if (entry.librarySectionId != null && entry.librarySectionId != library.key) continue
                        val track = tracksById["plex:${entry.ratingKey}"] ?: continue
                        if (playHistoryRepository.importPlexPlay(
                                track = track.withPlexHistoryFallbacks(entry),
                                serverId = server.id,
                                historyKey = entry.historyKey,
                                playedAtMs = entry.viewedAtMs,
                                importedAtMs = importedAtMs,
                                mergeWindowMs = MergeWindowMs,
                            )
                        ) {
                            imported += 1
                        }
                    }

                    val total = page.totalSize
                    val next = page.offset + page.size
                    if (page.size <= 0 || (total != null && next >= total)) break
                    start = next
                }
            }.onFailure { error ->
                if (error is CancellationException) throw error
                historyFailed = true
                PhoebeLog.d("PlexPlayHistorySyncer") {
                    "Plex history endpoint failed, continuing with track view counts: ${error.message}"
                }
            }
        } else {
            PhoebeLog.d("PlexPlayHistorySyncer") {
                "catalog has no Plex tracks yet; syncing view counts from Plex metadata"
            }
        }

        val stats = runCatching {
            syncTrackPlaybackStats(
                server = server,
                library = library,
                token = token,
                tracksById = tracksById,
                importedAtMs = importedAtMs,
            )
        }.onFailure { error ->
            if (error is CancellationException) throw error
            PhoebeLog.d("PlexPlayHistorySyncer") {
                "Plex track playback stats sync failed: ${error.message}"
            }
        }.getOrDefault(StatsSyncResult(imported = 0, seen = 0))
        imported += stats.imported
        seen += stats.seen

        if (imported == 0 && seen == 0) return PlexPlayHistorySyncResult.Skipped

        if (historyFailed) {
            PhoebeLog.d("PlexPlayHistorySyncer") {
                "synced Plex track playback stats fallback → seen=${stats.seen} imported=${stats.imported}"
            }
        } else {
            PhoebeLog.d("PlexPlayHistorySyncer") {
                "synced Plex play history → seen=$seen imported=$imported minViewedAtMs=$minViewedAtMs " +
                    "(history events + view counts)"
            }
        }
        return PlexPlayHistorySyncResult.Synced(imported = imported, seen = seen)
    }

    suspend fun refreshViewCountsForTrackIds(session: PlexSession?, trackIds: Collection<String>): Int {
        val server = session?.selectedServer ?: return 0
        val token = session.serverAuthToken() ?: return 0
        if (trackIds.isEmpty()) return 0
        val resolvedTracks = catalogRepository.resolveTracksByIds(trackIds)
        val importedAtMs = currentTimeMs()
        var imported = 0
        for (trackId in trackIds) {
            val ratingKey = trackId.removePrefix("plex:").takeIf { it.isNotBlank() && it != trackId } ?: continue
            val stat = runCatching { plexClient.trackPlaybackStat(server, ratingKey, token) }
                .onFailure { error ->
                    PhoebeLog.d("PlexPlayHistorySyncer") {
                        "track view count refresh failed for '$trackId': ${error.message}"
                    }
                }
                .getOrNull() ?: continue
            val track = stat.toPlayHistoryTrack(resolvedTracks[trackId])
            imported += playHistoryRepository.importPlexPlayCountFallback(
                track = track,
                serverId = server.id,
                lastPlayedAtMs = stat.lastViewedAtMs ?: 0L,
                playCount = stat.viewCount,
                importedAtMs = importedAtMs,
            )
        }
        if (imported > 0) {
            PhoebeLog.d("PlexPlayHistorySyncer") {
                "refreshed Plex view counts for top tracks → imported=$imported"
            }
        }
        return imported
    }

    private suspend fun syncTrackPlaybackStats(
        server: PlexServer,
        library: MusicLibrary,
        token: String,
        tracksById: Map<String, Track>,
        importedAtMs: Long,
    ): StatsSyncResult {
        var start = 0
        var seen = 0
        var imported = 0
        while (start < PlaybackStatsPageSize * PlaybackStatsMaxPages) {
            val stats = plexClient.trackPlaybackStatsPage(
                server = server,
                library = library,
                token = token,
                start = start,
                size = PlaybackStatsPageSize,
            )
            if (stats.isEmpty()) break
            seen += stats.size
            val tracksToPublish = LinkedHashMap<String, Track>()
            for (stat in stats) {
                val existing = tracksById["plex:${stat.ratingKey}"]
                if (existing == null) {
                    tracksToPublish.getOrPut("plex:${stat.ratingKey}") { stat.toPlayHistoryTrack() }
                }
            }
            imported += playHistoryRepository.importPlexPlayCountFallbackBatch(
                stats = stats,
                serverId = server.id,
                tracksById = tracksById,
                importedAtMs = importedAtMs,
            )
            if (tracksToPublish.isNotEmpty()) {
                catalogRepository.publishPlexTracks(tracksToPublish.values.toList())
            }
            if (stats.size < PlaybackStatsPageSize) break
            start += PlaybackStatsPageSize
        }
        return StatsSyncResult(imported = imported, seen = seen)
    }

    private fun Track.withPlexHistoryFallbacks(entry: PlexPlaybackHistoryEntry): Track =
        copy(
            artist = artist.ifBlank { entry.artist },
            album = album.ifBlank { entry.album },
        )

    private data class StatsSyncResult(
        val imported: Int,
        val seen: Int,
    )

    companion object {
        const val PageSize = 100
        const val MaxPages = 25
        const val IncrementalLookbackMs = 10L * 60L * 1000L
        const val MergeWindowMs = 10L * 60L * 1000L
        const val PlaybackStatsPageSize = 500
        const val PlaybackStatsMaxPages = 400
        private const val PlexTrackTypeName = "track"
    }
}

sealed interface PlexPlayHistorySyncResult {
    data object Skipped : PlexPlayHistorySyncResult
    data class Synced(val imported: Int, val seen: Int) : PlexPlayHistorySyncResult
}
