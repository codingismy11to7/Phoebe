package com.phoebe.app.data

import app.cash.sqldelight.async.coroutines.awaitAsList
import com.phoebe.app.db.PhoebeDatabase
import com.phoebe.app.domain.LocalFolderMediaSourceConfig
import com.phoebe.app.domain.MediaSourcesState
import com.phoebe.app.platform.PhoebeLog
import com.phoebe.app.platform.PlatformStorage
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import kotlin.random.Random

@SingleIn(AppScope::class)
@Inject
class MediaSourcesRepository(
    private val database: PhoebeDatabase,
    private val storage: PlatformStorage,
) {
    private val json = PhoebeDataJson
    private val mutableState = MutableStateFlow(MediaSourcesState())
    val state: StateFlow<MediaSourcesState> = mutableState.asStateFlow()

    suspend fun restore() {
        PhoebeLog.d("MediaSourcesRepository") { "restore" }
        val rows = withContext(Dispatchers.Default) {
            database.mediaSourcesQueries.selectAll().awaitAsList()
        }
        if (rows.isNotEmpty()) {
            mutableState.value = MediaSourcesState(rows.map { it.toConfig() })
            PhoebeLog.d("MediaSourcesRepository") { "restore from DB → ${rows.size} local folders" }
            return
        }
        val legacy = storage.readText(LegacySourcesFile) ?: return
        val parsed = runCatching {
            json.decodeFromString<MediaSourcesState>(legacy)
        }.getOrNull() ?: return
        withContext(Dispatchers.Default) {
            database.transaction {
                parsed.localFolders.forEach { folder ->
                    database.mediaSourcesQueries.insertOrReplace(
                        id = folder.id,
                        rootUri = folder.rootUri,
                        label = folder.label,
                        enabled = folder.enabled.toDb(),
                    )
                }
            }
        }
        mutableState.value = parsed
        storage.delete(LegacySourcesFile)
        PhoebeLog.d("MediaSourcesRepository") { "restore from legacy file → ${parsed.localFolders.size} local folders" }
    }

    suspend fun addLocalFolder(rootUri: String, label: String) {
        PhoebeLog.d("MediaSourcesRepository") { "addLocalFolder label='$label' uri=$rootUri" }
        val id = "lf-${(Random.nextLong() and Long.MAX_VALUE).toString(16)}"
        val cleanLabel = label.ifBlank { "Local folder" }
        database.mediaSourcesQueries.insertOrReplace(
            id = id,
            rootUri = rootUri,
            label = cleanLabel,
            enabled = 1L,
        )
        reload()
    }

    suspend fun removeLocalFolder(id: String) {
        PhoebeLog.d("MediaSourcesRepository") { "removeLocalFolder id=$id" }
        withContext(Dispatchers.Default) {
            database.transaction {
                database.mediaSourcesQueries.delete(id)
                database.catalogQueries.clearLocalFileMetadataCacheForFolder(id)
            }
            reload()
        }
    }

    suspend fun setLocalFolderEnabled(id: String, enabled: Boolean) {
        PhoebeLog.d("MediaSourcesRepository") { "setLocalFolderEnabled id=$id enabled=$enabled" }
        withContext(Dispatchers.Default) {
            database.mediaSourcesQueries.setEnabled(enabled.toDb(), id)
            reload()
        }
    }

    fun clearInMemoryState() {
        mutableState.value = MediaSourcesState()
    }

    private suspend fun reload() {
        val folders = database.mediaSourcesQueries.selectAll().awaitAsList().map { it.toConfig() }
        mutableState.value = MediaSourcesState(folders)
    }

    private fun com.phoebe.app.db.LocalFolderRow.toConfig(): LocalFolderMediaSourceConfig =
        LocalFolderMediaSourceConfig(
            id = id,
            rootUri = rootUri,
            label = label,
            enabled = enabled.toBool(),
        )

    private companion object {
        const val LegacySourcesFile = "media_sources.json"
    }
}

private fun Boolean.toDb(): Long = if (this) 1L else 0L
private fun Long.toBool(): Boolean = this != 0L
