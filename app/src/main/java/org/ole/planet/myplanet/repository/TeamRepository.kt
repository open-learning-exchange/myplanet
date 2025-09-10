package org.ole.planet.myplanet.repository

import org.ole.planet.myplanet.model.RealmMyLibrary
import org.ole.planet.myplanet.model.RealmMyTeam

interface TeamRepository {
    suspend fun getTeamResources(teamId: String): List<RealmMyLibrary>
    suspend fun getTeams(type: String?, search: String?): List<RealmMyTeam>
    suspend fun sortTeams(list: List<RealmMyTeam>): List<RealmMyTeam>
}

