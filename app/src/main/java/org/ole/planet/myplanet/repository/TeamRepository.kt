package org.ole.planet.myplanet.repository

import org.ole.planet.myplanet.model.RealmMyLibrary
import org.ole.planet.myplanet.model.RealmTeamTask

interface TeamRepository {
    suspend fun getTeamResources(teamId: String): List<RealmMyLibrary>
    suspend fun isMember(userId: String?, teamId: String): Boolean
    suspend fun deleteTask(taskId: String)
    suspend fun upsertTask(task: RealmTeamTask)
}

