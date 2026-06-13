package com.phoebe.app.ui

/**
 * Render a "last played" timestamp as a short human-friendly label
 * ("Just now" / "5m ago" / "3h ago" / "Yesterday" / "3d ago" / "5w ago" /
 * "8mo ago" / "2y ago"). Returns "Never" for null timestamps.
 */
fun formatLastPlayed(playedAtMs: Long?, nowMs: Long): String {
    if (playedAtMs == null || playedAtMs <= 0L) return "Never"
    val delta = (nowMs - playedAtMs).coerceAtLeast(0L)
    val minute = 60_000L
    val hour = 60L * minute
    val day = 24L * hour
    val week = 7L * day
    val month = 30L * day
    val year = 365L * day
    return when {
        delta < minute -> "Just now"
        delta < hour -> "${delta / minute}m ago"
        delta < day -> "${delta / hour}h ago"
        delta < 2 * day -> "Yesterday"
        delta < week -> "${delta / day}d ago"
        delta < month -> "${delta / week}w ago"
        delta < year -> "${delta / month}mo ago"
        else -> "${delta / year}y ago"
    }
}

fun formatHoursMinutes(ms: Long): String {
    if (ms <= 0L) return "—"
    val totalMinutes = ms / 60_000L
    val hours = totalMinutes / 60
    val minutes = totalMinutes % 60
    return if (hours > 0) "${hours}h ${minutes}m" else "${minutes}m"
}
