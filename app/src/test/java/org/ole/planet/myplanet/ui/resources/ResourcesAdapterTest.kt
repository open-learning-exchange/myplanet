package org.ole.planet.myplanet.ui.resources

import android.app.Application
import android.content.Context
import androidx.recyclerview.widget.RecyclerView
import kotlinx.coroutines.Dispatchers
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.Mock
import org.mockito.Mockito.times
import org.mockito.Mockito.verify
import org.mockito.MockitoAnnotations
import org.ole.planet.myplanet.model.MyLibrary
import org.ole.planet.myplanet.model.ResourceItem
import org.ole.planet.myplanet.model.ResourceListModel
import org.ole.planet.myplanet.utils.DispatcherProvider
import org.ole.planet.myplanet.utils.ListViewMode
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [32], application = Application::class)
class ResourcesAdapterTest {

    @Mock
    lateinit var mockContext: Context

    @Mock
    lateinit var mockObserver: RecyclerView.AdapterDataObserver

    private lateinit var adapter: ResourcesAdapter
    private val dispatcherProvider = object : DispatcherProvider {
        override val main = Dispatchers.Unconfined
        override val default = Dispatchers.Unconfined
        override val io = Dispatchers.Unconfined
        override val unconfined = Dispatchers.Unconfined
    }

    @Before
    fun setUp() {
        MockitoAnnotations.initMocks(this)
        val testContext = androidx.test.core.app.ApplicationProvider.getApplicationContext<Context>()
        testContext.setTheme(com.google.android.material.R.style.Theme_MaterialComponents)
        adapter = ResourcesAdapter(testContext, false, emptySet(), "user", ListViewMode.GRID, dispatcherProvider)
        adapter.registerAdapterDataObserver(mockObserver)
    }

    @Test
    fun `test setViewMode passes PAYLOAD_VIEW_MODE`() {
        val item = ResourceItem(
            id = "1", title = "A", description = "desc", createdDate = 0L, averageRating = "0",
            timesRated = 0, resourceId = "res1", isOffline = false, _rev = "rev1", uploadDate = "date",
            filename = "file"
        )
        val library = MyLibrary().apply { id = "1" }
        val resources = listOf(
            ResourceListModel(library, item, null, emptyList())
        )
        adapter.setLibraryList(resources)

        adapter.setViewMode(ListViewMode.LIST)

        verify(mockObserver, times(1)).onItemRangeChanged(0, 1, ResourcesAdapter.PAYLOAD_VIEW_MODE)
    }

    @Test
    fun `test updateIdentity passes PAYLOAD_IDENTITY`() {
        val item = ResourceItem(
            id = "1", title = "A", description = "desc", createdDate = 0L, averageRating = "0",
            timesRated = 0, resourceId = "res1", isOffline = false, _rev = "rev1", uploadDate = "date",
            filename = "file"
        )
        val library = MyLibrary().apply { id = "1" }
        val resources = listOf(
            ResourceListModel(library, item, null, emptyList())
        )
        adapter.setLibraryList(resources)

        adapter.updateIdentity(true, "guest")

        verify(mockObserver, times(1)).onItemRangeChanged(0, 1, ResourcesAdapter.PAYLOAD_IDENTITY)
    }

    @Test
    fun `test partial bind handles PAYLOAD_IDENTITY`() {
        val item = ResourceItem(
            id = "1", title = "A", description = "desc", createdDate = 0L, averageRating = "0",
            timesRated = 0, resourceId = "res1", isOffline = false, _rev = "rev1", uploadDate = "date",
            filename = "file"
        )
        val library = MyLibrary().apply { id = "1" }
        val resourceListModel = ResourceListModel(library, item, null, emptyList())
        val resources = listOf(resourceListModel)
        adapter.setLibraryList(resources)

        val context = androidx.test.core.app.ApplicationProvider.getApplicationContext<Context>()
        context.setTheme(com.google.android.material.R.style.Theme_MaterialComponents)
        val parent = android.widget.LinearLayout(context)
        val holder = adapter.onCreateViewHolder(parent, adapter.getItemViewType(0)) as ResourcesAdapter.GridViewHolder
        adapter.onBindViewHolder(holder, 0)

        adapter.updateIdentity(true, "guest")
        adapter.onBindViewHolder(holder, 0, mutableListOf(ResourcesAdapter.PAYLOAD_IDENTITY))

        assertEquals(android.view.View.GONE, holder.binding.checkbox.visibility)
        assertEquals(false, holder.binding.checkbox.hasOnClickListeners())
    }
}
