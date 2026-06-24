package com.phoebe.app.domain

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse

class AudioProcessingSettingsTest {
    @Test
    fun crossfeedDisablesExclusiveAndBitPerfectWhenDspIsActive() {
        val settings = AudioProcessingSettings(
            crossfeedEnabled = true,
            exclusiveMode = true,
            bitPerfectPreference = true,
        ).normalized()

        assertFalse(settings.exclusiveMode)
        assertFalse(settings.bitPerfectPreference)
    }

    @Test
    fun crossfeedAmountIsClamped() {
        val settings = AudioProcessingSettings(crossfeedAmount = 1.5f).normalized()

        assertEquals(1f, settings.crossfeedAmount)
    }
}
