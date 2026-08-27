package org.ole.planet.myplanet.ui.life

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.recyclerview.widget.RecyclerView
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
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
    fun `onItemMove swaps positions and onItemMoveFinished invokes reorderCallback and submitList`() {
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
        assertEquals(true, moved)
        adapter.onItemMoveFinished()

        assertNotNull(receivedList)
        assertEquals(listOf("b", "c", "a"), receivedList!!.map { it.title })
        // submitList made its own copy; adapter list reflects the reordered order
        assertEquals(listOf("b", "c", "a"), adapter.currentList.map { it.title })
    }

    @Test
    fun `onItemMoveFinished is a no-op when no drag occurred`() {
        val context: Context = ApplicationProvider.getApplicationContext()
        val adapter = LifeAdapter(
            context = context,
            mDragStartListener = noopDrag,
            visibilityCallback = { _, _ -> },
            reorderCallback = { _ -> throw AssertionError("reorderCallback should not be invoked") }
        )

        adapter.submitList(listOf(lifeItem("a", 0), lifeItem("b", 1)))
        adapter.onItemMoveFinished()
        assertEquals(listOf("a", "b"), adapter.currentList.map { it.title })
    }

    @Test
    fun `reorderCallback receives the same list instance that submitList uses`() {
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
        assertEquals(listOf("b", "a"), adapter.currentList.map { it.title })
    }
}
