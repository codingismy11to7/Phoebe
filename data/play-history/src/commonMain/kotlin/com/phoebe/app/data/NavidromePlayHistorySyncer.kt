package com.phoebe.app.data

import com.phoebe.app.domain.CatalogSnapshot
import com.phoebe.app.domain.PlexSession
import com.phoebe.app.domain.Track
import com.phoebe.app.domain.catalogPrefix
import com.phoebe.app.domain.isNavidrome
import com.phoebe.app.platform.PhoebeLog
import com.phoebe.app.platform.currentTimeMs
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn

@SingleIn(AppScope::class)
@Inject
class NavidromePlayHistorySyncer(
    private val subsonicClient: SubsonicClient,
    private val playHistoryRepository: PlayHistoryRepository,
    private val catalogRepository: CatalogRepository,
) {
    suspend fun sync(session: PlexSession?, catalog: CatalogSnapshot): NavidromePlayHistorySyncResult {
        if (session?.isNavidrome() != true) return NavidromePlayHistorySyncResult.Skipped
        val server = session.selectedServer ?: return NavidromePlayHistorySyncResult.Skipped
        val username = session.userName
        val password = session.token.takeIf { it.isNotBlank() } ?: return NavidromePlayHistorySyncResult.Skipped

        val stats = runCatching {
            subsonicClient.playHistoryStats(server, username, password)
        }.getOrElse { error ->
            PhoebeLog.d("NavidromePlayHistorySyncer") { "play history fetch failed: ${error.message}" }
            return NavidromePlayHistorySyncResult.Skipped
        }
        if (stats.isEmpty()) return NavidromePlayHistorySyncResult.Skipped

        val prefix = session.providerType.catalogPrefix
        val catalogTracksById = catalog.tracksByParent.values
            .asSequence()
            .flatten()
            .filter { it.id.startsWith("$prefix:") }
            .associateBy { it.id }

        val tracksToPublish = stats.map { stat ->
            val prefixed = stat.track.withNavidromePrefix(prefix)
            catalogTracksById[prefixed.id] ?: prefixed
        }
        catalogRepository.publishNavidromeTracks(tracksToPublish)

        val source = prefix
        val importedAtMs = currentTimeMs()
        var imported = 0
        var seen = 0
        for (stat in stats) {
            seen += 1
            val track = (catalogTracksById[stat.track.withNavidromePrefix(prefix).id] ?: stat.track.withNavidromePrefix(prefix))
                .copy(
                    artist = stat.track.artist.ifBlank { "Unknown Artist" },
                    album = stat.track.album.ifBlank { "Unknown Album" },
                )
            if (stat.playCount > 0L) {
                imported += playHistoryRepository.importRemotePlayCountFallback(
                    track = track,
                    source = source,
                    serverId = server.id,
                    lastPlayedAtMs = stat.lastPlayedMs ?: 0L,
                    playCount = stat.playCount,
                    importedAtMs = importedAtMs,
                )
            }
            val lastPlayedMs = stat.lastPlayedMs ?: continue
            if (playHistoryRepository.importRemotePlay(
                    track = track,
                    source = source,
                    serverId = server.id,
                    historyKey = "$source:${server.id}:${track.id.removePrefix("$prefix:")}:$lastPlayedMs",
                    playedAtMs = lastPlayedMs,
                    importedAtMs = importedAtMs,
                )
            ) {
                imported += 1
            }
        }

        PhoebeLog.d("NavidromePlayHistorySyncer") {
            "synced Navidrome play history → seen=$seen imported=$imported"
        }
        return NavidromePlayHistorySyncResult.Synced(imported = imported, seen = seen)
    }

    private fun Track.withNavidromePrefix(prefix: String): Track {
        val p = "$prefix:"
        return copy(
            id = if (id.startsWith(p)) id else p + id.removePrefix(p),
            parentAlbumId = parentAlbumId?.let { albumId ->
                if (albumId.startsWith(p)) albumId else p + albumId.removePrefix(p)
            },
        )
    }
}

sealed interface NavidromePlayHistorySyncResult {
    data object Skipped : NavidromePlayHistorySyncResult
    data class Synced(val imported: Int, val seen: Int) : NavidromePlayHistorySyncResult
}
