package com.phoebe.app.domain

import kotlin.test.Test
import kotlin.test.assertEquals

class AdvancedSearchParserTest {
    @Test
    fun parsesKnownTokensAndKeepsRemainingText() {
        val query = parseAdvancedSearchQuery("moon artist:Phoebe year:1990..1999 rating:>=4 downloaded:true codec:flac")

        assertEquals("moon", query.text)
        assertEquals(
            listOf(
                TrackFilterRule(FilterField.Artist, FilterOperator.Contains, "Phoebe"),
                TrackFilterRule(FilterField.Year, FilterOperator.Between, "1990..1999"),
                TrackFilterRule(FilterField.Rating, FilterOperator.GreaterThanOrEquals, "4"),
                TrackFilterRule(FilterField.Downloaded, FilterOperator.IsTrue),
                TrackFilterRule(FilterField.Codec, FilterOperator.Equals, "flac"),
            ),
            query.filter.rules,
        )
    }

    @Test
    fun leavesUnknownTokensAsText() {
        val query = parseAdvancedSearchQuery("source:future shimmer")

        assertEquals("source:future shimmer", query.text)
        assertEquals(emptyList(), query.filter.rules)
    }
}
