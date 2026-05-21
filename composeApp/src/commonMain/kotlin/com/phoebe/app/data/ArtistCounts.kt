package com.phoebe.app.data

import com.phoebe.app.domain.Album
import com.phoebe.app.domain.Artist
import com.phoebe.app.domain.CatalogSnapshot
import com.phoebe.app.domain.CatalogSyncPhase
import com.phoebe.app.domain.CatalogSyncState
import com.phoebe.app.domain.Track

/**
 * Album counts only (fast path during catalog refresh). Song totals are derived on the artist detail screen.
 */
internal fun enrichArtistAlbumCountsOnly(
    artists: List<Artist>,
    albums: List<Album>,
): List<Artist> {
    if (artists.isEmpty()) return artists
    val needsDerivedCount = artists.any { it.albumCount <= 0 }
    val derivedCounts = if (needsDerivedCount && albums.isNotEmpty()) {
        buildDerivedArtistAlbumCounts(artists, albums)
    } else {
        emptyMap()
    }
    return artists.map { artist ->
        val newAlbumCount = when {
            artist.albumCount > 0 -> artist.albumCount
            else -> derivedCounts[artist.title.trim().lowercase()] ?: 0
        }
        artist.copy(
            albumCount = newAlbumCount,
            songCount = if (artist.songCount > 0) artist.songCount else 0,
        )
    }
}

internal fun enrichJellyfinCatalogArtwork(snapshot: CatalogSnapshot): CatalogSnapshot {
    val albumsById = snapshot.albums.associateBy { it.id }
    val tracksByParent = snapshot.tracksByParent.mapValues { (_, tracks) ->
        tracks.map { track ->
            val album = track.parentAlbumId?.let(albumsById::get)
            if (album == null) {
                track
            } else {
                track.copy(
                    thumbUrl = track.thumbUrl ?: album.thumbUrl,
                    artist = track.artist.takeUnless { it == "Unknown artist" } ?: album.artist,
                    album = track.album.takeUnless { it == "Unknown album" } ?: album.title,
                )
            }
        }
    }
    return snapshot.copy(
        artists = dedupeArtistsByTitle(enrichArtistArtwork(snapshot.artists, snapshot.albums)),
        tracksByParent = tracksByParent,
    )
}

internal fun enrichArtistArtwork(artists: List<Artist>, albums: List<Album>): List<Artist> {
    if (artists.isEmpty()) return artists
    val thumbByAlbumArtist = buildMap<String, String> {
        albums.asSequence()
            .filter { !it.thumbUrl.isNullOrBlank() }
            .forEach { album ->
                val key = album.artist.trim().lowercase()
                if (key !in this) {
                    put(key, album.thumbUrl!!)
                }
            }
    }
    return artists.map { artist ->
        if (!artist.thumbUrl.isNullOrBlank()) {
            artist
        } else {
            val exact = thumbByAlbumArtist[artist.title.trim().lowercase()]
            val fuzzy = albums.firstOrNull { albumMatchesArtist(it, artist.title) && !it.thumbUrl.isNullOrBlank() }?.thumbUrl
            artist.copy(thumbUrl = exact ?: fuzzy)
        }
    }
}

/** Prefer real API artist ids over legacy synthetic `album-artist-*` entries. */
internal fun dedupeArtistsByTitle(artists: List<Artist>): List<Artist> =
    artists
        .sortedWith(
            compareBy<Artist> { it.id.substringAfter(':').startsWith("album-artist-") }
                .thenBy { it.thumbUrl.isNullOrBlank() }
                .thenByDescending { it.albumCount },
        )
        .distinctBy { it.title.trim().lowercase() }

/**
 * Counts albums per artist title for artists that did not already have [Artist.albumCount] from the server.
 * Uses one album pass with an exact title index, then a fuzzy pass only for unmatched albums.
 */
private fun buildDerivedArtistAlbumCounts(
    artists: List<Artist>,
    albums: List<Album>,
): Map<String, Int> {
    val titlesNeedingCount = artists
        .asSequence()
        .filter { it.albumCount <= 0 }
        .map { it.title.trim() }
        .distinctBy { it.lowercase() }
        .toList()
    if (titlesNeedingCount.isEmpty()) return emptyMap()

    val counts = titlesNeedingCount.associate { it.lowercase() to 0 }.toMutableMap()
    val titleByLower = titlesNeedingCount.associateBy { it.lowercase() }
    val fuzzyTitles = titlesNeedingCount.sortedByDescending { it.length }
    val unmatchedAlbums = ArrayList<Album>()

    for (album in albums) {
        val exactKey = album.artist.trim().lowercase()
        if (exactKey in counts) {
            counts[exactKey] = counts.getValue(exactKey) + 1
        } else {
            unmatchedAlbums += album
        }
    }

    if (unmatchedAlbums.isEmpty()) return counts

    for (album in unmatchedAlbums) {
        for (title in fuzzyTitles) {
            if (albumMatchesArtist(album, title)) {
                val key = title.lowercase()
                counts[key] = counts.getValue(key) + 1
                break
            }
        }
    }
    return counts
}

fun catalogAlbumsForArtist(catalog: CatalogSnapshot, artistTitle: String): List<Album> =
    catalog.albums.filter { albumMatchesArtist(it, artistTitle) }.sortedBy { it.title.lowercase() }

fun catalogTracksForArtist(catalog: CatalogSnapshot, artistTitle: String): List<Track> =
    catalog.tracksByParent.values.asSequence()
        .flatten()
        .distinctBy { it.id }
        .filter { trackMatchesArtist(it, artistTitle) }
        .sortedWith(compareBy({ it.album.lowercase() }, { it.title.lowercase() }))
        .toList()

fun catalogArtistForAlbum(catalog: CatalogSnapshot, album: Album): Artist? {
    val title = album.artist.trim()
    if (title.isBlank()) return null
    return catalog.artists.firstOrNull { it.title.equals(title, ignoreCase = true) }
        ?: catalog.artists.firstOrNull { albumMatchesArtist(album, it.title) }
}

internal fun albumMatchesArtist(album: Album, artistTitle: String): Boolean {
    val t = artistTitle.trim()
    if (t.isEmpty()) return false
    val a = album.artist.trim()
    return a.equals(t, ignoreCase = true) ||
        a.startsWith("$t ", ignoreCase = true) ||
        a.startsWith("$t,", ignoreCase = true) ||
        a.startsWith("$t&", ignoreCase = true) ||
        a.contains(" $t ", ignoreCase = true) ||
        a.contains(" $t(", ignoreCase = true)
}

private fun trackMatchesArtist(track: Track, artistTitle: String): Boolean {
    val t = artistTitle.trim()
    if (t.isEmpty()) return false
    return track.artist.contains(t, ignoreCase = true) ||
        track.album.contains(t, ignoreCase = true)
}

fun catalogTracksForAlbum(catalog: CatalogSnapshot, albumId: String): List<Track> =
    catalog.tracksByParent[albumId].orEmpty()

/** True when album count for [artistTitle] is not yet available in [catalog]. */
fun catalogArtistAlbumCountLoading(catalog: CatalogSnapshot, artist: Artist, sync: CatalogSyncState): Boolean {
    val albums = catalogAlbumsForArtist(catalog, artist.title)
    if (albums.isNotEmpty()) return false
    if (artist.albumCount > 0) {
        return sync.isActive && sync.phase <= CatalogSyncPhase.LoadingLibrary
    }
    return sync.isActive && sync.phase <= CatalogSyncPhase.LoadingLibrary
}

/** True when per-track stats (songs, duration, genre) for [artistTitle] are still being fetched. */
fun catalogArtistTrackStatsLoading(
    catalog: CatalogSnapshot,
    artist: Artist,
    sync: CatalogSyncState,
    catalogRefreshing: Boolean = false,
): Boolean {
    if (catalogTracksForArtist(catalog, artist.title).isNotEmpty()) return false
    val albums = catalogAlbumsForArtist(catalog, artist.title)
    if (albums.isEmpty()) {
        return sync.isActive &&
            (sync.phase == CatalogSyncPhase.LoadingSongs || sync.phase == CatalogSyncPhase.LoadingLibrary)
    }
    val pending = albums.any { !catalog.tracksByParent.containsKey(it.id) }
    if (!pending) return false
    return (sync.isActive && sync.phase == CatalogSyncPhase.LoadingSongs) || catalogRefreshing
}

/** True when track list / duration for [albumId] has not been loaded into [catalog] yet. */
fun catalogAlbumTrackStatsLoading(
    catalog: CatalogSnapshot,
    album: Album,
    sync: CatalogSyncState,
    catalogRefreshing: Boolean = false,
): Boolean {
    val loaded = catalog.tracksByParent[album.id]
    if (!loaded.isNullOrEmpty()) return false
    if (loaded != null) return false
    return (sync.isActive && sync.phase == CatalogSyncPhase.LoadingSongs) || catalogRefreshing
}

/** Best-effort genre for an artist: most frequent non-blank genre across their tracks/albums. */
fun catalogArtistGenre(catalog: CatalogSnapshot, artistTitle: String): String? {
    val tracks = catalogTracksForArtist(catalog, artistTitle)
    val tally = LinkedHashMap<String, Int>()
    tracks.asSequence().mapNotNull { it.genre }.filter { it.isNotBlank() }.forEach {
        tally[it] = (tally[it] ?: 0) + 1
    }
    if (tally.isEmpty()) return null
    return tally.maxByOrNull { it.value }?.key
}

/** Total duration across all tracks for [artistTitle] (in ms). */
fun catalogArtistTotalDurationMs(catalog: CatalogSnapshot, artistTitle: String): Long =
    catalogTracksForArtist(catalog, artistTitle).sumOf { it.durationMs }

fun catalogAlbumTotalDurationMs(catalog: CatalogSnapshot, albumId: String): Long =
    catalogTracksForAlbum(catalog, albumId).sumOf { it.durationMs }

fun catalogAlbumTrackCount(catalog: CatalogSnapshot, albumId: String): Int =
    catalogTracksForAlbum(catalog, albumId).size

/** Best-effort codec label for the album (e.g. "FLAC"). */
fun catalogAlbumCodec(catalog: CatalogSnapshot, albumId: String): String? {
    val tracks = catalogTracksForAlbum(catalog, albumId)
    val tally = LinkedHashMap<String, Int>()
    tracks.asSequence().mapNotNull { it.audioCodec }.filter { it.isNotBlank() }.forEach {
        tally[it.uppercase()] = (tally[it.uppercase()] ?: 0) + 1
    }
    return tally.maxByOrNull { it.value }?.key
}

/** Best-effort genre label for the album. */
fun catalogAlbumGenre(catalog: CatalogSnapshot, albumId: String): String? {
    val tracks = catalogTracksForAlbum(catalog, albumId)
    val tally = LinkedHashMap<String, Int>()
    tracks.asSequence().mapNotNull { it.genre }.filter { it.isNotBlank() }.forEach {
        tally[it] = (tally[it] ?: 0) + 1
    }
    return tally.maxByOrNull { it.value }?.key
}

/** Average bitrate when known, otherwise null. */
fun catalogAlbumBitrateKbps(catalog: CatalogSnapshot, albumId: String): Int? {
    val rates = catalogTracksForAlbum(catalog, albumId).mapNotNull { it.bitrateKbps }.filter { it > 0 }
    if (rates.isEmpty()) return null
    return rates.average().toInt()
}

/** Top N most-representative tracks for an artist (currently: stable order by album then title). */
fun catalogArtistTopTracks(catalog: CatalogSnapshot, artistTitle: String, n: Int = 5): List<Track> =
    catalogTracksForArtist(catalog, artistTitle).take(n)
