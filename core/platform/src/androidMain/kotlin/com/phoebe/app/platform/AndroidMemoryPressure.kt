package com.phoebe.app.platform

private const val TrimMemoryRunningModerate = 5
private const val TrimMemoryRunningLow = 10
private const val TrimMemoryRunningCritical = 15
private const val TrimMemoryUiHidden = 20
private const val TrimMemoryBackground = 40
private const val TrimMemoryModerate = 60
private const val TrimMemoryComplete = 80

/**
 * Maps Android component trim-memory levels to app pressure tiers.
 *
 * Running and background levels share numeric space but mean different things — never compare
 * against the running-critical value with `>=` or UI-hidden is misclassified as critical.
 *
 * The Android constants for these levels are deprecated on newer SDKs, but the platform still
 * delivers these stable integer values through the trim-memory callback.
 */
fun memoryPressureLevelForTrimLevel(level: Int): MemoryPressureLevel? =
    when {
        level >= TrimMemoryComplete -> MemoryPressureLevel.Critical
        level == TrimMemoryRunningCritical -> MemoryPressureLevel.Critical
        level >= TrimMemoryModerate -> MemoryPressureLevel.Moderate
        level >= TrimMemoryBackground -> MemoryPressureLevel.Moderate
        level == TrimMemoryRunningLow -> MemoryPressureLevel.Moderate
        level >= TrimMemoryUiHidden -> MemoryPressureLevel.UiHidden
        level == TrimMemoryRunningModerate -> MemoryPressureLevel.UiHidden
        else -> null
    }
