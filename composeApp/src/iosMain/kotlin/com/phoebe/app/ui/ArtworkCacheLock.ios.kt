package com.phoebe.app.ui

import kotlinx.coroutines.internal.SynchronizedObject
import kotlinx.coroutines.internal.synchronized

internal actual class ArtworkCacheLock {
    private val lock = SynchronizedObject()
}

internal actual inline fun <T> ArtworkCacheLock.withCacheLock(block: () -> T): T =
    synchronized(lock, block)
