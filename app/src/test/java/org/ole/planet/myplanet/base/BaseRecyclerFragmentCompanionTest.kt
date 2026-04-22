package org.ole.planet.myplanet.base

import android.view.View
import android.widget.TextView
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.junit.Test
import org.ole.planet.myplanet.R

class BaseRecyclerFragmentCompanionTest {

    @Test
    fun testShowNoFilter_nullView() {
        BaseRecyclerFragment.showNoFilter(null, 0)
        // Should return early, no exception
    }

    @Test
    fun testShowNoFilter_countZero() {
        val mockView = mockk<TextView>(relaxed = true)

        BaseRecyclerFragment.showNoFilter(mockView, 0)

        verify { mockView.visibility = View.VISIBLE }
        verify { mockView.setText(R.string.no_course_matched_filter) }
    }

    @Test
    fun testShowNoFilter_countGreaterThanZero() {
        val mockView = mockk<TextView>(relaxed = true)

        BaseRecyclerFragment.showNoFilter(mockView, 1)

        verify { mockView.visibility = View.GONE }
        verify { mockView.setText(R.string.no_course_matched_filter) }
    }

    @Test
    fun testShowNoFilter_viewNotTextView() {
        val mockView = mockk<View>(relaxed = true)
        val mockTextView = mockk<TextView>(relaxed = true)
        every { mockView.findViewById<TextView>(R.id.tv_empty_message) } returns mockTextView

        BaseRecyclerFragment.showNoFilter(mockView, 0)

        verify { mockView.visibility = View.VISIBLE }
        verify { mockTextView.setText(R.string.no_course_matched_filter) }
    }
}
