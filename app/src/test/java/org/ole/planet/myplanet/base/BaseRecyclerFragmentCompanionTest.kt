package org.ole.planet.myplanet.base

import android.view.View
import android.widget.TextView
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.junit.Test
import org.ole.planet.myplanet.R

class BaseRecyclerFragmentCompanionTest {

    private val expectedMessageResId = R.string.no_course_matched_filter

    @Test
    fun testShowNoFilter_nullView() {
        BaseRecyclerFragment.showNoFilter(null, 0)
        // Should return early, no exception
    }

    @Test
    fun testShowNoFilter_countZero() {
        val mockView = mockk<TextView>(relaxed = true)

        BaseRecyclerFragment.showNoFilter(mockView, 0)

        verify(exactly = 1) { mockView.visibility = View.VISIBLE }
        verify(exactly = 1) { mockView.setText(expectedMessageResId) }
    }

    @Test
    fun testShowNoFilter_countGreaterThanZero() {
        val mockView = mockk<TextView>(relaxed = true)

        BaseRecyclerFragment.showNoFilter(mockView, 1)

        verify(exactly = 1) { mockView.visibility = View.GONE }
        verify(exactly = 1) { mockView.setText(expectedMessageResId) }
    }

    @Test
    fun testShowNoFilter_viewNotTextView() {
        // Use a strict mock to ensure determinism and avoid relaxed mock interference
        val mockView = mockk<View>()
        val mockTextView = mockk<TextView>(relaxed = true)

        every { mockView.visibility = any() } returns Unit
        every { mockView.findViewById<TextView>(R.id.tv_empty_message) } returns mockTextView

        BaseRecyclerFragment.showNoFilter(mockView, 0)

        verify(exactly = 1) { mockView.visibility = View.VISIBLE }
        verify(exactly = 1) { mockView.findViewById<TextView>(R.id.tv_empty_message) }
        verify(exactly = 1) { mockTextView.setText(expectedMessageResId) }
    }
}
