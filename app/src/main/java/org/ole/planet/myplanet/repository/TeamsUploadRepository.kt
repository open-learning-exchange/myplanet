package org.ole.planet.myplanet.repository

import org.ole.planet.myplanet.callback.OnSuccessListener

interface TeamsUploadRepository {
    suspend fun uploadTeams()
    suspend fun uploadTeamActivities()
    suspend fun uploadResource(listener: OnSuccessListener?)
}
