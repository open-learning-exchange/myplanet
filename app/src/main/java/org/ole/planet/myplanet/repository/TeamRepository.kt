package org.ole.planet.myplanet.repository

import org.ole.planet.myplanet.model.RealmMyLibrary
import org.ole.planet.myplanet.model.RealmMyTeam
import org.ole.planet.myplanet.model.RealmTeamTask
import org.ole.planet.myplanet.model.RealmUserModel

sealed class TaskFilter {
    object ALL : TaskFilter()
    object MY_TASKS : TaskFilter()
    object COMPLETED : TaskFilter()
}

interface TeamRepository {
    suspend fun getTeamResources(teamId: String): List<RealmMyLibrary>
    suspend fun getTeams(userId: String, type: String?): List<RealmMyTeam>
    suspend fun searchTeams(query: String, type: String?): List<RealmMyTeam>
    suspend fun createTeam(name: String, description: String, type: String, teamType: String, services: String, rules: String, isPublic: Boolean, createdBy: String, parentCode: String, planetCode: String, userId: String)
    suspend fun updateTeam(teamId: String, name: String, description: String, services: String, rules: String, userId: String)
    suspend fun isTeamLeader(teamId: String, userId: String): Boolean
    suspend fun isMyTeam(teamId: String, userId: String): Boolean
    suspend fun getVisitCountForTeam(teamId: String): Long
    suspend fun getTeamLeaderId(teamId: String): String?
    suspend fun hasPendingRequest(teamId: String, userId: String): Boolean
    suspend fun leaveTeam(teamId: String, userId: String)
    suspend fun requestToJoin(teamId: String, userId: String, teamType: String)
    suspend fun getTasks(teamId: String, filter: TaskFilter, userId: String): List<RealmTeamTask>
    suspend fun createOrUpdateTask(taskId: String?, title: String, description: String, deadline: Long, teamId: String, planetCode: String, parentCode: String)
    suspend fun setTaskCompleted(taskId: String, isCompleted: Boolean)
    suspend fun deleteTask(taskId: String)
    suspend fun assignTask(taskId: String, assigneeUserId: String)
    suspend fun getJoinedMembers(teamId: String): List<RealmUserModel>
    suspend fun getUserById(userId: String): RealmUserModel?
    suspend fun getMyTeamsByUserId(userId: String, type: String?): List<RealmMyTeam>
}

