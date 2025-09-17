package org.ole.planet.myplanet.repository

import android.content.Context
import org.ole.planet.myplanet.model.RealmMyLibrary
import org.ole.planet.myplanet.model.RealmTeamTask
import org.ole.planet.myplanet.model.RealmUserModel
import org.ole.planet.myplanet.service.UploadManager

interface TeamRepository {
    suspend fun getTeamResources(teamId: String): List<RealmMyLibrary>
    suspend fun isMember(userId: String?, teamId: String): Boolean
    suspend fun getTeamLeaderId(teamId: String): String?
    suspend fun isTeamLeader(teamId: String, userId: String?): Boolean
    suspend fun hasPendingRequest(teamId: String, userId: String?): Boolean
    suspend fun requestToJoin(teamId: String, user: RealmUserModel?, teamType: String?)
    suspend fun leaveTeam(teamId: String, userId: String?)
    suspend fun deleteTask(taskId: String)
    suspend fun upsertTask(task: RealmTeamTask)
    suspend fun assignTask(taskId: String, assigneeId: String?)
    suspend fun syncTeamActivities(context: Context, uploadManager: UploadManager)
}
