package org.ole.planet.myplanet.repository

import android.content.Context
import org.ole.planet.myplanet.model.RealmMyLibrary
import org.ole.planet.myplanet.model.RealmMyTeam
import org.ole.planet.myplanet.model.RealmTeamTask
import org.ole.planet.myplanet.model.RealmUserModel

interface TeamRepository {
    suspend fun getTeamResources(teamId: String): List<RealmMyLibrary>
    suspend fun getTeamByDocumentIdOrTeamId(id: String): RealmMyTeam?
    suspend fun getTeamLinks(): List<RealmMyTeam>
    suspend fun getTeamById(teamId: String): RealmMyTeam?
    suspend fun isMember(userId: String?, teamId: String): Boolean
    suspend fun getTeamLeaderId(teamId: String): String?
    suspend fun isTeamLeader(teamId: String, userId: String?): Boolean
    suspend fun hasPendingRequest(teamId: String, userId: String?): Boolean
    suspend fun requestToJoin(teamId: String, user: RealmUserModel?, teamType: String?)
    suspend fun leaveTeam(teamId: String, userId: String?)
    suspend fun addResourceLinks(teamId: String, resources: List<RealmMyLibrary>, user: RealmUserModel?)
    suspend fun deleteTask(taskId: String)
    suspend fun upsertTask(task: RealmTeamTask)
    suspend fun assignTask(taskId: String, assigneeId: String?)
    suspend fun syncTeamActivities(context: Context)
}
