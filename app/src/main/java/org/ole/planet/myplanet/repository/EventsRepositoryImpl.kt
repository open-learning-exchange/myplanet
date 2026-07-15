package org.ole.planet.myplanet.repository

import com.google.gson.JsonObject
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
    private val timeProvider: org.ole.planet.myplanet.utils.TimeProvider
) : RealmRepository(databaseService, realmDispatcher), EventsRepository {

    override suspend fun getMeetupsForTeam(teamId: String): List<RealmMeetup> {
        return queryList(RealmMeetup::class.java) {
            equalTo("teamId", teamId)
        }
    }

    override suspend fun updateMeetup(
        meetupId: String, title: String, description: String,
        startDate: Long, endDate: Long, startTime: String,
        endTime: String, meetupLocation: String, meetupLink: String,
        recurring: String
    ): Boolean {
        return try {
            update(RealmMeetup::class.java, "id", meetupId) { meetup ->
                meetup.title = title
                meetup.description = description
                meetup.startDate = startDate
                meetup.endDate = endDate
                meetup.startTime = startTime
                meetup.endTime = endTime
                meetup.meetupLocation = meetupLocation
                meetup.meetupLink = meetupLink
                meetup.recurring = recurring
                meetup.updated = true
            }
            true
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }

    override suspend fun getMeetupById(meetupId: String): RealmMeetup? {
        if (meetupId.isBlank()) {
            return null
        }
        return findByField(RealmMeetup::class.java, "meetupId", meetupId)
    }

    override suspend fun getMeetupByLocalId(id: String): RealmMeetup? {
        if (id.isBlank()) return null
        return findByField(RealmMeetup::class.java, "id", id)
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

    override suspend fun batchInsertMeetups(documents: List<JsonObject>): Int {
        var processedCount = 0
        try {
            executeTransaction { realm ->
                try {
                    RealmMeetup.insertList(realm, "", documents)
                    processedCount = documents.size
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return processedCount
    }

    override suspend fun createMeetup(params: org.ole.planet.myplanet.model.MeetupCreationParams): Boolean {
        val gson = com.google.gson.Gson()
        val meetup = RealmMeetup().apply {
            id = "${java.util.UUID.randomUUID()}"
            title = params.title
            meetupLink = params.meetupLink
            description = params.description
            meetupLocation = params.location
            creator = params.userName
            startDate = params.startMillis
            endDate = params.endMillis
            startTime = params.startTime
            endTime = params.endTime
            createdDate = timeProvider.now()
            sourcePlanet = params.teamPlanetCode
            val jo = com.google.gson.JsonObject()
            jo.addProperty("type", "local")
            jo.addProperty("planetCode", params.teamPlanetCode)
            sync = gson.toJson(jo)
            if (params.recurringText != null) {
                recurring = params.recurringText
            }
            val ob = com.google.gson.JsonObject()
            ob.addProperty("teams", params.teamId)
            link = gson.toJson(ob)
            teamId = params.teamId
        }
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
