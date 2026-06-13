package com.phoebe.app.ui

internal actual class ArtworkCacheLock

internal actual inline fun <T> ArtworkCacheLock.withCacheLock(block: () -> T): T =
    synchronized(this, block)
