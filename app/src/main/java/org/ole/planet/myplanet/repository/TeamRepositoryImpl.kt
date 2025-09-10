package org.ole.planet.myplanet.repository

import javax.inject.Inject
import java.util.Date
import org.ole.planet.myplanet.datamanager.DatabaseService
import org.ole.planet.myplanet.model.RealmMyLibrary
import org.ole.planet.myplanet.model.RealmMyTeam
import org.ole.planet.myplanet.model.RealmUserModel
import org.ole.planet.myplanet.utilities.AndroidDecrypter

class TeamRepositoryImpl @Inject constructor(
    databaseService: DatabaseService,
) : RealmRepository(databaseService), TeamRepository {

    override suspend fun getTeamResources(teamId: String): List<RealmMyLibrary> {
        val resourceIds = getResourceIds(teamId)
        return if (resourceIds.isEmpty()) {
            emptyList()
        } else {
            queryList(RealmMyLibrary::class.java) {
                `in`("resourceId", resourceIds.toTypedArray())
            }
        }
    }

    private suspend fun getResourceIds(teamId: String): List<String> {
        return queryList(RealmMyTeam::class.java) {
            equalTo("teamId", teamId)
        }.mapNotNull { it.resourceId?.takeIf { id -> id.isNotBlank() } }
    }

    override suspend fun createTeam(
        name: String?,
        teamType: String?,
        map: Map<String, String>,
        isPublic: Boolean,
        isEnterprise: Boolean,
        user: RealmUserModel
    ) {
        executeTransaction { realm ->
            val teamId = AndroidDecrypter.generateIv()
            val team = realm.createObject(RealmMyTeam::class.java, teamId)
            team.status = "active"
            team.createdDate = Date().time
            if (isEnterprise) {
                team.type = "enterprise"
                team.services = map["services"]
                team.rules = map["rules"]
            } else {
                team.type = "team"
                team.teamType = teamType
            }
            team.name = name
            team.description = map["desc"]
            team.createdBy = user._id
            team.teamId = ""
            team.isPublic = isPublic
            team.userId = user.id
            team.parentCode = user.parentCode
            team.teamPlanetCode = user.planetCode
            team.updated = true

            val teamMemberObj =
                realm.createObject(RealmMyTeam::class.java, AndroidDecrypter.generateIv())
            teamMemberObj.userId = user._id
            teamMemberObj.teamId = teamId
            teamMemberObj.teamPlanetCode = user.planetCode
            teamMemberObj.userPlanetCode = user.planetCode
            teamMemberObj.docType = "membership"
            teamMemberObj.isLeader = true
            teamMemberObj.teamType = teamType
            teamMemberObj.updated = true
        }
    }
}

