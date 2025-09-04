package org.ole.planet.myplanet.repository

import org.ole.planet.myplanet.model.RealmMyLibrary
import org.ole.planet.myplanet.model.RealmMyTeam

interface TeamRepository {
    suspend fun getTeamResources(teamId: String): List<RealmMyLibrary>
    suspend fun getSelectableTeams(type: String): List<RealmMyTeam>
    suspend fun saveLinkItem(title: String, type: String, teamId: String)
}

