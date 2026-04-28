package org.ole.planet.myplanet.base

import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.JUnit4

@RunWith(JUnit4::class)
class BaseRecyclerFragmentTest {

    class TestBaseRecyclerFragment : BaseRecyclerFragment<String>() {
        override fun getLayout(): Int = 0
        override suspend fun getAdapter(): androidx.recyclerview.widget.RecyclerView.Adapter<out androidx.recyclerview.widget.RecyclerView.ViewHolder> {
            throw NotImplementedError()
        }
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
