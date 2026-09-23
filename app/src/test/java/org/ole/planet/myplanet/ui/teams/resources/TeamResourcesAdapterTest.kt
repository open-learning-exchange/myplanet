package org.ole.planet.myplanet.ui.teams.resources

import android.app.Application
import android.content.Context
import android.view.ContextThemeWrapper
import android.widget.FrameLayout
import androidx.recyclerview.widget.RecyclerView
import androidx.test.core.app.ApplicationProvider
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.ole.planet.myplanet.R
import org.ole.planet.myplanet.callback.OnResourcesUpdateListener
import org.ole.planet.myplanet.model.MyLibrary
import org.ole.planet.myplanet.utils.TestDispatcherProvider
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowLooper

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [32], application = Application::class)
class TeamResourcesAdapterTest {

    private lateinit var context: Context
    private val testDispatcher = UnconfinedTestDispatcher()
    private val dispatcherProvider = TestDispatcherProvider(testDispatcher)
    private var isUpdatedCalled = false
    private var removedResource: MyLibrary? = null
    private var removedPosition: Int = -1

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
            }
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
        ShadowLooper.runUiThreadTasksIncludingDelayedTasks()
        assertEquals(2, adapter.currentList.size)

        adapter.removeResourceAt(0)
        ShadowLooper.runUiThreadTasksIncludingDelayedTasks()

        assertEquals(1, adapter.currentList.size)
        assertEquals("res_2", adapter.currentList[0].id)
        assertTrue(isUpdatedCalled)
    }

    @Test
    fun testViewHolderJobCancellation() {
        val themedContext = ContextThemeWrapper(
            context,
            com.google.android.material.R.style.Theme_MaterialComponents_Light_NoActionBar
        )
        val binding = org.ole.planet.myplanet.databinding.RowTeamResourceBinding.inflate(
            android.view.LayoutInflater.from(themedContext),
            FrameLayout(themedContext),
            false
        )
        val viewHolder = TeamResourcesAdapter.ViewHolderTeamResources(binding)

        viewHolder.cancelPreviewJob()
        viewHolder.cancelPreviewJob()
    }
}
