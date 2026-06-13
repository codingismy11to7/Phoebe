package com.phoebe.app.feature.home

import com.phoebe.app.data.PlayHistorySnapshot
import com.phoebe.app.domain.Album
import com.phoebe.app.domain.Artist
import com.phoebe.app.domain.CatalogSnapshot
import com.phoebe.app.domain.Playlist
import com.phoebe.app.domain.Track
import kotlin.math.roundToInt
import kotlin.test.Test
import kotlin.test.assertTrue
import kotlin.time.measureTime

/**
 * JVM micro-benchmarks for home derivation cost. Run with:
 * `./gradlew :feature:home:desktopTest --tests com.phoebe.app.feature.home.HomeUiStatePerformanceTest`
 */
class HomeUiStatePerformanceTest {

    @Test
    fun deriveHomeUiStateScalingReport() {
        val sizes = listOf(
            LibrarySize(albums = 50, tracksPerAlbum = 12),
            LibrarySize(albums = 500, tracksPerAlbum = 12),
            LibrarySize(albums = 2_000, tracksPerAlbum = 12),
            LibrarySize(albums = 5_000, tracksPerAlbum = 12),
        )
        val playHistory = PlayHistorySnapshot(
            byTrack = (1..500).associate { "t$it" to it.toLong() * 1_000L },
            playCountByTrack = (1..500).associate { "t$it" to (it % 20).toLong() },
        )
        val lines = buildList {
            add("deriveHomeUiState scaling (warm 3 runs, median of 5 timed runs):")
            sizes.forEach { size ->
                val catalog = size.toCatalog()
                val trackCount = catalog.tracksByParent.values.sumOf { it.size }
                // Warm JIT
                repeat(3) {
                    deriveHomeUiState(catalog, playHistory, randomArtistSeed = 1, randomAlbumSeed = 2, nowMs = 1_000_000L)
                }
                val samples = List(5) {
                    measureTime {
                        deriveHomeUiState(catalog, playHistory, randomArtistSeed = 1, randomAlbumSeed = 2, nowMs = 1_000_000L)
                    }.inWholeNanoseconds
                }.sorted()
                add(
                    "  albums=${size.albums} tracks=$trackCount artists=${catalog.artists.size} -> ${formatMilliseconds(samples[samples.size / 2])} ms",
                )
            }
        }
        println(lines.joinToString("\n"))
        // Sanity: 5k-album library should complete in reasonable time on CI desktop JVM
        val largeCatalog = sizes.last().toCatalog()
        val largeMs = measureTime {
            deriveHomeUiState(largeCatalog, playHistory, 1, 2, 1_000_000L)
        }.inWholeMilliseconds
        assertTrue(largeMs < 5_000, "deriveHomeUiState took ${largeMs}ms for 60k tracks; investigate regression")
    }

    private fun formatMilliseconds(nanos: Long): String {
        val hundredths = (nanos / 10_000.0).roundToInt()
        val whole = hundredths / 100
        val fraction = (hundredths % 100).toString().padStart(2, '0')
        return "$whole.$fraction"
    }

    private data class LibrarySize(val albums: Int, val tracksPerAlbum: Int) {
        fun toCatalog(): CatalogSnapshot {
            val artists = (1..(albums / 10).coerceAtLeast(1)).map { index ->
                Artist(id = "a$index", title = "Artist $index", dateAddedMs = index.toLong())
            }
            val albumsList = (1..albums).map { index ->
                Album(
                    id = "al$index",
                    title = "Album $index",
                    artist = "Artist ${index % artists.size.coerceAtLeast(1)}",
                    dateAddedMs = index.toLong(),
                    thumbUrl = "https://example.com/album/$index.jpg",
                )
            }
            val tracksByParent = albumsList.associate { album ->
                album.id to (1..tracksPerAlbum).map { trackIndex ->
                    Track(
                        id = "t${album.id}-$trackIndex",
                        title = "Song $trackIndex",
                        artist = album.artist,
                        album = album.title,
                        durationMs = 180_000,
                        streamUrl = "http://example/stream",
                        downloadUrl = "http://example/download",
                        parentAlbumId = album.id,
                        dateAddedMs = trackIndex.toLong(),
                        thumbUrl = "https://example.com/track/$trackIndex.jpg",
                    )
                }
            }
            return CatalogSnapshot(
                artists = artists,
                albums = albumsList,
                playlists = listOf(Playlist(id = "p1", title = "Mix", trackCount = 0)),
                tracksByParent = tracksByParent,
            )
        }
    }
}
