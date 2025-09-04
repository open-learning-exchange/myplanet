package org.ole.planet.myplanet.repository

import java.util.Locale
import java.util.UUID
import javax.inject.Inject
import org.ole.planet.myplanet.datamanager.DatabaseService
import org.ole.planet.myplanet.datamanager.queryList
import org.ole.planet.myplanet.model.RealmMyLibrary
import org.ole.planet.myplanet.model.RealmMyTeam

class TeamRepositoryImpl @Inject constructor(
    databaseService: DatabaseService,
) : RealmRepository(databaseService), TeamRepository {

    override suspend fun getTeamResources(teamId: String): List<RealmMyLibrary> {
        return withRealm { realm ->
            val resourceIds = RealmMyTeam.getResourceIds(teamId, realm)
            if (resourceIds.isEmpty()) emptyList() else
                realm.queryList(RealmMyLibrary::class.java) {
                    `in`("id", resourceIds.toTypedArray())
                }
        }
    }

    override suspend fun getSelectableTeams(type: String): List<RealmMyTeam> {
        val teamType = if (type == "Enterprises") "enterprise" else ""
        return queryList(RealmMyTeam::class.java) {
            isEmpty("teamId")
            isNotEmpty("name")
            equalTo("type", teamType)
            notEqualTo("status", "archived")
        }
    }

    override suspend fun saveLinkItem(title: String, type: String, teamId: String) {
        executeTransaction { realm ->
            val team = realm.createObject(RealmMyTeam::class.java, UUID.randomUUID().toString())
            team.docType = "link"
            team.updated = true
            team.title = title
            team.route = "/${type.lowercase(Locale.ROOT)}/view/$teamId"
        }
    }
}

