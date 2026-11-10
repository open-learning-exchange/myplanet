package org.ole.planet.myplanet.ui.user

import android.content.Context
import android.widget.FrameLayout
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.mockk.mockk
import io.mockk.verify
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.ole.planet.myplanet.callback.OnUserProfileClickListener
import org.ole.planet.myplanet.model.User
import org.robolectric.shadows.ShadowLooper

@RunWith(AndroidJUnit4::class)
class UsersAdapterTest {

    private lateinit var context: Context
    private lateinit var listener: OnUserProfileClickListener

    private val user1 = User(name = "user1", fullName = "User One", image = "image1.png")
    private val user2 = User(name = "user2", fullName = "User Two", image = "image2.png")

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        listener = mockk(relaxed = true)
    }

    @Test
    fun testClickListener_deliversCurrentRowUserOnTap() {
        val adapter = UsersAdapter(listener)
        val recyclerView = RecyclerView(context)
        recyclerView.layoutManager = LinearLayoutManager(context)
        recyclerView.adapter = adapter

        var committed = false
        adapter.submitList(listOf(user1, user2)) {
            committed = true
        }
        while (!committed) {
            ShadowLooper.idleMainLooper()
        }

        recyclerView.measure(0, 0)
        recyclerView.layout(0, 0, 1000, 1000)

        val viewHolder = recyclerView.findViewHolderForAdapterPosition(1) as UsersAdapter.ViewHolder
        viewHolder.itemView.performClick()

        verify(exactly = 1) { listener.onItemClick(user2) }
    }

    @Test
    fun testClickListener_deliversUpdatedUserAfterListUpdate() {
        val adapter = UsersAdapter(listener)
        val recyclerView = RecyclerView(context)
        recyclerView.layoutManager = LinearLayoutManager(context)
        recyclerView.adapter = adapter

        var committed = false
        adapter.submitList(listOf(user1)) {
            committed = true
        }
        while (!committed) {
            ShadowLooper.idleMainLooper()
        }

        recyclerView.measure(0, 0)
        recyclerView.layout(0, 0, 1000, 1000)

        val updatedUser1 = User(name = "user1", fullName = "User One Updated", image = "image1_updated.png")
        var committed2 = false
        adapter.submitList(listOf(updatedUser1)) {
            committed2 = true
        }
        while (!committed2) {
            ShadowLooper.idleMainLooper()
        }

        val viewHolder = recyclerView.findViewHolderForAdapterPosition(0) as UsersAdapter.ViewHolder
        viewHolder.itemView.performClick()

        verify(exactly = 1) { listener.onItemClick(updatedUser1) }
    }

    @Test
    fun testClickListener_doesNotInvokeListenerWhenNoPosition() {
        val adapter = UsersAdapter(listener)
        val parent = FrameLayout(context)
        val viewHolder = adapter.onCreateViewHolder(parent, 0)

        viewHolder.itemView.performClick()

        verify(exactly = 0) { listener.onItemClick(any()) }
    }
}
