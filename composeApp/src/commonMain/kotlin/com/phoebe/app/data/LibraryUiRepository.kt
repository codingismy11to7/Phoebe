package com.phoebe.app.data

import app.cash.sqldelight.async.coroutines.awaitAsOneOrNull
import com.phoebe.app.db.PhoebeDatabase
import com.phoebe.app.domain.LibraryColumnVisibility
import com.phoebe.app.domain.LibrarySortBy
import com.phoebe.app.domain.LibraryUiPreferences
import com.phoebe.app.platform.PlatformStorage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext

class LibraryUiRepository(
    private val database: PhoebeDatabase,
    private val storage: PlatformStorage,
) {
    private val json = PlexClient.PlexJson
    private val mutableState = MutableStateFlow(LibraryUiPreferences())
    val preferences: StateFlow<LibraryUiPreferences> = mutableState.asStateFlow()

    /**
     * Loads preferences from SQLite. On first run after the SQLDelight migration, falls back to
     * the legacy JSON file (and imports its values into SQLite before deleting the file).
     */
    suspend fun restore() {
        val row = withContext(Dispatchers.Default) {
            database.libraryPrefsQueries.selectCurrent().awaitAsOneOrNull()
        }
        if (row != null) {
            mutableState.value = row.toPreferences()
            return
        }
        val legacy = storage.readText(LegacyPrefsFile) ?: return
        val parsed = runCatching {
            json.decodeFromString<LibraryUiPreferences>(legacy)
        }.getOrNull() ?: return
        withContext(Dispatchers.Default) { persist(parsed) }
        mutableState.value = parsed
        storage.delete(LegacyPrefsFile)
    }

    suspend fun setSortBy(sortBy: LibrarySortBy) {
        save(mutableState.value.copy(sortBy = sortBy))
    }

    suspend fun setAscending(ascending: Boolean) {
        save(mutableState.value.copy(ascending = ascending))
    }

    suspend fun setColumns(columns: LibraryColumnVisibility) {
        applyColumns(columns)
        persistCurrentToDisk()
    }

    /** Updates UI state immediately; pair with [persistCurrentToDisk] on a background coroutine. */
    fun applyColumns(columns: LibraryColumnVisibility) {
        mutableState.value = mutableState.value.copy(columns = columns)
    }

    suspend fun persistCurrentToDisk() {
        withContext(Dispatchers.Default) { persist(mutableState.value) }
    }

    private suspend fun save(prefs: LibraryUiPreferences) {
        mutableState.value = prefs
        withContext(Dispatchers.Default) { persist(prefs) }
    }

    private suspend fun persist(prefs: LibraryUiPreferences) {
        val c = prefs.columns
        database.libraryPrefsQueries.upsert(
            sortBy = prefs.sortBy.name,
            ascending = prefs.ascending.toDb(),
            colYear = c.year.toDb(),
            colGenre = c.genre.toDb(),
            colFilepath = c.filepath.toDb(),
            colAudioCodec = c.audioCodec.toDb(),
            colBitrate = c.bitrate.toDb(),
            colDuration = c.duration.toDb(),
            colSampleRate = c.sampleRate.toDb(),
            colFileType = c.fileType.toDb(),
            colDateAdded = c.dateAdded.toDb(),
        )
    }

    private fun com.phoebe.app.db.LibraryPrefsRow.toPreferences(): LibraryUiPreferences =
        LibraryUiPreferences(
            sortBy = runCatching { LibrarySortBy.valueOf(sortBy) }.getOrDefault(LibrarySortBy.Name),
            ascending = ascending.toBool(),
            columns = LibraryColumnVisibility(
                year = colYear.toBool(),
                genre = colGenre.toBool(),
                filepath = colFilepath.toBool(),
                audioCodec = colAudioCodec.toBool(),
                bitrate = colBitrate.toBool(),
                duration = colDuration.toBool(),
                sampleRate = colSampleRate.toBool(),
                fileType = colFileType.toBool(),
                dateAdded = colDateAdded.toBool(),
            ),
        )

    private companion object {
        const val LegacyPrefsFile = "library_ui_prefs.json"
    }
}

private fun Boolean.toDb(): Long = if (this) 1L else 0L
private fun Long.toBool(): Boolean = this != 0L
