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
@Config(application = Application::class)
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
            ResourceListModel(library, item, emptyList())
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
            ResourceListModel(library, item, emptyList())
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
        val resourceListModel = ResourceListModel(library, item, emptyList())
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

    @Test
    fun `test setLibraryList drops null elements and updates flags on valid elements`() {
        adapter.markItemAsOffline("2")
        adapter.setOpenedResourceIds(setOf("1"))

        val item1 = ResourceItem(
            id = "1", title = "Res 1", description = "desc", createdDate = 0L, averageRating = "0",
            timesRated = 0, resourceId = "res1", isOffline = false, _rev = "rev1", uploadDate = "date",
            filename = "file"
        )
        val item2 = ResourceItem(
            id = "2", title = "Res 2", description = "desc", createdDate = 0L, averageRating = "0",
            timesRated = 0, resourceId = "res2", isOffline = false, _rev = "rev2", uploadDate = "date",
            filename = "file"
        )
        val model1 = ResourceListModel(MyLibrary().apply { id = "1" }, item1, emptyList())
        val model2 = ResourceListModel(MyLibrary().apply { id = "2" }, item2, emptyList())

        adapter.setLibraryList(listOf(model1, null, model2))

        val currentList = adapter.currentList
        assertEquals(2, currentList.size)
        assertEquals("1", currentList[0].item.id)
        assertEquals(true, currentList[0].isOpened)
        assertEquals(false, currentList[0].isLocallyOffline)

        assertEquals("2", currentList[1].item.id)
        assertEquals(false, currentList[1].isOpened)
        assertEquals(true, currentList[1].isLocallyOffline)
    }

    @Test
    fun `meta line shows the size of a downloaded file stored under the library folder`() {
        val context = androidx.test.core.app.ApplicationProvider.getApplicationContext<Context>()
        context.setTheme(com.google.android.material.R.style.Theme_MaterialComponents)
        val dir = org.ole.planet.myplanet.utils.FileUtils.getExternalFilesDir(context)!!
        val file = org.ole.planet.myplanet.utils.FileUtils.getLibraryFile(dir, "lib1", "notes.txt")
        file.parentFile?.mkdirs()
        file.writeBytes(ByteArray(2048))

        val item = ResourceItem(
            id = "lib1", title = "Notes", description = "desc", createdDate = 0L, averageRating = "0",
            timesRated = 0, resourceId = "res1", isOffline = true, _rev = "rev1", uploadDate = "date",
            filename = "notes.txt"
        )
        val library = MyLibrary().apply { id = "lib1"; resourceLocalAddress = "notes.txt" }
        adapter.setLibraryList(listOf(ResourceListModel(library, item, emptyList())))

        val holder = adapter.onCreateViewHolder(android.widget.LinearLayout(context), adapter.getItemViewType(0)) as ResourcesAdapter.GridViewHolder
        adapter.onBindViewHolder(holder, 0)
        org.robolectric.shadows.ShadowLooper.idleMainLooper()

        val expectedSize = org.ole.planet.myplanet.utils.FileUtils.formatSize(context, 2048)
        org.junit.Assert.assertTrue(
            "meta was: ${holder.binding.tvMeta.text}",
            holder.binding.tvMeta.text.toString().contains(expectedSize)
        )
    }

    @Test
    fun `test selectAllItems false clears selections and notifies listener with empty list`() {
        val item1 = ResourceItem(
            id = "1", title = "Res 1", description = "desc", createdDate = 0L, averageRating = "0",
            timesRated = 0, resourceId = "res1", isOffline = false, _rev = "rev1", uploadDate = "date",
            filename = "file1"
        )
        val item2 = ResourceItem(
            id = "2", title = "Res 2", description = "desc", createdDate = 0L, averageRating = "0",
            timesRated = 0, resourceId = "res2", isOffline = false, _rev = "rev2", uploadDate = "date",
            filename = "file2"
        )
        val model1 = ResourceListModel(MyLibrary().apply { id = "1" }, item1, emptyList())
        val model2 = ResourceListModel(MyLibrary().apply { id = "2" }, item2, emptyList())
        adapter.setLibraryList(listOf(model1, model2))

        var selectedList: List<ResourceItem>? = null
        val listener = object : org.ole.planet.myplanet.callback.OnLibraryItemSelectedListener {
            override fun onSelectedListChange(list: List<ResourceItem>) {
                selectedList = list
            }
            override fun onTagClicked(tag: org.ole.planet.myplanet.model.TagItem) {}
            override fun onResourceClicked(item: ResourceItem) {}
        }
        adapter.setListener(listener)

        adapter.selectAllItems(true)
        assertEquals(true, adapter.areAllSelected())
        assertEquals(2, selectedList?.size)

        adapter.selectAllItems(false)
        assertEquals(false, adapter.areAllSelected())
        assertEquals(0, selectedList?.size)
    }
}
