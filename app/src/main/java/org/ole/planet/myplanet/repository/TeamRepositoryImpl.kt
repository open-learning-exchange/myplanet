package org.ole.planet.myplanet.repository

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
}

