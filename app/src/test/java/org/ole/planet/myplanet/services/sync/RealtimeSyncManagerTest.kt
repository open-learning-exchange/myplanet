package org.ole.planet.myplanet.services.sync

import io.mockk.mockk
import io.mockk.verify
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Before
import org.junit.Test
import org.ole.planet.myplanet.callback.OnRealtimeSyncListener
import org.ole.planet.myplanet.model.TableDataUpdate

@OptIn(ExperimentalCoroutinesApi::class)
class RealtimeSyncManagerTest {

    @Before
    @After
    fun resetSingleton() {
        try {
            val clazz = RealtimeSyncManager.Companion::class.java
            val field = clazz.getDeclaredFields().find { it.name == "INSTANCE" }
                ?: RealtimeSyncManager::class.java.getDeclaredFields().find { it.name == "INSTANCE" }

            field?.let {
                it.isAccessible = true
                if (java.lang.reflect.Modifier.isStatic(it.modifiers)) {
                    it.set(null, null)
                } else {
                    it.set(RealtimeSyncManager.Companion, null)
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

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
        val secondUpdate = TableDataUpdate("test_table_2", 0, 0, true)
        manager.notifyTableUpdated(secondUpdate)
        verify(exactly = 0) { listener.onTableDataUpdated(secondUpdate) }
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

    @Test
    fun testConcurrentListenerModification() {
        val manager = RealtimeSyncManager()
        val executor = Executors.newFixedThreadPool(10)
        val listeners = List(100) { mockk<OnRealtimeSyncListener>(relaxed = true) }

        listeners.forEach { listener ->
            executor.execute {
                manager.addListener(listener)
                manager.removeListener(listener)
                manager.addListener(listener)
            }
        }

        executor.shutdown()
        executor.awaitTermination(5, TimeUnit.SECONDS)

        val update = TableDataUpdate("concurrent_test", 0, 0)
        manager.notifyTableUpdated(update)

        listeners.forEach { listener ->
            verify(exactly = 1) { listener.onTableDataUpdated(update) }
        }
    }
}
