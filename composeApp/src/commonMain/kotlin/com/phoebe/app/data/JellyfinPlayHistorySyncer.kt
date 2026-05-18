package com.phoebe.app.data

import com.phoebe.app.domain.CatalogSnapshot
import com.phoebe.app.domain.MediaProviderType
import com.phoebe.app.domain.PlexSession
import com.phoebe.app.domain.Track
import com.phoebe.app.domain.catalogPrefix
import com.phoebe.app.domain.isEmbyFamily
import com.phoebe.app.platform.PhoebeLog
import com.phoebe.app.platform.currentTimeMs

class JellyfinPlayHistorySyncer(
    private val jellyfinClient: JellyfinClient,
    private val embyClient: EmbyClient,
    private val playHistoryRepository: PlayHistoryRepository,
) {
    suspend fun sync(session: PlexSession?, catalog: CatalogSnapshot): JellyfinPlayHistorySyncResult {
        val provider = session?.providerType ?: return JellyfinPlayHistorySyncResult.Skipped
        if (!provider.isEmbyFamily()) return JellyfinPlayHistorySyncResult.Skipped
        val server = session.selectedServer ?: return JellyfinPlayHistorySyncResult.Skipped
        val library = session.selectedLibrary ?: return JellyfinPlayHistorySyncResult.Skipped
        val userId = session.userId?.takeIf { it.isNotBlank() } ?: return JellyfinPlayHistorySyncResult.Skipped
        val token = session.token.takeIf { it.isNotBlank() } ?: return JellyfinPlayHistorySyncResult.Skipped
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
        if (tracksById.isEmpty()) return JellyfinPlayHistorySyncResult.Skipped

        val source = prefix
        val latestImported = playHistoryRepository.maxImportedRemotePlayedAt(source, server.id)
        val minPlayedAtMs = latestImported?.minus(IncrementalLookbackMs)?.coerceAtLeast(0L)
        val importedAtMs = currentTimeMs()
        var start = 0
        var seen = 0
        var imported = 0

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
            for (stat in stats) {
                if (minPlayedAtMs != null && stat.lastPlayedAtMs < minPlayedAtMs) continue
                val track = tracksById["$prefix:${stat.itemId}"] ?: continue
                imported += playHistoryRepository.importRemotePlayCountFallback(
                    track = track.withJellyfinHistoryFallbacks(),
                    source = source,
                    serverId = server.id,
                    lastPlayedAtMs = stat.lastPlayedAtMs,
                    playCount = stat.playCount,
                    importedAtMs = importedAtMs,
                )
            }
            if (stats.size < PageSize) break
            start += PageSize
        }

        PhoebeLog.d("JellyfinPlayHistorySyncer") {
            "synced ${provider.name} play history → seen=$seen imported=$imported minPlayedAtMs=$minPlayedAtMs"
        }
        return JellyfinPlayHistorySyncResult.Synced(imported = imported, seen = seen)
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
