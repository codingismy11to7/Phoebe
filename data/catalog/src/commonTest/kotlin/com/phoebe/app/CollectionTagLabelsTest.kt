package com.phoebe.app

import com.phoebe.app.data.splitCollectionTagLabels
import kotlin.test.Test
import kotlin.test.assertEquals

class CollectionTagLabelsTest {
    @Test
    fun splitsMultiValueGenreStrings() {
        assertEquals(listOf("Rock", "Pop"), splitCollectionTagLabels("Rock; Pop"))
        assertEquals(listOf("Electronic", "Synthpop"), splitCollectionTagLabels("Electronic, Synthpop"))
    }
}
