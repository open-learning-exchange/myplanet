package org.ole.planet.myplanet.ui.sync

import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import io.mockk.Runs
import io.mockk.coVerify
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.ole.planet.myplanet.callback.OnDiffRefreshListener
import org.ole.planet.myplanet.model.TableDataUpdate
import org.ole.planet.myplanet.services.sync.RealtimeSyncManager
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.android.controller.ActivityController
import org.robolectric.annotation.LooperMode

// Workaround for multiple inheritances via mockk interfaces
abstract class DiffRefreshAdapter : RecyclerView.Adapter<RecyclerView.ViewHolder>(), OnDiffRefreshListener

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@org.robolectric.annotation.Config(application = android.app.Application::class, sdk = [34])
@LooperMode(LooperMode.Mode.PAUSED)
class RealtimeSyncHelperTest {
    private val testDispatcher = StandardTestDispatcher()
    private lateinit var fragment: Fragment
    private lateinit var viewLifecycleOwner: LifecycleOwner
    private lateinit var mixin: RealtimeSyncMixin
    private lateinit var helper: RealtimeSyncHelper
    private lateinit var lifecycleRegistry: LifecycleRegistry
    private lateinit var viewLifecycleRegistry: LifecycleRegistry

    @Before
    fun setup() {
        Dispatchers.setMain(testDispatcher)

        fragment = mockk(relaxed = true)
        viewLifecycleOwner = mockk(relaxed = true)

        lifecycleRegistry = LifecycleRegistry(fragment)
        viewLifecycleRegistry = LifecycleRegistry(viewLifecycleOwner)

        every { fragment.lifecycle } returns lifecycleRegistry
        every { fragment.viewLifecycleOwner } returns viewLifecycleOwner
        every { viewLifecycleOwner.lifecycle } returns viewLifecycleRegistry

        mixin = mockk<RealtimeSyncMixin>(relaxed = true)
        helper = RealtimeSyncHelper(fragment, mixin)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun testFlowFilteringAndDebounce() = runTest(testDispatcher) {
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_START)
        viewLifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_START)
        runCurrent()

        every { mixin.getWatchedTables() } returns listOf("courses")
        every { mixin.shouldAutoRefresh(any()) } returns false

        helper.setupRealtimeSync()
        runCurrent()

        val syncManager = RealtimeSyncManager.getInstance()

        // Emitting for unwatched table
        syncManager.notifyTableUpdated(TableDataUpdate("surveys", 1, 0))
        runCurrent()

        // Emitting for watched table
        syncManager.notifyTableUpdated(TableDataUpdate("courses", 1, 0))
        runCurrent()

        // Distinct update for watched table, should be ignored
        syncManager.notifyTableUpdated(TableDataUpdate("courses", 1, 0))
        runCurrent()

        // Another update for watched table, to test debounce
        syncManager.notifyTableUpdated(TableDataUpdate("courses", 2, 0))
        runCurrent()

        advanceUntilIdle()

        // Only the last debounced update for 'courses' should be triggered
        coVerify(exactly = 1) { mixin.onDataUpdated("courses", any()) }
        coVerify(exactly = 0) { mixin.onDataUpdated("surveys", any()) }
    }

    @Test
    fun testAdapterBranchBehaviorForOnDiffRefreshListener() = runTest(testDispatcher) {
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_START)
        viewLifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_START)
        runCurrent()

        val recyclerView = mockk<RecyclerView>()
        val adapter = mockk<DiffRefreshAdapter>(relaxed = true)

        every { mixin.getWatchedTables() } returns listOf("courses")
        every { mixin.shouldAutoRefresh("courses") } returns true
        every { mixin.getSyncRecyclerView() } returns recyclerView
        every { recyclerView.adapter } returns adapter

        helper.setupRealtimeSync()
        runCurrent()

        val syncManager = RealtimeSyncManager.getInstance()
        syncManager.notifyTableUpdated(TableDataUpdate("courses", 1, 0))

        advanceUntilIdle()

        verify(exactly = 1) { adapter.refreshWithDiff() }
    }

    @Test
    fun testAdapterBranchBehaviorForListAdapter() = runTest(testDispatcher) {
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_START)
        viewLifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_START)
        runCurrent()

        val recyclerView = mockk<RecyclerView>()
        val adapter = mockk<ListAdapter<Any, RecyclerView.ViewHolder>>(relaxed = true)
        val currentList = listOf(Any(), Any())

        every { mixin.getWatchedTables() } returns listOf("courses")
        every { mixin.shouldAutoRefresh("courses") } returns true
        every { mixin.getSyncRecyclerView() } returns recyclerView
        every { recyclerView.adapter } returns adapter
        every { adapter.currentList } returns currentList

        helper.setupRealtimeSync()
        runCurrent()

        val syncManager = RealtimeSyncManager.getInstance()
        syncManager.notifyTableUpdated(TableDataUpdate("courses", 1, 0))

        advanceUntilIdle()

        verify(exactly = 1) { adapter.submitList(currentList) }
    }

    @Test
    fun testLifecycleBoundCollectionStopsWhenNotStarted() = runTest(testDispatcher) {
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_START)
        viewLifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_START)
        runCurrent()

        every { mixin.getWatchedTables() } returns listOf("courses")
        every { mixin.shouldAutoRefresh(any()) } returns false

        helper.setupRealtimeSync()
        runCurrent()

        val syncManager = RealtimeSyncManager.getInstance()

        // Move to STOPPED state
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_STOP)
        runCurrent()

        syncManager.notifyTableUpdated(TableDataUpdate("courses", 1, 0))
        runCurrent()

        advanceUntilIdle()

        coVerify(exactly = 0) { mixin.onDataUpdated(any(), any()) }

        // Move back to STARTED state
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_START)
        runCurrent()

        syncManager.notifyTableUpdated(TableDataUpdate("courses", 1, 0))
        runCurrent()

        advanceUntilIdle()

        coVerify(exactly = 1) { mixin.onDataUpdated("courses", any()) }
    }
}
