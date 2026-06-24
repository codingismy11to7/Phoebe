package com.phoebe.app.domain

fun parseAdvancedSearchQuery(rawQuery: String): AdvancedSearchQuery {
    val rules = mutableListOf<TrackFilterRule>()
    val textParts = mutableListOf<String>()
    tokenizeSearchQuery(rawQuery).forEach { token ->
        val separator = token.indexOf(':')
        if (separator <= 0 || separator == token.lastIndex) {
            textParts += token
            return@forEach
        }
        val key = token.substring(0, separator).lowercase()
        val value = token.substring(separator + 1).trim()
        val rule = when (key) {
            "artist" -> TrackFilterRule(FilterField.Artist, FilterOperator.Contains, value)
            "album" -> TrackFilterRule(FilterField.Album, FilterOperator.Contains, value)
            "genre" -> TrackFilterRule(FilterField.Genre, FilterOperator.Contains, value)
            "mood" -> TrackFilterRule(FilterField.Mood, FilterOperator.Contains, value)
            "style" -> TrackFilterRule(FilterField.Style, FilterOperator.Contains, value)
            "year" -> parseNumericToken(FilterField.Year, value)
            "rating" -> parseNumericToken(FilterField.Rating, value)
            "downloaded" -> parseBooleanToken(FilterField.Downloaded, value)
            "local" -> parseBooleanToken(FilterField.Local, value)
            "explicit" -> parseBooleanToken(FilterField.Explicit, value)
            "codec" -> TrackFilterRule(FilterField.Codec, FilterOperator.Equals, value)
            "provider" -> TrackFilterRule(FilterField.Provider, FilterOperator.Equals, value)
            else -> null
        }
        if (rule != null) {
            rules += rule
        } else {
            textParts += token
        }
    }
    return AdvancedSearchQuery(
        text = textParts.joinToString(" ").trim(),
        filter = TrackFilterSpec(rules),
    )
}

private fun parseNumericToken(field: FilterField, value: String): TrackFilterRule? =
    when {
        value.contains("..") -> TrackFilterRule(field, FilterOperator.Between, value)
        value.startsWith(">=") -> TrackFilterRule(field, FilterOperator.GreaterThanOrEquals, value.drop(2))
        value.startsWith("<=") -> TrackFilterRule(field, FilterOperator.LessThanOrEquals, value.drop(2))
        value.startsWith(">") -> TrackFilterRule(field, FilterOperator.GreaterThan, value.drop(1))
        value.startsWith("<") -> TrackFilterRule(field, FilterOperator.LessThan, value.drop(1))
        value.startsWith("=") -> TrackFilterRule(field, FilterOperator.Equals, value.drop(1))
        value.toDoubleOrNull() != null -> TrackFilterRule(field, FilterOperator.Equals, value)
        else -> null
    }

private fun parseBooleanToken(field: FilterField, value: String): TrackFilterRule? =
    when (value.lowercase()) {
        "true", "yes", "1" -> TrackFilterRule(field, FilterOperator.IsTrue)
        "false", "no", "0" -> TrackFilterRule(field, FilterOperator.IsFalse)
        else -> null
    }

private fun tokenizeSearchQuery(rawQuery: String): List<String> {
    val tokens = mutableListOf<String>()
    val current = StringBuilder()
    var quoted = false
    rawQuery.forEach { char ->
        when {
            char == '"' -> quoted = !quoted
            char.isWhitespace() && !quoted -> {
                if (current.isNotEmpty()) {
                    tokens += current.toString()
                    current.clear()
                }
            }
            else -> current.append(char)
        }
    }
    if (current.isNotEmpty()) tokens += current.toString()
    return tokens
}
