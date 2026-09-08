package org.ole.planet.myplanet.ui.courses

import android.app.Application
import android.content.Context
import android.widget.LinearLayout
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.RecyclerView
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.ArgumentMatchers.any
import org.mockito.ArgumentMatchers.eq
import org.mockito.Mock
import org.mockito.Mockito.times
import org.mockito.Mockito.verify
import org.mockito.MockitoAnnotations
import org.ole.planet.myplanet.model.Course
import org.ole.planet.myplanet.utils.ListViewMode
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class)
class CoursesAdapterTest {

    @Mock
    lateinit var mockContext: Context

    @Mock
    lateinit var mockObserver: RecyclerView.AdapterDataObserver

    private lateinit var adapter: CoursesAdapter

    @Before
    fun setUp() {
        MockitoAnnotations.initMocks(this)
        val testContext = ApplicationProvider.getApplicationContext<Context>()
        testContext.setTheme(com.google.android.material.R.style.Theme_MaterialComponents)
        adapter = CoursesAdapter(testContext, false, false)
        adapter.registerAdapterDataObserver(mockObserver)
    }

    @Test
    fun `test selectAllItems sets all unowned courses and triggers notifyItemRangeChanged`() {
        val courses = listOf(
            Course("1", "A", "desc", "grade", "subject", 0, 10, isMyCourse = false),
            Course("2", "B", "desc", "grade", "subject", 0, 10, isMyCourse = true),
            Course("3", "C", "desc", "grade", "subject", 0, 10, isMyCourse = false)
        )
        adapter.submitList(courses)

        adapter.selectAllItems(true)

        assertEquals(true, adapter.areAllSelected())
        verify(mockObserver, times(1)).onItemRangeChanged(eq(0), eq(1), any())
        verify(mockObserver, times(0)).onItemRangeChanged(eq(1), eq(1), any())
        verify(mockObserver, times(1)).onItemRangeChanged(eq(2), eq(1), any())
    }

    @Test
    fun `test clearAllItems clears selection and triggers notifyItemRangeChanged`() {
        val courses = listOf(
            Course("1", "A", "desc", "grade", "subject", 0, 10, isMyCourse = false),
            Course("2", "B", "desc", "grade", "subject", 0, 10, isMyCourse = false)
        )
        adapter.submitList(courses)
        adapter.selectAllItems(true)

        adapter.selectAllItems(false)

        assertEquals(false, adapter.areAllSelected())
        verify(mockObserver, times(2)).onItemRangeChanged(eq(0), eq(1), any())
    }

    @Test
    fun `test empty list selection handles gracefully`() {
        adapter.submitList(emptyList())

        adapter.selectAllItems(true)

        assertEquals(false, adapter.areAllSelected())
    }

    @Test
    fun `test setViewMode passes PAYLOAD_VIEW_MODE`() {
        val courses = listOf(
            Course("1", "A", "desc", "grade", "subject", 0, 10, isMyCourse = false)
        )
        adapter.submitList(courses)

        adapter.setViewMode(ListViewMode.LIST)

        verify(mockObserver, times(1)).onItemRangeChanged(0, 1, CoursesAdapter.PAYLOAD_VIEW_MODE)
    }

    @Test
    fun `test updateIdentity passes PAYLOAD_IDENTITY`() {
        val courses = listOf(
            Course("1", "A", "desc", "grade", "subject", 0, 10, isMyCourse = false)
        )
        adapter.submitList(courses)

        adapter.updateIdentity(true)

        verify(mockObserver, times(1)).onItemRangeChanged(0, 1, CoursesAdapter.PAYLOAD_IDENTITY)
    }

    @Test
    fun `test partial bind handles PAYLOAD_IDENTITY`() {
        val course = Course("1", "A", "desc", "grade", "subject", 0, 10, isMyCourse = false)
        adapter.submitList(listOf(course))

        val context = ApplicationProvider.getApplicationContext<Context>()
        context.setTheme(com.google.android.material.R.style.Theme_MaterialComponents)
        val parent = LinearLayout(context)
        val holder = adapter.onCreateViewHolder(parent, adapter.getItemViewType(0)) as CoursesAdapter.GridViewHolder
        adapter.onBindViewHolder(holder, 0)

        adapter.updateIdentity(true)
        adapter.onBindViewHolder(holder, 0, mutableListOf(CoursesAdapter.PAYLOAD_IDENTITY))

        assertEquals(android.view.View.GONE, holder.binding.checkbox.visibility)
        assertEquals(false, holder.binding.checkbox.hasOnClickListeners())
    }

    @Test
    fun `test onViewRecycled handles GridViewHolder and ListViewHolder with destroyed activity`() {
        val activity = Robolectric.buildActivity(AppCompatActivity::class.java).setup().destroy().get()
        activity.setTheme(com.google.android.material.R.style.Theme_MaterialComponents)

        val destroyedActivityAdapter = CoursesAdapter(activity, false, false)
        val course = Course("1", "A", "desc", "grade", "subject", 0, 10, isMyCourse = false)
        destroyedActivityAdapter.submitList(listOf(course))

        val parent = LinearLayout(activity)

        destroyedActivityAdapter.setViewMode(ListViewMode.GRID)
        val gridHolder = destroyedActivityAdapter.onCreateViewHolder(parent, destroyedActivityAdapter.getItemViewType(0))
        destroyedActivityAdapter.onViewRecycled(gridHolder)

        destroyedActivityAdapter.setViewMode(ListViewMode.LIST)
        val listHolder = destroyedActivityAdapter.onCreateViewHolder(parent, destroyedActivityAdapter.getItemViewType(0))
        destroyedActivityAdapter.onViewRecycled(listHolder)
    }
}
