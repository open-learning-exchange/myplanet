package org.ole.planet.myplanet.repository

import org.ole.planet.myplanet.model.RealmMeetup
import org.ole.planet.myplanet.model.RealmUserModel

interface MeetupRepository {
    suspend fun getMeetupsForTeam(teamId: String): List<RealmMeetup>
    suspend fun getMeetupById(meetupId: String): RealmMeetup?
    suspend fun getJoinedMembers(meetupId: String): List<RealmUserModel>
    suspend fun toggleAttendance(meetupId: String, currentUserId: String?): RealmMeetup?
    suspend fun createLocalTeamMeetup(
        teamId: String,
        teamPlanetCode: String?,
        creatorName: String?,
        title: String,
        description: String,
        meetupLink: String,
        location: String,
        startDateMillis: Long,
        endDateMillis: Long,
        startTime: String?,
        endTime: String?,
        recurring: String?,
    ): Result<RealmMeetup>
}
