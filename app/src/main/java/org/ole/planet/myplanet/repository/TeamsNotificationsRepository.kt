package org.ole.planet.myplanet.repository

import org.ole.planet.myplanet.model.TeamTask

data class TeamLabelInfo(
    val teamId: String,
    val name: String,
    val type: String
)

data class JoinRequestInfo(
    val id: String,
    val teamId: String,
    val userId: String
)

interface TeamsNotificationsRepository {
    suspend fun getTeamLabelInfo(teamId: String): TeamLabelInfo?
    suspend fun getJoinRequestInfo(requestId: String?): JoinRequestInfo?
    suspend fun getJoinRequestsInfo(requestIds: List<String>): List<JoinRequestInfo>
    suspend fun getTeamNamesByIds(ids: List<String>): Map<String, String>
    suspend fun getTaskById(id: String): TeamTask?
    suspend fun getTasksByIds(ids: List<String>): List<TeamTask>
    suspend fun getTasksByTitles(titles: List<String>): List<TeamTask>
    suspend fun getTasksForUserBetween(userId: String, start: Long, end: Long): List<TeamTask>
}
