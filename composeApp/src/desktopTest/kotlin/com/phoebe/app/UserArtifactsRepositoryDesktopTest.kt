package com.phoebe.app

import app.cash.sqldelight.db.SqlDriver
import com.phoebe.app.data.AppSettingsRepository
import com.phoebe.app.data.BackupRestoreMode
import com.phoebe.app.data.ImportExportService
import com.phoebe.app.data.UserArtifactsRepository
import com.phoebe.app.domain.AdvancedSearchQuery
import com.phoebe.app.domain.AudioProcessingSettings
import com.phoebe.app.domain.DownloadPolicySettings
import com.phoebe.app.domain.FilterField
import com.phoebe.app.domain.FilterOperator
import com.phoebe.app.domain.FilterSort
import com.phoebe.app.domain.LocalMetadataOverride
import com.phoebe.app.domain.MediaProviderType
import com.phoebe.app.domain.MetadataOverrideSyncStatus
import com.phoebe.app.domain.SavedSearch
import com.phoebe.app.domain.SmartPlaylist
import com.phoebe.app.domain.TrackFilterRule
import com.phoebe.app.domain.TrackFilterSpec
import com.phoebe.app.domain.TrackMetadataUpdate
import com.phoebe.app.testing.newInMemoryPhoebeDatabase
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class UserArtifactsRepositoryDesktopTest {
    private var driver: SqlDriver? = null

    @After
    fun cleanup() {
        driver?.close()
        driver = null
    }

    @Test
    fun userArtifactsPersistAndRestore() = runTest {
        val (db, d) = newInMemoryPhoebeDatabase()
        driver = d
        val repository = UserArtifactsRepository(db)

        repository.upsertSmartPlaylist(
            SmartPlaylist(
                id = "${SmartPlaylist.IdPrefix}lossless",
                title = "Lossless",
                filter = TrackFilterSpec(
                    rules = listOf(TrackFilterRule(FilterField.Codec, FilterOperator.Equals, "flac")),
                ),
                sort = FilterSort(FilterField.Year, ascending = false),
                limit = 25,
                createdAtMs = 1L,
                updatedAtMs = 2L,
            ),
        )
        repository.upsertSavedSearch(
            SavedSearch(
                id = "saved:lossless",
                title = "Lossless search",
                query = AdvancedSearchQuery(
                    text = "live",
                    filter = TrackFilterSpec(
                        rules = listOf(TrackFilterRule(FilterField.Downloaded, FilterOperator.IsTrue)),
                    ),
                ),
                createdAtMs = 3L,
                updatedAtMs = 4L,
            ),
        )
        repository.upsertMetadataOverride(
            LocalMetadataOverride(
                trackId = "navidrome:track:1",
                update = TrackMetadataUpdate(
                    trackId = "navidrome:track:1",
                    title = "Corrected",
                    artist = "Artist",
                    album = "Album",
                    genre = "Jazz",
                ),
                providerType = MediaProviderType.Navidrome,
                syncStatus = MetadataOverrideSyncStatus.ProviderUnsupported,
                updatedAtMs = 5L,
            ),
        )

        val restored = UserArtifactsRepository(db).apply { restore() }

        assertEquals("Lossless", restored.smartPlaylists.value.single().title)
        assertEquals(25, restored.smartPlaylists.value.single().limit)
        assertEquals(FilterField.Year, restored.smartPlaylists.value.single().sort.field)
        assertEquals("live", restored.savedSearches.value.single().query.text)
        assertEquals("Corrected", restored.metadataOverrides.value.single().update.title)
        assertEquals(MetadataOverrideSyncStatus.ProviderUnsupported, restored.metadataOverrides.value.single().syncStatus)
    }

    @Test
    fun smartPlaylistUpdateAndDuplicatePersist() = runTest {
        val (db, d) = newInMemoryPhoebeDatabase()
        driver = d
        val repository = UserArtifactsRepository(db)
        repository.upsertSmartPlaylist(smartPlaylist("one"))

        repository.updateSmartPlaylist("${SmartPlaylist.IdPrefix}one", updatedAtMs = 20L) {
            it.copy(title = "Renamed", enabled = false)
        }
        val duplicate = repository.duplicateSmartPlaylist("${SmartPlaylist.IdPrefix}one", nowMs = 30L, suffix = "two")

        assertEquals("Renamed", repository.smartPlaylists.value.first { it.id == "${SmartPlaylist.IdPrefix}one" }.title)
        assertEquals(20L, repository.smartPlaylists.value.first { it.id == "${SmartPlaylist.IdPrefix}one" }.updatedAtMs)
        assertEquals("${SmartPlaylist.IdPrefix}two", duplicate?.id)
        assertEquals("Renamed Copy", duplicate?.title)

        val restored = UserArtifactsRepository(db).apply { restore() }
        assertEquals(
            listOf("${SmartPlaylist.IdPrefix}one", "${SmartPlaylist.IdPrefix}two"),
            restored.smartPlaylists.value.map { it.id },
        )
    }

    @Test
    fun backupRestorePreviewMergeAndReplaceUserArtifacts() = runTest {
        val (db, d) = newInMemoryPhoebeDatabase()
        driver = d
        val settingsRepository = AppSettingsRepository(db)
        val artifactsRepository = UserArtifactsRepository(db)
        val service = ImportExportService(settingsRepository, artifactsRepository)

        settingsRepository.setDownloadPolicySettings(DownloadPolicySettings(wifiOnly = true, maxConcurrentDownloads = 2))
        settingsRepository.setAudioProcessingSettings(
            AudioProcessingSettings(gaplessEnabled = false, crossfeedEnabled = true, crossfeedAmount = 0.6f),
        )
        artifactsRepository.upsertSmartPlaylist(smartPlaylist("one"))
        artifactsRepository.upsertSavedSearch(savedSearch("one"))
        artifactsRepository.upsertMetadataOverride(metadataOverride("one"))

        val payload = service.exportBackupPackage()
        assertEquals(1, service.previewBackupPackage(payload).smartPlaylistCount)

        artifactsRepository.upsertSmartPlaylist(smartPlaylist("two"))
        assertEquals(2, artifactsRepository.smartPlaylists.value.size)

        service.restoreBackupPackage(payload, BackupRestoreMode.Replace)

        assertEquals(listOf("${SmartPlaylist.IdPrefix}one"), artifactsRepository.smartPlaylists.value.map { it.id })
        assertEquals("saved:one", artifactsRepository.savedSearches.value.single().id)
        assertEquals("track:one", artifactsRepository.metadataOverrides.value.single().trackId)
        assertTrue(settingsRepository.settings.value.downloadPolicy.wifiOnly)
        assertEquals(2, settingsRepository.settings.value.downloadPolicy.maxConcurrentDownloads)
        assertFalse(settingsRepository.settings.value.audioProcessing.gaplessEnabled)
        assertTrue(settingsRepository.settings.value.audioProcessing.crossfeedEnabled)
        assertEquals(0.6f, settingsRepository.settings.value.audioProcessing.crossfeedAmount)
    }

    @Test
    fun backupPreviewAcceptsLegacySeatGeekEventProvider() = runTest {
        val (db, d) = newInMemoryPhoebeDatabase()
        driver = d
        val settingsRepository = AppSettingsRepository(db)
        val artifactsRepository = UserArtifactsRepository(db)
        val service = ImportExportService(settingsRepository, artifactsRepository)
        artifactsRepository.upsertSmartPlaylist(smartPlaylist("legacy-events"))

        val payload = service.exportBackupPackage()
            .replace("\"provider\":\"Ticketmaster\"", "\"provider\":\"SeatGeek\"")

        assertEquals(1, service.previewBackupPackage(payload).smartPlaylistCount)
    }

    @Test
    fun invalidArtifactJsonIsSkippedDuringRestore() = runTest {
        val (db, d) = newInMemoryPhoebeDatabase()
        driver = d

        d.execute(
            identifier = null,
            sql = """
                INSERT INTO SmartPlaylistRow(
                    id,
                    title,
                    filterSpec,
                    sortSpec,
                    trackLimit,
                    createdAtMs,
                    updatedAtMs,
                    enabled
                ) VALUES (
                    'smart:playlist:bad',
                    'Bad',
                    '{broken',
                    '{}',
                    NULL,
                    1,
                    1,
                    1
                )
            """.trimIndent(),
            parameters = 0,
        )

        val repository = UserArtifactsRepository(db).apply { restore() }

        assertTrue(repository.smartPlaylists.value.isEmpty())
        assertFalse(repository.smartPlaylists.value.any { it.id == "${SmartPlaylist.IdPrefix}bad" })
    }

    private fun smartPlaylist(suffix: String): SmartPlaylist =
        SmartPlaylist(
            id = "${SmartPlaylist.IdPrefix}$suffix",
            title = "Smart $suffix",
            filter = TrackFilterSpec(
                rules = listOf(TrackFilterRule(FilterField.Rating, FilterOperator.GreaterThanOrEquals, "4")),
            ),
            createdAtMs = 10L,
            updatedAtMs = 11L,
        )

    private fun savedSearch(suffix: String): SavedSearch =
        SavedSearch(
            id = "saved:$suffix",
            title = "Saved $suffix",
            query = AdvancedSearchQuery(text = suffix),
            createdAtMs = 12L,
            updatedAtMs = 13L,
        )

    private fun metadataOverride(suffix: String): LocalMetadataOverride =
        LocalMetadataOverride(
            trackId = "track:$suffix",
            update = TrackMetadataUpdate(
                trackId = "track:$suffix",
                title = "Title $suffix",
                artist = "Artist $suffix",
                album = "Album $suffix",
            ),
            providerType = MediaProviderType.MusicAssistant,
            syncStatus = MetadataOverrideSyncStatus.LocalOnly,
            updatedAtMs = 14L,
        )
}
