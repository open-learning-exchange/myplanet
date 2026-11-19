package org.ole.planet.myplanet.services.upload

import java.util.concurrent.ConcurrentHashMap
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * One lock per upload kind, shared by every caller in the process (login, manual sync, auto-sync,
 * reconnect, the survey screen). A second run of the same kind waits for the first instead of
 * reading the same pending rows and POSTing new documents a second time; once it gets the lock
 * those rows are already marked uploaded, so it finds nothing left to send.
 */
internal object UploadLocks {
    private val locks = ConcurrentHashMap<String, Mutex>()

    suspend fun <R> withLock(key: String, block: suspend () -> R): R =
        locks.computeIfAbsent(key) { Mutex() }.withLock { block() }
}
