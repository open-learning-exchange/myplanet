package org.ole.planet.myplanet.repository

import org.ole.planet.myplanet.model.RealmMeetup

interface MeetupRepository {
    suspend fun getMeetupsForTeam(teamId: String): List<RealmMeetup>
}
