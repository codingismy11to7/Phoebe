package com.phoebe.app

import app.cash.sqldelight.async.coroutines.awaitAsList
import app.cash.sqldelight.db.SqlDriver
import com.phoebe.app.data.RecentMixTracksRepository
import com.phoebe.app.testing.newInMemoryPhoebeDatabase
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class RecentMixTracksRepositoryDesktopTest {
    private var driver: SqlDriver? = null

    @After
    fun tearDown() {
        driver?.close()
        driver = null
    }

    @Test
    fun recordSurfacedPersistsAndReadsBack() = runBlocking {
        val (db, d) = newInMemoryPhoebeDatabase()
        driver = d
        val repo = RecentMixTracksRepository(db)

        repo.recordSurfaced(listOf("a|artist|0", "b|artist|0"), keepCount = 10, atMs = 1_000L)

        assertEquals(setOf("a|artist|0", "b|artist|0"), repo.recentTrackKeys(10))
    }

    @Test
    fun recordSurfacedPrunesBeyondKeepCount() = runBlocking {
        val (db, d) = newInMemoryPhoebeDatabase()
        driver = d
        val repo = RecentMixTracksRepository(db)

        repo.recordSurfaced(listOf("old-1", "old-2", "old-3"), keepCount = 10, atMs = 1_000L)
        repo.recordSurfaced(listOf("new-1", "new-2"), keepCount = 3, atMs = 2_000L)

        val recent = repo.recentTrackKeys(10)
        assertEquals(3, recent.size)
        assertTrue(recent.containsAll(listOf("new-1", "new-2")))
        assertEquals(3, db.recentMixTrackQueries.selectRecentTrackIds(100L).awaitAsList().size)
    }

    @Test
    fun newerSurfacesWinRecencyOrdering() = runBlocking {
        val (db, d) = newInMemoryPhoebeDatabase()
        driver = d
        val repo = RecentMixTracksRepository(db)

        repo.recordSurfaced(listOf("first"), keepCount = 10, atMs = 1_000L)
        repo.recordSurfaced(listOf("second"), keepCount = 10, atMs = 2_000L)

        assertEquals(listOf("second", "first"), db.recentMixTrackQueries.selectRecentTrackIds(10L).awaitAsList())
    }

    @Test
    fun recordSurfacedWithMixSizedBatchDoesNotHang() = runBlocking {
        val (db, d) = newInMemoryPhoebeDatabase()
        driver = d
        val repo = RecentMixTracksRepository(db)
        val keys = (1..50).map { "track-$it|artist|0" }

        repo.recordSurfaced(keys, keepCount = 150, atMs = 1_000L)
        assertEquals(50, repo.recentTrackKeys(150).size)

        repo.recordSurfaced(keys.map { "next-$it" }, keepCount = 50, atMs = 2_000L)
        assertEquals(50, repo.recentTrackKeys(150).size)
    }
}
