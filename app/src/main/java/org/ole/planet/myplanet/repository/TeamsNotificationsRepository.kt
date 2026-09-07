package org.ole.planet.myplanet.repository

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
}
