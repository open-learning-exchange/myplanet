package org.ole.planet.myplanet.repository

import android.content.SharedPreferences
import com.google.gson.Gson
import com.google.gson.JsonObject
import javax.inject.Inject
import kotlinx.coroutines.flow.Flow
import org.ole.planet.myplanet.data.DatabaseService
import org.ole.planet.myplanet.di.AppPreferences
import org.ole.planet.myplanet.model.RealmMyLibrary
import org.ole.planet.myplanet.model.RealmMyTeam
import org.ole.planet.myplanet.model.RealmTeamTask
import org.ole.planet.myplanet.model.RealmUserModel
import org.ole.planet.myplanet.model.Transaction
import org.ole.planet.myplanet.service.UploadManager
import org.ole.planet.myplanet.service.UserSessionManager
import org.ole.planet.myplanet.service.sync.ServerUrlMapper

class TeamsRepositoryImpl @Inject constructor(
    databaseService: DatabaseService,
    userSessionManager: UserSessionManager,
    uploadManager: UploadManager,
    gson: Gson,
    @AppPreferences preferences: SharedPreferences,
    serverUrlMapper: ServerUrlMapper,
    private val teamMapper: TeamMapper
) : RealmRepository(databaseService), TeamsRepository {

    private val teamRepository = TeamRepositoryImpl(databaseService, uploadManager, preferences, serverUrlMapper, teamMapper)
    private val teamMemberRepository = TeamMemberRepositoryImpl(databaseService, userSessionManager, preferences)
    private val teamTaskRepository = TeamTaskRepositoryImpl(databaseService, userSessionManager, gson)

    // TeamRepository Delegation
    override suspend fun getMyTeamsFlow(userId: String) = teamRepository.getMyTeamsFlow(userId)
    override suspend fun getShareableTeams() = teamRepository.getShareableTeams()
    override suspend fun getShareableEnterprises() = teamRepository.getShareableEnterprises()
    override suspend fun getTeamResources(teamId: String) = teamRepository.getTeamResources(teamId)
    override suspend fun getTeamByDocumentIdOrTeamId(id: String) = teamRepository.getTeamByDocumentIdOrTeamId(id)
    override suspend fun getTeamByIdOrTeamId(id: String) = teamRepository.getTeamByIdOrTeamId(id)
    override suspend fun getTeamLinks() = teamRepository.getTeamLinks()
    override suspend fun getTeamById(teamId: String) = teamRepository.getTeamById(teamId)
    override suspend fun getTeamTransactions(teamId: String, startDate: Long?, endDate: Long?, sortAscending: Boolean) = teamRepository.getTeamTransactions(teamId, startDate, endDate, sortAscending)
    override suspend fun getTeamTransactionsWithBalance(teamId: String, startDate: Long?, endDate: Long?, sortAscending: Boolean) = teamRepository.getTeamTransactionsWithBalance(teamId, startDate, endDate, sortAscending)
    override suspend fun createTransaction(teamId: String, type: String, note: String, amount: Int, date: Long, parentCode: String?, planetCode: String?) = teamRepository.createTransaction(teamId, type, note, amount, date, parentCode, planetCode)
    override suspend fun getReportsFlow(teamId: String) = teamRepository.getReportsFlow(teamId)
    override suspend fun exportReportsAsCsv(reports: List<RealmMyTeam>, teamName: String) = teamRepository.exportReportsAsCsv(reports, teamName)
    override suspend fun addReport(report: JsonObject) = teamRepository.addReport(report)
    override suspend fun updateReport(reportId: String, payload: JsonObject) = teamRepository.updateReport(reportId, payload)
    override suspend fun archiveReport(reportId: String) = teamRepository.archiveReport(reportId)
    override suspend fun logTeamVisit(teamId: String, userName: String?, userPlanetCode: String?, userParentCode: String?, teamType: String?) = teamRepository.logTeamVisit(teamId, userName, userPlanetCode, userParentCode, teamType)
    override suspend fun createTeamAndAddMember(teamObject: JsonObject, user: RealmUserModel) = teamRepository.createTeamAndAddMember(teamObject, user)
    override suspend fun updateTeam(teamId: String, name: String, description: String, services: String, rules: String, updatedBy: String?) = teamRepository.updateTeam(teamId, name, description, services, rules, updatedBy)
    override suspend fun updateTeamDetails(teamId: String, name: String, description: String, services: String, rules: String, teamType: String, isPublic: Boolean, createdBy: String) = teamRepository.updateTeamDetails(teamId, name, description, services, rules, teamType, isPublic, createdBy)
    override suspend fun syncTeamActivities() = teamRepository.syncTeamActivities()
    override suspend fun getTeamType(teamId: String) = teamRepository.getTeamType(teamId)
    override suspend fun isTeamNameExists(name: String, type: String, excludeTeamId: String?) = teamRepository.isTeamNameExists(name, type, excludeTeamId)
    override suspend fun createEnterprise(name: String, description: String, services: String, rules: String, isPublic: Boolean, user: RealmUserModel) = teamRepository.createEnterprise(name, description, services, rules, isPublic, user)

    // TeamMemberRepository Delegation
    override suspend fun isMember(userId: String?, teamId: String) = teamMemberRepository.isMember(userId, teamId)
    override suspend fun isTeamLeader(teamId: String, userId: String?) = teamMemberRepository.isTeamLeader(teamId, userId)
    override suspend fun hasPendingRequest(teamId: String, userId: String?) = teamMemberRepository.hasPendingRequest(teamId, userId)
    override suspend fun getTeamMemberStatuses(userId: String?, teamIds: Collection<String>) = teamMemberRepository.getTeamMemberStatuses(userId, teamIds)
    override suspend fun getRecentVisitCounts(teamIds: Collection<String>) = teamMemberRepository.getRecentVisitCounts(teamIds)
    override suspend fun requestToJoin(teamId: String, userId: String?, userPlanetCode: String?, teamType: String?) = teamMemberRepository.requestToJoin(teamId, userId, userPlanetCode, teamType)
    override suspend fun respondToMemberRequest(teamId: String, userId: String, accept: Boolean) = teamMemberRepository.respondToMemberRequest(teamId, userId, accept)
    override suspend fun leaveTeam(teamId: String, userId: String?) = teamMemberRepository.leaveTeam(teamId, userId)
    override suspend fun removeMember(teamId: String, userId: String) = teamMemberRepository.removeMember(teamId, userId)
    override suspend fun addResourceLinks(teamId: String, resources: List<RealmMyLibrary>, user: RealmUserModel?) = teamMemberRepository.addResourceLinks(teamId, resources, user)
    override suspend fun removeResourceLink(teamId: String, resourceId: String) = teamMemberRepository.removeResourceLink(teamId, resourceId)
    override suspend fun getJoinedMembers(teamId: String) = teamMemberRepository.getJoinedMembers(teamId)
    override suspend fun getJoinedMembersWithVisitInfo(teamId: String) = teamMemberRepository.getJoinedMembersWithVisitInfo(teamId)
    override suspend fun getJoinedMemberCount(teamId: String) = teamMemberRepository.getJoinedMemberCount(teamId)
    override suspend fun getRequestedMembers(teamId: String) = teamMemberRepository.getRequestedMembers(teamId)
    override suspend fun updateTeamLeader(teamId: String, newLeaderId: String) = teamMemberRepository.updateTeamLeader(teamId, newLeaderId)
    override suspend fun getNextLeaderCandidate(teamId: String, excludeUserId: String?) = teamMemberRepository.getNextLeaderCandidate(teamId, excludeUserId)

    // TeamTaskRepository Delegation
    override suspend fun getTasksFlow(userId: String?) = teamTaskRepository.getTasksFlow(userId)
    override suspend fun getTasks(userId: String?) = teamTaskRepository.getTasks(userId)
    override suspend fun getTaskTeamInfo(taskId: String) = teamTaskRepository.getTaskTeamInfo(taskId)
    override suspend fun getJoinRequestTeamId(requestId: String) = teamTaskRepository.getJoinRequestTeamId(requestId)
    override suspend fun getTaskNotifications(userId: String?) = teamTaskRepository.getTaskNotifications(userId)
    override suspend fun getJoinRequestNotifications(userId: String?) = teamTaskRepository.getJoinRequestNotifications(userId)
    override suspend fun deleteTask(taskId: String) = teamTaskRepository.deleteTask(taskId)
    override suspend fun upsertTask(task: RealmTeamTask) = teamTaskRepository.upsertTask(task)
    override suspend fun createTask(title: String, description: String, deadline: Long, teamId: String, assigneeId: String?) = teamTaskRepository.createTask(title, description, deadline, teamId, assigneeId)
    override suspend fun updateTask(taskId: String, title: String, description: String, deadline: Long, assigneeId: String?) = teamTaskRepository.updateTask(taskId, title, description, deadline, assigneeId)
    override suspend fun assignTask(taskId: String, assigneeId: String?) = teamTaskRepository.assignTask(taskId, assigneeId)
    override suspend fun setTaskCompletion(taskId: String, completed: Boolean) = teamTaskRepository.setTaskCompletion(taskId, completed)
    override suspend fun getPendingTasksForUser(userId: String, start: Long, end: Long) = teamTaskRepository.getPendingTasksForUser(userId, start, end)
    override suspend fun markTasksNotified(taskIds: Collection<String>) = teamTaskRepository.markTasksNotified(taskIds)
    override suspend fun getTasksByTeamId(teamId: String) = teamTaskRepository.getTasksByTeamId(teamId)
    override suspend fun getAssignee(userId: String) = teamTaskRepository.getAssignee(userId)
}
