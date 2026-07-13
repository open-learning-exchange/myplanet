package org.ole.planet.myplanet.services.sync

import java.lang.reflect.Modifier
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import org.ole.planet.myplanet.utils.MainDispatcherRule
import kotlinx.coroutines.test.runTest
import org.ole.planet.myplanet.utils.MainDispatcherRule
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
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
                if (Modifier.isStatic(it.modifiers)) {
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
}
