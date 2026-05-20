package com.phoebe.app.data.db

import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/** Serializes SQLite writes across repositories that share one [PhoebeDatabase] connection. */
class DatabaseWriteGate {
    private val mutex = Mutex()

    suspend fun <T> withWrite(block: suspend () -> T): T = mutex.withLock { block() }
}
