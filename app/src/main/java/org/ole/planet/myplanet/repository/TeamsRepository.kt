package org.ole.planet.myplanet.repository

import com.google.gson.JsonObject
import kotlinx.coroutines.flow.Flow
import org.ole.planet.myplanet.model.RealmMyLibrary
import org.ole.planet.myplanet.model.RealmMyTeam
import org.ole.planet.myplanet.model.RealmTeamTask
import org.ole.planet.myplanet.model.RealmUserModel
import org.ole.planet.myplanet.model.Transaction

data class JoinedMemberData(
    val user: RealmUserModel,
    val visitCount: Long,
    val lastVisitDate: Long?,
    val offlineVisits: String,
    val profileLastVisit: String,
    var isLeader: Boolean
)

data class TeamMemberStatus(
    val isMember: Boolean,
    val isLeader: Boolean,
    val hasPendingRequest: Boolean
)

data class JoinRequestNotification(
    val requesterName: String,
    val teamName: String,
    val requestId: String
)

interface TeamsRepository : TeamRepository, TeamMemberRepository, TeamTaskRepository
