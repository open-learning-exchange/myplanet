package org.ole.planet.myplanet.repository

import org.ole.planet.myplanet.model.RealmMyLibrary
import org.ole.planet.myplanet.model.RealmUserModel

interface TeamRepository {
    suspend fun getTeamResources(teamId: String): List<RealmMyLibrary>

    suspend fun createTeam(
        name: String?,
        teamType: String?,
        map: Map<String, String>,
        isPublic: Boolean,
        isEnterprise: Boolean,
        user: RealmUserModel
    )
}

