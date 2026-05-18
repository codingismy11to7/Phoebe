package com.phoebe.app.data

import app.cash.sqldelight.coroutines.asFlow
import app.cash.sqldelight.async.coroutines.awaitAsOneOrNull
import app.cash.sqldelight.coroutines.mapToList
import com.phoebe.app.db.PhoebeDatabase
import com.phoebe.app.domain.Track
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.cancelAndJoin
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
    private val job = SupervisorJob()
    private val scope = CoroutineScope(job + Dispatchers.Default)

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

    val playCountsByTrack: StateFlow<Map<String, Long>> = database.playHistoryQueries
        .selectPlayCountsByTrack()
        .asFlow()
        .mapToList(Dispatchers.Default)
        .map { rows ->
            buildMap(rows.size) {
                for (row in rows) put(row.track_id, row.playCount)
            }
        }
        .stateIn(scope, SharingStarted.Eagerly, emptyMap())

    val playEventsByTrack: StateFlow<Map<String, List<Long>>> = database.playHistoryQueries
        .selectLatestPlayEventsByTrack()
        .asFlow()
        .mapToList(Dispatchers.Default)
        .map { rows ->
            buildMap {
                for (row in rows) {
                    val plays = getOrPut(row.track_id) { mutableListOf() }
                    plays.add(row.played_at_ms)
                }
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

    suspend fun maxImportedPlexPlayedAt(serverId: String): Long? =
        withContext(Dispatchers.Default) {
            database.playHistoryQueries.selectMaxImportedPlexPlayedAt(serverId).awaitAsOneOrNull()?.lastPlayed
        }

    suspend fun maxImportedRemotePlayedAt(source: String, serverId: String): Long? =
        withContext(Dispatchers.Default) {
            database.playHistoryQueries.selectMaxImportedRemotePlayedAt(source, serverId).awaitAsOneOrNull()?.lastPlayed
        }

    suspend fun importPlexPlay(
        track: Track,
        serverId: String,
        historyKey: String,
        playedAtMs: Long,
        importedAtMs: Long,
        mergeWindowMs: Long,
    ): Boolean {
        if (track.id.isBlank() || historyKey.isBlank()) return false
        val cleanArtist = track.artist.ifBlank { "Unknown Artist" }
        val cleanAlbum = track.album.ifBlank { "Unknown Album" }
        return withContext(Dispatchers.Default) {
            val alreadyImported = database.playHistoryQueries
                .selectImportedPlexHistoryKey(historyKey)
                .awaitAsOneOrNull() != null
            if (alreadyImported) return@withContext false

            val candidatePlayedAtMs = database.playHistoryQueries
                .selectLocalMergeCandidate(
                    track_id = track.id,
                    played_at_ms = playedAtMs - mergeWindowMs,
                    played_at_ms_ = playedAtMs + mergeWindowMs,
                )
                .awaitAsOneOrNull()

            if (candidatePlayedAtMs != null) {
                database.playHistoryQueries.markLocalPlayAsImportedPlex(
                    played_at_ms = playedAtMs,
                    plex_server_id = serverId,
                    plex_history_key = historyKey,
                    plex_imported_at_ms = importedAtMs,
                    track_id = track.id,
                    played_at_ms_ = candidatePlayedAtMs,
                )
            } else {
                database.playHistoryQueries.insertImportedPlexPlay(
                    track_id = track.id,
                    artist = cleanArtist,
                    album = cleanAlbum,
                    played_at_ms = playedAtMs,
                    plex_server_id = serverId,
                    plex_history_key = historyKey,
                    plex_imported_at_ms = importedAtMs,
                )
            }
            true
        }
    }

    suspend fun importPlexPlayCountFallback(
        track: Track,
        serverId: String,
        lastPlayedAtMs: Long,
        playCount: Long,
        importedAtMs: Long,
    ): Int {
        val cappedCount = playCount.coerceIn(0L, 500L).toInt()
        if (cappedCount <= 0) return 0
        var imported = 0
        repeat(cappedCount) { index ->
            val playedAtMs = (lastPlayedAtMs - index).coerceAtLeast(0L)
            if (importPlexPlay(
                    track = track,
                    serverId = serverId,
                    historyKey = "plex-stats:$serverId:${track.id}:$index",
                    playedAtMs = playedAtMs,
                    importedAtMs = importedAtMs,
                    mergeWindowMs = 0L,
                )
            ) {
                imported += 1
            }
        }
        return imported
    }

    suspend fun importRemotePlayCountFallback(
        track: Track,
        source: String,
        serverId: String,
        lastPlayedAtMs: Long,
        playCount: Long,
        importedAtMs: Long,
    ): Int {
        val cappedCount = playCount.coerceIn(0L, 500L).toInt()
        if (track.id.isBlank() || source.isBlank() || serverId.isBlank() || cappedCount <= 0) return 0
        val cleanArtist = track.artist.ifBlank { "Unknown Artist" }
        val cleanAlbum = track.album.ifBlank { "Unknown Album" }
        var imported = 0
        withContext(Dispatchers.Default) {
            repeat(cappedCount) { index ->
                val playedAtMs = (lastPlayedAtMs - index).coerceAtLeast(0L)
                val historyKey = "$source-stats:$serverId:${track.id}:$index"
                val alreadyImported = database.playHistoryQueries
                    .selectImportedPlexHistoryKey(historyKey)
                    .awaitAsOneOrNull() != null
                if (alreadyImported) return@repeat
                database.playHistoryQueries.insertImportedRemotePlay(
                    track_id = track.id,
                    artist = cleanArtist,
                    album = cleanAlbum,
                    played_at_ms = playedAtMs,
                    source = source,
                    plex_server_id = serverId,
                    plex_history_key = historyKey,
                    plex_imported_at_ms = importedAtMs,
                )
                imported += 1
            }
        }
        return imported
    }

    /** Cancel background aggregate collectors. Call before closing the backing [SqlDriver] in tests. */
    fun close() {
        job.cancel()
    }

    /** Like [close], but waits until eager collectors have stopped. */
    suspend fun closeAndJoin() {
        job.cancelAndJoin()
    }
}
