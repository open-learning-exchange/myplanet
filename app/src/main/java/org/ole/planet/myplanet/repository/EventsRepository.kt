package org.ole.planet.myplanet.repository

import com.google.gson.JsonObject
import org.ole.planet.myplanet.model.MeetupCreationParams
import org.ole.planet.myplanet.model.RealmMeetup
import org.ole.planet.myplanet.model.RealmUser

interface EventsRepository {
    suspend fun createMeetup(params: MeetupCreationParams): Boolean
    suspend fun getMeetupsForTeam(teamId: String): List<RealmMeetup>
    suspend fun getMeetupById(meetupId: String): RealmMeetup?
    suspend fun getMeetupByLocalId(id: String): RealmMeetup?
    suspend fun getJoinedMembers(meetupId: String): List<RealmUser>
    suspend fun toggleAttendance(meetupId: String, currentUserId: String?): RealmMeetup?
    suspend fun batchInsertMeetups(documents: List<JsonObject>): Int
    suspend fun updateMeetup(meetupId: String, title: String, description: String,
                             startDate: Long, endDate: Long, startTime: String,
                             endTime: String, meetupLocation: String, meetupLink: String,
                             recurring: String): Boolean
    suspend fun getMeetupIdsForUser(userId: String?): List<String>
    suspend fun getPendingMeetupUploads(): List<RealmMeetup>
    suspend fun markMeetupUploaded(localId: String, remoteId: String, remoteRev: String): Boolean
}
