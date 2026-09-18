package org.ole.planet.myplanet.ui.teams.resources

import android.app.Application
import android.content.Context
import android.view.View
import android.widget.LinearLayout
import androidx.test.core.app.ApplicationProvider
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.Dispatchers
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.Mock
import org.mockito.Mockito.verify
import org.mockito.MockitoAnnotations
import org.ole.planet.myplanet.R
import org.ole.planet.myplanet.callback.OnResourcesUpdateListener
import org.ole.planet.myplanet.model.MyLibrary
import org.ole.planet.myplanet.utils.DispatcherProvider
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowLooper

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [32], application = Application::class)
class TeamResourcesAdapterTest {

    @Mock
    private lateinit var mockUpdateListener: OnResourcesUpdateListener

    private lateinit var context: Context
    private val testDispatcher = Dispatchers.Unconfined
    private val dispatcherProvider = object : DispatcherProvider {
        override val main = testDispatcher
        override val default = testDispatcher
        override val io = testDispatcher
        override val unconfined = testDispatcher
    }

    @Before
    fun setUp() {
        MockitoAnnotations.initMocks(this)
        context = ApplicationProvider.getApplicationContext()
        context.setTheme(com.google.android.material.R.style.Theme_MaterialComponents)
    }

    @Test
    fun `test binding resource binds title and metadata`() {
        var removedResource: MyLibrary? = null
        var removedPosition: Int? = null

        val adapter = TeamResourcesAdapter(
            context = context,
            canRemoveResources = true,
            updateListener = mockUpdateListener,
            dispatcherProvider = dispatcherProvider,
            onRemoveResource = { res, pos ->
                removedResource = res
                removedPosition = pos
            }
        )

        val resource = MyLibrary().apply {
            id = "res1"
            title = "Test PDF Document"
            resourceLocalAddress = "test.pdf"
            language = "English"
        }

        val latch = CountDownLatch(1)
        adapter.submitList(listOf(resource)) { latch.countDown() }
        latch.await(2, TimeUnit.SECONDS)
        ShadowLooper.runUiThreadTasksIncludingDelayedTasks()

        val parent = LinearLayout(context)
        val holder = adapter.onCreateViewHolder(parent, 0)
        adapter.onBindViewHolder(holder, 0)

        assertEquals("Test PDF Document", holder.binding.tvTitle.text.toString())
        assertTrue(holder.binding.tvMeta.text.toString().contains(context.getString(R.string.filter_pdfs)))
        assertTrue(holder.binding.tvMeta.text.toString().contains("English"))
        assertEquals(View.VISIBLE, holder.binding.flRemoveContainer.visibility)
        assertEquals(context.getString(R.string.remove), holder.binding.flRemoveContainer.contentDescription)
    }

    @Test
    fun `test remove container hidden when canRemoveResources is false`() {
        val adapter = TeamResourcesAdapter(
            context = context,
            canRemoveResources = false,
            updateListener = mockUpdateListener,
            dispatcherProvider = dispatcherProvider,
            onRemoveResource = { _, _ -> }
        )

        val resource = MyLibrary().apply {
            id = "res1"
            title = "Test Document"
        }

        val latch = CountDownLatch(1)
        adapter.submitList(listOf(resource)) { latch.countDown() }
        latch.await(2, TimeUnit.SECONDS)
        ShadowLooper.runUiThreadTasksIncludingDelayedTasks()

        val parent = LinearLayout(context)
        val holder = adapter.onCreateViewHolder(parent, 0)
        adapter.onBindViewHolder(holder, 0)

        assertEquals(View.GONE, holder.binding.flRemoveContainer.visibility)
    }

    @Test
    fun `test clicking remove container triggers onRemoveResource`() {
        var removedResource: MyLibrary? = null
        var removedPosition: Int? = null

        val adapter = TeamResourcesAdapter(
            context = context,
            canRemoveResources = true,
            updateListener = mockUpdateListener,
            dispatcherProvider = dispatcherProvider,
            onRemoveResource = { res, pos ->
                removedResource = res
                removedPosition = pos
            }
        )

        val resource = MyLibrary().apply {
            id = "res1"
            title = "Remove Me"
        }

        val latch = CountDownLatch(1)
        adapter.submitList(listOf(resource)) { latch.countDown() }
        latch.await(2, TimeUnit.SECONDS)
        ShadowLooper.runUiThreadTasksIncludingDelayedTasks()

        val parent = LinearLayout(context)
        val holder = adapter.onCreateViewHolder(parent, 0)
        adapter.onBindViewHolder(holder, 0)

        holder.binding.flRemoveContainer.performClick()

        assertEquals(resource, removedResource)
        assertEquals(0, removedPosition)
    }

    @Test
    fun `test removeResourceAt updates list and calls updateListener`() {
        val adapter = TeamResourcesAdapter(
            context = context,
            canRemoveResources = true,
            updateListener = mockUpdateListener,
            dispatcherProvider = dispatcherProvider,
            onRemoveResource = { _, _ -> }
        )

        val res1 = MyLibrary().apply { id = "1"; title = "First" }
        val res2 = MyLibrary().apply { id = "2"; title = "Second" }

        val initLatch = CountDownLatch(1)
        adapter.submitList(listOf(res1, res2)) { initLatch.countDown() }
        initLatch.await(2, TimeUnit.SECONDS)
        ShadowLooper.runUiThreadTasksIncludingDelayedTasks()
        assertEquals(2, adapter.currentList.size)

        adapter.removeResourceAt(0)

        for (i in 0..10) {
            ShadowLooper.runUiThreadTasksIncludingDelayedTasks()
            if (adapter.currentList.size == 1) break
            Thread.sleep(50)
        }

        assertEquals(1, adapter.currentList.size)
        assertEquals("2", adapter.currentList[0].id)
        verify(mockUpdateListener).onResourceListUpdated()
    }
}
