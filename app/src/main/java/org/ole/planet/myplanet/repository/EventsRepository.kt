package org.ole.planet.myplanet.repository

import org.ole.planet.myplanet.model.Meetup
import org.ole.planet.myplanet.model.MeetupCreationParams
import org.ole.planet.myplanet.model.UserEntity

interface EventsRepository {
    suspend fun createMeetup(params: MeetupCreationParams): Boolean
    suspend fun getMeetupsForTeam(teamId: String): List<Meetup>
    suspend fun getMeetupById(meetupId: String): Meetup?
    suspend fun getMeetupByLocalId(id: String): Meetup?
    suspend fun getJoinedMembers(meetupId: String, allUsers: List<UserEntity>): List<UserEntity>
    suspend fun toggleAttendance(meetupId: String, userId: String): Meetup?
    suspend fun updateMeetup(meetupId: String, title: String, description: String,
                             startDate: Long, endDate: Long, startTime: String,
                             endTime: String, meetupLocation: String, meetupLink: String,
                             recurring: String): Boolean
    suspend fun getPendingMeetupUploads(): List<Meetup>
    suspend fun markMeetupUploaded(localId: String, remoteId: String, remoteRev: String): Boolean
}
