package com.phoebe.app.data

import app.cash.sqldelight.async.coroutines.awaitAsList
import com.phoebe.app.db.PhoebeDatabase
import com.phoebe.app.platform.PhoebeDispatchers
import com.phoebe.app.platform.PhoebeLog
import com.phoebe.app.platform.currentTimeMs
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.withContext

/** Personal Mix dedupe window = this multiplier × configured mix length limit. */
const val RecentPersonalMixWindowMultiplier = 2

/**
 * Persists track identity keys recently surfaced in a Personal Mix so soft
 * deprioritization survives process death. Window size is owned by the caller
 * (typically [RecentPersonalMixWindowMultiplier] × mix limit).
 */
@SingleIn(AppScope::class)
@Inject
class RecentMixTracksRepository(
    private val database: PhoebeDatabase,
) {
    private val databaseDispatcher = PhoebeDispatchers.io

    suspend fun recentTrackKeys(limit: Int): Set<String> {
        if (limit <= 0) return emptySet()
        return withContext(databaseDispatcher) {
            database.recentMixTrackQueries
                .selectRecentTrackIds(limit.toLong())
                .awaitAsList()
                .toSet()
        }
    }

    suspend fun recordSurfaced(
        trackKeys: Collection<String>,
        keepCount: Int,
        atMs: Long = currentTimeMs(),
    ) {
        val keys = trackKeys.map { it.trim() }.filter { it.isNotEmpty() }
        if (keys.isEmpty()) return
        val cappedKeep = keepCount.coerceAtLeast(keys.size)
        withContext(databaseDispatcher) {
            // Batch all per-key replace writes into one transaction — issuing each
            // delete/insert as its own implicit transaction serializes a commit
            // (and fsync) per statement, which made Personal Mix playback wait
            // seconds before starting for a full mix-sized key set.
            database.transaction {
                keys.forEach { key ->
                    // Replace any prior row for this identity so a re-surfaced track
                    // does not occupy multiple slots and shrink the effective window.
                    database.recentMixTrackQueries.deleteByTrackId(key)
                    database.recentMixTrackQueries.recordSurfaced(
                        track_id = key,
                        surfaced_at_ms = atMs,
                    )
                }
            }
            pruneToKeepCount(cappedKeep)
        }
    }

    /**
     * Drop oldest rows beyond [keepCount]. Done in Kotlin (rowid list + per-row
     * deletes) instead of a self-referencing DELETE subquery — that pattern can
     * hang indefinitely on the JDBC SQLite driver used by desktop. The deletes
     * are batched into one transaction for the same reason as [recordSurfaced].
     */
    private suspend fun pruneToKeepCount(keepCount: Int) {
        val ids = database.recentMixTrackQueries
            .selectIdsNewestFirst()
            .awaitAsList()
        if (ids.size <= keepCount) return
        val idsToDelete = ids.drop(keepCount)
        database.transaction {
            idsToDelete.forEach { id ->
                database.recentMixTrackQueries.deleteById(id)
            }
        }
    }
}
