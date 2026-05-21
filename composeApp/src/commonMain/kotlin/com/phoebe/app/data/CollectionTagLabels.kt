package com.phoebe.app.data

/** Splits provider tag strings that encode multiple values in one field. */
internal fun splitCollectionTagLabels(raw: String?): List<String> =
    raw?.trim()?.takeIf { it.isNotBlank() }?.let { value ->
        value.split(',', ';', '/')
            .map { it.trim() }
            .filter { it.isNotBlank() }
    } ?: emptyList()

internal fun dominantCollectionTagLabel(labels: Iterable<String>): String? {
    val tally = LinkedHashMap<String, Int>()
    labels.forEach { label ->
        splitCollectionTagLabels(label).forEach { part ->
            tally[part] = (tally[part] ?: 0) + 1
        }
    }
    return tally.maxByOrNull { it.value }?.key
}
