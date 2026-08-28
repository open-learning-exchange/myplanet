package org.ole.planet.myplanet.ui.life

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.recyclerview.widget.RecyclerView
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.ole.planet.myplanet.callback.OnStartDragListener
import org.ole.planet.myplanet.model.MyLife

@RunWith(AndroidJUnit4::class)
class LifeAdapterTest {

    private fun lifeItem(id: String, weight: Int): MyLife {
        return MyLife().apply {
            _id = id
            title = id
            imageId = id
            this.weight = weight
        }
    }

    private val noopDrag = object : OnStartDragListener {
        override fun onStartDrag(viewHolder: RecyclerView.ViewHolder?) {}
    }

    @Test
    fun `onItemMove swaps positions and onItemMoveFinished invokes reorderCallback with reordered list`() {
        var receivedList: List<MyLife>? = null

        val context: Context = ApplicationProvider.getApplicationContext()
        val adapter = LifeAdapter(
            context = context,
            mDragStartListener = noopDrag,
            visibilityCallback = { _, _ -> },
            reorderCallback = { list -> receivedList = list }
        )

        adapter.submitList(listOf(lifeItem("a", 0), lifeItem("b", 1), lifeItem("c", 2)))
        val moved = adapter.onItemMove(0, 2)
        assertTrue(moved)
        adapter.onItemMoveFinished()

        assertNotNull(receivedList)
        assertEquals(listOf("b", "c", "a"), receivedList!!.map { it.title })
        assertEquals(3, receivedList!!.size)
    }

    @Test
    fun `onItemMoveFinished is a no-op when no drag occurred`() {
        var reorderInvoked = false

        val context: Context = ApplicationProvider.getApplicationContext()
        val adapter = LifeAdapter(
            context = context,
            mDragStartListener = noopDrag,
            visibilityCallback = { _, _ -> },
            reorderCallback = { _ -> reorderInvoked = true }
        )

        adapter.submitList(listOf(lifeItem("a", 0), lifeItem("b", 1)))
        adapter.onItemMoveFinished()

        assertFalse(reorderInvoked)
        assertEquals(2, adapter.itemCount)
    }

    @Test
    fun `reorderCallback receives the reordered drag list directly`() {
        var receivedList: List<MyLife>? = null

        val context: Context = ApplicationProvider.getApplicationContext()
        val adapter = LifeAdapter(
            context = context,
            mDragStartListener = noopDrag,
            visibilityCallback = { _, _ -> },
            reorderCallback = { list -> receivedList = list }
        )

        adapter.submitList(listOf(lifeItem("a", 0), lifeItem("b", 1)))
        adapter.onItemMove(0, 1)
        adapter.onItemMoveFinished()

        assertNotNull(receivedList)
        assertEquals(listOf("b", "a"), receivedList!!.map { it.title })
    }
}
