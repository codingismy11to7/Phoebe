package com.phoebe.app.ui

import com.phoebe.app.domain.Album
import com.phoebe.app.domain.Artist
import com.phoebe.app.domain.CatalogSnapshot
import com.phoebe.app.domain.PersonalMixPreferences
import com.phoebe.app.domain.Playlist
import com.phoebe.app.domain.Track
import kotlin.random.Random

internal const val RecentlyAddedWindowMs = 7L * 24L * 60L * 60L * 1000L
internal const val HeavyRotationWindowMs = 14L * 24L * 60L * 60L * 1000L
private const val HeavyRotationMinimumRecentPlays = 2L

internal data class HomeUiState(
    val recentlyAddedTracks: List<Track> = emptyList(),
    val recentlyAddedArtists: List<Artist> = emptyList(),
    val recentlyAddedAlbums: List<Album> = emptyList(),
    val heavyRotationTracks: List<HomePlayedTrack> = emptyList(),
    val recentlyPlayedTracks: List<HomePlayedTrack> = emptyList(),
    val mostPlayedTracks: List<HomePlayedTrack> = emptyList(),
    val favoriteArtists: List<Artist> = emptyList(),
    val favoriteAlbums: List<Album> = emptyList(),
    val favoritePlaylists: List<Playlist> = emptyList(),
    val randomArtists: List<Artist> = emptyList(),
    val randomAlbums: List<Album> = emptyList(),
)

internal data class HomePlayedTrack(
    val track: Track,
    val lastPlayedMs: Long? = null,
    val playCount: Long = 0L,
)

internal fun deriveHomeUiState(
    catalog: CatalogSnapshot,
    playHistory: PlayHistorySnapshot,
    randomArtistSeed: Int,
    randomAlbumSeed: Int,
    nowMs: Long,
    limit: Int = 10,
): HomeUiState {
    val cutoffMs = nowMs - RecentlyAddedWindowMs
    val albumAddedByTitle = albumAddedByTitle(catalog)
    val artistAddedByTitle = artistAddedByTitle(catalog)
    val tracks = catalog.tracksByParent.values
        .asSequence()
        .flatten()
        .distinctBy { it.id }
        .toList()
    val tracksById = tracks.associateBy { it.id }
    val recentTracks = tracks
        .filter { effectiveTrackDateAdded(it, albumAddedByTitle) >= cutoffMs }
        .sortedByDescending { effectiveTrackDateAdded(it, albumAddedByTitle) }
        .take(limit)
    val recentArtists = catalog.artists
        .filter { artist -> recentlyAddedAt(artist, artistAddedByTitle) >= cutoffMs }
        .sortedByDescending { artist ->
            recentlyAddedAt(artist, artistAddedByTitle)
        }
        .take(limit)
    val recentAlbums = catalog.albums
        .filter { (it.dateAddedMs ?: Long.MIN_VALUE) >= cutoffMs }
        .sortedByDescending { it.dateAddedMs ?: 0L }
        .take(limit)
    val recentlyPlayed = playHistory.byTrack.entries
        .sortedByDescending { it.value }
        .mapNotNull { (trackId, playedAt) ->
            tracksById[trackId]?.let { HomePlayedTrack(it, lastPlayedMs = playedAt, playCount = playHistory.playCountByTrack[trackId] ?: 0L) }
        }
        .take(limit)
    val mostPlayed = playHistory.playCountByTrack.entries
        .filter { it.value > 0L }
        .sortedWith(compareByDescending<Map.Entry<String, Long>> { it.value }.thenByDescending { playHistory.byTrack[it.key] ?: 0L })
        .mapNotNull { (trackId, count) ->
            tracksById[trackId]?.let { HomePlayedTrack(it, lastPlayedMs = playHistory.byTrack[trackId], playCount = count) }
        }
        .take(limit)
    val heavyRotation = heavyRotationTracks(
        playHistory = playHistory,
        tracksById = tracksById,
        nowMs = nowMs,
        limit = limit,
    )

    return HomeUiState(
        recentlyAddedTracks = recentTracks,
        recentlyAddedArtists = recentArtists,
        recentlyAddedAlbums = recentAlbums,
        heavyRotationTracks = heavyRotation,
        recentlyPlayedTracks = recentlyPlayed,
        mostPlayedTracks = mostPlayed,
        favoriteArtists = catalog.artists.filter { it.favorite }.sortedBy { it.title.lowercase() }.take(limit),
        favoriteAlbums = catalog.albums.filter { it.favorite }.sortedBy { it.title.lowercase() }.take(limit),
        favoritePlaylists = catalog.playlists.filter { it.favorite }.sortedBy { it.title.lowercase() }.take(limit),
        randomArtists = catalog.artists.shuffled(Random(randomArtistSeed)).take(limit),
        randomAlbums = catalog.albums.shuffled(Random(randomAlbumSeed)).take(limit),
    )
}

private fun heavyRotationTracks(
    playHistory: PlayHistorySnapshot,
    tracksById: Map<String, Track>,
    nowMs: Long,
    limit: Int,
): List<HomePlayedTrack> {
    val cutoffMs = nowMs - HeavyRotationWindowMs
    val recentPlayCounts = if (playHistory.playEventsByTrack.isNotEmpty()) {
        playHistory.playEventsByTrack.mapValues { (_, playedAt) ->
            playedAt.count { it >= cutoffMs }.toLong()
        }
    } else {
        playHistory.byTrack.mapValues { (trackId, lastPlayedAt) ->
            if (lastPlayedAt >= cutoffMs) playHistory.playCountByTrack[trackId] ?: 1L else 0L
        }
    }
    return recentPlayCounts.entries
        .filter { it.value >= HeavyRotationMinimumRecentPlays }
        .sortedWith(
            compareByDescending<Map.Entry<String, Long>> { it.value }
                .thenByDescending { playHistory.byTrack[it.key] ?: 0L }
                .thenByDescending { playHistory.playCountByTrack[it.key] ?: 0L },
        )
        .mapNotNull { (trackId, recentCount) ->
            tracksById[trackId]?.let { track ->
                HomePlayedTrack(
                    track = track,
                    lastPlayedMs = playHistory.byTrack[trackId],
                    playCount = recentCount,
                )
            }
        }
        .take(limit)
}

internal fun albumAddedByTitle(catalog: CatalogSnapshot): Map<String, Long> =
    catalog.albums
        .asSequence()
        .mapNotNull { album -> album.dateAddedMs?.let { album.title.lowercase() to it } }
        .groupingBy { it.first }
        .aggregate { _, accumulator: Long?, element, _ -> maxOf(accumulator ?: Long.MIN_VALUE, element.second) }

internal fun artistAddedByTitle(catalog: CatalogSnapshot): Map<String, Long> =
    catalog.albums
        .asSequence()
        .mapNotNull { album -> album.dateAddedMs?.let { album.artist.lowercase() to it } }
        .groupingBy { it.first }
        .aggregate { _, accumulator: Long?, element, _ -> maxOf(accumulator ?: Long.MIN_VALUE, element.second) }

internal fun recentlyAddedAt(artist: Artist, artistAddedByTitle: Map<String, Long>): Long =
    artist.dateAddedMs ?: artistAddedByTitle[artist.title.lowercase()] ?: Long.MIN_VALUE

internal fun effectiveTrackDateAdded(track: Track, albumAddedByTitle: Map<String, Long>): Long =
    track.dateAddedMs ?: albumAddedByTitle[track.album.lowercase()] ?: Long.MIN_VALUE

internal fun allLoadedTracks(catalog: CatalogSnapshot): List<Track> =
    catalog.tracksByParent.values
        .asSequence()
        .flatten()
        .distinctBy { it.personalMixIdentityKey() }
        .filter { it.streamUrl.isNotBlank() || !it.localUri.isNullOrBlank() }
        .toList()

internal fun availableDecades(catalog: CatalogSnapshot): List<Int> =
    allLoadedTracks(catalog)
        .mapNotNull { it.year }
        .map { (it / 10) * 10 }
        .distinct()
        .sortedDescending()

internal fun defaultMixDecades(): List<Int> =
    (1900..2020 step 10).toList().asReversed()

internal fun decadeMix(catalog: CatalogSnapshot, decade: Int): List<Track> =
    allLoadedTracks(catalog)
        .filter { track -> track.year?.let { it >= decade && it <= decade + 9 } == true }
        .shuffled()

internal fun personalMix(
    catalog: CatalogSnapshot,
    state: HomeUiState,
    preferences: PersonalMixPreferences = PersonalMixPreferences.Default,
    limit: Int = preferences.normalized().limit,
): List<Track> {
    val mixPrefs = preferences.normalized().copy(limit = limit)
    val tracks = allLoadedTracks(catalog)
    if (tracks.isEmpty()) return emptyList()
    val tracksByIdentity = tracks.associateBy { it.personalMixIdentityKey() }
    val heavyRotation = state.heavyRotationTracks.mapNotNull { tracksByIdentity[it.track.personalMixIdentityKey()] }
    val recent = state.recentlyPlayedTracks.mapNotNull { tracksByIdentity[it.track.personalMixIdentityKey()] }
    val most = state.mostPlayedTracks.mapNotNull { tracksByIdentity[it.track.personalMixIdentityKey()] }
    val seeds = (heavyRotation + recent + most).distinctBy { it.personalMixIdentityKey() }
    if (seeds.isEmpty()) return tracks.shuffled().take(mixPrefs.limit)

    val seedArtists = seeds.map { it.artist.lowercase() }.toSet()
    val seedGenres = seeds.mapNotNull { it.genre?.lowercase() }.toSet()
    val seedDecades = seeds.mapNotNull { it.year?.let { year -> (year / 10) * 10 } }.toSet()
    val seedKeys = seeds.map { it.personalMixIdentityKey() }.toSet()
    val similar = tracks.filter { track ->
        track.personalMixIdentityKey() !in seedKeys &&
            (track.artist.lowercase() in seedArtists ||
                track.genre?.lowercase() in seedGenres ||
                track.year?.let { (it / 10) * 10 }?.let { it in seedDecades } == true)
    }
    val playedKeys = (state.recentlyPlayedTracks + state.mostPlayedTracks)
        .map { it.track.personalMixIdentityKey() }
        .toSet()
    val discovery = tracks.filter { it.personalMixIdentityKey() !in playedKeys }
        .sortedByDescending { it.dateAddedMs ?: 0L }

    val target = mixPrefs.limit.coerceAtLeast(1)
    val slices = mixSliceCounts(target, mixPrefs)
    return buildList<Track> {
        fun addSlice(candidates: List<Track>, maxCount: Int) {
            var added = 0
            candidates.shuffled().forEach { track ->
                if (size < target && added < maxCount && none { existing -> existing.personalMixIdentityKey() == track.personalMixIdentityKey() }) {
                    add(track)
                    added++
                }
            }
        }
        addSlice(heavyRotation, slices[0])
        addSlice(recent, slices[1])
        addSlice(most, slices[2])
        addSlice(similar, slices[3])
        addSlice(discovery, slices[4])
        tracks.shuffled().forEach {
            if (size < target && none { existing -> existing.personalMixIdentityKey() == it.personalMixIdentityKey() }) add(it)
        }
    }
}

private fun mixSliceCounts(target: Int, preferences: PersonalMixPreferences): List<Int> {
    val weights = listOf(
        preferences.heavyRotationWeight,
        preferences.recentWeight,
        preferences.mostPlayedWeight,
        preferences.similarWeight,
        preferences.discoveryWeight,
    ).map { it.coerceAtLeast(0) }
    val totalWeight = weights.sum()
    if (target <= 0 || totalWeight <= 0) return listOf(0, 0, 0, 0, target.coerceAtLeast(0))
    val raw = weights.map { weight -> (target.toDouble() * weight.toDouble()) / totalWeight.toDouble() }
    val base = raw.map { it.toInt() }.toMutableList()
    var remaining = target - base.sum()
    raw.indices
        .sortedWith(compareByDescending<Int> { raw[it] - base[it] }.thenBy { it })
        .forEach { index ->
            if (remaining > 0) {
                base[index]++
                remaining--
            }
        }
    return base
}

internal fun Track.personalMixIdentityKey(): String {
    val metadataKey = listOf(title, artist, album)
        .map { it.trim().lowercase() }
        .takeIf { parts -> parts.any { it.isNotBlank() } }
        ?.joinToString("|", prefix = "meta:", postfix = "|${durationMs.coerceAtLeast(0L)}")
    return metadataKey ?: providerEquivalentId()
}

private fun Track.providerEquivalentId(): String {
    val normalized = id.trim()
    val prefix = normalized.substringBefore(':', missingDelimiterValue = "")
    return when (prefix) {
        "plex", "jellyfin", "emby", "navidrome", "musicassistant" -> normalized.substringAfter(':')
        else -> normalized
    }
}
