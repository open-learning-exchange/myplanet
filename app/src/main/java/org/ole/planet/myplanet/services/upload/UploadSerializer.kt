package org.ole.planet.myplanet.services.upload

import java.util.concurrent.ConcurrentHashMap
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

internal object UploadSerializer {
    private val locks = ConcurrentHashMap<String, Mutex>()

    suspend fun <R> withLock(key: String, block: suspend () -> R): R =
        locks.computeIfAbsent(key) { Mutex() }.withLock { block() }
}
