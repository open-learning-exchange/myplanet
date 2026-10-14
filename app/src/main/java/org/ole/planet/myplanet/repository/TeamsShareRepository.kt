package org.ole.planet.myplanet.repository

import org.ole.planet.myplanet.model.TeamSummary

interface TeamsShareRepository {
    suspend fun getTeamSummaries(userId: String?): List<TeamSummary>
    suspend fun getShareableEnterpriseSummaries(userId: String?): List<TeamSummary>
    suspend fun getTeamSummaryById(teamId: String): TeamSummary?
}
