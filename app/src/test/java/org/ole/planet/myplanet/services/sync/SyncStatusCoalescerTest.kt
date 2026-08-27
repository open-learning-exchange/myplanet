package org.ole.planet.myplanet.services.sync

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class SyncStatusCoalescerTest {

    private fun syncing(itemsDone: Int) = SyncManager.SyncStatus.Syncing(
        phase = "phase", phaseIndex = 1, totalPhases = 4,
        itemsDone = itemsDone, itemsTotal = 14, countLabel = "$itemsDone/14",
    )

    @Test
    fun `burst of reports collapses to a single flush emission`() = runTest(UnconfinedTestDispatcher()) {
        val emitted = mutableListOf<SyncManager.SyncStatus.Syncing>()
        val coalescer = SyncStatusCoalescer({ emitted += it }, intervalMillis = 150L)

        // No scope bound, so reports only stash the value; nothing emits until flushed.
        repeat(14) { coalescer.report(syncing(it + 1)) }
        coalescer.flush()

        assertEquals(1, emitted.size)
        assertEquals(14, emitted[0].itemsDone)
    }

    @Test
    fun `reports within one quiet window collapse to a single deferred emission`() =
        runTest(UnconfinedTestDispatcher()) {
            val emitted = mutableListOf<SyncManager.SyncStatus.Syncing>()
            val coalescer = SyncStatusCoalescer({ emitted += it }, intervalMillis = 150L)
            coalescer.start(this)

            // Several reports within one window keep re-arming the same deferred.
            coalescer.report(syncing(1))
            coalescer.report(syncing(2))
            coalescer.report(syncing(3))
            advanceUntilIdle()

            assertEquals(1, emitted.size)
            assertEquals(3, emitted[0].itemsDone)

            // A second window of reports produces one more deferred emission.
            coalescer.report(syncing(10))
            coalescer.report(syncing(11))
            advanceUntilIdle()

            assertEquals(2, emitted.size)
            assertEquals(11, emitted[1].itemsDone)

            // stop() cancels the pending deferred, so the terminal value lands only via flush().
            coalescer.report(syncing(14))
            coalescer.stop()
            coalescer.flush()

            assertEquals(3, emitted.size)
            assertEquals(14, emitted[2].itemsDone)
        }

    @Test
    fun `fourteen concurrent reports collapse to far fewer than fourteen emissions`() =
        runTest(UnconfinedTestDispatcher()) {
            val emitted = mutableListOf<SyncManager.SyncStatus.Syncing>()
            val coalescer = SyncStatusCoalescer({ emitted += it }, intervalMillis = 150L)
            coalescer.start(this)

            // Simulate ~14 near-simultaneous completions within one quiet window.
            repeat(14) { coalescer.report(syncing(it + 1)) }
            advanceUntilIdle()
            coalescer.stop()
            coalescer.flush()

            assertTrue("expected far fewer than 14 emissions, got ${emitted.size}", emitted.size <= 2)
            assertEquals(14, emitted.last().itemsDone)
        }

    @Test
    fun `flush with nothing pending emits nothing`() = runTest(UnconfinedTestDispatcher()) {
        val emitted = mutableListOf<SyncManager.SyncStatus.Syncing>()
        val coalescer = SyncStatusCoalescer({ emitted += it }, intervalMillis = 150L)
        coalescer.start(this)
        coalescer.stop()
        coalescer.flush()
        assertTrue(emitted.isEmpty())
    }
}
