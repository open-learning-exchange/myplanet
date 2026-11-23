package org.ole.planet.myplanet.ui.teams.tasks

import android.content.Context
import android.view.View
import android.widget.FrameLayout
import android.widget.LinearLayout
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.ole.planet.myplanet.R
import org.ole.planet.myplanet.model.News
import org.ole.planet.myplanet.model.TeamTask
import org.ole.planet.myplanet.utils.TimeUtils.formatDate
import org.robolectric.shadows.ShadowLooper

@RunWith(AndroidJUnit4::class)
class TeamsTasksAdapterTest {

    private lateinit var context: Context
    private lateinit var adapter: TeamsTasksAdapter

    @Before
    fun setUp() {
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

    private fun bindDeadlineText(task: TeamTask): String {
        adapter.submitList(listOf(task))
        val holder = adapter.onCreateViewHolder(FrameLayout(context), 0)
        adapter.onBindViewHolder(holder, 0)
        return holder.binding.deadline.text.toString()
    }

    @Test
    fun completedTask_showsCompletedTimeNotDeadline() {
        val deadline = 1_700_000_000_000L
        val completedTime = 1_705_000_000_000L
        val task = TeamTask().apply {
            id = "t1"
            title = "Task"
            this.deadline = deadline
            this.completedTime = completedTime
            completed = true
        }

        val expected = context.getString(
            R.string.two_strings,
            context.getString(R.string.deadline_colon, formatDate(deadline)),
            context.getString(R.string.completed_colon, formatDate(completedTime))
        )
        assertEquals(expected, bindDeadlineText(task))
    }

    @Test
    fun completedTaskWithoutCompletedTime_showsOnlyDeadline() {
        val deadline = 1_700_000_000_000L
        val task = TeamTask().apply {
            id = "t1"
            title = "Task"
            this.deadline = deadline
            completedTime = 0
            completed = true
        }

        assertEquals(context.getString(R.string.deadline_colon, formatDate(deadline)), bindDeadlineText(task))
    }

    @Test
    fun rowBoundForNonMember_isUnlockedWhenReboundAfterJoining() {
        val task = TeamTask().apply { id = "t1"; title = "Task" }
        val lockedAdapter = TeamsTasksAdapter(context, nonTeamMember = true)
        lockedAdapter.submitList(listOf(task))
        val holder = lockedAdapter.onCreateViewHolder(FrameLayout(context), 0)
        lockedAdapter.onBindViewHolder(holder, 0)
        assertEquals(android.view.View.GONE, holder.binding.editTask.visibility)

        lockedAdapter.nonTeamMember = false
        lockedAdapter.onBindViewHolder(holder, 0)

        assertEquals(android.view.View.VISIBLE, holder.binding.editTask.visibility)
        assertEquals(android.view.View.VISIBLE, holder.binding.deleteTask.visibility)
        assertEquals(android.view.View.VISIBLE, holder.binding.icMore.visibility)
        assertEquals(true, holder.binding.statusBadge.isClickable)
    }

    @Test
    fun changingMembership_notifiesRowsToRebind() {
        adapter.submitList(listOf(TeamTask().apply { id = "t1" }, TeamTask().apply { id = "t2" }))
        var changedCount = 0
        adapter.registerAdapterDataObserver(object : androidx.recyclerview.widget.RecyclerView.AdapterDataObserver() {
            override fun onItemRangeChanged(positionStart: Int, itemCount: Int, payload: Any?) {
                changedCount += itemCount
            }
        })

        adapter.nonTeamMember = true
        adapter.nonTeamMember = true

        assertEquals(2, changedCount)
    }
}
