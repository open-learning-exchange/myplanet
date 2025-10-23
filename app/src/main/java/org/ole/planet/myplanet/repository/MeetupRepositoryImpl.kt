package org.ole.planet.myplanet.repository

import com.google.gson.Gson
import com.google.gson.JsonObject
import java.util.UUID
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
        return withRealmAsync { realm ->
            val meetupMembers = realm.where(RealmMeetup::class.java)
                .equalTo("meetupId", meetupId)
                .isNotEmpty("userId")
                .findAll()
            val memberIds = meetupMembers.mapNotNull { member ->
                member.userId?.takeUnless { it.isBlank() }
            }.distinct()
            if (memberIds.isEmpty()) {
                emptyList()
            } else {
                val users = realm.where(RealmUserModel::class.java)
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

    override suspend fun createLocalTeamMeetup(
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
    ): Result<RealmMeetup> {
        val meetupId = UUID.randomUUID().toString()
        return runCatching {
            withRealmAsync { realm ->
                realm.executeTransaction { transactionRealm ->
                    val meetup = transactionRealm.createObject(RealmMeetup::class.java, meetupId)
                    meetup.meetupId = meetupId
                    meetup.title = title
                    meetup.meetupLink = meetupLink
                    meetup.description = description
                    meetup.meetupLocation = location
                    meetup.creator = creatorName
                    meetup.startDate = startDateMillis
                    meetup.endDate = endDateMillis
                    meetup.startTime = startTime ?: ""
                    meetup.endTime = endTime ?: ""
                    meetup.createdDate = System.currentTimeMillis()
                    meetup.sourcePlanet = teamPlanetCode

                    val syncObject = JsonObject().apply {
                        addProperty("type", "local")
                        addProperty("planetCode", teamPlanetCode)
                    }
                    meetup.sync = Gson().toJson(syncObject)

                    recurring?.let { meetup.recurring = it }

                    val linkObject = JsonObject().apply {
                        addProperty("teams", teamId)
                    }
                    meetup.link = Gson().toJson(linkObject)
                    meetup.teamId = teamId
                }

                val createdMeetup = realm.where(RealmMeetup::class.java)
                    .equalTo("meetupId", meetupId)
                    .findFirst()
                    ?: throw IllegalStateException("Meetup was not persisted")
                realm.copyFromRealm(createdMeetup)
            }
        }
    }
}
