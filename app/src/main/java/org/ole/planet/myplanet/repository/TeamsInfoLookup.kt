package org.ole.planet.myplanet.repository

interface TeamsInfoLookup {
    suspend fun getTeamLabelInfo(teamId: String): TeamLabelInfo?
    suspend fun getJoinRequestInfo(requestId: String?): JoinRequestInfo?
    suspend fun getJoinRequestsInfo(requestIds: List<String>): List<JoinRequestInfo>
    suspend fun getTeamNamesByIds(ids: List<String>): Map<String, String>
}
