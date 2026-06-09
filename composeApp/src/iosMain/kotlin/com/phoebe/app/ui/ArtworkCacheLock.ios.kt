package com.phoebe.app.ui

import platform.Foundation.NSLock

internal actual class ArtworkCacheLock {
    private val lock = NSLock()

    @PublishedApi
    internal inline fun <T> locked(block: () -> T): T {
        lock.lock()
        try {
            return block()
        } finally {
            lock.unlock()
        }
    }
}

internal actual inline fun <T> ArtworkCacheLock.withCacheLock(block: () -> T): T = locked(block)
