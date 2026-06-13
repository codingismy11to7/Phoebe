package com.phoebe.app.data

import com.phoebe.app.domain.CatalogSnapshot
import com.phoebe.app.domain.MediaProviderType
import com.phoebe.app.domain.PlexSession
import com.phoebe.app.domain.Track
import com.phoebe.app.domain.catalogPrefix
import com.phoebe.app.domain.isEmbyFamily
import com.phoebe.app.platform.PhoebeLog
import com.phoebe.app.platform.currentTimeMs
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn

@SingleIn(AppScope::class)
@Inject
class JellyfinPlayHistorySyncer(
    private val jellyfinClient: JellyfinClient,
    private val embyClient: EmbyClient,
    private val playHistoryRepository: PlayHistoryRepository,
    private val catalogRepository: CatalogRepository,
) {
    suspend fun sync(session: PlexSession?, catalog: CatalogSnapshot): JellyfinPlayHistorySyncResult {
        val provider = session?.providerType
        if (provider == null) {
            PhoebeLog.d("JellyfinPlayHistorySyncer") { "skipped: no session" }
            return JellyfinPlayHistorySyncResult.Skipped
        }
        if (!provider.isEmbyFamily()) {
            PhoebeLog.d("JellyfinPlayHistorySyncer") { "skipped: provider=${provider.name} is not Emby/Jellyfin" }
            return JellyfinPlayHistorySyncResult.Skipped
        }
        val server = session.selectedServer
        if (server == null) {
            PhoebeLog.d("JellyfinPlayHistorySyncer") { "skipped: no selected server" }
            return JellyfinPlayHistorySyncResult.Skipped
        }
        val library = session.selectedLibrary
        if (library == null) {
            PhoebeLog.d("JellyfinPlayHistorySyncer") { "skipped: no selected library" }
            return JellyfinPlayHistorySyncResult.Skipped
        }
        val userId = session.userId?.takeIf { it.isNotBlank() }
        if (userId == null) {
            PhoebeLog.d("JellyfinPlayHistorySyncer") { "skipped: missing userId" }
            return JellyfinPlayHistorySyncResult.Skipped
        }
        val token = session.token.takeIf { it.isNotBlank() }
        if (token == null) {
            PhoebeLog.d("JellyfinPlayHistorySyncer") { "skipped: missing token" }
            return JellyfinPlayHistorySyncResult.Skipped
        }
        PhoebeLog.d("JellyfinPlayHistorySyncer") {
            "sync start → provider=${provider.name} server=${server.id} library=${library.key} userId=$userId"
        }
        val client = when (provider) {
            MediaProviderType.Emby -> embyClient
            else -> jellyfinClient
        }
        val prefix = provider.catalogPrefix
        val tracksById = catalog.tracksByParent.values
            .asSequence()
            .flatten()
            .filter { it.id.startsWith("$prefix:") }
            .distinctBy { it.id }
            .associateBy { it.id }

        val statsSource = "$prefix-stats"
        val latestImported = playHistoryRepository.maxImportedRemoteStatsLastPlayedAt(statsSource, server.id)
        val minPlayedAtMs = latestImported?.minus(IncrementalLookbackMs)?.coerceAtLeast(0L)
        val importedAtMs = currentTimeMs()
        var start = 0
        var seen = 0
        var imported = 0
        val tracksToPublish = LinkedHashMap<String, Track>()

        suspend fun importStats(stats: List<JellyfinPlaybackStat>) {
            val freshStats = if (minPlayedAtMs != null) {
                stats.takeWhile { it.lastPlayedAtMs >= minPlayedAtMs }
            } else {
                stats
            }
            for (stat in freshStats) {
                val trackId = "$prefix:${stat.itemId}"
                val track = resolvePlayHistoryTrack(
                    prefix = prefix,
                    trackId = trackId,
                    catalogTrack = tracksById[trackId],
                    remoteTrack = stat.track,
                ) ?: continue
                tracksToPublish[track.id] = track
                imported += playHistoryRepository.importRemotePlayCountFallback(
                    track = track.withJellyfinHistoryFallbacks(),
                    source = prefix,
                    serverId = server.id,
                    lastPlayedAtMs = stat.lastPlayedAtMs,
                    playCount = stat.playCount,
                    importedAtMs = importedAtMs,
                )
                if (playHistoryRepository.importRemotePlay(
                        track = track.withJellyfinHistoryFallbacks(),
                        source = prefix,
                        serverId = server.id,
                        historyKey = "$prefix:${server.id}:${stat.itemId}:${stat.lastPlayedAtMs}",
                        playedAtMs = stat.lastPlayedAtMs,
                        importedAtMs = importedAtMs,
                    )
                ) {
                    imported += 1
                }
            }
        }

        if (provider == MediaProviderType.Emby) {
            val latestStats = client.playbackLatestPlayedPage(
                server = server,
                library = library,
                token = token,
                userId = userId,
                size = PageSize,
            )
            if (latestStats.isNotEmpty()) {
                seen += latestStats.size
                importStats(latestStats)
            }

            var resumeStart = 0
            while (resumeStart < PageSize * MaxPages) {
                val resumeStats = client.playbackResumePage(
                    server = server,
                    library = library,
                    token = token,
                    userId = userId,
                    start = resumeStart,
                    size = PageSize,
                )
                if (resumeStats.isEmpty()) break
                seen += resumeStats.size
                importStats(resumeStats)
                if (resumeStats.size < PageSize) break
                resumeStart += PageSize
            }
        }

        while (start < PageSize * MaxPages) {
            val stats = client.playbackStatsPage(
                server = server,
                library = library,
                token = token,
                userId = userId,
                start = start,
                size = PageSize,
            )
            if (stats.isEmpty()) break
            seen += stats.size
            val freshStats = if (minPlayedAtMs != null) {
                stats.takeWhile { it.lastPlayedAtMs >= minPlayedAtMs }
            } else {
                stats
            }
            importStats(freshStats)
            if (stats.size < PageSize || freshStats.size < stats.size) break
            start += PageSize
        }

        if (tracksToPublish.isNotEmpty()) {
            catalogRepository.publishProviderTracks(prefix, tracksToPublish.values.toList())
        }

        if (imported == 0 && seen == 0) {
            PhoebeLog.d("JellyfinPlayHistorySyncer") {
                "skipped: ${provider.name} API returned no play history (minPlayedAtMs=$minPlayedAtMs)"
            }
            return JellyfinPlayHistorySyncResult.Skipped
        }

        PhoebeLog.d("JellyfinPlayHistorySyncer") {
            "synced ${provider.name} play history → seen=$seen imported=$imported minPlayedAtMs=$minPlayedAtMs"
        }
        return JellyfinPlayHistorySyncResult.Synced(imported = imported, seen = seen)
    }

    private fun resolvePlayHistoryTrack(
        prefix: String,
        trackId: String,
        catalogTrack: Track?,
        remoteTrack: Track?,
    ): Track? {
        val source = catalogTrack ?: remoteTrack ?: return null
        val bareItemId = trackId.removePrefix("$prefix:")
        val normalizedParent = source.parentAlbumId?.takeIf { it.isNotBlank() }?.let { parentId ->
            if (parentId.startsWith("$prefix:")) parentId else "$prefix:$parentId"
        }
        return source.copy(id = trackId, parentAlbumId = normalizedParent)
            .takeIf { bareItemId.isNotBlank() && bareItemId != trackId }
    }

    private fun Track.withJellyfinHistoryFallbacks(): Track =
        copy(
            artist = artist.ifBlank { "Unknown Artist" },
            album = album.ifBlank { "Unknown Album" },
        )

    companion object {
        const val PageSize = 1000
        const val MaxPages = 25
        const val IncrementalLookbackMs = 10L * 60L * 1000L
    }
}

sealed interface JellyfinPlayHistorySyncResult {
    data object Skipped : JellyfinPlayHistorySyncResult
    data class Synced(val imported: Int, val seen: Int) : JellyfinPlayHistorySyncResult
}
