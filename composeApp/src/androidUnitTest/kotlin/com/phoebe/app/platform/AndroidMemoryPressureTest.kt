package com.phoebe.app.platform

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

private const val TrimMemoryRunningModerate = 5
private const val TrimMemoryRunningLow = 10
private const val TrimMemoryRunningCritical = 15
private const val TrimMemoryUiHidden = 20
private const val TrimMemoryBackground = 40
private const val TrimMemoryModerate = 60
private const val TrimMemoryComplete = 80

class AndroidMemoryPressureTest {
    @Test
    fun uiHiddenMapsToUiHiddenNotCritical() {
        assertEquals(
            MemoryPressureLevel.UiHidden,
            memoryPressureLevelForTrimLevel(TrimMemoryUiHidden),
        )
    }

    @Test
    fun runningCriticalMapsToCriticalWithoutMisclassifyingUiHidden() {
        assertEquals(
            MemoryPressureLevel.Critical,
            memoryPressureLevelForTrimLevel(TrimMemoryRunningCritical),
        )
        assertEquals(
            MemoryPressureLevel.UiHidden,
            memoryPressureLevelForTrimLevel(TrimMemoryUiHidden),
        )
    }

    @Test
    fun backgroundLadderMapsToModerateThenCritical() {
        assertEquals(
            MemoryPressureLevel.Moderate,
            memoryPressureLevelForTrimLevel(TrimMemoryBackground),
        )
        assertEquals(
            MemoryPressureLevel.Moderate,
            memoryPressureLevelForTrimLevel(TrimMemoryModerate),
        )
        assertEquals(
            MemoryPressureLevel.Critical,
            memoryPressureLevelForTrimLevel(TrimMemoryComplete),
        )
    }

    @Test
    fun runningModerateAndLowMapToLightAndModeratePressure() {
        assertEquals(
            MemoryPressureLevel.UiHidden,
            memoryPressureLevelForTrimLevel(TrimMemoryRunningModerate),
        )
        assertEquals(
            MemoryPressureLevel.Moderate,
            memoryPressureLevelForTrimLevel(TrimMemoryRunningLow),
        )
    }

    @Test
    fun unknownTrimLevelsAreIgnored() {
        assertNull(memoryPressureLevelForTrimLevel(0))
    }
}
