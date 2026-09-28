package org.ole.planet.myplanet.ui.teams.tasks

import android.content.Context
import android.widget.FrameLayout
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.ole.planet.myplanet.R
import org.ole.planet.myplanet.model.TeamTask
import org.ole.planet.myplanet.utils.TimeUtils.formatDate

@RunWith(AndroidJUnit4::class)
class TeamsTasksAdapterTest {

    private lateinit var context: Context
    private lateinit var adapter: TeamsTasksAdapter

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        adapter = TeamsTasksAdapter(context, nonTeamMember = false)
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
}
