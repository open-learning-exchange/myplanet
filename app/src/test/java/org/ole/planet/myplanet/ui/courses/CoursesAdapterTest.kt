package org.ole.planet.myplanet.ui.courses

import android.app.Application
import android.content.Context
import android.view.View
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
        adapter = CoursesAdapter(mockContext, false, false)
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
    fun `joined course keeps the checkbox laid out so list rows stay aligned`() {
        val joined = Course("1", "A", "desc", "grade", "subject", 0, 10, isMyCourse = true)

        assertEquals(View.INVISIBLE, adapter.checkboxVisibility(joined))
    }

    @Test
    fun `selectable course shows the checkbox`() {
        val selectable = Course("1", "A", "desc", "grade", "subject", 0, 10, isMyCourse = false)

        assertEquals(View.VISIBLE, adapter.checkboxVisibility(selectable))
    }

    @Test
    fun `my courses tab shows the checkbox for joined courses`() {
        val myCoursesAdapter = CoursesAdapter(mockContext, false, true)
        val joined = Course("1", "A", "desc", "grade", "subject", 0, 10, isMyCourse = true)

        assertEquals(View.VISIBLE, myCoursesAdapter.checkboxVisibility(joined))
    }

    @Test
    fun `guest collapses the checkbox on every row`() {
        val guestAdapter = CoursesAdapter(mockContext, true, false)
        val joined = Course("1", "A", "desc", "grade", "subject", 0, 10, isMyCourse = true)
        val selectable = Course("2", "B", "desc", "grade", "subject", 0, 10, isMyCourse = false)

        assertEquals(View.GONE, guestAdapter.checkboxVisibility(joined))
        assertEquals(View.GONE, guestAdapter.checkboxVisibility(selectable))
    }
}
