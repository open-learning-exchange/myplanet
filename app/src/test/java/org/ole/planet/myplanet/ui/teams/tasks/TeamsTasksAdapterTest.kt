package org.ole.planet.myplanet.ui.teams.tasks

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
import org.ole.planet.myplanet.model.TeamTask
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowLooper

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [Build.VERSION_CODES.P], application = Application::class)
class TeamsTasksAdapterTest {

    private lateinit var context: Context
    private lateinit var adapter: TeamsTasksAdapter

    @Before
    fun setup() {
        context = ApplicationProvider.getApplicationContext()
        context.setTheme(androidx.appcompat.R.style.Theme_AppCompat)
        adapter = TeamsTasksAdapter(context, nonTeamMember = false)
    }

    @Test
    fun `test comments section toggle and send actions`() {
        val task = TeamTask().apply {
            id = "t1"
            title = "Task Title"
            description = "Task Desc"
            deadline = 1000L
            completed = false
        }

        var sentTaskId: String? = null
        var sentMessage: String? = null
        adapter.setOnCommentActions(
            onSend = { id, msg ->
                sentTaskId = id
                sentMessage = msg
            },
            onDelete = {}
        )

        val comments = listOf(
            News().apply {
                id = "c1"
                replyTo = "t1"
                message = "Task comment 1"
                userName = "User1"
            }
        )
        adapter.updateComments(mapOf("t1" to comments))

        var committed = false
        adapter.submitList(listOf(task)) { committed = true }
        while (!committed) { ShadowLooper.idleMainLooper() }

        val parent = LinearLayout(context)
        val holder = adapter.onCreateViewHolder(parent, 0)
        adapter.onBindViewHolder(holder, 0)

        assertEquals("Comments (1)", holder.binding.tvCommentCount.text.toString())
        assertEquals(View.GONE, holder.binding.llCommentsContainer.visibility)

        // Toggle expand
        holder.binding.llCommentToggle.performClick()
        assertEquals(View.VISIBLE, holder.binding.llCommentsContainer.visibility)

        // Send comment
        holder.binding.etComment.setText("New task comment")
        holder.binding.btnSendComment.performClick()

        assertEquals("t1", sentTaskId)
        assertEquals("New task comment", sentMessage)
        assertEquals("", holder.binding.etComment.text.toString())
    }

    @Test
    fun `test nonTeamMember hides comment input`() {
        adapter.nonTeamMember = true
        val task = TeamTask().apply {
            id = "t2"
            title = "Task 2"
            deadline = 1000L
        }

        var committed = false
        adapter.submitList(listOf(task)) { committed = true }
        while (!committed) { ShadowLooper.idleMainLooper() }

        val parent = LinearLayout(context)
        val holder = adapter.onCreateViewHolder(parent, 0)
        adapter.onBindViewHolder(holder, 0)

        assertEquals(View.GONE, holder.binding.llCommentInput.visibility)
    }
}
