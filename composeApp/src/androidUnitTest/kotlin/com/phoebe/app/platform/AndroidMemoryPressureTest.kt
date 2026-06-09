package com.phoebe.app.platform

import android.content.ComponentCallbacks2
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class AndroidMemoryPressureTest {
    @Test
    fun uiHiddenMapsToUiHiddenNotCritical() {
        assertEquals(
            MemoryPressureLevel.UiHidden,
            memoryPressureLevelForTrimLevel(ComponentCallbacks2.TRIM_MEMORY_UI_HIDDEN),
        )
    }

    @Test
    fun runningCriticalMapsToCriticalWithoutMisclassifyingUiHidden() {
        assertEquals(
            MemoryPressureLevel.Critical,
            memoryPressureLevelForTrimLevel(ComponentCallbacks2.TRIM_MEMORY_RUNNING_CRITICAL),
        )
        assertEquals(
            MemoryPressureLevel.UiHidden,
            memoryPressureLevelForTrimLevel(ComponentCallbacks2.TRIM_MEMORY_UI_HIDDEN),
        )
    }

    @Test
    fun backgroundLadderMapsToModerateThenCritical() {
        assertEquals(
            MemoryPressureLevel.Moderate,
            memoryPressureLevelForTrimLevel(ComponentCallbacks2.TRIM_MEMORY_BACKGROUND),
        )
        assertEquals(
            MemoryPressureLevel.Moderate,
            memoryPressureLevelForTrimLevel(ComponentCallbacks2.TRIM_MEMORY_MODERATE),
        )
        assertEquals(
            MemoryPressureLevel.Critical,
            memoryPressureLevelForTrimLevel(ComponentCallbacks2.TRIM_MEMORY_COMPLETE),
        )
    }

    @Test
    fun runningModerateAndLowMapToLightAndModeratePressure() {
        assertEquals(
            MemoryPressureLevel.UiHidden,
            memoryPressureLevelForTrimLevel(ComponentCallbacks2.TRIM_MEMORY_RUNNING_MODERATE),
        )
        assertEquals(
            MemoryPressureLevel.Moderate,
            memoryPressureLevelForTrimLevel(ComponentCallbacks2.TRIM_MEMORY_RUNNING_LOW),
        )
    }

    @Test
    fun unknownTrimLevelsAreIgnored() {
        assertNull(memoryPressureLevelForTrimLevel(0))
    }
}
