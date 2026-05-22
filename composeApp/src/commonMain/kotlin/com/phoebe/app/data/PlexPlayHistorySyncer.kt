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
import kotlinx.coroutines.withTimeoutOrNull

class PlexPlayHistorySyncer(
    private val plexClient: PlexClient,
    private val playHistoryRepository: PlayHistoryRepository,
    private val catalogRepository: CatalogRepository,
) {
    suspend fun sync(session: PlexSession?, catalog: CatalogSnapshot): PlexPlayHistorySyncResult {
        val inputs = syncInputs(session, catalog) ?: return PlexPlayHistorySyncResult.Skipped
        val (server, library, token, tracksById) = inputs
        PhoebeLog.d("PlexPlayHistorySyncer") {
            "sync start server=${server.name} library=${library.title} catalogPlexTracks=${tracksById.size}"
        }

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
                    imported += importHistoryEntries(
                        server = server,
                        library = library,
                        tracksById = tracksById,
                        entries = page.entries,
                        importedAtMs = importedAtMs,
                        allowFallbackTrack = false,
                    )

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

        if (imported == 0 && seen == 0) {
            PhoebeLog.d("PlexPlayHistorySyncer") {
                "sync skipped: no Plex plays found minViewedAtMs=$minViewedAtMs catalogPlexTracks=${tracksById.size}"
            }
            return PlexPlayHistorySyncResult.Skipped
        }

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

    suspend fun syncRecent(session: PlexSession?, catalog: CatalogSnapshot): PlexPlayHistorySyncResult {
        val inputs = syncInputs(session, catalog) ?: return PlexPlayHistorySyncResult.Skipped
        val (server, library, token, tracksById) = inputs
        val importedAtMs = currentTimeMs()
        PhoebeLog.d("PlexPlayHistorySyncer") {
            "recent sync start server=${server.name} library=${library.title} catalogPlexTracks=${tracksById.size}"
        }

        val stats = runCatching {
            syncRecentTrackPlaybackStats(server, library, token, tracksById, importedAtMs)
        }.onFailure { error ->
            if (error is CancellationException) throw error
            PhoebeLog.d("PlexPlayHistorySyncer") {
                "recent track playback stats sync failed: ${error.message}"
            }
        }.getOrDefault(StatsSyncResult(imported = 0, seen = 0))

        if (stats.imported == 0 && stats.seen == 0) {
            PhoebeLog.d("PlexPlayHistorySyncer") {
                "recent sync skipped: no Plex plays found"
            }
            return PlexPlayHistorySyncResult.Skipped
        }
        PhoebeLog.d("PlexPlayHistorySyncer") {
            "recent sync complete → seen=${stats.seen} imported=${stats.imported}"
        }
        return PlexPlayHistorySyncResult.Synced(imported = stats.imported, seen = stats.seen)
    }

    suspend fun refreshViewCountsForTrackIds(session: PlexSession?, trackIds: Collection<String>): Int {
        val server = session?.selectedServer ?: run {
            PhoebeLog.d("PlexPlayHistorySyncer") { "top track view count refresh skipped: no selected server" }
            return 0
        }
        val token = session.serverAuthToken() ?: run {
            PhoebeLog.d("PlexPlayHistorySyncer") { "top track view count refresh skipped: no Plex token" }
            return 0
        }
        if (trackIds.isEmpty()) {
            PhoebeLog.d("PlexPlayHistorySyncer") { "top track view count refresh skipped: no track ids" }
            return 0
        }
        PhoebeLog.d("PlexPlayHistorySyncer") {
            "top track view count refresh start ids=${trackIds.size}"
        }
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
        } else {
            PhoebeLog.d("PlexPlayHistorySyncer") {
                "top track view count refresh finished with no updates ids=${trackIds.size}"
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
            imported += playHistoryRepository.importPlexPlayCountFallbackBatch(
                stats = stats,
                serverId = server.id,
                tracksById = tracksById,
                importedAtMs = importedAtMs,
            )
            if (stats.size < PlaybackStatsPageSize) break
            start += PlaybackStatsPageSize
        }
        PhoebeLog.d("PlexPlayHistorySyncer") {
            "track playback stats sync finished → seen=$seen imported=$imported"
        }
        return StatsSyncResult(imported = imported, seen = seen)
    }

    private suspend fun syncRecentTrackPlaybackStats(
        server: PlexServer,
        library: MusicLibrary,
        token: String,
        tracksById: Map<String, Track>,
        importedAtMs: Long,
    ): StatsSyncResult {
        PhoebeLog.d("PlexPlayHistorySyncer") { "recent track playback stats fetch start size=$RecentStatsPageSize" }
        val stats = withTimeoutOrNull(RecentStatsTimeoutMs) {
            val base = withTimeoutOrNull(RecentBaseResolveTimeoutMs) {
                plexClient.resolveFastestBase(server, token, timeoutMs = RecentBaseResolveTimeoutMs)
            }
            PhoebeLog.d("PlexPlayHistorySyncer") {
                "recent track playback stats base ${base ?: "unresolved; using cached/default"}"
            }
            plexClient.recentTrackPlaybackStatsPage(
                server = server,
                library = library,
                token = token,
                start = 0,
                size = RecentStatsPageSize,
            )
        } ?: error("timed out after ${RecentStatsTimeoutMs}ms")
        val imported = playHistoryRepository.importPlexPlayCountFallbackBatch(
            stats = stats,
            serverId = server.id,
            tracksById = tracksById,
            importedAtMs = importedAtMs,
        )
        PhoebeLog.d("PlexPlayHistorySyncer") {
            "recent track playback stats sync finished → seen=${stats.size} imported=$imported"
        }
        return StatsSyncResult(imported = imported, seen = stats.size)
    }

    private suspend fun importHistoryEntries(
        server: PlexServer,
        library: MusicLibrary,
        tracksById: Map<String, Track>,
        entries: List<PlexPlaybackHistoryEntry>,
        importedAtMs: Long,
        allowFallbackTrack: Boolean,
    ): Int {
        var imported = 0
        for (entry in entries) {
            if (entry.type != null && entry.type != PlexTrackTypeName) continue
            if (entry.librarySectionId != null && entry.librarySectionId != library.key) continue
            val track = tracksById["plex:${entry.ratingKey}"]
                ?: if (allowFallbackTrack) entry.toPlayHistoryTrack() else continue
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
        return imported
    }

    private fun Track.withPlexHistoryFallbacks(entry: PlexPlaybackHistoryEntry): Track =
        copy(
            artist = artist.ifBlank { entry.artist },
            album = album.ifBlank { entry.album },
        )

    private fun PlexPlaybackHistoryEntry.toPlayHistoryTrack(): Track =
        Track(
            id = "plex:$ratingKey",
            title = title.ifBlank { "Unknown Song" },
            artist = artist.ifBlank { "Unknown Artist" },
            album = album.ifBlank { "Unknown Album" },
            durationMs = 0L,
            streamUrl = "",
            downloadUrl = "",
        )

    private fun syncInputs(session: PlexSession?, catalog: CatalogSnapshot): SyncInputs? {
        val server = session?.selectedServer ?: run {
            PhoebeLog.d("PlexPlayHistorySyncer") { "skipped: no selected server" }
            return null
        }
        val library = session.selectedLibrary ?: run {
            PhoebeLog.d("PlexPlayHistorySyncer") { "skipped: no selected library" }
            return null
        }
        val token = session.serverAuthToken() ?: run {
            PhoebeLog.d("PlexPlayHistorySyncer") { "skipped: no Plex token" }
            return null
        }
        val tracksById = catalog.tracksByParent.values
            .asSequence()
            .flatten()
            .filter { it.isPlexLibraryTrack() }
            .distinctBy { it.id }
            .associateBy { it.id }
        return SyncInputs(server, library, token, tracksById)
    }

    private data class SyncInputs(
        val server: PlexServer,
        val library: MusicLibrary,
        val token: String,
        val tracksById: Map<String, Track>,
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
        const val RecentStatsPageSize = 50
        const val RecentStatsTimeoutMs = 10_000L
        const val RecentBaseResolveTimeoutMs = 1_500L
        private const val PlexTrackTypeName = "track"
    }
}

sealed interface PlexPlayHistorySyncResult {
    data object Skipped : PlexPlayHistorySyncResult
    data class Synced(val imported: Int, val seen: Int) : PlexPlayHistorySyncResult
}
