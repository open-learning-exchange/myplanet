package org.ole.planet.myplanet.ui.team

import androidx.lifecycle.ViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import org.ole.planet.myplanet.repository.TeamRepository
import org.ole.planet.myplanet.repository.TeamMemberStatus
import javax.inject.Inject

@HiltViewModel
class TeamViewModel @Inject constructor(
    private val teamRepository: TeamRepository
) : ViewModel() {
    suspend fun getTeamMemberStatuses(userId: String?, teamIds: List<String>): Map<String, TeamMemberStatus> {
        return teamRepository.getTeamMemberStatuses(userId, teamIds)
    }

    suspend fun isTeamNameExists(name: String, type: String, excludeTeamId: String? = null): Boolean {
        return teamRepository.isTeamNameExists(name, type, excludeTeamId)
    }

    suspend fun createTeam(
        category: String?,
        name: String,
        description: String,
        services: String,
        rules: String,
        teamType: String?,
        isPublic: Boolean,
        user: org.ole.planet.myplanet.model.RealmUserModel,
    ): Result<String> {
        return teamRepository.createTeam(category, name, description, services, rules, teamType, isPublic, user)
    }

    suspend fun updateTeam(
        teamId: String,
        name: String,
        description: String,
        services: String,
        rules: String,
        updatedBy: String?,
    ): Result<Boolean> {
        return teamRepository.updateTeam(teamId, name, description, services, rules, updatedBy)
    }

    suspend fun getMyTeamsFlow(userId: String): kotlinx.coroutines.flow.Flow<List<org.ole.planet.myplanet.model.RealmMyTeam>> {
        return teamRepository.getMyTeamsFlow(userId)
    }

    suspend fun getShareableEnterprises(): List<org.ole.planet.myplanet.model.RealmMyTeam> {
        return teamRepository.getShareableEnterprises()
    }

    suspend fun getShareableTeams(): List<org.ole.planet.myplanet.model.RealmMyTeam> {
        return teamRepository.getShareableTeams()
    }

    suspend fun getRecentVisitCounts(teamIds: List<String>): Map<String, Long> {
        return teamRepository.getRecentVisitCounts(teamIds)
    }

    suspend fun requestToJoin(teamId: String, userId: String?, userPlanetCode: String?, teamType: String?) {
        teamRepository.requestToJoin(teamId, userId, userPlanetCode, teamType)
    }

    suspend fun leaveTeam(teamId: String, userId: String?) {
        teamRepository.leaveTeam(teamId, userId)
    }
}
