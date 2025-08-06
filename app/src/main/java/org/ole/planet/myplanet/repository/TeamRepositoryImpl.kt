package org.ole.planet.myplanet.repository

import javax.inject.Inject
import org.ole.planet.myplanet.datamanager.DatabaseService
import org.ole.planet.myplanet.model.RealmMyTeam

class TeamRepositoryImpl @Inject constructor(
    private val databaseService: DatabaseService
) : TeamRepository {

    override suspend fun acceptRequest(teamId: String, userId: String) {
        databaseService.executeTransactionAsync { realm ->
            realm.where(RealmMyTeam::class.java)
                .equalTo("teamId", teamId)
                .equalTo("userId", userId)
                .findFirst()
                ?.apply {
                    docType = "membership"
                    updated = true
                }
        }
    }

    override suspend fun rejectRequest(teamId: String, userId: String) {
        databaseService.executeTransactionAsync { realm ->
            realm.where(RealmMyTeam::class.java)
                .equalTo("teamId", teamId)
                .equalTo("userId", userId)
                .findFirst()
                ?.deleteFromRealm()
        }
    }
}

