package org.ole.planet.myplanet.services.sync

import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Coalesces parallel-phase progress reports so that a burst of near-simultaneous
 * completions (e.g. ~14 tables finishing within milliseconds) does not push one
 * [SyncManager.SyncStatus.Syncing] write per completion onto the StateFlow.
 *
 * Each parallel worker calls [report] with its progress. Rather than emitting
 * immediately, [report] stashes the value and arms a single deferred emission that
 * fires after [intervalMillis] of quiet. Repeated reports within that window keep
 * re-arming the same deferred, so a burst collapses to one (the latest) emission per
 * window. [flush] emits the stashed value immediately so the terminal progress lands
 * even when the quiet window never elapses.
 *
 * Lifecycle: call [start] to bind the owning [CoroutineScope] (the per-window deferred
 * becomes a child of that scope), then [flush] once the parallel work has been awaited,
 * then [stop] to cancel any pending deferred so the scope can complete.
 *
 * @param intervalMillis how long to wait for further reports before emitting the latest.
 */
class SyncStatusCoalescer(
    private val emit: (SyncManager.SyncStatus.Syncing) -> Unit,
    private val intervalMillis: Long,
) {
    private val latest = AtomicReference<SyncManager.SyncStatus.Syncing?>(null)
    val emissionCount = AtomicInteger(0)
    private var scope: CoroutineScope? = null
    private var pending: Job? = null

    fun start(scope: CoroutineScope) {
        this.scope = scope
    }

    fun report(status: SyncManager.SyncStatus.Syncing) {
        latest.set(status)
        val s = scope ?: return
        // Re-arm: a single deferred emission per quiet window coalesces the burst.
        pending?.cancel()
        pending = s.launch {
            delay(intervalMillis)
            flush()
        }
    }

    /** Emits any stashed value immediately; the per-window deferred also calls this. */
    fun flush() {
        latest.getAndSet(null)?.let { status ->
            emit(status)
            emissionCount.incrementAndGet()
        }
    }

    /** Cancels any pending deferred emission so the owning scope can complete. */
    suspend fun stop() {
        pending?.cancelAndJoin()
        pending = null
    }
}
