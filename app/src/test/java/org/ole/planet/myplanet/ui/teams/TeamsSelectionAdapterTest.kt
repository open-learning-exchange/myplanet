package org.ole.planet.myplanet.ui.teams

import android.app.Application
import android.content.Context
import android.view.ContextThemeWrapper
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.ole.planet.myplanet.R
import org.ole.planet.myplanet.model.TeamSummary
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class)
class TeamsSelectionAdapterTest {

    private lateinit var context: Context

    @Before
    fun setup() {
        context = ContextThemeWrapper(ApplicationProvider.getApplicationContext(), R.style.AppTheme_MaterialComponents)
    }

    private fun createTeamSummary(id: String, name: String): TeamSummary {
        return TeamSummary(
            _id = id,
            name = name,
            teamType = null,
            teamPlanetCode = null,
            createdDate = null,
            type = null,
            status = null,
            teamId = null,
            description = null,
            services = null,
            rules = null
        )
    }

    @Test
    fun testBind_setsTeamIconForTeamsSection() {
        val teamsSectionLabel = context.getString(R.string.teams)
        val adapter = TeamsSelectionAdapter(section = teamsSectionLabel) {}
        val team = createTeamSummary("1", "Team Alpha")

        val recyclerView = RecyclerView(context)
        recyclerView.layoutManager = LinearLayoutManager(context)
        recyclerView.adapter = adapter
        adapter.submitList(listOf(team))
        recyclerView.measure(0, 0)
        recyclerView.layout(0, 0, 100, 100)

        val holder = recyclerView.findViewHolderForAdapterPosition(0) as TeamsSelectionAdapter.TeamSelectionViewHolder

        val textView = holder.itemView.findViewById<TextView>(R.id.textView)
        val teamIcon = holder.itemView.findViewById<ImageView>(R.id.teamIcon)

        assertEquals("Team Alpha", textView.text.toString())
        assertEquals(R.drawable.team, shadowOf(teamIcon.drawable).createdFromResId)
    }

    @Test
    fun testBind_setsBusinessIconForNonTeamsSection() {
        val adapter = TeamsSelectionAdapter(section = "Other Section") {}
        val team = createTeamSummary("2", "Enterprise Beta")

        val recyclerView = RecyclerView(context)
        recyclerView.layoutManager = LinearLayoutManager(context)
        recyclerView.adapter = adapter
        adapter.submitList(listOf(team))
        recyclerView.measure(0, 0)
        recyclerView.layout(0, 0, 100, 100)

        val holder = recyclerView.findViewHolderForAdapterPosition(0) as TeamsSelectionAdapter.TeamSelectionViewHolder

        val textView = holder.itemView.findViewById<TextView>(R.id.textView)
        val teamIcon = holder.itemView.findViewById<ImageView>(R.id.teamIcon)

        assertEquals("Enterprise Beta", textView.text.toString())
        assertEquals(R.drawable.business, shadowOf(teamIcon.drawable).createdFromResId)
    }

    @Test
    fun testAreContentsTheSame() {
        val oldTeam = TeamSummary(
            _id = "1",
            name = "Team A",
            teamType = null,
            teamPlanetCode = null,
            createdDate = null,
            type = null,
            status = null,
            teamId = null,
            description = null,
            services = null,
            rules = null
        )

        val newTeamSame = TeamSummary(
            _id = "1",
            name = "Team A",
            teamType = "type",
            teamPlanetCode = null,
            createdDate = null,
            type = null,
            status = null,
            teamId = null,
            description = null,
            services = null,
            rules = null
        )

        val newTeamDifferentName = TeamSummary(
            _id = "1",
            name = "Team B",
            teamType = null,
            teamPlanetCode = null,
            createdDate = null,
            type = null,
            status = null,
            teamId = null,
            description = null,
            services = null,
            rules = null
        )

        val newTeamDifferentId = TeamSummary(
            _id = "2",
            name = "Team A",
            teamType = null,
            teamPlanetCode = null,
            createdDate = null,
            type = null,
            status = null,
            teamId = null,
            description = null,
            services = null,
            rules = null
        )

        val callback = org.ole.planet.myplanet.utils.DiffUtils.itemCallback<TeamSummary>(
            { old, new -> old._id == new._id },
            { old, new -> old.name == new.name && old._id == new._id }
        )

        assertTrue(callback.areContentsTheSame(oldTeam, newTeamSame))
        assertFalse(callback.areContentsTheSame(oldTeam, newTeamDifferentName))
        assertFalse(callback.areContentsTheSame(oldTeam, newTeamDifferentId))
    }
}
