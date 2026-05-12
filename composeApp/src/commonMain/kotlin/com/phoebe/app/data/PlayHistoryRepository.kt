package com.phoebe.app.data

import app.cash.sqldelight.coroutines.asFlow
import app.cash.sqldelight.coroutines.mapToList
import com.phoebe.app.db.PhoebeDatabase
import com.phoebe.app.domain.Track
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.withContext

/**
 * Tracks per-track play timestamps. Surfaces "last played" aggregates per
 * artist / album / track so the library UI can show how recently each entry
 * was heard.
 *
 * Backed by SQLDelight's `asFlow()`, so each insert into `PlayHistoryRow`
 * automatically re-runs the aggregate queries and pushes a new map onto the
 * exposed [StateFlow]s — no manual refresh required.
 */
class PlayHistoryRepository(
    private val database: PhoebeDatabase,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    val lastPlayedByArtist: StateFlow<Map<String, Long>> = database.playHistoryQueries
        .selectLastPlayedByArtist()
        .asFlow()
        .mapToList(Dispatchers.Default)
        .map { rows ->
            buildMap(rows.size) {
                for (row in rows) row.lastPlayed?.let { put(row.artist, it) }
            }
        }
        .stateIn(scope, SharingStarted.Eagerly, emptyMap())

    val lastPlayedByAlbum: StateFlow<Map<String, Long>> = database.playHistoryQueries
        .selectLastPlayedByAlbum()
        .asFlow()
        .mapToList(Dispatchers.Default)
        .map { rows ->
            buildMap(rows.size) {
                for (row in rows) row.lastPlayed?.let { put(row.album, it) }
            }
        }
        .stateIn(scope, SharingStarted.Eagerly, emptyMap())

    val lastPlayedByTrack: StateFlow<Map<String, Long>> = database.playHistoryQueries
        .selectLastPlayedByTrack()
        .asFlow()
        .mapToList(Dispatchers.Default)
        .map { rows ->
            buildMap(rows.size) {
                for (row in rows) row.lastPlayed?.let { put(row.track_id, it) }
            }
        }
        .stateIn(scope, SharingStarted.Eagerly, emptyMap())

    /**
     * Eager warm-up. The aggregate flows are already subscribed via
     * `stateIn(Eagerly)`, so this exists only so callers can keep the same
     * "restore on startup" ergonomics as the other repositories.
     */
    suspend fun restore() {
        // No-op — see class docs.
    }

    /**
     * Persist a fresh play event for [track]. The eagerly-subscribed [StateFlow]s
     * react automatically when SQLDelight notifies that the table changed.
     */
    suspend fun recordPlay(track: Track, atMs: Long) {
        if (track.id.isBlank()) return
        val cleanArtist = track.artist.ifBlank { "Unknown Artist" }
        val cleanAlbum = track.album.ifBlank { "Unknown Album" }
        withContext(Dispatchers.Default) {
            database.playHistoryQueries.recordPlay(
                track_id = track.id,
                artist = cleanArtist,
                album = cleanAlbum,
                played_at_ms = atMs,
            )
        }
    }
}
