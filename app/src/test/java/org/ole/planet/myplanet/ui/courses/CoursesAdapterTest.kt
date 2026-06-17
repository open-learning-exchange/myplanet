package org.ole.planet.myplanet.ui.courses

import android.content.Context
import androidx.recyclerview.widget.RecyclerView
import com.google.gson.JsonObject
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.Mock
import org.mockito.Mockito.verify
import org.mockito.Mockito.times
import org.mockito.MockitoAnnotations
import org.ole.planet.myplanet.model.Course
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import io.realm.Realm

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [32], application = android.app.Application::class)
class CoursesAdapterTest {

    @Mock
    lateinit var mockContext: Context

    @Mock
    lateinit var mockObserver: RecyclerView.AdapterDataObserver

    private lateinit var adapter: CoursesAdapter

    @Before
    fun setUp() {
        MockitoAnnotations.initMocks(this)
        val map = HashMap<String?, JsonObject>()
        adapter = CoursesAdapter(mockContext, map, false, false)
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
        verify(mockObserver).onItemRangeChanged(org.mockito.ArgumentMatchers.eq(0), org.mockito.ArgumentMatchers.eq(3), org.mockito.ArgumentMatchers.any())
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
        verify(mockObserver, times(2)).onItemRangeChanged(org.mockito.ArgumentMatchers.eq(0), org.mockito.ArgumentMatchers.eq(2), org.mockito.ArgumentMatchers.any())
    }

    @Test
    fun `test empty list selection handles gracefully`() {
        adapter.submitList(emptyList())

        adapter.selectAllItems(true)

        assertEquals(false, adapter.areAllSelected())
    }
}
