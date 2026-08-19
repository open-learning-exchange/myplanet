package org.ole.planet.myplanet.repository

import kotlinx.coroutines.flow.Flow
import org.ole.planet.myplanet.model.Meetup
import org.ole.planet.myplanet.model.MeetupCreationParams
import org.ole.planet.myplanet.model.News
import org.ole.planet.myplanet.model.UserEntity

interface EventsRepository {
    suspend fun createMeetup(params: MeetupCreationParams): Boolean
    suspend fun getMeetupsForTeam(teamId: String): List<Meetup>
    suspend fun getMeetupById(meetupId: String): Meetup?
    suspend fun getMeetupByLocalId(id: String): Meetup?
    suspend fun getJoinedMembers(meetupId: String): List<UserEntity>
    suspend fun toggleCurrentUserAttendance(meetupId: String): Meetup?
    suspend fun updateMeetup(meetupId: String, title: String, description: String,
                             startDate: Long, endDate: Long, startTime: String,
                             endTime: String, meetupLocation: String, meetupLink: String,
                             recurring: String): Boolean
    suspend fun getPendingMeetupUploads(): List<Meetup>
    suspend fun markMeetupUploaded(localId: String, remoteId: String, remoteRev: String): Boolean
    fun getCommentsForMeetupFlow(meetupId: String): Flow<List<News>>
    fun getCommentsForMeetupsFlow(meetupIds: List<String>): Flow<List<News>>
    suspend fun addComment(parentId: String, teamId: String?, message: String, user: UserEntity?): News
    suspend fun deleteComment(commentId: String)
}
