package org.ole.planet.myplanet.ui.chat

import android.app.Activity
import android.app.Application
import android.os.Looper
import android.widget.LinearLayout
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import io.mockk.mockk
import io.mockk.verify
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import org.junit.Assert.assertNotNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class)
class ChatShareTargetAdapterTest {

    private lateinit var activity: Activity
    private lateinit var onItemClick: (ChatShareTargetItem) -> Unit
    private lateinit var adapter: ChatShareTargetAdapter

    @Before
    fun setUp() {
        activity = Robolectric.buildActivity(AppCompatActivity::class.java).setup().get()
        activity.setTheme(com.google.android.material.R.style.Theme_MaterialComponents)
        onItemClick = mockk(relaxed = true)
        adapter = ChatShareTargetAdapter(onItemClick)
    }

    @Test
    fun `click item on replaced list invokes callback with new item`() {
        val recyclerView = RecyclerView(activity)
        recyclerView.layoutManager = LinearLayoutManager(activity)
        recyclerView.adapter = adapter

        val itemX = ChatShareTargetItem(title = "x", isGroup = false)
        val itemY = ChatShareTargetItem(title = "y", isGroup = false)

        val latch1 = CountDownLatch(1)
        adapter.submitList(listOf(itemX)) { latch1.countDown() }
        shadowOf(Looper.getMainLooper()).idle()
        latch1.await(2, TimeUnit.SECONDS)
        recyclerView.measure(0, 0)
        recyclerView.layout(0, 0, 1000, 1000)

        val latch2 = CountDownLatch(1)
        adapter.submitList(listOf(itemY)) { latch2.countDown() }
        latch2.await(2, TimeUnit.SECONDS)
        shadowOf(Looper.getMainLooper()).idle()
        recyclerView.measure(0, 0)
        recyclerView.layout(0, 0, 1000, 1000)

        val holder = recyclerView.findViewHolderForAdapterPosition(0)
        assertNotNull(holder)

        holder!!.itemView.performClick()

        verify(exactly = 1) { onItemClick(itemY) }
        verify(exactly = 0) { onItemClick(itemX) }
    }

    @Test
    fun `click on unattached holder does not invoke callback`() {
        val parent = LinearLayout(activity)
        val holder = adapter.onCreateViewHolder(parent, 1) // VIEW_TYPE_CHILD

        holder.itemView.performClick()

        verify(exactly = 0) { onItemClick(any()) }
    }
}
