package com.phoebe.app

import com.phoebe.app.data.lyricsAreSynced
import com.phoebe.app.data.parseLyricsLines
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class LyricsParserTest {
    @Test
    fun parsesSyncedLyricsWithMultipleTimestamps() {
        val lines = parseLyricsLines(
            """
            [ar:Artist]
            [00:01.50][00:03.25] Hello
            [00:04] World
            """.trimIndent(),
        )

        assertTrue(lyricsAreSynced(lines))
        assertEquals(listOf(1_500L, 3_250L, 4_000L), lines.map { it.startMs })
        assertEquals(listOf("Hello", "Hello", "World"), lines.map { it.text })
    }

    @Test
    fun keepsPlainLyricsAndDropsMetadataOnlyLines() {
        val lines = parseLyricsLines(
            """
            [ti:Song]

            First line
            Second line
            """.trimIndent(),
        )

        assertFalse(lyricsAreSynced(lines))
        assertEquals(listOf(null, null), lines.map { it.startMs })
        assertEquals(listOf("First line", "Second line"), lines.map { it.text })
    }

    @Test
    fun malformedTimestampsFallBackToPlainText() {
        val lines = parseLyricsLines("[00:99.00] Not a real timestamp")

        assertFalse(lyricsAreSynced(lines))
        assertEquals("[00:99.00] Not a real timestamp", lines.single().text)
    }
}
