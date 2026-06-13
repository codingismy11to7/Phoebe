package com.phoebe.app.ui

import com.phoebe.app.domain.Track

fun displayFileTypeLabel(track: Track): String {
    val path = track.filepath?.lowercase()
    if (!path.isNullOrBlank()) {
        val dot = path.lastIndexOf('.')
        if (dot > 0) {
            val ext = path.substring(dot)
            if (ext.length in 2..6) return ext
        }
    }
    return when (track.audioCodec?.uppercase()) {
        "FLAC" -> ".flac"
        "AAC", "ALAC" -> ".m4a"
        "MP3" -> ".mp3"
        "OGG", "VORBIS" -> ".ogg"
        else -> "—"
    }
}

fun displayBitrateLabel(track: Track): String {
    val codec = track.audioCodec?.uppercase()
    if (codec == "FLAC" || codec == "ALAC") return "Lossless"
    val k = track.bitrateKbps
    if (k != null && k > 0) return "$k kbps"
    return "—"
}

fun displaySampleRateLabel(track: Track): String {
    return if (isLossless(track)) "44.1 kHz" else "—"
}

fun isLossless(track: Track): Boolean {
    val c = track.audioCodec?.uppercase() ?: return false
    return c == "FLAC" || c == "ALAC" || c == "WAV" || c == "APE"
}
