package com.phoebe.app

import app.cash.sqldelight.async.coroutines.awaitAsList
import app.cash.sqldelight.db.SqlDriver
import com.phoebe.app.data.PlayHistoryRepository
import com.phoebe.app.domain.Track
import com.phoebe.app.testing.newInMemoryPhoebeDatabase
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Test
import kotlin.test.assertEquals

class PlayHistoryRepositoryDesktopTest {
    private var driver: SqlDriver? = null

    @After
    fun tearDown() {
        driver?.close()
        driver = null
    }

    @Test
    fun recordPlayPersistsRow() = runBlocking {
        val (db, d) = newInMemoryPhoebeDatabase()
        driver = d
        val repo = PlayHistoryRepository(db)
        val track = Track("tid", "Song", "Art", "Alb", 30_000L, "", "")
        repo.recordPlay(track, 12345L)
        val rows = db.playHistoryQueries.selectLastPlayedByTrack().awaitAsList()
        assertEquals(1, rows.size)
        assertEquals("tid", rows.single().track_id)
        assertEquals(12345L, rows.single().lastPlayed)
    }
}
