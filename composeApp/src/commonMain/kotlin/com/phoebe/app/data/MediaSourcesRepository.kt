package com.phoebe.app.data

import app.cash.sqldelight.async.coroutines.awaitAsList
import com.phoebe.app.db.PhoebeDatabase
import com.phoebe.app.domain.LocalFolderMediaSourceConfig
import com.phoebe.app.domain.MediaSourcesState
import com.phoebe.app.platform.PlatformStorage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import kotlin.random.Random

class MediaSourcesRepository(
    private val database: PhoebeDatabase,
    private val storage: PlatformStorage,
) {
    private val json = PlexClient.PlexJson
    private val mutableState = MutableStateFlow(MediaSourcesState())
    val state: StateFlow<MediaSourcesState> = mutableState.asStateFlow()

    suspend fun restore() {
        val rows = withContext(Dispatchers.Default) {
            database.mediaSourcesQueries.selectAll().awaitAsList()
        }
        if (rows.isNotEmpty()) {
            mutableState.value = MediaSourcesState(rows.map { it.toConfig() })
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
    }

    suspend fun addLocalFolder(rootUri: String, label: String) {
        val id = "lf-${(Random.nextLong() and Long.MAX_VALUE).toString(16)}"
        val cleanLabel = label.ifBlank { "Local folder" }
        withContext(Dispatchers.Default) {
            database.mediaSourcesQueries.insertOrReplace(
                id = id,
                rootUri = rootUri,
                label = cleanLabel,
                enabled = 1L,
            )
            reload()
        }
    }

    suspend fun removeLocalFolder(id: String) {
        withContext(Dispatchers.Default) {
            database.mediaSourcesQueries.delete(id)
            reload()
        }
    }

    suspend fun setLocalFolderEnabled(id: String, enabled: Boolean) {
        withContext(Dispatchers.Default) {
            database.mediaSourcesQueries.setEnabled(enabled.toDb(), id)
            reload()
        }
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
