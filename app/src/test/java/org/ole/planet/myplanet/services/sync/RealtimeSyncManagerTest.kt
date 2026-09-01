package org.ole.planet.myplanet.services.sync

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.ole.planet.myplanet.model.TableDataUpdate

@OptIn(ExperimentalCoroutinesApi::class)
class RealtimeSyncManagerTest {

    @Test
    fun testNotifyTableUpdatedFlow() = runTest {
        val manager = RealtimeSyncManager()
        val update = TableDataUpdate("test_table_flow", 2, 3, false)

        val results = mutableListOf<TableDataUpdate>()
        val job = launch(UnconfinedTestDispatcher()) {
            manager.dataUpdateFlow.collect {
                results.add(it)
            }
        }

        manager.notifyTableUpdated(update)

        assertEquals(1, results.size)
        assertEquals(update, results[0])
        job.cancel()
    }

    @Test
    fun testMultipleCollectorsReceiveUpdates() = runTest {
        val manager = RealtimeSyncManager()
        val update = TableDataUpdate("shared_table", 1, 0, true)

        val results1 = mutableListOf<TableDataUpdate>()
        val results2 = mutableListOf<TableDataUpdate>()
        val job1 = launch(UnconfinedTestDispatcher()) {
            manager.dataUpdateFlow.collect { results1.add(it) }
        }
        val job2 = launch(UnconfinedTestDispatcher()) {
            manager.dataUpdateFlow.collect { results2.add(it) }
        }

        manager.notifyTableUpdated(update)

        assertEquals(listOf(update), results1)
        assertEquals(listOf(update), results2)
        job1.cancel()
        job2.cancel()
    }

    @Test
    fun testNotifyWithoutCollectorsDoesNotThrow() {
        val manager = RealtimeSyncManager()
        manager.notifyTableUpdated(TableDataUpdate("no_collectors", 0, 0))
        assertTrue(true)
    }

    @Test
    fun testBurstTableUpdatesNotDropped() = runTest {
        val manager = RealtimeSyncManager()
        val results = mutableListOf<TableDataUpdate>()

        val job = launch(StandardTestDispatcher(testScheduler)) {
            manager.dataUpdateFlow.collect { results.add(it) }
        }
        runCurrent()

        val updates = List(30) { index ->
            TableDataUpdate("table_$index", index, 0, false)
        }

        updates.forEach { update ->
            manager.notifyTableUpdated(update)
        }

        advanceUntilIdle()

        assertEquals(30, results.size)
        assertEquals(updates, results)
        job.cancel()
    }

    @Test
    fun testNewSubscriberDoesNotReceiveHistoricalUpdatesWhenReplayIsZero() = runTest {
        val manager = RealtimeSyncManager()
        val updates = List(5) { index ->
            TableDataUpdate("table_$index", index, 1, false)
        }

        updates.forEach { manager.notifyTableUpdated(it) }

        val results = mutableListOf<TableDataUpdate>()
        val job = launch(UnconfinedTestDispatcher()) {
            manager.dataUpdateFlow.collect { results.add(it) }
        }

        assertTrue(results.isEmpty())
        job.cancel()
    }
}
