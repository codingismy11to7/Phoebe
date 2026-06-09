package com.phoebe.app.ui

internal expect class ArtworkCacheLock()

internal expect inline fun <T> ArtworkCacheLock.withCacheLock(block: () -> T): T
