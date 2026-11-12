package org.ole.planet.myplanet.utils

import androidx.arch.core.executor.ArchTaskExecutor
import androidx.arch.core.executor.TaskExecutor
import androidx.fragment.app.Fragment
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class FlowExtensionsTest {

    private val testDispatcher = StandardTestDispatcher()

    @Before
    fun setup() {
        Dispatchers.setMain(testDispatcher)
        ArchTaskExecutor.getInstance().setDelegate(object : TaskExecutor() {
            override fun executeOnDiskIO(runnable: Runnable) {
                runnable.run()
            }
            override fun postToMainThread(runnable: Runnable) {
                runnable.run()
            }
            override fun isMainThread(): Boolean {
                return true
            }
        })
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
        ArchTaskExecutor.getInstance().setDelegate(null)
    }

    @Test
    fun `Fragment collectWhenStarted collects values when STARTED`() = runTest {
        val flow = MutableStateFlow(0)
        val collectedValues = mutableListOf<Int>()

        val lifecycleOwner = mockk<LifecycleOwner>()
        val lifecycleRegistry = LifecycleRegistry(lifecycleOwner)
        every { lifecycleOwner.lifecycle } returns lifecycleRegistry

        val fragment = mockk<Fragment>()
        every { fragment.viewLifecycleOwner } returns lifecycleOwner

        fragment.collectWhenStarted(flow) {
            collectedValues.add(it)
        }

        lifecycleRegistry.currentState = Lifecycle.State.CREATED
        testDispatcher.scheduler.advanceUntilIdle()
        assertEquals(emptyList<Int>(), collectedValues)

        lifecycleRegistry.currentState = Lifecycle.State.STARTED
        testDispatcher.scheduler.advanceUntilIdle()
        assertEquals(listOf(0), collectedValues)

        flow.value = 1
        testDispatcher.scheduler.advanceUntilIdle()
        assertEquals(listOf(0, 1), collectedValues)

        lifecycleRegistry.currentState = Lifecycle.State.CREATED
        flow.value = 2
        testDispatcher.scheduler.advanceUntilIdle()
        assertEquals(listOf(0, 1), collectedValues) // Should not collect when stopped

        lifecycleRegistry.currentState = Lifecycle.State.STARTED
        testDispatcher.scheduler.advanceUntilIdle()
        assertEquals(listOf(0, 1, 2), collectedValues) // Should collect latest value when restarted
    }

    @Test
    fun `Fragment collectLatestWhenStarted collects latest values when STARTED`() = runTest {
        val flow = MutableStateFlow(0)
        val collectedValues = mutableListOf<Int>()

        val lifecycleOwner = mockk<LifecycleOwner>()
        val lifecycleRegistry = LifecycleRegistry(lifecycleOwner)
        every { lifecycleOwner.lifecycle } returns lifecycleRegistry

        val fragment = mockk<Fragment>()
        every { fragment.viewLifecycleOwner } returns lifecycleOwner

        fragment.collectLatestWhenStarted(flow) {
            collectedValues.add(it)
        }

        lifecycleRegistry.currentState = Lifecycle.State.CREATED
        testDispatcher.scheduler.advanceUntilIdle()
        assertEquals(emptyList<Int>(), collectedValues)

        lifecycleRegistry.currentState = Lifecycle.State.STARTED
        testDispatcher.scheduler.advanceUntilIdle()
        assertEquals(listOf(0), collectedValues)

        flow.value = 1
        testDispatcher.scheduler.advanceUntilIdle()
        assertEquals(listOf(0, 1), collectedValues)
    }

    @Test
    fun `LifecycleOwner collectWhenStarted collects values when STARTED`() = runTest {
        val flow = MutableStateFlow(0)
        val collectedValues = mutableListOf<Int>()

        val lifecycleOwner = mockk<LifecycleOwner>()
        val lifecycleRegistry = LifecycleRegistry(lifecycleOwner)
        every { lifecycleOwner.lifecycle } returns lifecycleRegistry

        lifecycleOwner.collectWhenStarted(flow) {
            collectedValues.add(it)
        }

        lifecycleRegistry.currentState = Lifecycle.State.CREATED
        testDispatcher.scheduler.advanceUntilIdle()
        assertEquals(emptyList<Int>(), collectedValues)

        lifecycleRegistry.currentState = Lifecycle.State.STARTED
        testDispatcher.scheduler.advanceUntilIdle()
        assertEquals(listOf(0), collectedValues)

        flow.value = 1
        testDispatcher.scheduler.advanceUntilIdle()
        assertEquals(listOf(0, 1), collectedValues)

        lifecycleRegistry.currentState = Lifecycle.State.CREATED
        flow.value = 2
        testDispatcher.scheduler.advanceUntilIdle()
        assertEquals(listOf(0, 1), collectedValues)

        lifecycleRegistry.currentState = Lifecycle.State.STARTED
        testDispatcher.scheduler.advanceUntilIdle()
        assertEquals(listOf(0, 1, 2), collectedValues)
    }

    @Test
    fun `LifecycleOwner collectLatestWhenStarted collects latest values when STARTED`() = runTest {
        val flow = MutableStateFlow(0)
        val collectedValues = mutableListOf<Int>()

        val lifecycleOwner = mockk<LifecycleOwner>()
        val lifecycleRegistry = LifecycleRegistry(lifecycleOwner)
        every { lifecycleOwner.lifecycle } returns lifecycleRegistry

        lifecycleOwner.collectLatestWhenStarted(flow) {
            collectedValues.add(it)
        }

        lifecycleRegistry.currentState = Lifecycle.State.CREATED
        testDispatcher.scheduler.advanceUntilIdle()
        assertEquals(emptyList<Int>(), collectedValues)

        lifecycleRegistry.currentState = Lifecycle.State.STARTED
        testDispatcher.scheduler.advanceUntilIdle()
        assertEquals(listOf(0), collectedValues)

        flow.value = 1
        testDispatcher.scheduler.advanceUntilIdle()
        assertEquals(listOf(0, 1), collectedValues)
    }
}
