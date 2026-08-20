package org.ole.planet.myplanet.ui.courses

import android.app.Application
import android.content.Context
import androidx.recyclerview.widget.RecyclerView
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.Mock
import org.mockito.Mockito.times
import org.mockito.Mockito.verify
import org.mockito.MockitoAnnotations
import org.ole.planet.myplanet.model.Course
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [32], application = Application::class)
class CoursesAdapterTest {

    @Mock
    lateinit var mockContext: Context

    @Mock
    lateinit var mockObserver: RecyclerView.AdapterDataObserver

    private lateinit var adapter: CoursesAdapter

    @Before
    fun setUp() {
        MockitoAnnotations.initMocks(this)
        val testContext = androidx.test.core.app.ApplicationProvider.getApplicationContext<Context>()
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
        verify(mockObserver, times(1)).onItemRangeChanged(org.mockito.ArgumentMatchers.eq(0), org.mockito.ArgumentMatchers.eq(1), org.mockito.ArgumentMatchers.any())
        verify(mockObserver, times(0)).onItemRangeChanged(org.mockito.ArgumentMatchers.eq(1), org.mockito.ArgumentMatchers.eq(1), org.mockito.ArgumentMatchers.any())
        verify(mockObserver, times(1)).onItemRangeChanged(org.mockito.ArgumentMatchers.eq(2), org.mockito.ArgumentMatchers.eq(1), org.mockito.ArgumentMatchers.any())
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
        verify(mockObserver, times(2)).onItemRangeChanged(org.mockito.ArgumentMatchers.eq(0), org.mockito.ArgumentMatchers.eq(1), org.mockito.ArgumentMatchers.any())
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

        adapter.setViewMode(org.ole.planet.myplanet.utils.ListViewMode.LIST)

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

        val context = androidx.test.core.app.ApplicationProvider.getApplicationContext<Context>()
        context.setTheme(com.google.android.material.R.style.Theme_MaterialComponents)
        val parent = android.widget.LinearLayout(context)
        val holder = adapter.onCreateViewHolder(parent, adapter.getItemViewType(0)) as CoursesAdapter.GridViewHolder
        adapter.onBindViewHolder(holder, 0)

        adapter.updateIdentity(true)
        adapter.onBindViewHolder(holder, 0, mutableListOf(CoursesAdapter.PAYLOAD_IDENTITY))

        assertEquals(android.view.View.GONE, holder.binding.checkbox.visibility)
        assertEquals(false, holder.binding.checkbox.hasOnClickListeners())
    }
}
