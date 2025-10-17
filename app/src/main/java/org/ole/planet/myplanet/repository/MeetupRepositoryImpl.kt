package org.ole.planet.myplanet.repository

import javax.inject.Inject
import org.ole.planet.myplanet.datamanager.DatabaseService
import org.ole.planet.myplanet.model.RealmMeetup

class MeetupRepositoryImpl @Inject constructor(
    databaseService: DatabaseService,
) : RealmRepository(databaseService), MeetupRepository {

    override suspend fun getMeetupsForTeam(teamId: String): List<RealmMeetup> {
        return queryList(RealmMeetup::class.java) {
            equalTo("teamId", teamId)
        }
    }
}
