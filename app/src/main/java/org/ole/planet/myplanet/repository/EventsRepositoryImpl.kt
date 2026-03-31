package org.ole.planet.myplanet.repository

import org.ole.planet.myplanet.utils.Utilities
import javax.inject.Inject
import kotlinx.coroutines.CoroutineDispatcher
import org.ole.planet.myplanet.data.DatabaseService
import org.ole.planet.myplanet.data.queryList
import org.ole.planet.myplanet.di.RealmDispatcher
import org.ole.planet.myplanet.model.RealmMeetup
import org.ole.planet.myplanet.model.RealmUser

class EventsRepositoryImpl @Inject constructor(
    databaseService: DatabaseService,
    @RealmDispatcher realmDispatcher: CoroutineDispatcher,
) : RealmRepository(databaseService, realmDispatcher), EventsRepository {

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
        return withRealmAsync { realm ->
            val meetupMembers = realm.queryList(RealmMeetup::class.java) {
                equalTo("meetupId", meetupId)
                isNotEmpty("userId")
            }
            val memberIds = meetupMembers.mapNotNull { member ->
                member.userId?.takeUnless { it.isBlank() }
            }.distinct()
            if (memberIds.isEmpty()) {
                emptyList()
            } else {
                val users = realm.where(RealmUser::class.java)
                    .`in`("id", memberIds.toTypedArray())
                    .findAll()
                realm.copyFromRealm(users)
            }
        }
    }

    override suspend fun toggleAttendance(meetupId: String, currentUserId: String?): RealmMeetup? {
        if (meetupId.isBlank()) {
            return null
        }

        update(RealmMeetup::class.java, "meetupId", meetupId) { meetup ->
            val isJoined = !meetup.userId.isNullOrEmpty()
            if (isJoined || !currentUserId.isNullOrEmpty()) {
                meetup.userId = if (isJoined) "" else currentUserId
            }
        }
        return getMeetupById(meetupId)
    }

    override suspend fun createMeetup(meetup: RealmMeetup): Boolean {
        return try {
            executeTransaction { realm ->
                realm.copyToRealmOrUpdate(meetup)
            }
            true
        } catch (e: Exception) {
            Utilities.logException(e, "EventsRepositoryImpl")
            false
        }
    }
}
