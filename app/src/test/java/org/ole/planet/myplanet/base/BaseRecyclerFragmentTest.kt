package org.ole.planet.myplanet.base

import android.app.Application
import android.content.Context
import android.view.View
import android.widget.TextView
import androidx.recyclerview.widget.ListAdapter
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.ole.planet.myplanet.R
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], application = Application::class)
class BaseRecyclerFragmentTest {

    class TestBaseRecyclerFragment : BaseRecyclerFragment<Any>() {
        override fun getLayout(): Int = 0
        override suspend fun getAdapter(): ListAdapter<*, *> {
            throw NotImplementedError()
        }
    }

    @Test
    fun showNoData_withZeroCount_makesViewVisibleAndSetsMessage() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val textView = TextView(context)

        BaseRecyclerFragment.showNoData(textView, 0, "courses")

        assertEquals(View.VISIBLE, textView.visibility)
        assertEquals(context.getString(R.string.no_courses), textView.text.toString())
    }

    @Test
    fun showNoData_withNonZeroCount_makesViewGoneAndSetsMessage() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val textView = TextView(context)

        BaseRecyclerFragment.showNoData(textView, 1, "resources")

        assertEquals(View.GONE, textView.visibility)
        assertEquals(context.getString(R.string.no_resources), textView.text.toString())
    }

    @Test
    fun showNoData_withUnknownSource_setsDefaultMessage() {
        val context = ApplicationProvider.getApplicationContext<Context>()
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
