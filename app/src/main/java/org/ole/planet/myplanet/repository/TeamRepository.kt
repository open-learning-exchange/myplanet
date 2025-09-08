package org.ole.planet.myplanet.repository

import org.ole.planet.myplanet.model.RealmMyLibrary
import org.ole.planet.myplanet.model.RealmMyTeam

interface TeamRepository {
    suspend fun getTeamResources(teamId: String): List<RealmMyLibrary>
    suspend fun getSelectableTeams(isEnterprise: Boolean): List<RealmMyTeam>
    suspend fun addLink(type: String, title: String, teamId: String)
}

