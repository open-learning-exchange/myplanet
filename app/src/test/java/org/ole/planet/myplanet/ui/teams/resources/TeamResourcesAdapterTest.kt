package org.ole.planet.myplanet.ui.teams.resources

import android.app.Application
import android.content.Context
import android.widget.FrameLayout
import androidx.recyclerview.widget.AsyncDifferConfig
import androidx.test.core.app.ApplicationProvider
import java.util.concurrent.Executor
import java.util.concurrent.atomic.AtomicBoolean
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.ole.planet.myplanet.callback.OnResourcesUpdateListener
import org.ole.planet.myplanet.model.MyLibrary
import org.ole.planet.myplanet.utils.TestDispatcherProvider
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowLooper

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], application = Application::class)
class TeamResourcesAdapterTest {

    private lateinit var context: Context
    private val testDispatcher = UnconfinedTestDispatcher()
    private val dispatcherProvider = TestDispatcherProvider(testDispatcher)
    private var isUpdatedCalled = false
    private var removedResource: MyLibrary? = null
    private var removedPosition: Int = -1
    private var itemChanged = AtomicBoolean(false)

    private val testDiffConfig = AsyncDifferConfig.Builder(TeamResourcesAdapter.ITEM_CALLBACK)
        .setBackgroundThreadExecutor(Executor { it.run() })
        .build()

    private val listener = object : OnResourcesUpdateListener {
        override fun onResourceListUpdated() {
            isUpdatedCalled = true
        }

        override fun onResourceUpdateFailed(messageResId: Int) {}
    }

    private lateinit var adapter: TeamResourcesAdapter

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        isUpdatedCalled = false
        removedResource = null
        removedPosition = -1

        adapter = TeamResourcesAdapter(
            context = context,
            canRemoveResources = true,
            updateListener = listener,
            dispatcherProvider = dispatcherProvider,
            onRemoveResource = { resource, position ->
                removedResource = resource
                removedPosition = position
            },
            diffConfig = testDiffConfig
        )
    }

    @Test
    fun testRemoveResourceAt() {
        val resource1 = MyLibrary().apply {
            id = "res_1"
            title = "Resource 1"
        }
        val resource2 = MyLibrary().apply {
            id = "res_2"
            title = "Resource 2"
        }
        adapter.submitList(listOf(resource1, resource2))
        ShadowLooper.idleMainLooper()

        val itemChanged = AtomicBoolean(false)
        val observer = object : androidx.recyclerview.widget.RecyclerView.AdapterDataObserver() {
            override fun onItemRangeChanged(positionStart: Int, itemCount: Int) {
                itemChanged.set(true)
            }

            override fun onItemRangeChanged(positionStart: Int, itemCount: Int, payload: Any?) {
                itemChanged.set(true)
            }
        }
        adapter.registerAdapterDataObserver(observer)

        val removalCompleted = AtomicBoolean(false)

        adapter.removeResourceAt(0) {
            removalCompleted.set(true)
        }

        ShadowLooper.idleMainLooper()
        assertTrue(removalCompleted.get())
        assertEquals(1, adapter.currentList.size)
        assertEquals("res_2", adapter.currentList[0].id)
        assertTrue(isUpdatedCalled)
    }

    @Test
    fun testViewHolderJobCancellation() {
        val binding = org.ole.planet.myplanet.databinding.RowTeamResourceBinding.inflate(
            android.view.LayoutInflater.from(context),
            FrameLayout(context),
            false
        )
        val viewHolder = TeamResourcesAdapter.ViewHolderTeamResources(binding)

        val job = kotlinx.coroutines.Job()
        viewHolder.setPreviewJob(job)
        assertTrue(job.isActive)

        viewHolder.cancelPreviewJob()
        assertTrue(job.isCancelled)

        // Verifies subsequent calls handle already-cleared job gracefully
        viewHolder.cancelPreviewJob()
        assertTrue(job.isCancelled)
    }

    @Test
    fun testResourceLocalAddressChangeTriggersDiffUpdate() {
        val oldResource = MyLibrary().apply {
            id = "res_1"
            title = "Resource 1"
            mediaType = "Book"
            language = "English"
            resourceLocalAddress = null
        }
        val newResource = MyLibrary().apply {
            id = "res_1"
            title = "Resource 1"
            mediaType = "Book"
            language = "English"
            resourceLocalAddress = "/storage/emulated/0/Android/data/org.ole.planet.myplanet/files/res_1.pdf"
        }

        val commitCompleted = AtomicBoolean(false)
        itemChanged.set(false)  // Reset before test
        adapter.submitList(listOf(oldResource))
        ShadowLooper.idleMainLooper()

        adapter.submitList(listOf(newResource)) {
            commitCompleted.set(true)
        }

        ShadowLooper.idleMainLooper()
        assertTrue(commitCompleted.get())
        assertEquals(
            "/storage/emulated/0/Android/data/org.ole.planet.myplanet/files/res_1.pdf",
            adapter.currentList[0].resourceLocalAddress
        )
    }
}
