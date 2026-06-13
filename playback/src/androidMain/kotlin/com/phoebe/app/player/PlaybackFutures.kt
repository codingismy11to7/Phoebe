package com.phoebe.app.player

import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture
import com.google.common.util.concurrent.SettableFuture
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

internal fun <T> listenableFuture(tag: String, block: suspend () -> T): ListenableFuture<T> {
    val future = SettableFuture.create<T>()
    CoroutineScope(Dispatchers.IO).launch {
        try {
            future.set(block())
        } catch (error: Throwable) {
            future.setException(error)
        }
    }
    return future
}

internal fun <T> immediateFuture(value: T): ListenableFuture<T> = Futures.immediateFuture(value)
