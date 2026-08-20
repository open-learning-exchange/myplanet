package org.ole.planet.myplanet.ui.teams

import android.app.Application
import android.content.Context
import android.os.Build
import android.view.View
import android.widget.LinearLayout
import androidx.core.content.ContextCompat
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.ole.planet.myplanet.R
import org.ole.planet.myplanet.model.TeamDetails
import org.ole.planet.myplanet.model.TeamStatus
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowLooper

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [Build.VERSION_CODES.P], application = Application::class)
class TeamsAdapterTest {

    private lateinit var context: Context

    @Before
    fun setup() {
        context = ApplicationProvider.getApplicationContext()
        context.setTheme(R.style.AppTheme)
    }

    private fun createAdapter(isGuestUser: Boolean = false): TeamsAdapter {
        return TeamsAdapter(
            isGuestUser = isGuestUser,
            onItemClick = {},
            onFeedbackClick = {},
            onEditTeamClick = {},
            onLeaveTeamClick = {},
            onRequestToJoinClick = {}
        )
    }

    private fun createTeamDetails(
        id: String,
        name: String,
        visitCount: Long = 0,
        teamStatus: TeamStatus? = null
    ): TeamDetails {
        return TeamDetails(
            _id = id,
            name = name,
            teamType = "team",
            createdDate = 1000L,
            type = "team",
            status = "active",
            visitCount = visitCount,
            teamStatus = teamStatus,
            description = "Desc",
            services = null,
            rules = null,
            teamId = id
        )
    }

    private fun bindViewHolderForTeam(adapter: TeamsAdapter, team: TeamDetails): TeamsAdapter.TeamsViewHolder {
        var committed = false
        adapter.submitList(listOf(team)) {
            committed = true
        }

        while (!committed) {
            ShadowLooper.idleMainLooper()
        }

        val parent = LinearLayout(context)
        val holder = adapter.onCreateViewHolder(parent, 0)
        adapter.onBindViewHolder(holder, 0)
        return holder
    }

    @Test
    fun `test member team shows leave action button with daynight_textColor tint`() {
        val adapter = createAdapter(isGuestUser = false)
        val team = createTeamDetails(
            id = "team1",
            name = "Test Team",
            visitCount = 5,
            teamStatus = TeamStatus(
                isMember = true,
                isLeader = false,
                hasPendingRequest = false
            )
        )

        val holder = bindViewHolderForTeam(adapter, team)

        assertEquals(View.VISIBLE, holder.binding.joinLeave.visibility)
        assertTrue(holder.binding.joinLeave.isEnabled)
        assertNotNull(holder.binding.joinLeave.imageTintList)

        val expectedColor = ContextCompat.getColor(context, R.color.daynight_textColor)
        assertEquals(expectedColor, holder.binding.joinLeave.imageTintList?.defaultColor)
    }

    @Test
    fun `test leader team shows edit action button with daynight_textColor tint`() {
        val adapter = createAdapter(isGuestUser = false)
        val team = createTeamDetails(
            id = "team2",
            name = "Leader Team",
            visitCount = 10,
            teamStatus = TeamStatus(
                isMember = true,
                isLeader = true,
                hasPendingRequest = false
            )
        )

        val holder = bindViewHolderForTeam(adapter, team)

        assertEquals(View.VISIBLE, holder.binding.joinLeave.visibility)
        assertTrue(holder.binding.joinLeave.isEnabled)
        assertNotNull(holder.binding.joinLeave.imageTintList)

        val expectedColor = ContextCompat.getColor(context, R.color.daynight_textColor)
        assertEquals(expectedColor, holder.binding.joinLeave.imageTintList?.defaultColor)
    }

    @Test
    fun `test pending request team shows pending indicator tint and is disabled`() {
        val adapter = createAdapter(isGuestUser = false)
        val team = createTeamDetails(
            id = "team3",
            name = "Pending Team",
            visitCount = 2,
            teamStatus = TeamStatus(
                isMember = false,
                isLeader = false,
                hasPendingRequest = true
            )
        )

        val holder = bindViewHolderForTeam(adapter, team)

        assertEquals(View.VISIBLE, holder.binding.joinLeave.visibility)
        assertFalse(holder.binding.joinLeave.isEnabled)
        assertNotNull(holder.binding.joinLeave.imageTintList)

        val expectedColor = ContextCompat.getColor(context, R.color.pending_request_indicator)
        assertEquals(expectedColor, holder.binding.joinLeave.imageTintList?.defaultColor)
    }

    @Test
    fun `test non member team shows request to join action button with daynight_textColor tint`() {
        val adapter = createAdapter(isGuestUser = false)
        val team = createTeamDetails(
            id = "team4",
            name = "Joinable Team",
            visitCount = 0,
            teamStatus = TeamStatus(
                isMember = false,
                isLeader = false,
                hasPendingRequest = false
            )
        )

        val holder = bindViewHolderForTeam(adapter, team)

        assertEquals(View.VISIBLE, holder.binding.joinLeave.visibility)
        assertTrue(holder.binding.joinLeave.isEnabled)
        assertNotNull(holder.binding.joinLeave.imageTintList)

        val expectedColor = ContextCompat.getColor(context, R.color.daynight_textColor)
        assertEquals(expectedColor, holder.binding.joinLeave.imageTintList?.defaultColor)
    }

    @Test
    fun `test guest user hides action button`() {
        val adapter = createAdapter(isGuestUser = true)
        val team = createTeamDetails(
            id = "team5",
            name = "Guest Team",
            visitCount = 1,
            teamStatus = TeamStatus(
                isMember = true,
                isLeader = false,
                hasPendingRequest = false
            )
        )

        val holder = bindViewHolderForTeam(adapter, team)

        assertEquals(View.GONE, holder.binding.joinLeave.visibility)
    }
}
