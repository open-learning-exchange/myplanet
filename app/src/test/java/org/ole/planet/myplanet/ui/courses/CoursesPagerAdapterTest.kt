package org.ole.planet.myplanet.ui.courses

import androidx.fragment.app.Fragment
import io.mockk.mockk
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class CoursesPagerAdapterTest {

    @Test
    fun `containsItem tests`() {
        val hostFragment = mockk<Fragment>(relaxed = true)
        val adapter = CoursesPagerAdapter(hostFragment, "course_1")

        // containsItem(0L) is always true (COURSE_DETAIL_ID)
        assertTrue(adapter.containsItem(0L))

        // Unknown id returns false
        assertFalse(adapter.containsItem(999L))

        // After submitList(listOf("a", "b")), getItemId(1) and getItemId(2) are contained
        adapter.submitList(listOf("a", "b"))
        val idA = adapter.getItemId(1)
        val idB = adapter.getItemId(2)

        assertTrue(adapter.containsItem(0L))
        assertTrue(adapter.containsItem(idA))
        assertTrue(adapter.containsItem(idB))

        // After submitList(listOf("b")), the old id of "a" is not contained and "b" keeps its id
        adapter.submitList(listOf("b"))
        assertFalse(adapter.containsItem(idA))
        assertTrue(adapter.containsItem(idB))
        val idB2 = adapter.getItemId(1)
        assertTrue(idB == idB2)

        // After re-adding "a", it gets the same id back and is contained again
        adapter.submitList(listOf("b", "a"))
        val readdedIdA = adapter.getItemId(2)
        assertTrue(idA == readdedIdA)
        assertTrue(adapter.containsItem(idA))
        assertTrue(adapter.containsItem(idB))

        // An unknown id returns false
        assertFalse(adapter.containsItem(999L))
    }
}
