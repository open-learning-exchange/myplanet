package org.ole.planet.myplanet.repository

import javax.inject.Inject
import java.util.Locale
import java.util.UUID
import org.ole.planet.myplanet.datamanager.DatabaseService
import org.ole.planet.myplanet.model.RealmMyLibrary
import org.ole.planet.myplanet.model.RealmMyTeam

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

    override suspend fun getSelectableTeams(isEnterprise: Boolean): List<RealmMyTeam> {
        val type = if (isEnterprise) "enterprise" else ""
        return queryList(RealmMyTeam::class.java) {
            isEmpty("teamId")
            isNotEmpty("name")
            equalTo("type", type)
            notEqualTo("status", "archived")
        }
    }

    override suspend fun addLink(type: String, title: String, teamId: String) {
        executeTransaction { realm ->
            val team = realm.createObject(RealmMyTeam::class.java, UUID.randomUUID().toString())
            team.docType = "link"
            team.updated = true
            team.title = title
            team.route = "/${type.lowercase(Locale.ROOT)}/view/$teamId"
        }
    }

    private suspend fun getResourceIds(teamId: String): List<String> {
        return queryList(RealmMyTeam::class.java) {
            equalTo("teamId", teamId)
        }.mapNotNull { it.resourceId?.takeIf { id -> id.isNotBlank() } }
    }
}

