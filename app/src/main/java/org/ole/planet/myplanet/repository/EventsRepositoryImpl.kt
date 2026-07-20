package org.ole.planet.myplanet.repository

import com.google.gson.Gson
import com.google.gson.JsonObject
import java.util.UUID
import javax.inject.Inject
import kotlinx.coroutines.CoroutineDispatcher
import org.ole.planet.myplanet.data.DatabaseService
import org.ole.planet.myplanet.data.room.dao.MeetupDao
import org.ole.planet.myplanet.di.RealmDispatcher
import org.ole.planet.myplanet.model.MeetupCreationParams
import org.ole.planet.myplanet.model.Meetup
import org.ole.planet.myplanet.model.RealmUser
import org.ole.planet.myplanet.utils.JsonUtils
import org.ole.planet.myplanet.utils.TimeProvider

class EventsRepositoryImpl @Inject constructor(
    databaseService: DatabaseService,
    @RealmDispatcher realmDispatcher: CoroutineDispatcher,
    private val timeProvider: TimeProvider,
    private val meetupDao: MeetupDao
) : RealmRepository(databaseService, realmDispatcher), EventsRepository {

    override suspend fun getMeetupsForTeam(teamId: String): List<Meetup> {
        return meetupDao.getByTeamId(teamId)
    }

    override suspend fun updateMeetup(
        meetupId: String, title: String, description: String,
        startDate: Long, endDate: Long, startTime: String,
        endTime: String, meetupLocation: String, meetupLink: String,
        recurring: String
    ): Boolean {
        return try {
            val meetup = meetupDao.getById(meetupId) ?: return false
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
            meetupDao.upsert(meetup)
            true
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }

    override suspend fun getMeetupById(meetupId: String): Meetup? {
        if (meetupId.isBlank()) {
            return null
        }
        return meetupDao.getByMeetupId(meetupId)
    }

    override suspend fun getMeetupByLocalId(id: String): Meetup? {
        if (id.isBlank()) return null
        return meetupDao.getById(id)
    }

    override suspend fun getJoinedMembers(meetupId: String): List<RealmUser> {
        if (meetupId.isBlank()) {
            return emptyList()
        }
        val memberIds = meetupDao.getMembersByMeetupId(meetupId)
            .mapNotNull { member -> member.userId?.takeUnless { it.isBlank() } }
            .distinct()
        if (memberIds.isEmpty()) {
            return emptyList()
        }
        // RealmUser is still on Realm, so resolve members through the Realm store.
        return withRealmAsync { realm ->
            val users = realm.where(RealmUser::class.java)
                .`in`("id", memberIds.toTypedArray())
                .findAll()
            realm.copyFromRealm(users)
        }
    }

    override suspend fun toggleAttendance(meetupId: String, currentUserId: String?): Meetup? {
        if (meetupId.isBlank()) {
            return null
        }

        val meetup = meetupDao.getByMeetupId(meetupId) ?: return null
        val isJoined = !meetup.userId.isNullOrEmpty()
        if (isJoined || !currentUserId.isNullOrEmpty()) {
            meetup.userId = if (isJoined) "" else currentUserId
            meetupDao.upsert(meetup)
        }
        return getMeetupById(meetupId)
    }

    override suspend fun batchInsertMeetups(documents: List<JsonObject>): Int {
        if (documents.isEmpty()) return 0
        return try {
            val ids = documents.map { JsonUtils.getString("_id", it) }
            val existingByMeetupId = meetupDao.getByMeetupIds(ids).associateBy { it.meetupId }

            val meetupsToInsert = documents.mapNotNull { meetupDoc ->
                val id = JsonUtils.getString("_id", meetupDoc)
                val existing = existingByMeetupId[id]
                if (existing?.updated == true) {
                    null
                } else {
                    Meetup.fromJson(meetupDoc, "", existing)
                }
            }
            if (meetupsToInsert.isNotEmpty()) {
                meetupDao.upsertAll(meetupsToInsert)
            }
            documents.size
        } catch (e: Exception) {
            e.printStackTrace()
            0
        }
    }

    override suspend fun createMeetup(params: MeetupCreationParams): Boolean {
        val gson = Gson()
        val meetup = Meetup().apply {
            id = "${UUID.randomUUID()}"
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
            val jo = JsonObject()
            jo.addProperty("type", "local")
            jo.addProperty("planetCode", params.teamPlanetCode)
            sync = gson.toJson(jo)
            if (params.recurringText != null) {
                recurring = params.recurringText
            }
            val ob = JsonObject()
            ob.addProperty("teams", params.teamId)
            link = gson.toJson(ob)
            teamId = params.teamId
        }
        return try {
            meetupDao.upsert(meetup)
            true
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }

    override suspend fun getMeetupIdsForUser(userId: String?): List<String> {
        if (userId.isNullOrBlank()) return emptyList()
        return meetupDao.getByUserId(userId).mapNotNull { it.meetupId }
    }

    override suspend fun getPendingMeetupUploads(): List<Meetup> {
        return meetupDao.getPendingUploads()
    }

    override suspend fun markMeetupUploaded(localId: String, remoteId: String, remoteRev: String): Boolean {
        val meetup = meetupDao.getById(localId) ?: return false
        meetup.meetupId = remoteId
        meetup.meetupIdRev = remoteRev
        meetup.updated = false
        meetupDao.upsert(meetup)
        return true
    }
}
