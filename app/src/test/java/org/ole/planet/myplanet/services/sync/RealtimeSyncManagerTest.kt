package org.ole.planet.myplanet.services.sync

import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Test
import org.ole.planet.myplanet.callback.OnRealtimeSyncListener
import org.ole.planet.myplanet.model.TableDataUpdate

@OptIn(ExperimentalCoroutinesApi::class)
class RealtimeSyncManagerTest {

    @Test
    fun testGetInstance() {
        val instance1 = RealtimeSyncManager.getInstance()
        val instance2 = RealtimeSyncManager.getInstance()
        assertSame(instance1, instance2)
    }

    @Test
    fun testAddAndRemoveListener() {
        val manager = RealtimeSyncManager()
        val listener = mockk<OnRealtimeSyncListener>(relaxed = true)
        val update = TableDataUpdate("test_table", 1, 1, true)

        manager.addListener(listener)
        manager.notifyTableUpdated(update)
        verify(exactly = 1) { listener.onTableDataUpdated(update) }

        manager.removeListener(listener)
        manager.notifyTableUpdated(update)
        // verify still exactly 1 from the first call
        verify(exactly = 1) { listener.onTableDataUpdated(update) }
    }

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
}
