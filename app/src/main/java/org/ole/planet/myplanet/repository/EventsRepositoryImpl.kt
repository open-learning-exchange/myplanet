package org.ole.planet.myplanet.repository

import javax.inject.Inject
import org.ole.planet.myplanet.data.DatabaseService
import org.ole.planet.myplanet.model.RealmMeetup
import org.ole.planet.myplanet.model.RealmUser

class EventsRepositoryImpl @Inject constructor(
    databaseService: DatabaseService,
) : RealmRepository(databaseService), EventsRepository {

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

    override suspend fun getJoinedMembers(meetupId: String): List<RealmUser> {
        if (meetupId.isBlank()) {
            return emptyList()
        }
        val meetupMembers = queryList(RealmMeetup::class.java) {
            equalTo("meetupId", meetupId)
            isNotEmpty("userId")
        }
        val memberIds = meetupMembers.mapNotNull { member ->
            member.userId?.takeUnless { it.isBlank() }
        }.distinct()
        if (memberIds.isEmpty()) {
            return emptyList()
        }
        return withRealmAsync { realm ->
            val users = realm.where(RealmUser::class.java)
                .`in`("id", memberIds.toTypedArray())
                .findAll()
            realm.copyFromRealm(users)
        }
    }

    override suspend fun toggleAttendance(meetupId: String, currentUserId: String?): RealmMeetup? {
        if (meetupId.isBlank()) {
            return null
        }
        val meetup = findByField(RealmMeetup::class.java, "meetupId", meetupId) ?: return null

        val isJoined = !meetup.userId.isNullOrEmpty()
        if (!isJoined && currentUserId.isNullOrEmpty()) {
            return meetup
        }

        meetup.userId = if (isJoined) "" else currentUserId
        var updatedMeetup: RealmMeetup? = null
        executeTransaction { realm ->
            val managedMeetup = realm.copyToRealmOrUpdate(meetup)
            updatedMeetup = realm.copyFromRealm(managedMeetup)
        }
        return updatedMeetup ?: getMeetupById(meetupId)
    }

    override suspend fun createMeetup(meetup: RealmMeetup): Boolean {
        return try {
            executeTransaction { realm ->
                realm.copyToRealmOrUpdate(meetup)
            }
            true
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }
}
