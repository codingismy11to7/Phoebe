package com.phoebe.app

import com.phoebe.app.domain.EqualizerProfile
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class EqualizerProfileTest {
    @Test
    fun normalizesBandCountAndGains() {
        val profile = EqualizerProfile(
            enabled = true,
            bandCount = 9,
            gainsDb = listOf(-20f, -0.26f, 0.24f, 0.26f, 12.8f),
        ).normalized()

        assertEquals(10, profile.bandCount)
        assertEquals(-12f, profile.gainsDb[0])
        assertEquals(-0.5f, profile.gainsDb[1])
        assertEquals(0f, profile.gainsDb[2])
        assertEquals(0.5f, profile.gainsDb[3])
        assertEquals(12f, profile.gainsDb[4])
        assertEquals(10, profile.gainsDb.size)
    }

    @Test
    fun changingBandCountKeepsMatchingFrequencyGains() {
        val tenBand = EqualizerProfile.Default
            .normalized()
            .withGain(1, 3f)
            .withGain(7, -4f)

        val fiveBand = tenBand.withBandCount(5)

        assertEquals(5, fiveBand.bandCount)
        assertEquals(3f, fiveBand.gainsDb[0])
        assertEquals(-4f, fiveBand.gainsDb[3])
        assertFalse(fiveBand.isFlat)
    }

    @Test
    fun flatProfileReportsAllZeroGains() {
        assertTrue(EqualizerProfile.Default.normalized().isFlat)
    }
}
