package com.phoebe.app.data

import app.cash.sqldelight.async.coroutines.awaitAsOneOrNull
import com.phoebe.app.db.PhoebeDatabase
import com.phoebe.app.domain.LyricsDocument
import com.phoebe.app.domain.LyricsLoadState
import com.phoebe.app.domain.LyricsSource
import com.phoebe.app.domain.Track
import com.phoebe.app.platform.PhoebeLog
import com.phoebe.app.sources.LocalLibraryIO
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.parameter
import io.ktor.http.HttpStatusCode
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlin.math.roundToInt

class LyricsRepository(
    private val database: PhoebeDatabase,
    private val httpClient: HttpClient,
) {
    private val memoryCache = mutableMapOf<String, LyricsLoadState>()
    private val lookupMutex = Mutex()

    suspend fun lyricsFor(track: Track, forceRefresh: Boolean = false): LyricsLoadState = withContext(Dispatchers.Default) {
        val fingerprint = track.lyricsFingerprint()
        if (!forceRefresh) {
            memoryCache[fingerprint]?.let { return@withContext it }
        }
        lookupMutex.withLock {
            if (!forceRefresh) {
                memoryCache[fingerprint]?.let { return@withLock it }
            }
            val loaded = loadLyrics(track, fingerprint, forceRefresh)
            memoryCache[fingerprint] = loaded
            loaded
        }
    }

    private suspend fun loadLyrics(track: Track, fingerprint: String, forceRefresh: Boolean): LyricsLoadState {
        if (!forceRefresh) {
            cachedLyrics(fingerprint)?.let { return LyricsLoadState.Loaded(it) }
        }
        localLyrics(track, fingerprint)?.let { document ->
            cache(document, rawSynced = if (document.synced) document.lines.toLrcText() else null, rawPlain = document.lines.toPlainText())
            return LyricsLoadState.Loaded(document)
        }
        fetchLrclib(track, fingerprint)?.let { document ->
            cache(
                document = document,
                rawSynced = if (document.synced) document.lines.toLrcText() else null,
                rawPlain = document.lines.toPlainText(),
            )
            return LyricsLoadState.Loaded(document)
        }
        return LyricsLoadState.NotFound
    }

    private suspend fun cachedLyrics(fingerprint: String): LyricsDocument? {
        val row = runCatching {
            database.lyricsQueries.selectLyrics(fingerprint).awaitAsOneOrNull()
        }.onFailure { error ->
            PhoebeLog.d("LyricsRepository") { "lyrics cache read failed: ${error.message}" }
        }.getOrNull() ?: return null
        val raw = row.syncedLyrics ?: row.plainLyrics ?: return null
        val lines = parseLyricsLines(raw)
        return LyricsDocument(
            trackFingerprint = fingerprint,
            lines = lines,
            source = LyricsSource.Cache,
            synced = lyricsAreSynced(lines),
            instrumental = row.instrumental != 0L,
        )
    }

    private suspend fun localLyrics(track: Track, fingerprint: String): LyricsDocument? {
        val raw = LocalLibraryIO.readLyrics(track.localUri ?: return null)?.trim()?.takeIf { it.isNotBlank() } ?: return null
        val lines = parseLyricsLines(raw)
        if (lines.isEmpty()) return null
        return LyricsDocument(
            trackFingerprint = fingerprint,
            lines = lines,
            source = if (lyricsAreSynced(lines)) LyricsSource.LocalSidecar else LyricsSource.LocalEmbedded,
            synced = lyricsAreSynced(lines),
        )
    }

    private suspend fun fetchLrclib(track: Track, fingerprint: String): LyricsDocument? {
        val response = runCatching {
            httpClient.get("https://lrclib.net/api/get") {
                header("User-Agent", "Phoebe/1.0 (https://github.com)")
                parameter("track_name", track.title)
                parameter("artist_name", track.artist)
                parameter("album_name", track.album)
                if (track.durationMs > 0L) {
                    parameter("duration", (track.durationMs / 1000.0).roundToInt())
                }
            }
        }.getOrElse { error ->
            PhoebeLog.d("LyricsRepository") { "LRCLIB request failed: ${error.message}" }
            return null
        }
        if (response.status == HttpStatusCode.NotFound) return null
        if (response.status.value !in 200..299) {
            PhoebeLog.d("LyricsRepository") { "LRCLIB returned HTTP ${response.status.value}" }
            return null
        }
        val dto = runCatching { response.body<LrclibLyricsResponse>() }.getOrNull() ?: return null
        if (dto.instrumental) {
            return LyricsDocument(
                trackFingerprint = fingerprint,
                lines = emptyList(),
                source = LyricsSource.Lrclib,
                synced = false,
                instrumental = true,
            )
        }
        val raw = dto.syncedLyrics?.takeIf { it.isNotBlank() }
            ?: dto.plainLyrics?.takeIf { it.isNotBlank() }
            ?: return null
        val lines = parseLyricsLines(raw)
        if (lines.isEmpty()) return null
        return LyricsDocument(
            trackFingerprint = fingerprint,
            lines = lines,
            source = LyricsSource.Lrclib,
            synced = lyricsAreSynced(lines),
        )
    }

    private suspend fun cache(document: LyricsDocument, rawSynced: String?, rawPlain: String?) {
        runCatching {
            database.lyricsQueries.upsertLyrics(
                trackFingerprint = document.trackFingerprint,
                source = document.source.name,
                syncedLyrics = rawSynced,
                plainLyrics = rawPlain,
                instrumental = if (document.instrumental) 1L else 0L,
            )
        }.onFailure { error ->
            PhoebeLog.d("LyricsRepository") { "lyrics cache write failed: ${error.message}" }
        }
    }
}

fun Track.lyricsFingerprint(): String =
    listOf(
        id.takeIf { it.isNotBlank() },
        title.normalizedLyricsKey(),
        artist.normalizedLyricsKey(),
        album.normalizedLyricsKey(),
        durationMs.takeIf { it > 0L }?.toString(),
    ).filterNotNull().joinToString("|")

private fun String.normalizedLyricsKey(): String =
    trim().lowercase().replace(Regex("""\s+"""), " ")

private fun List<com.phoebe.app.domain.LyricsLine>.toPlainText(): String =
    joinToString("\n") { it.text }

private fun List<com.phoebe.app.domain.LyricsLine>.toLrcText(): String =
    joinToString("\n") { line ->
        val startMs = line.startMs ?: 0L
        val minutes = startMs / 60_000L
        val seconds = (startMs % 60_000L) / 1_000L
        val centiseconds = (startMs % 1_000L) / 10L
        "[${minutes.toString().padStart(2, '0')}:${seconds.toString().padStart(2, '0')}.${centiseconds.toString().padStart(2, '0')}] ${line.text}"
    }

@Serializable
private data class LrclibLyricsResponse(
    val id: Long? = null,
    val name: String? = null,
    val instrumental: Boolean = false,
    @SerialName("plainLyrics") val plainLyrics: String? = null,
    @SerialName("syncedLyrics") val syncedLyrics: String? = null,
)
