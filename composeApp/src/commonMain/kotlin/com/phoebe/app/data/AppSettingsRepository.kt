package com.phoebe.app.data

import app.cash.sqldelight.async.coroutines.awaitAsOneOrNull
import com.phoebe.app.db.PhoebeDatabase
import com.phoebe.app.domain.AppSettings
import com.phoebe.app.domain.EqualizerProfile
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerializationException
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

class AppSettingsRepository(
    private val database: PhoebeDatabase,
) {
    private val mutableState = MutableStateFlow(AppSettings.Default)
    val settings: StateFlow<AppSettings> = mutableState.asStateFlow()
    private val saveMutex = Mutex()
    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    suspend fun restore() {
        val row = withContext(Dispatchers.Default) {
            database.appSettingsQueries.selectCurrent().awaitAsOneOrNull()
        }
        mutableState.value = row?.toSettings() ?: AppSettings.Default
    }

    suspend fun setCrossfadeSeconds(seconds: Int) {
        updateAndSave { current ->
            current.copy(crossfadeSeconds = seconds)
        }
    }

    suspend fun setScanLibraryOnLaunch(enabled: Boolean) {
        updateAndSave { current ->
            current.copy(scanLibraryOnLaunch = enabled)
        }
    }

    suspend fun setNotifyWhenDownloadFinishes(enabled: Boolean) {
        updateAndSave { current ->
            current.copy(notifyWhenDownloadFinishes = enabled)
        }
    }

    suspend fun setPersistEqualizerSettings(enabled: Boolean, currentProfile: EqualizerProfile? = null) {
        updateAndSave { current ->
            val profile = (currentProfile ?: current.equalizerProfile).normalized()
            current.copy(
                persistEqualizerSettings = enabled,
                equalizerProfile = profile,
            )
        }
    }

    suspend fun setEqualizerProfile(profile: EqualizerProfile) {
        updateAndSave { current ->
            current.copy(equalizerProfile = profile.normalized())
        }
    }

    fun resetInMemoryState() {
        mutableState.value = AppSettings.Default
    }

    private suspend fun updateAndSave(transform: (AppSettings) -> AppSettings) {
        withContext(NonCancellable + Dispatchers.Default) {
            saveMutex.withLock {
                val normalized = transform(mutableState.value).normalized()
                database.appSettingsQueries.upsert(
                    crossfadeSeconds = normalized.crossfadeSeconds.toLong(),
                    scanLibraryOnLaunch = normalized.scanLibraryOnLaunch.toDb(),
                    notifyWhenDownloadFinishes = normalized.notifyWhenDownloadFinishes.toDb(),
                    persistEqualizerSettings = normalized.persistEqualizerSettings.toDb(),
                    equalizerProfile = json.encodeToString(normalized.equalizerProfile),
                )
                mutableState.value = normalized
            }
        }
    }

    private fun com.phoebe.app.db.AppSettingsRow.toSettings(): AppSettings =
        AppSettings(
            crossfadeSeconds = crossfadeSeconds.toInt(),
            scanLibraryOnLaunch = scanLibraryOnLaunch.toBool(),
            notifyWhenDownloadFinishes = notifyWhenDownloadFinishes.toBool(),
            persistEqualizerSettings = persistEqualizerSettings.toBool(),
            equalizerProfile = decodeEqualizerProfile(equalizerProfile),
        ).normalized()

    private fun decodeEqualizerProfile(value: String): EqualizerProfile =
        try {
            json.decodeFromString<EqualizerProfile>(value).normalized()
        } catch (_: SerializationException) {
            EqualizerProfile.Default
        } catch (_: IllegalArgumentException) {
            EqualizerProfile.Default
        }
}

private fun Boolean.toDb(): Long = if (this) 1L else 0L
private fun Long.toBool(): Boolean = this != 0L
