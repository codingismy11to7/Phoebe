package com.phoebe.app.data

import app.cash.sqldelight.async.coroutines.awaitAsOneOrNull
import com.phoebe.app.db.PhoebeDatabase
import com.phoebe.app.domain.AppSettings
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext

class AppSettingsRepository(
    private val database: PhoebeDatabase,
) {
    private val mutableState = MutableStateFlow(AppSettings.Default)
    val settings: StateFlow<AppSettings> = mutableState.asStateFlow()

    suspend fun restore() {
        val row = withContext(Dispatchers.Default) {
            database.appSettingsQueries.selectCurrent().awaitAsOneOrNull()
        }
        mutableState.value = row?.toSettings() ?: AppSettings.Default
    }

    suspend fun setCrossfadeSeconds(seconds: Int) {
        save(mutableState.value.copy(crossfadeSeconds = seconds).normalized())
    }

    suspend fun setScanLibraryOnLaunch(enabled: Boolean) {
        save(mutableState.value.copy(scanLibraryOnLaunch = enabled).normalized())
    }

    suspend fun setNotifyWhenDownloadFinishes(enabled: Boolean) {
        save(mutableState.value.copy(notifyWhenDownloadFinishes = enabled).normalized())
    }

    fun resetInMemoryState() {
        mutableState.value = AppSettings.Default
    }

    private suspend fun save(settings: AppSettings) {
        val normalized = settings.normalized()
        mutableState.value = normalized
        withContext(Dispatchers.Default) {
            database.appSettingsQueries.upsert(
                crossfadeSeconds = normalized.crossfadeSeconds.toLong(),
                scanLibraryOnLaunch = normalized.scanLibraryOnLaunch.toDb(),
                notifyWhenDownloadFinishes = normalized.notifyWhenDownloadFinishes.toDb(),
            )
        }
    }

    private fun com.phoebe.app.db.AppSettingsRow.toSettings(): AppSettings =
        AppSettings(
            crossfadeSeconds = crossfadeSeconds.toInt(),
            scanLibraryOnLaunch = scanLibraryOnLaunch.toBool(),
            notifyWhenDownloadFinishes = notifyWhenDownloadFinishes.toBool(),
        ).normalized()
}

private fun Boolean.toDb(): Long = if (this) 1L else 0L
private fun Long.toBool(): Boolean = this != 0L
