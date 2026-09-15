package org.ole.planet.myplanet.ui.life

import android.app.Application
import android.content.Context
import android.widget.LinearLayout
import androidx.test.core.app.ApplicationProvider
import io.mockk.mockk
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.ole.planet.myplanet.callback.OnStartDragListener
import org.ole.planet.myplanet.model.MyLife
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class, sdk = [34])
class LifeAdapterTest {

    private lateinit var adapter: LifeAdapter
    private lateinit var context: Context

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        context.setTheme(com.google.android.material.R.style.Theme_MaterialComponents)
        adapter = LifeAdapter(
            context = context,
            mDragStartListener = mockk<OnStartDragListener>(),
            visibilityCallback = { _, _ -> },
            reorderCallback = { },
        )
    }

    private fun lifeItem(id: String, weight: Int): MyLife {
        return MyLife().apply {
            _id = id
            title = id
            imageId = id
            this.weight = weight
        }
    }

    private fun adapterWith(reorderCallback: (List<MyLife>) -> Unit): LifeAdapter {
        return LifeAdapter(
            context = context,
            mDragStartListener = mockk<OnStartDragListener>(),
            visibilityCallback = { _, _ -> },
            reorderCallback = reorderCallback,
        )
    }

    @Test
    fun `onCreateViewHolder inflates ViewBinding holder with all views`() {
        val parent = LinearLayout(context)
        val holder = adapter.onCreateViewHolder(parent, 0) as LifeAdapter.LifeViewHolder

        assertNotNull(holder.binding)
        assertNotNull(holder.binding.titleTextView)
        assertNotNull(holder.binding.itemImageView)
        assertNotNull(holder.binding.dragImageButton)
        assertNotNull(holder.binding.visibilityImageButton)
        assertNotNull(holder.binding.rvItemParentLayout)
    }

    @Test
    fun `onBindViewHolder binds title to TextView`() {
        val life = MyLife("ic_myhealth", "user1", "My Health").apply { isVisible = true }
        adapter.submitList(listOf(life))

        val parent = LinearLayout(context)
        val holder = adapter.onCreateViewHolder(parent, 0)
        adapter.onBindViewHolder(holder, 0)

        val lifeHolder = holder as LifeAdapter.LifeViewHolder
        assertEquals("My Health", lifeHolder.binding.titleTextView.text.toString())
    }

    @Test
    fun `onBindViewHolder applies visibility alpha based on isVisible`() {
        val visible = MyLife("ic_calendar", "user1", "Calendar").apply { isVisible = true }
        val hidden = MyLife("ic_references", "user1", "References").apply { isVisible = false }
        adapter.submitList(listOf(visible, hidden))

        val parent = LinearLayout(context)
        val holder = adapter.onCreateViewHolder(parent, 0)
        adapter.onBindViewHolder(holder, 0)
        val visibleAlpha = (holder as LifeAdapter.LifeViewHolder).binding.rvItemParentLayout.alpha

        val holder2 = adapter.onCreateViewHolder(parent, 0)
        adapter.onBindViewHolder(holder2, 1)
        val hiddenAlpha = (holder2 as LifeAdapter.LifeViewHolder).binding.rvItemParentLayout.alpha

        assertEquals(1f, visibleAlpha, 0.001f)
        assertEquals(0.5f, hiddenAlpha, 0.001f)
    }

    @Test
    fun `onItemMove reorders list and onItemMoveFinished propagates order`() {
        val a = MyLife("ic_a", "u", "A").apply { isVisible = true }
        val b = MyLife("ic_b", "u", "B").apply { isVisible = true }

        val captured = mutableListOf<List<MyLife>>()
        val reorderAdapter = adapterWith { captured += it }
        reorderAdapter.submitList(listOf(a, b))
        reorderAdapter.onItemMove(0, 1)
        reorderAdapter.onItemMoveFinished()

        assertEquals(1, captured.size)
        assertEquals("B", captured.first()[0].title)
        assertEquals("A", captured.first()[1].title)
    }

    @Test
    fun `onItemMove swaps positions and onItemMoveFinished invokes reorderCallback with reordered list`() {
        var receivedList: List<MyLife>? = null
        val reorderAdapter = adapterWith { list -> receivedList = list }

        reorderAdapter.submitList(listOf(lifeItem("a", 0), lifeItem("b", 1), lifeItem("c", 2)))
        val moved = reorderAdapter.onItemMove(0, 2)
        assertTrue(moved)
        reorderAdapter.onItemMoveFinished()

        assertNotNull(receivedList)
        assertEquals(listOf("b", "c", "a"), receivedList!!.map { it.title })
        assertEquals(3, receivedList!!.size)
    }

    @Test
    fun `onItemMoveFinished is a no-op when no drag occurred`() {
        var reorderInvoked = false
        val reorderAdapter = adapterWith { reorderInvoked = true }

        reorderAdapter.submitList(listOf(lifeItem("a", 0), lifeItem("b", 1)))
        reorderAdapter.onItemMoveFinished()

        assertFalse(reorderInvoked)
        assertEquals(2, reorderAdapter.itemCount)
    }

    @Test
    fun `onItemMoveFinished clears drag state before the callback so re-entry is a no-op`() {
        var invocations = 0
        lateinit var reentrantAdapter: LifeAdapter
        reentrantAdapter = adapterWith {
            invocations++
            reentrantAdapter.onItemMoveFinished()
        }

        reentrantAdapter.submitList(listOf(lifeItem("a", 0), lifeItem("b", 1)))
        reentrantAdapter.onItemMove(0, 1)
        reentrantAdapter.onItemMoveFinished()

        assertEquals(1, invocations)
    }
}
