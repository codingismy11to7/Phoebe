package com.phoebe.app.ui

import com.phoebe.app.domain.Album
import com.phoebe.app.domain.Artist
import com.phoebe.app.domain.CatalogSnapshot
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
        .distinctBy { it.id }
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

internal fun personalMix(catalog: CatalogSnapshot, state: HomeUiState, limit: Int = 50): List<Track> {
    val tracks = allLoadedTracks(catalog)
    if (tracks.isEmpty()) return emptyList()
    val tracksById = tracks.associateBy { it.id }
    val heavyRotation = state.heavyRotationTracks.mapNotNull { tracksById[it.track.id] }
    val recent = state.recentlyPlayedTracks.mapNotNull { tracksById[it.track.id] }
    val most = state.mostPlayedTracks.mapNotNull { tracksById[it.track.id] }
    val seeds = (heavyRotation + recent + most).distinctBy { it.id }
    if (seeds.isEmpty()) return tracks.shuffled().take(limit)

    val seedArtists = seeds.map { it.artist.lowercase() }.toSet()
    val seedGenres = seeds.mapNotNull { it.genre?.lowercase() }.toSet()
    val seedDecades = seeds.mapNotNull { it.year?.let { year -> (year / 10) * 10 } }.toSet()
    val similar = tracks.filter { track ->
        track.id !in seeds.map { it.id }.toSet() &&
            (track.artist.lowercase() in seedArtists ||
                track.genre?.lowercase() in seedGenres ||
                track.year?.let { (it / 10) * 10 }?.let { it in seedDecades } == true)
    }
    val playedIds = (state.recentlyPlayedTracks + state.mostPlayedTracks).map { it.track.id }.toSet()
    val discovery = tracks.filter { it.id !in playedIds }
        .sortedByDescending { it.dateAddedMs ?: 0L }

    val target = limit.coerceAtLeast(1)
    return buildList<Track> {
        fun addSlice(candidates: List<Track>, maxCount: Int) {
            var added = 0
            candidates.shuffled().forEach { track ->
                if (size < target && added < maxCount && none { existing -> existing.id == track.id }) {
                    add(track)
                    added++
                }
            }
        }
        addSlice(heavyRotation, (target * 25) / 100)
        addSlice(recent, (target * 30) / 100)
        addSlice(most, (target * 25) / 100)
        addSlice(similar, (target * 15) / 100)
        addSlice(discovery, target - size)
        tracks.shuffled().forEach { if (size < target && none { existing -> existing.id == it.id }) add(it) }
    }
}
