package com.phoebe.app.sources

import com.phoebe.app.domain.Album
import com.phoebe.app.domain.Artist
import com.phoebe.app.domain.CatalogSnapshot
import com.phoebe.app.domain.Playlist
import com.phoebe.app.domain.Track

object CatalogMerge {
    private const val Sep = ":"

    fun withPrefix(prefix: String, snapshot: CatalogSnapshot): CatalogSnapshot {
        val p = "$prefix$Sep"
        return CatalogSnapshot(
            artists = snapshot.artists.map { it.copy(id = p + it.id) },
            albums = snapshot.albums.map { it.copy(id = p + it.id) },
            playlists = snapshot.playlists.map { it.copy(id = p + it.id) },
            tracksByParent = snapshot.tracksByParent.mapKeys { (k, _) -> p + k }.mapValues { (_, tracks) ->
                tracks.map { t -> t.copy(id = p + t.id) }
            },
            collectionValues = snapshot.collectionValues,
            collectionValueLoads = snapshot.collectionValueLoads,
            collectionTags = snapshot.collectionTags.map { it.copy(itemId = p + it.itemId) },
            downloads = snapshot.downloads,
        )
    }

    fun merge(first: CatalogSnapshot, vararg rest: CatalogSnapshot): CatalogSnapshot {
        if (rest.isEmpty()) return first
        val all = listOf(first) + rest
        return CatalogSnapshot(
            artists = all.flatMap { it.artists },
            albums = all.flatMap { it.albums },
            playlists = all.flatMap { it.playlists },
            tracksByParent = all.fold(emptyMap()) { acc, s -> acc + s.tracksByParent },
            collectionValues = all.flatMap { it.collectionValues }.distinct(),
            collectionValueLoads = all.flatMap { it.collectionValueLoads }.distinct(),
            collectionTags = all.flatMap { it.collectionTags }.distinct(),
            downloads = all.flatMap { it.downloads }.distinctBy { it.trackId },
        )
    }

    fun stripPlexId(prefixedId: String): String =
        if (prefixedId.startsWith("plex$Sep")) prefixedId.removePrefix("plex$Sep") else prefixedId
}
