package org.ole.planet.myplanet.ui.sync

import androidx.fragment.app.Fragment
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import io.mockk.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.ole.planet.myplanet.callback.OnDiffRefreshListener
import org.ole.planet.myplanet.model.TableDataUpdate
import org.ole.planet.myplanet.services.sync.RealtimeSyncManager
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import androidx.lifecycle.lifecycleScope

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class RealtimeSyncHelperTest {

    private val testDispatcher = StandardTestDispatcher()
    private val testScope = TestScope(testDispatcher)

    private lateinit var mockFragment: Fragment
    private lateinit var mockMixin: RealtimeSyncMixin
    private lateinit var mockSyncManager: RealtimeSyncManager
    private lateinit var lifecycleRegistry: LifecycleRegistry

    private lateinit var helper: RealtimeSyncHelper

    @Before
    fun setup() {
        Dispatchers.setMain(testDispatcher)

        mockFragment = mockk(relaxed = true)
        mockMixin = mockk(relaxed = true)
        mockSyncManager = mockk(relaxed = true)

        lifecycleRegistry = LifecycleRegistry(mockFragment)

        mockkObject(RealtimeSyncManager.Companion)
        every { RealtimeSyncManager.getInstance() } returns mockSyncManager

        every { mockFragment.lifecycle } returns lifecycleRegistry
        every { mockFragment.viewLifecycleOwner } returns mockFragment


        helper = RealtimeSyncHelper(mockFragment, mockMixin)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
        unmockkAll()
    }

    @Test
    fun `test setupRealtimeSync distinct filtering and debounce`() = testScope.runTest {
        val flow = MutableSharedFlow<TableDataUpdate>()
        every { mockSyncManager.dataUpdateFlow } returns flow
        every { mockMixin.getWatchedTables() } returns listOf("test_table", "other_table")

        helper.setupRealtimeSync()

        // Start collection by moving lifecycle to STARTED
        lifecycleRegistry.currentState = Lifecycle.State.STARTED
        advanceUntilIdle()

        // 1. Unwatched table (should be filtered out completely)
        flow.emit(TableDataUpdate("unwatched", 1, 0))
        advanceTimeBy(301)
        advanceUntilIdle()
        verify(exactly = 0) { mockMixin.onDataUpdated("unwatched", any()) }

        // 2. Watched table
        flow.emit(TableDataUpdate("test_table", 1, 0))
        advanceTimeBy(301)
        advanceUntilIdle()
        verify(exactly = 1) { mockMixin.onDataUpdated("test_table", any()) }

        // 3. Same data (should be filtered by distinctUntilChanged)
        flow.emit(TableDataUpdate("test_table", 1, 0))
        advanceTimeBy(301)
        advanceUntilIdle()
        verify(exactly = 1) { mockMixin.onDataUpdated("test_table", any()) } // still 1

        // 4. New data for watched table
        flow.emit(TableDataUpdate("test_table", 2, 0))
        advanceTimeBy(301)
        advanceUntilIdle()
        verify(exactly = 2) { mockMixin.onDataUpdated("test_table", any()) }
    }

    @Test
    fun `test lifecycle collection stops when not started`() = testScope.runTest {
        val flow = MutableSharedFlow<TableDataUpdate>()
        every { mockSyncManager.dataUpdateFlow } returns flow
        every { mockMixin.getWatchedTables() } returns listOf("test_table")

        helper.setupRealtimeSync()

        lifecycleRegistry.currentState = Lifecycle.State.CREATED
        advanceUntilIdle()
        flow.emit(TableDataUpdate("test_table", 1, 0))
        advanceTimeBy(301)
        advanceUntilIdle()
        verify(exactly = 0) { mockMixin.onDataUpdated("test_table", any()) }

        lifecycleRegistry.currentState = Lifecycle.State.STARTED
        advanceUntilIdle()
        flow.emit(TableDataUpdate("test_table", 2, 0))
        advanceTimeBy(301)
        advanceUntilIdle()
        verify(exactly = 1) { mockMixin.onDataUpdated("test_table", any()) }

        lifecycleRegistry.currentState = Lifecycle.State.CREATED
        advanceUntilIdle()
        flow.emit(TableDataUpdate("test_table", 3, 0))
        advanceTimeBy(301)
        advanceUntilIdle()
        verify(exactly = 1) { mockMixin.onDataUpdated("test_table", any()) } // Still 1
    }

    @Test
    fun `test refreshRecyclerView with OnDiffRefreshListener`() = testScope.runTest {
        val flow = MutableSharedFlow<TableDataUpdate>()
        every { mockSyncManager.dataUpdateFlow } returns flow
        every { mockMixin.getWatchedTables() } returns listOf("test_table")
        every { mockMixin.shouldAutoRefresh("test_table") } returns true

        val mockRecyclerView = mockk<RecyclerView>(relaxed = true)
        val combinedAdapter = mockk<TestDiffAdapter>(relaxed = true)

        every { mockRecyclerView.adapter } returns combinedAdapter
        every { mockMixin.getSyncRecyclerView() } returns mockRecyclerView

        helper.setupRealtimeSync()
        lifecycleRegistry.currentState = Lifecycle.State.STARTED
        advanceUntilIdle()

        flow.emit(TableDataUpdate("test_table", 1, 0))
        advanceTimeBy(301)
        advanceUntilIdle()

        verify(exactly = 1) { combinedAdapter.refreshWithDiff() }
    }

    @Test
    fun `test refreshRecyclerView with ListAdapter`() = testScope.runTest {
        val flow = MutableSharedFlow<TableDataUpdate>()
        every { mockSyncManager.dataUpdateFlow } returns flow
        every { mockMixin.getWatchedTables() } returns listOf("test_table")
        every { mockMixin.shouldAutoRefresh("test_table") } returns true

        val mockRecyclerView = mockk<RecyclerView>(relaxed = true)
        val mockAdapter = mockk<ListAdapter<Any, *>>(relaxed = true)
        val testList = listOf(Any())
        every { mockAdapter.currentList } returns testList

        every { mockRecyclerView.adapter } returns mockAdapter
        every { mockMixin.getSyncRecyclerView() } returns mockRecyclerView

        helper.setupRealtimeSync()
        lifecycleRegistry.currentState = Lifecycle.State.STARTED
        advanceUntilIdle()

        flow.emit(TableDataUpdate("test_table", 1, 0))
        advanceTimeBy(301)
        advanceUntilIdle()

        verify(exactly = 1) { mockAdapter.submitList(testList) }
    }

    abstract class TestDiffAdapter : RecyclerView.Adapter<RecyclerView.ViewHolder>(), OnDiffRefreshListener
}
