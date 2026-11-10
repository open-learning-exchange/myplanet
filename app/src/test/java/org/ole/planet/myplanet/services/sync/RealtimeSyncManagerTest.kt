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

    @Test
    fun testUpdatesForSetIgnoresUnwatchedTables() = runTest {
        val manager = RealtimeSyncManager()
        val updateOutside = TableDataUpdate("table_other", 1, 0, true)
        val updateInside = TableDataUpdate("table_a", 2, 0, true)

        val results = mutableListOf<TableDataUpdate>()
        val job = launch(UnconfinedTestDispatcher()) {
            manager.updatesFor(setOf("table_a", "table_b")).collect { results.add(it) }
        }

        manager.notifyTableUpdated(updateOutside)
        manager.notifyTableUpdated(updateInside)

        assertEquals(listOf(updateInside), results)
        job.cancel()
    }

    @Test
    fun testUpdatesForSetEmitsInOrderForMultipleTables() = runTest {
        val manager = RealtimeSyncManager()
        val update1 = TableDataUpdate("table_a", 1, 0, true)
        val update2 = TableDataUpdate("table_b", 2, 0, true)
        val update3 = TableDataUpdate("table_a", 3, 0, true)

        val results = mutableListOf<TableDataUpdate>()
        val job = launch(UnconfinedTestDispatcher()) {
            manager.updatesFor(setOf("table_a", "table_b")).collect { results.add(it) }
        }

        manager.notifyTableUpdated(update1)
        manager.notifyTableUpdated(update2)
        manager.notifyTableUpdated(update3)

        assertEquals(listOf(update1, update2, update3), results)
        job.cancel()
    }

    @Test
    fun testUpdatesForSingleStringDelegatesToSet() = runTest {
        val manager = RealtimeSyncManager()
        val updateTarget = TableDataUpdate("target_table", 1, 1, false)
        val updateOther = TableDataUpdate("other_table", 1, 1, false)

        val resultsSingle = mutableListOf<TableDataUpdate>()
        val resultsSet = mutableListOf<TableDataUpdate>()

        val job1 = launch(UnconfinedTestDispatcher()) {
            manager.updatesFor("target_table").collect { resultsSingle.add(it) }
        }
        val job2 = launch(UnconfinedTestDispatcher()) {
            manager.updatesFor(setOf("target_table")).collect { resultsSet.add(it) }
        }

        manager.notifyTableUpdated(updateOther)
        manager.notifyTableUpdated(updateTarget)

        assertEquals(listOf(updateTarget), resultsSingle)
        assertEquals(listOf(updateTarget), resultsSet)

        job1.cancel()
        job2.cancel()
    }

    @Test
    fun testUpdatesForEmptySetEmitsNothingAndDoesNotComplete() = runTest {
        val manager = RealtimeSyncManager()
        val update = TableDataUpdate("any_table", 1, 0, true)

        val results = mutableListOf<TableDataUpdate>()
        var isCompleted = false
        val job = launch(UnconfinedTestDispatcher()) {
            manager.updatesFor(emptySet()).collect { results.add(it) }
            isCompleted = true
        }

        manager.notifyTableUpdated(update)

        assertTrue(results.isEmpty())
        assertTrue(!isCompleted)
        job.cancel()
    }
}
