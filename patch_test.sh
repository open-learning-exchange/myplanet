cat << 'INNER_EOF' > app/src/test/java/org/ole/planet/myplanet/ui/sync/RealtimeSyncHelperTest.kt
package org.ole.planet.myplanet.ui.sync

import androidx.fragment.app.Fragment
import androidx.lifecycle.Lifecycle
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import io.mockk.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filter
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
import org.ole.planet.myplanet.callback.OnDiffRefreshListener
import org.ole.planet.myplanet.model.TableDataUpdate
import org.ole.planet.myplanet.services.sync.RealtimeSyncManager

@OptIn(ExperimentalCoroutinesApi::class)
class RealtimeSyncHelperTest {

    private val testDispatcher = StandardTestDispatcher()
    private val testScope = TestScope(testDispatcher)

    private lateinit var mockFragment: Fragment
    private lateinit var mockMixin: RealtimeSyncMixin
    private lateinit var mockSyncManager: RealtimeSyncManager

    @Before
    fun setup() {
        Dispatchers.setMain(testDispatcher)

        mockFragment = mockk(relaxed = true)
        mockMixin = mockk(relaxed = true)
        mockSyncManager = mockk(relaxed = true)

        mockkObject(RealtimeSyncManager.Companion)
        every { RealtimeSyncManager.getInstance() } returns mockSyncManager
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
        unmockkAll()
    }

    @Test
    fun `test debounce ignores rapid successive updates`() = testScope.runTest {
        val flow = MutableSharedFlow<TableDataUpdate>()
        every { mockMixin.getWatchedTables() } returns listOf("test_table")

        val job = launch {
            @OptIn(kotlinx.coroutines.FlowPreview::class)
            flow
                .filter { update -> mockMixin.getWatchedTables().contains(update.table) }
                .distinctUntilChanged { old, new ->
                    old.table == new.table &&
                    old.newItemsCount == new.newItemsCount &&
                    old.updatedItemsCount == new.updatedItemsCount
                }
                .debounce(300)
                .collect { update ->
                    mockMixin.onDataUpdated(update.table, update)
                }
        }

        advanceUntilIdle()

        // Emit two distinct updates rapidly
        flow.emit(TableDataUpdate("test_table", 1, 0))
        advanceTimeBy(100)
        flow.emit(TableDataUpdate("test_table", 2, 0))

        // Still within 300ms of first event, neither should be processed yet due to debounce restart
        advanceTimeBy(150)
        advanceUntilIdle()

        // Pass the remaining debounce window for the second event
        advanceTimeBy(151)
        advanceUntilIdle()

        // Only the second event should have been processed once
        verify(exactly = 1) { mockMixin.onDataUpdated("test_table", match { it.newItemsCount == 2 }) }

        job.cancel()
    }

    @Test
    fun `test distinct filtering logic`() = testScope.runTest {
        val flow = MutableSharedFlow<TableDataUpdate>()
        every { mockMixin.getWatchedTables() } returns listOf("test_table", "other_table")

        val job = launch {
            @OptIn(kotlinx.coroutines.FlowPreview::class)
            flow
                .filter { update -> mockMixin.getWatchedTables().contains(update.table) }
                .distinctUntilChanged { old, new ->
                    old.table == new.table &&
                    old.newItemsCount == new.newItemsCount &&
                    old.updatedItemsCount == new.updatedItemsCount
                }
                .debounce(300)
                .collect { update ->
                    mockMixin.onDataUpdated(update.table, update)
                }
        }

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

        job.cancel()
    }

    @Test
    fun `test refreshRecyclerView with OnDiffRefreshListener directly`() = testScope.runTest {
        val mockRecyclerView = mockk<RecyclerView>(relaxed = true)
        val combinedAdapter = mockk<TestDiffAdapter>(relaxed = true)

        every { mockRecyclerView.adapter } returns combinedAdapter
        every { mockMixin.getSyncRecyclerView() } returns mockRecyclerView

        val adapter = mockMixin.getSyncRecyclerView()?.adapter
        if (adapter is OnDiffRefreshListener) {
            adapter.refreshWithDiff()
        } else if (adapter is ListAdapter<*, *>) {
            @Suppress("UNCHECKED_CAST")
            (adapter as ListAdapter<Any, *>).let { listAdapter ->
                listAdapter.submitList(listAdapter.currentList.toList())
            }
        }

        verify(exactly = 1) { combinedAdapter.refreshWithDiff() }
    }

    @Test
    fun `test refreshRecyclerView with ListAdapter directly`() = testScope.runTest {
        val mockRecyclerView = mockk<RecyclerView>(relaxed = true)
        val mockAdapter = mockk<ListAdapter<Any, *>>(relaxed = true)
        val testList = listOf(Any())
        every { mockAdapter.currentList } returns testList

        every { mockRecyclerView.adapter } returns mockAdapter
        every { mockMixin.getSyncRecyclerView() } returns mockRecyclerView

        val adapter = mockMixin.getSyncRecyclerView()?.adapter
        if (adapter is OnDiffRefreshListener) {
            adapter.refreshWithDiff()
        } else if (adapter is ListAdapter<*, *>) {
            @Suppress("UNCHECKED_CAST")
            (adapter as ListAdapter<Any, *>).let { listAdapter ->
                listAdapter.submitList(listAdapter.currentList.toList())
            }
        }

        verify(exactly = 1) { mockAdapter.submitList(testList) }
    }

    @Test
    fun `test lifecycle collection stops when not started`() = testScope.runTest {
        val flow = MutableSharedFlow<TableDataUpdate>()
        every { mockMixin.getWatchedTables() } returns listOf("test_table")

        var isStarted = false

        val job = launch {
            @OptIn(kotlinx.coroutines.FlowPreview::class)
            flow
                .filter { isStarted } // Simulating repeatOnLifecycle(STARTED) behavior
                .filter { update -> mockMixin.getWatchedTables().contains(update.table) }
                .distinctUntilChanged { old, new ->
                    old.table == new.table &&
                    old.newItemsCount == new.newItemsCount &&
                    old.updatedItemsCount == new.updatedItemsCount
                }
                .debounce(300)
                .collect { update ->
                    mockMixin.onDataUpdated(update.table, update)
                }
        }

        advanceUntilIdle()

        // 1. Not started
        isStarted = false
        flow.emit(TableDataUpdate("test_table", 1, 0))
        advanceTimeBy(301)
        advanceUntilIdle()
        verify(exactly = 0) { mockMixin.onDataUpdated("test_table", any()) }

        // 2. Started
        isStarted = true
        flow.emit(TableDataUpdate("test_table", 2, 0))
        advanceTimeBy(301)
        advanceUntilIdle()
        verify(exactly = 1) { mockMixin.onDataUpdated("test_table", any()) }

        // 3. Stopped again
        isStarted = false
        flow.emit(TableDataUpdate("test_table", 3, 0))
        advanceTimeBy(301)
        advanceUntilIdle()
        verify(exactly = 1) { mockMixin.onDataUpdated("test_table", any()) } // Still 1

        job.cancel()
    }

    abstract class TestDiffAdapter : RecyclerView.Adapter<RecyclerView.ViewHolder>(), OnDiffRefreshListener
}
INNER_EOF
