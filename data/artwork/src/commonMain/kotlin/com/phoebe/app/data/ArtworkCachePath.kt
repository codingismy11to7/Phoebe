package com.phoebe.app.data

fun cachedArtworkPathForUrl(url: String): String {
    val extension = url.substringBefore('?')
        .substringAfterLast('/', "")
        .substringAfterLast('.', "")
        .takeIf { it.length in 2..5 && it.all { c -> c.isLetterOrDigit() } }
        ?: "jpg"
    return "artwork/cache-${url.stableArtworkHash()}.$extension"
}

private fun String.stableArtworkHash(): String {
    var hash = 1125899906842597L
    forEach { c -> hash = (hash * 31) + c.code }
    return hash.toString()
}
