package org.ole.planet.myplanet.utils

import android.view.View
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.ole.planet.myplanet.callback.OnItemDragStateListener
import org.ole.planet.myplanet.callback.OnItemMoveListener

class ItemReorderHelperTest {

    private lateinit var adapter: OnItemMoveListener
    private lateinit var itemReorderHelper: ItemReorderHelper

    @Before
    fun setUp() {
        adapter = mockk(relaxed = true)
        itemReorderHelper = ItemReorderHelper(adapter)
    }

    @Test
    fun `isLongPressDragEnabled should return true`() {
        assertTrue(itemReorderHelper.isLongPressDragEnabled)
    }

    @Test
    fun `isItemViewSwipeEnabled should return false`() {
        assertFalse(itemReorderHelper.isItemViewSwipeEnabled)
    }

    @Test
    fun `getMovementFlags with GridLayoutManager should return proper flags`() {
        val recyclerView: RecyclerView = mockk()
        val layoutManager: GridLayoutManager = mockk()
        val viewHolder: RecyclerView.ViewHolder = mockk()

        every { recyclerView.layoutManager } returns layoutManager

        val flags = itemReorderHelper.getMovementFlags(recyclerView, viewHolder)

        val expectedDragFlags = ItemTouchHelper.UP or ItemTouchHelper.DOWN or ItemTouchHelper.LEFT or ItemTouchHelper.RIGHT
        val expectedFlags = ItemTouchHelper.Callback.makeMovementFlags(expectedDragFlags, 0)
        assertEquals(expectedFlags, flags)
    }

    @Test
    fun `getMovementFlags with LinearLayoutManager should return proper flags`() {
        val recyclerView: RecyclerView = mockk()
        val layoutManager: LinearLayoutManager = mockk()
        val viewHolder: RecyclerView.ViewHolder = mockk()

        every { recyclerView.layoutManager } returns layoutManager

        val flags = itemReorderHelper.getMovementFlags(recyclerView, viewHolder)

        val expectedDragFlags = ItemTouchHelper.UP or ItemTouchHelper.DOWN
        val expectedSwipeFlags = ItemTouchHelper.START or ItemTouchHelper.END
        val expectedFlags = ItemTouchHelper.Callback.makeMovementFlags(expectedDragFlags, expectedSwipeFlags)
        assertEquals(expectedFlags, flags)
    }

    @Test
    fun `onMove should return false if itemViewTypes are different`() {
        val recyclerView: RecyclerView = mockk()
        val source: RecyclerView.ViewHolder = mockk()
        val target: RecyclerView.ViewHolder = mockk()

        every { source.itemViewType } returns 1
        every { target.itemViewType } returns 2

        val result = itemReorderHelper.onMove(recyclerView, source, target)

        assertFalse(result)
        verify(exactly = 0) { adapter.onItemMove(any(), any()) }
    }

    @Test
    fun `onMove should notify adapter and return true if itemViewTypes are same`() {
        val recyclerView: RecyclerView = mockk()
        val source: RecyclerView.ViewHolder = mockk()
        val target: RecyclerView.ViewHolder = mockk()

        every { source.itemViewType } returns 1
        every { target.itemViewType } returns 1
        every { source.bindingAdapterPosition } returns 0
        every { target.bindingAdapterPosition } returns 1
        every { adapter.onItemMove(0, 1) } returns true

        val result = itemReorderHelper.onMove(recyclerView, source, target)

        assertTrue(result)
        verify(exactly = 1) { adapter.onItemMove(0, 1) }
    }

    @Test
    fun `onSelectedChanged should call onItemSelected when actionState is not idle and viewHolder is OnItemDragStateListener`() {
        val itemView: View = mockk(relaxed = true)
        val viewHolder = object : RecyclerView.ViewHolder(itemView), OnItemDragStateListener {
            var selectedCalled = false
            override fun onItemSelected() {
                selectedCalled = true
            }
            override fun onItemClear(viewHolder: RecyclerView.ViewHolder?) {}
        }

        itemReorderHelper.onSelectedChanged(viewHolder, ItemTouchHelper.ACTION_STATE_DRAG)

        assertTrue(viewHolder.selectedCalled)
    }

    @Test
    fun `clearView should call onItemClear and restore alpha`() {
        val itemView: View = mockk(relaxed = true)
        val viewHolder = object : RecyclerView.ViewHolder(itemView), OnItemDragStateListener {
            var clearCalled = false
            override fun onItemSelected() {}
            override fun onItemClear(viewHolder: RecyclerView.ViewHolder?) {
                clearCalled = true
            }
        }
        val recyclerView: RecyclerView = mockk()

        itemReorderHelper.clearView(recyclerView, viewHolder)

        verify(exactly = 1) { itemView.alpha = ItemReorderHelper.ALPHA_FULL }
        assertTrue(viewHolder.clearCalled)
    }
}
