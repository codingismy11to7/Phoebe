package com.phoebe.app.platform

import android.content.ComponentCallbacks2

/**
 * Maps Android [ComponentCallbacks2.onTrimMemory] levels to app pressure tiers.
 *
 * Running and background levels share numeric space but mean different things — never compare
 * against [ComponentCallbacks2.TRIM_MEMORY_RUNNING_CRITICAL] with `>=` or UI_HIDDEN (20) is
 * misclassified as critical.
 */
internal fun memoryPressureLevelForTrimLevel(level: Int): MemoryPressureLevel? =
    when {
        level >= ComponentCallbacks2.TRIM_MEMORY_COMPLETE -> MemoryPressureLevel.Critical
        level == ComponentCallbacks2.TRIM_MEMORY_RUNNING_CRITICAL -> MemoryPressureLevel.Critical
        level >= ComponentCallbacks2.TRIM_MEMORY_MODERATE -> MemoryPressureLevel.Moderate
        level >= ComponentCallbacks2.TRIM_MEMORY_BACKGROUND -> MemoryPressureLevel.Moderate
        level == ComponentCallbacks2.TRIM_MEMORY_RUNNING_LOW -> MemoryPressureLevel.Moderate
        level >= ComponentCallbacks2.TRIM_MEMORY_UI_HIDDEN -> MemoryPressureLevel.UiHidden
        level == ComponentCallbacks2.TRIM_MEMORY_RUNNING_MODERATE -> MemoryPressureLevel.UiHidden
        else -> null
    }
