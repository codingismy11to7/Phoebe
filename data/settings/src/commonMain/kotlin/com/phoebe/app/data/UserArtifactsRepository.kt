package com.phoebe.app.data

import app.cash.sqldelight.async.coroutines.awaitAsList
import com.phoebe.app.db.LocalMetadataOverrideRow
import com.phoebe.app.db.PhoebeDatabase
import com.phoebe.app.db.SavedSearchRow
import com.phoebe.app.db.SmartPlaylistRow
import com.phoebe.app.domain.AdvancedSearchQuery
import com.phoebe.app.domain.FilterSort
import com.phoebe.app.domain.LocalMetadataOverride
import com.phoebe.app.domain.MediaProviderType
import com.phoebe.app.domain.MetadataOverrideSyncStatus
import com.phoebe.app.domain.SavedSearch
import com.phoebe.app.domain.SmartPlaylist
import com.phoebe.app.domain.TrackFilterSpec
import com.phoebe.app.domain.TrackMetadataUpdate
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerializationException
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString

@SingleIn(AppScope::class)
@Inject
class UserArtifactsRepository(
    private val database: PhoebeDatabase,
) {
    private val mutex = Mutex()
    private val mutableSmartPlaylists = MutableStateFlow<List<SmartPlaylist>>(emptyList())
    private val mutableSavedSearches = MutableStateFlow<List<SavedSearch>>(emptyList())
    private val mutableMetadataOverrides = MutableStateFlow<List<LocalMetadataOverride>>(emptyList())

    val smartPlaylists: StateFlow<List<SmartPlaylist>> = mutableSmartPlaylists.asStateFlow()
    val savedSearches: StateFlow<List<SavedSearch>> = mutableSavedSearches.asStateFlow()
    val metadataOverrides: StateFlow<List<LocalMetadataOverride>> = mutableMetadataOverrides.asStateFlow()

    suspend fun restore() {
        withContext(Dispatchers.Default) {
            mutex.withLock {
                mutableSmartPlaylists.value = database.userArtifactsQueries
                    .selectSmartPlaylists()
                    .awaitAsList()
                    .mapNotNull { row -> row.toSmartPlaylistOrNull() }
                mutableSavedSearches.value = database.userArtifactsQueries
                    .selectSavedSearches()
                    .awaitAsList()
                    .mapNotNull { row -> row.toSavedSearchOrNull() }
                mutableMetadataOverrides.value = database.userArtifactsQueries
                    .selectLocalMetadataOverrides()
                    .awaitAsList()
                    .mapNotNull { row -> row.toLocalMetadataOverrideOrNull() }
            }
        }
    }

    suspend fun upsertSmartPlaylist(playlist: SmartPlaylist) {
        withContext(Dispatchers.Default) {
            mutex.withLock {
                upsertSmartPlaylistLocked(playlist)
                restoreSmartPlaylistsLocked()
            }
        }
    }

    suspend fun updateSmartPlaylist(id: String, updatedAtMs: Long, transform: (SmartPlaylist) -> SmartPlaylist): SmartPlaylist? =
        withContext(Dispatchers.Default) {
            mutex.withLock {
                val existing = mutableSmartPlaylists.value.firstOrNull { it.id == id }
                    ?: database.userArtifactsQueries
                        .selectSmartPlaylists()
                        .awaitAsList()
                        .mapNotNull { row -> row.toSmartPlaylistOrNull() }
                        .firstOrNull { it.id == id }
                    ?: return@withLock null
                val updated = transform(existing).copy(id = existing.id, createdAtMs = existing.createdAtMs, updatedAtMs = updatedAtMs)
                upsertSmartPlaylistLocked(updated)
                restoreSmartPlaylistsLocked()
                updated
            }
        }

    suspend fun duplicateSmartPlaylist(id: String, nowMs: Long, suffix: String = nowMs.toString()): SmartPlaylist? =
        withContext(Dispatchers.Default) {
            mutex.withLock {
                val existing = mutableSmartPlaylists.value.firstOrNull { it.id == id }
                    ?: database.userArtifactsQueries
                        .selectSmartPlaylists()
                        .awaitAsList()
                        .mapNotNull { row -> row.toSmartPlaylistOrNull() }
                        .firstOrNull { it.id == id }
                    ?: return@withLock null
                val duplicate = existing.copy(
                    id = "${SmartPlaylist.IdPrefix}$suffix",
                    title = "${existing.title} Copy",
                    createdAtMs = nowMs,
                    updatedAtMs = nowMs,
                    enabled = true,
                )
                upsertSmartPlaylistLocked(duplicate)
                restoreSmartPlaylistsLocked()
                duplicate
            }
        }

    suspend fun deleteSmartPlaylist(id: String) {
        withContext(Dispatchers.Default) {
            mutex.withLock {
                database.userArtifactsQueries.deleteSmartPlaylist(id)
                mutableSmartPlaylists.value = mutableSmartPlaylists.value.filterNot { it.id == id }
            }
        }
    }

    private suspend fun upsertSmartPlaylistLocked(playlist: SmartPlaylist) {
        database.userArtifactsQueries.upsertSmartPlaylist(
            id = playlist.id,
            title = playlist.title,
            filterSpec = PhoebeDataJson.encodeToString(playlist.filter),
            sortSpec = PhoebeDataJson.encodeToString(playlist.sort),
            trackLimit = playlist.limit?.toLong(),
            createdAtMs = playlist.createdAtMs,
            updatedAtMs = playlist.updatedAtMs,
            enabled = playlist.enabled.toDb(),
        )
    }

    private suspend fun restoreSmartPlaylistsLocked() {
        mutableSmartPlaylists.value = database.userArtifactsQueries
            .selectSmartPlaylists()
            .awaitAsList()
            .mapNotNull { row -> row.toSmartPlaylistOrNull() }
    }

    suspend fun upsertSavedSearch(search: SavedSearch) {
        withContext(Dispatchers.Default) {
            mutex.withLock {
                database.userArtifactsQueries.upsertSavedSearch(
                    id = search.id,
                    title = search.title,
                    querySpec = PhoebeDataJson.encodeToString(search.query),
                    createdAtMs = search.createdAtMs,
                    updatedAtMs = search.updatedAtMs,
                )
                mutableSavedSearches.value = database.userArtifactsQueries
                    .selectSavedSearches()
                    .awaitAsList()
                    .mapNotNull { row -> row.toSavedSearchOrNull() }
            }
        }
    }

    suspend fun deleteSavedSearch(id: String) {
        withContext(Dispatchers.Default) {
            mutex.withLock {
                database.userArtifactsQueries.deleteSavedSearch(id)
                mutableSavedSearches.value = mutableSavedSearches.value.filterNot { it.id == id }
            }
        }
    }

    suspend fun upsertMetadataOverride(override: LocalMetadataOverride) {
        withContext(Dispatchers.Default) {
            mutex.withLock {
                database.userArtifactsQueries.upsertLocalMetadataOverride(
                    trackId = override.trackId,
                    updateSpec = PhoebeDataJson.encodeToString(override.update),
                    providerType = override.providerType?.name,
                    syncStatus = override.syncStatus.name,
                    updatedAtMs = override.updatedAtMs,
                )
                mutableMetadataOverrides.value = database.userArtifactsQueries
                    .selectLocalMetadataOverrides()
                    .awaitAsList()
                    .mapNotNull { row -> row.toLocalMetadataOverrideOrNull() }
            }
        }
    }

    suspend fun deleteMetadataOverride(trackId: String) {
        withContext(Dispatchers.Default) {
            mutex.withLock {
                database.userArtifactsQueries.deleteLocalMetadataOverride(trackId)
                mutableMetadataOverrides.value = mutableMetadataOverrides.value.filterNot { it.trackId == trackId }
            }
        }
    }

    suspend fun clearUserArtifacts() {
        withContext(Dispatchers.Default) {
            mutex.withLock {
                database.transaction {
                    database.userArtifactsQueries.clearSmartPlaylists()
                    database.userArtifactsQueries.clearSavedSearches()
                    database.userArtifactsQueries.clearLocalMetadataOverrides()
                }
                mutableSmartPlaylists.value = emptyList()
                mutableSavedSearches.value = emptyList()
                mutableMetadataOverrides.value = emptyList()
            }
        }
    }

    fun resetInMemoryState() {
        mutableSmartPlaylists.value = emptyList()
        mutableSavedSearches.value = emptyList()
        mutableMetadataOverrides.value = emptyList()
    }

    private fun SmartPlaylistRow.toSmartPlaylistOrNull(): SmartPlaylist? =
        try {
            SmartPlaylist(
                id = id,
                title = title,
                filter = PhoebeDataJson.decodeFromString<TrackFilterSpec>(filterSpec),
                sort = PhoebeDataJson.decodeFromString<FilterSort>(sortSpec),
                limit = trackLimit?.toInt(),
                createdAtMs = createdAtMs,
                updatedAtMs = updatedAtMs,
                enabled = enabled.toBool(),
            )
        } catch (_: SerializationException) {
            null
        } catch (_: IllegalArgumentException) {
            null
        }

    private fun SavedSearchRow.toSavedSearchOrNull(): SavedSearch? =
        try {
            SavedSearch(
                id = id,
                title = title,
                query = PhoebeDataJson.decodeFromString<AdvancedSearchQuery>(querySpec),
                createdAtMs = createdAtMs,
                updatedAtMs = updatedAtMs,
            )
        } catch (_: SerializationException) {
            null
        } catch (_: IllegalArgumentException) {
            null
        }

    private fun LocalMetadataOverrideRow.toLocalMetadataOverrideOrNull(): LocalMetadataOverride? =
        try {
            LocalMetadataOverride(
                trackId = trackId,
                update = PhoebeDataJson.decodeFromString<TrackMetadataUpdate>(updateSpec),
                providerType = providerType?.let { MediaProviderType.valueOf(it) },
                syncStatus = runCatching { MetadataOverrideSyncStatus.valueOf(syncStatus) }
                    .getOrDefault(MetadataOverrideSyncStatus.LocalOnly),
                updatedAtMs = updatedAtMs,
            )
        } catch (_: SerializationException) {
            null
        } catch (_: IllegalArgumentException) {
            null
        }
}

private fun Boolean.toDb(): Long = if (this) 1L else 0L
private fun Long.toBool(): Boolean = this != 0L
