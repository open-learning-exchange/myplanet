package org.ole.planet.myplanet.repository

import javax.inject.Inject
import org.ole.planet.myplanet.datamanager.DatabaseService
import org.ole.planet.myplanet.model.RealmMeetup
import org.ole.planet.myplanet.model.RealmUserModel

class MeetupRepositoryImpl @Inject constructor(
    databaseService: DatabaseService,
) : RealmRepository(databaseService), MeetupRepository {

    override suspend fun getMeetupsForTeam(teamId: String): List<RealmMeetup> {
        return queryList(RealmMeetup::class.java) {
            equalTo("teamId", teamId)
        }
    }

    override suspend fun getMeetupById(meetupId: String): RealmMeetup? {
        if (meetupId.isBlank()) {
            return null
        }
        return findByField(RealmMeetup::class.java, "meetupId", meetupId)
    }

    override suspend fun getJoinedMembers(meetupId: String): List<RealmUserModel> {
        if (meetupId.isBlank()) {
            return emptyList()
        }
        val meetupMembers = queryList(RealmMeetup::class.java) {
            equalTo("meetupId", meetupId)
            isNotEmpty("userId")
        }
        val memberIds = meetupMembers
            .mapNotNull { member -> member.userId?.takeUnless { it.isBlank() } }
            .distinct()
        if (memberIds.isEmpty()) {
            return emptyList()
        }
        return queryList(RealmUserModel::class.java) {
            `in`("id", memberIds.toTypedArray())
        }
    }

    override suspend fun toggleAttendance(meetupId: String, currentUserId: String?): RealmMeetup? {
        if (meetupId.isBlank()) {
            return null
        }
        var updatedMeetup: RealmMeetup? = null
        executeTransaction { realm ->
            val meetup = realm.where(RealmMeetup::class.java)
                .equalTo("meetupId", meetupId)
                .findFirst()
                ?: return@executeTransaction

            val isJoined = !meetup.userId.isNullOrEmpty()
            if (!isJoined && currentUserId.isNullOrEmpty()) {
                return@executeTransaction
            }

            meetup.userId = if (isJoined) "" else currentUserId
            updatedMeetup = realm.copyFromRealm(meetup)
        }
        return updatedMeetup ?: getMeetupById(meetupId)
    }
}
