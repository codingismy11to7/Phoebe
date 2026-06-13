package com.phoebe.app.data

import com.phoebe.app.domain.LyricsLine

private val timestampPattern = Regex("""\[(\d{1,3}):(\d{2})(?:[.:](\d{1,3}))?]""")
private val metadataOnlyPattern = Regex("""^\[[A-Za-z]+:.*]$""")

fun parseLyricsLines(raw: String): List<LyricsLine> {
    val normalized = raw.replace("\r\n", "\n").replace('\r', '\n')
    val parsed = normalized.lineSequence()
        .flatMap { line ->
            val matches = timestampPattern.findAll(line).toList()
            if (matches.isEmpty()) {
                val trimmed = line.trim()
                if (trimmed.isBlank() || metadataOnlyPattern.matches(trimmed)) {
                    emptySequence()
                } else {
                    sequenceOf(LyricsLine(startMs = null, text = trimmed))
                }
            } else {
                val text = timestampPattern.replace(line, "").trim()
                val timed = matches.mapNotNull { match ->
                    val startMs = match.toTimestampMs() ?: return@mapNotNull null
                    LyricsLine(startMs = startMs, text = text)
                }
                if (timed.isEmpty()) {
                    sequenceOf(LyricsLine(startMs = null, text = line.trim()))
                } else {
                    timed.asSequence()
                }
            }
        }
        .toList()

    return parsed.sortedWith(
        compareBy<LyricsLine> { it.startMs == null }
            .thenBy { it.startMs ?: Long.MAX_VALUE },
    )
}

fun lyricsAreSynced(lines: List<LyricsLine>): Boolean = lines.any { it.startMs != null }

private fun MatchResult.toTimestampMs(): Long? {
    val minutes = groupValues.getOrNull(1)?.toLongOrNull() ?: return null
    val seconds = groupValues.getOrNull(2)?.toLongOrNull() ?: return null
    if (seconds !in 0..59) return null
    val fraction = groupValues.getOrNull(3).orEmpty()
    val fractionMs = when (fraction.length) {
        0 -> 0L
        1 -> fraction.toLongOrNull()?.times(100L)
        2 -> fraction.toLongOrNull()?.times(10L)
        else -> fraction.take(3).toLongOrNull()
    } ?: return null
    return minutes * 60_000L + seconds * 1_000L + fractionMs
}
