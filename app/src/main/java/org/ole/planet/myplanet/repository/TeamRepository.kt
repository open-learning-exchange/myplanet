package org.ole.planet.myplanet.repository

import org.ole.planet.myplanet.model.RealmMyLibrary

interface TeamRepository {
    suspend fun getTeamResources(teamId: String): List<RealmMyLibrary>
    suspend fun deleteTask(taskId: String)
}

