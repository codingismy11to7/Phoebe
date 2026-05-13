package com.phoebe.app.data

import com.phoebe.app.domain.PlexServer

/**
 * Ordered Plex server base URLs — Plex-advertised LAN first, synthesized fallbacks last.
 *
 * Plex often advertises `https://172-105-8-66.<token>.plex.direct:8443` alongside
 * `http://192.168.x.x:32400`. We synthesize `http://172.105.8.66:32400` from the plex.direct
 * hostname, but that address is usually the server's *public* IP and is often unreachable on
 * LAN; real local URLs from Plex must win.
 */
fun PlexServer.reachableBaseUris(preferredFirst: String? = null): List<String> {
    val advertised = advertisedConnectionUris.ifEmpty { connectionUris }
    val expanded = when {
        advertised.isNotEmpty() -> expandConnectionUris(advertised)
        uri.isNotBlank() -> listOf(uri.trimEnd('/'))
        else -> emptyList()
    }
    val advertisedSet = advertised.toSet()
    val localSet = localConnectionUris.toSet()
    val ordered = expanded.sortedWith(
        compareBy(
            { it !in localSet },
            { it !in advertisedSet },
            { connectionPriority(it) },
        ),
    )
    val withPreferred = listOfNotNull(preferredFirst?.trimEnd('/')) +
        ordered.filter { it != preferredFirst?.trimEnd('/') }
    return if (httpsRequired) {
        withPreferred.sortedBy { if (it.startsWith("https://")) 0 else 1 }
    } else {
        withPreferred
    }
}

/** @see reachableBaseUris */
fun PlexServer.timelineBaseUris(preferredFirst: String? = null): List<String> =
    reachableBaseUris(preferredFirst)

fun bestReachableBaseUri(
    advertisedUris: List<String>,
    localUris: List<String> = emptyList(),
    httpsRequired: Boolean = false,
): String? {
    val server = PlexServer(
        id = "",
        name = "",
        uri = advertisedUris.firstOrNull().orEmpty(),
        owned = false,
        connectionUris = expandConnectionUris(advertisedUris),
        advertisedConnectionUris = advertisedUris,
        localConnectionUris = localUris,
        httpsRequired = httpsRequired,
    )
    return server.reachableBaseUris().firstOrNull()
}

/** Advertised URLs first, then synthesized plain-IP fallbacks derived from plex.direct hosts. */
fun expandConnectionUris(advertisedUris: List<String>): List<String> =
    buildList {
        val advertised = advertisedUris.map { it.trimEnd('/') }.filter { it.isNotBlank() }
        addAll(advertised)
        for (uri in advertised) {
            val port = uri.substringAfter("://").substringAfter(':', "").substringBefore('/').toIntOrNull()
            decodedIpFromPlexDirect(uri)?.let { ip ->
                add("http://$ip:32400")
                add("https://$ip:32400")
                if (port == 8443) add("https://$ip:8443")
            }
        }
    }.distinct()

/** e.g. `172-105-8-66.<hash>.plex.direct` → `172.105.8.66` (Plex's encoded WAN address). */
internal fun decodedIpFromPlexDirect(uri: String): String? {
    val host = uri.substringAfter("://").substringBefore(':').substringBefore('/').lowercase()
    if (!host.endsWith(".plex.direct")) return null
    val dashed = host.substringBefore('.')
    val parts = dashed.split('-')
    if (parts.size != 4 || parts.any { it.toIntOrNull() !in 0..255 }) return null
    return parts.joinToString(".")
}

internal fun connectionPriority(uri: String): Int {
    if (uri.contains(".plex.direct", ignoreCase = true)) return 100
    val port = uri.substringAfter("://").substringAfter(':', "32400").substringBefore('/').toIntOrNull() ?: 32400
    val secure = uri.startsWith("https://")
    return when {
        !secure && port == 32400 -> 0
        secure && port == 32400 -> 10
        secure && port == 8443 -> 20
        !secure -> 30
        else -> 40
    }
}
