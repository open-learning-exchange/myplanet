package org.ole.planet.myplanet.ui.teams

import android.app.Application
import android.content.Context
import android.os.Build
import android.view.View
import android.widget.LinearLayout
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.ole.planet.myplanet.model.News
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowLooper

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [Build.VERSION_CODES.P], application = Application::class)
class InlineCommentsAdapterTest {

    private lateinit var context: Context
    private lateinit var adapter: InlineCommentsAdapter
    private var deletedComment: News? = null

    @Before
    fun setup() {
        context = ApplicationProvider.getApplicationContext()
        context.setTheme(androidx.appcompat.R.style.Theme_AppCompat)
        adapter = InlineCommentsAdapter(
            currentUserId = "user1",
            isLeader = false,
            onDeleteComment = { comment -> deletedComment = comment }
        )
    }

    @Test
    fun `test binding comment and delete button visibility for author`() {
        val comment1 = News().apply {
            id = "c1"
            userId = "user1"
            userName = "Alice"
            message = "Hello from Alice"
            time = 1000L
        }

        var committed = false
        adapter.submitList(listOf(comment1)) { committed = true }
        while (!committed) { ShadowLooper.idleMainLooper() }

        val parent = LinearLayout(context)
        val holder = adapter.onCreateViewHolder(parent, 0)
        adapter.onBindViewHolder(holder, 0)

        assertEquals("Alice", holder.binding.tvUserName.text.toString())
        assertEquals("Hello from Alice", holder.binding.tvMessage.text.toString())
        assertEquals(View.VISIBLE, holder.binding.btnDeleteComment.visibility)

        holder.binding.btnDeleteComment.performClick()
        assertEquals("c1", deletedComment?.id)
    }

    @Test
    fun `test delete button hidden for non-author and non-leader`() {
        val comment2 = News().apply {
            id = "c2"
            userId = "user2"
            userName = "Bob"
            message = "Hello from Bob"
            time = 1000L
        }

        var committed = false
        adapter.submitList(listOf(comment2)) { committed = true }
        while (!committed) { ShadowLooper.idleMainLooper() }

        val parent = LinearLayout(context)
        val holder = adapter.onCreateViewHolder(parent, 0)
        adapter.onBindViewHolder(holder, 0)

        assertEquals(View.GONE, holder.binding.btnDeleteComment.visibility)
    }

    @Test
    fun `test delete button visible for leader even if not author`() {
        adapter.updateCurrentUser(userId = "leader1", leader = true)

        val comment2 = News().apply {
            id = "c2"
            userId = "user2"
            userName = "Bob"
            message = "Hello from Bob"
            time = 1000L
        }

        var committed = false
        adapter.submitList(listOf(comment2)) { committed = true }
        while (!committed) { ShadowLooper.idleMainLooper() }

        val parent = LinearLayout(context)
        val holder = adapter.onCreateViewHolder(parent, 0)
        adapter.onBindViewHolder(holder, 0)

        assertEquals(View.VISIBLE, holder.binding.btnDeleteComment.visibility)
    }
}
