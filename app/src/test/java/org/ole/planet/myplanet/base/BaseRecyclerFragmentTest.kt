package org.ole.planet.myplanet.base

import android.app.Application
import android.view.View
import android.widget.TextView
import androidx.test.core.app.ApplicationProvider
import io.realm.RealmList
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.ole.planet.myplanet.R
import org.ole.planet.myplanet.model.RealmMyLibrary
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], application = Application::class)
class BaseRecyclerFragmentTest {

    class TestBaseRecyclerFragment : BaseRecyclerFragment<Any>() {
        override fun getLayout(): Int = 0
        override suspend fun getAdapter(): androidx.recyclerview.widget.RecyclerView.Adapter<out androidx.recyclerview.widget.RecyclerView.ViewHolder> {
            throw NotImplementedError()
        }
    }

    @Test
    fun showNoData_withZeroCount_makesViewVisibleAndSetsMessage() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val textView = TextView(context)

        BaseRecyclerFragment.showNoData(textView, 0, "courses")

        assertEquals(View.VISIBLE, textView.visibility)
        assertEquals(context.getString(R.string.no_courses), textView.text.toString())
    }

    @Test
    fun showNoData_withNonZeroCount_makesViewGoneAndSetsMessage() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val textView = TextView(context)

        BaseRecyclerFragment.showNoData(textView, 1, "resources")

        assertEquals(View.GONE, textView.visibility)
        assertEquals(context.getString(R.string.no_resources), textView.text.toString())
    }

    @Test
    fun showNoData_withUnknownSource_setsDefaultMessage() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val textView = TextView(context)

        BaseRecyclerFragment.showNoData(textView, 0, "unknown_source")

        assertEquals(View.VISIBLE, textView.visibility)
        assertEquals(context.getString(R.string.no_data_available_please_check_and_try_again), textView.text.toString())
    }

    @Test
    fun showNoData_withNullView_doesNothing() {
        // Just calling it to ensure no exceptions are thrown
        BaseRecyclerFragment.showNoData(null, 0, "courses")
    }

    @Test
    fun testApplyFilter() {
        val fragment = TestBaseRecyclerFragment()

        val lib1 = RealmMyLibrary().apply {
            subject = RealmList("Math")
            level = RealmList("Beginner")
            language = "English"
            mediaType = "Video"
        }

        val lib2 = RealmMyLibrary().apply {
            subject = RealmList("Science")
            level = RealmList("Advanced")
            language = "Spanish"
            mediaType = "PDF"
        }

        val libraries = listOf(lib1, lib2)

        // No filters
        assertEquals(2, fragment.applyFilter(libraries).size)

        // Subject filter
        fragment.subjects = mutableSetOf("Math")
        assertEquals(1, fragment.applyFilter(libraries).size)
        assertEquals(lib1, fragment.applyFilter(libraries)[0])

        // Reset and apply Language filter
        fragment.subjects = mutableSetOf()
        fragment.languages = mutableSetOf("Spanish")
        assertEquals(1, fragment.applyFilter(libraries).size)
        assertEquals(lib2, fragment.applyFilter(libraries)[0])

        // Multiple filters matching one
        fragment.languages = mutableSetOf("English")
        fragment.mediums = mutableSetOf("Video")
        assertEquals(1, fragment.applyFilter(libraries).size)
        assertEquals(lib1, fragment.applyFilter(libraries)[0])

        // Multiple filters matching none
        fragment.languages = mutableSetOf("English")
        fragment.mediums = mutableSetOf("PDF")
        assertEquals(0, fragment.applyFilter(libraries).size)
    }

    @Test
    fun testCountSelected_withNullSelectedItems() {
        val fragment = TestBaseRecyclerFragment()
        fragment.selectedItems = null
        assertEquals(0, fragment.countSelected())
    }

    @Test
    fun testCountSelected_withEmptySelectedItems() {
        val fragment = TestBaseRecyclerFragment()
        fragment.selectedItems = mutableListOf()
        assertEquals(0, fragment.countSelected())
    }

    @Test
    fun testCountSelected_withMultipleSelectedItems() {
        val fragment = TestBaseRecyclerFragment()
        fragment.selectedItems = mutableListOf("item1", "item2", "item3")
        assertEquals(3, fragment.countSelected())
    }
}
