package com.phoebe.app.data

import com.phoebe.app.platform.PhoebeLog
import com.phoebe.app.platform.currentTimeMs

internal enum class CatalogSyncStepKind {
    Network,
    Disk,
    Memory,
    Other,
}

/**
 * Timestamped debug trace for catalog sync. Filter logcat with tag [CatalogSyncTrace].
 * Each line: `+{elapsedSinceStart}ms (+{stepMs}ms step) {KIND} {step} → {detail}`.
 */
internal class CatalogSyncTrace(
    private val sessionLabel: String,
) {
    private val startedAtMs = currentTimeMs()

    fun mark(step: String, kind: CatalogSyncStepKind, detail: String? = null) {
        PhoebeLog.d(TAG) { formatLine(stepMs = null, kind = kind, step = step, detail = detail) }
    }

    fun markDone(step: String, kind: CatalogSyncStepKind, stepStartMs: Long, detail: String? = null) {
        PhoebeLog.d(TAG) {
            formatLine(stepMs = currentTimeMs() - stepStartMs, kind = kind, step = step, detail = detail)
        }
    }

    fun markFailed(step: String, kind: CatalogSyncStepKind, stepStartMs: Long, error: Throwable) {
        PhoebeLog.d(TAG) {
            formatLine(
                stepMs = currentTimeMs() - stepStartMs,
                kind = kind,
                step = "$step FAILED",
                detail = error.message ?: error::class.simpleName,
            )
        }
    }

    suspend fun <T> network(step: String, detail: (T) -> String? = { null }, block: suspend () -> T): T =
        trace(CatalogSyncStepKind.Network, step, detail, block)

    suspend fun <T> disk(step: String, detail: (T) -> String? = { null }, block: suspend () -> T): T =
        trace(CatalogSyncStepKind.Disk, step, detail, block)

    suspend fun <T> memory(step: String, detail: (T) -> String? = { null }, block: suspend () -> T): T =
        trace(CatalogSyncStepKind.Memory, step, detail, block)

    suspend fun <T> other(step: String, detail: (T) -> String? = { null }, block: suspend () -> T): T =
        trace(CatalogSyncStepKind.Other, step, detail, block)

    private suspend fun <T> trace(
        kind: CatalogSyncStepKind,
        step: String,
        detail: (T) -> String?,
        block: suspend () -> T,
    ): T {
        val stepStartMs = currentTimeMs()
        mark("$step start", kind)
        return try {
            val result = block()
            markDone(step, kind, stepStartMs, detail(result))
            result
        } catch (error: Throwable) {
            markFailed(step, kind, stepStartMs, error)
            throw error
        }
    }

    private fun formatLine(
        stepMs: Long?,
        kind: CatalogSyncStepKind,
        step: String,
        detail: String?,
    ): String {
        val elapsed = currentTimeMs() - startedAtMs
        return buildString {
            append("+${elapsed}ms")
            if (stepMs != null) append(" (+${stepMs}ms step)")
            append(' ')
            append(kind.name.uppercase())
            append(' ')
            append(step)
            if (!detail.isNullOrBlank()) {
                append(" → ")
                append(detail)
            }
            append(" [")
            append(sessionLabel)
            append(']')
        }
    }

    companion object {
        private const val TAG = "CatalogSyncTrace"
    }
}

/** Builds user-facing metadata fetch message/detail while parallel Plex requests complete. */
internal data class MetadataFetchProgress(
    val message: String,
    val detail: String?,
)

internal fun metadataFetchProgress(
    albumsDone: Boolean,
    albumCount: Int,
    artistsDone: Boolean,
    artistCount: Int,
    playlistsDone: Boolean,
    playlistCount: Int,
): MetadataFetchProgress {
    val done = buildList {
        if (albumsDone) add("$albumCount albums")
        if (artistsDone) add("$artistCount artists")
        if (playlistsDone) add("$playlistCount playlists")
    }
    val waiting = buildList {
        if (!albumsDone) add("albums")
        if (!artistsDone) add("artists")
        if (!playlistsDone) add("playlists")
    }
    val message = when (waiting.size) {
        0 -> "Loaded library metadata"
        1 -> "Fetching ${waiting.single()}…"
        else -> "Fetching ${waiting.joinToString(", ")}…"
    }
    val detail = when {
        done.isEmpty() -> "From your Plex server"
        waiting.isEmpty() -> done.joinToString(" · ")
        else -> "${done.joinToString(" · ")} · waiting for ${waiting.joinToString(", ")}"
    }
    return MetadataFetchProgress(message = message, detail = detail)
}
