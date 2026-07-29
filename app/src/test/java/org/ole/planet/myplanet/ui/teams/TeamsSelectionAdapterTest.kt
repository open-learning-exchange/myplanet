package org.ole.planet.myplanet.ui.teams

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.ole.planet.myplanet.model.TeamSummary

class TeamsSelectionAdapterTest {

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
