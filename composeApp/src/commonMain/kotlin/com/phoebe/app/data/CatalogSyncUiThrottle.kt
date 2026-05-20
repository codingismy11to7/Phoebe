package com.phoebe.app.data

import com.phoebe.app.domain.CatalogSyncPhase
import com.phoebe.app.domain.CatalogSyncState
import com.phoebe.app.platform.currentTimeMs

/**
 * Coalesces high-frequency catalog sync progress updates before they reach UI collectors.
 * Phase and message changes always emit; progress/detail are rate-limited.
 */
internal class CatalogSyncUiThrottle {
    private var lastEmitAtMs = 0L
    private var lastPhase: CatalogSyncPhase = CatalogSyncPhase.Idle
    private var lastMessage: String? = null
    private var lastProgressBucket: Int? = null

    fun reset() {
        lastEmitAtMs = 0L
        lastPhase = CatalogSyncPhase.Idle
        lastMessage = null
        lastProgressBucket = null
    }

    fun shouldEmit(state: CatalogSyncState, nowMs: Long = currentTimeMs()): Boolean {
        if (state.phase != lastPhase) return true
        if (state.message != lastMessage) return true
        if (state.blocking) return true
        if (!state.isActive && state.phase != CatalogSyncPhase.RestoringCache) return true
        val bucket = state.progress?.let { (it * 40).toInt() }
        if (bucket != lastProgressBucket && nowMs - lastEmitAtMs >= 400L) return true
        if (nowMs - lastEmitAtMs >= 500L) return true
        return false
    }

    fun markEmitted(state: CatalogSyncState, nowMs: Long = currentTimeMs()) {
        lastEmitAtMs = nowMs
        lastPhase = state.phase
        lastMessage = state.message
        lastProgressBucket = state.progress?.let { (it * 40).toInt() }
    }
}
