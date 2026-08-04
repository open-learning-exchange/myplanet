package org.ole.planet.myplanet.repository

import com.google.gson.Gson
import com.google.gson.JsonObject
import java.util.UUID
import javax.inject.Inject
import org.ole.planet.myplanet.data.room.dao.MeetupDao
import org.ole.planet.myplanet.data.room.dao.RemovedLogDao
import org.ole.planet.myplanet.model.Meetup
import org.ole.planet.myplanet.model.MeetupCreationParams
import org.ole.planet.myplanet.model.RemovedLog
import org.ole.planet.myplanet.model.UserEntity
import org.ole.planet.myplanet.model.TableDataUpdate
import org.ole.planet.myplanet.services.sync.RealtimeSyncManager
import org.ole.planet.myplanet.utils.JsonUtils
import org.ole.planet.myplanet.utils.TimeProvider

class EventsRepositoryImpl @Inject constructor(
    private val timeProvider: TimeProvider,
    private val meetupDao: MeetupDao,
    private val userRepository: UserRepository,
    private val removedLogDao: RemovedLogDao,
    private val realtimeSyncManager: RealtimeSyncManager
) : EventsRepository {

    override suspend fun getMeetupsForTeam(teamId: String): List<Meetup> {
        return meetupDao.getByTeamId(teamId)
    }

    override suspend fun updateMeetup(
        meetupId: String, title: String, description: String,
        startDate: Long, endDate: Long, startTime: String,
        endTime: String, meetupLocation: String, meetupLink: String,
        recurring: String, recurringNumber: Int
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
            meetup.recurringNumber = recurringNumber
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

    override suspend fun getJoinedMembers(meetupId: String): List<UserEntity> {
        if (meetupId.isBlank()) {
            return emptyList()
        }
        val memberIds = meetupDao.getMembersByMeetupId(meetupId)
            .mapNotNull { member -> member.userId?.takeUnless { it.isBlank() } }
            .distinct()
        if (memberIds.isEmpty()) {
            return emptyList()
        }
        val memberIdSet = memberIds.toSet()
        return userRepository.getAllUsers()
            .filter { user ->
                memberIdSet.contains(user.id) || user._id?.let(memberIdSet::contains) == true
            }
            .map { it }
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
            val currentUserId = userRepository.getUserModel()?.id
            val removedIds = (
                removedLogDao.getAllRemovedDocIds("meetups") +
                removedLogDao.getAllRemovedDocIds("meetup") +
                if (!currentUserId.isNullOrBlank()) {
                    removedLogDao.getRemovedDocIds("meetups", currentUserId) +
                    removedLogDao.getRemovedDocIds("meetup", currentUserId)
                } else emptyList()
            ).filterNotNull().toSet()

            val ids = documents.map { JsonUtils.getString("_id", it) }
            val existingByMeetupId = meetupDao.getByMeetupIds(ids).associateBy { it.meetupId }

            val meetupsToInsert = documents.mapNotNull { meetupDoc ->
                val id = JsonUtils.getString("_id", meetupDoc)
                if (removedIds.contains(id)) {
                    null
                } else {
                    val existing = existingByMeetupId[id]
                    if (existing?.updated == true) {
                        null
                    } else {
                        Meetup.fromJson(meetupDoc, "", existing)
                    }
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
        val currentUserId = userRepository.getUserModel()?.id
        val meetup = Meetup().apply {
            id = "${UUID.randomUUID()}"
            userId = currentUserId
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
            recurringNumber = params.recurringNumber
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
        val meetup = meetupDao.getById(localId) ?: meetupDao.getByMeetupId(localId) ?: return false
        if (meetup.isDeletePending) {
            meetupDao.deleteById(localId)
            meetup.meetupId?.let { if (it.isNotBlank()) meetupDao.deleteById(it) }
            return true
        }
        meetup.meetupId = remoteId
        meetup.meetupIdRev = remoteRev
        meetup.updated = false
        meetupDao.upsert(meetup)
        return true
    }

    override suspend fun deleteMeetup(meetupId: String): Boolean {
        if (meetupId.isBlank()) return false
        return try {
            val meetup = meetupDao.getById(meetupId) ?: meetupDao.getByMeetupId(meetupId)
            val currentUserId = userRepository.getUserModel()?.id

            val docIdsToLog = mutableSetOf<String>()
            if (meetupId.isNotBlank()) docIdsToLog.add(meetupId)
            meetup?.id?.let { if (it.isNotBlank()) docIdsToLog.add(it) }
            meetup?.meetupId?.let { if (it.isNotBlank()) docIdsToLog.add(it) }

            val userIdsToLog = mutableSetOf<String>()
            if (!currentUserId.isNullOrBlank()) userIdsToLog.add(currentUserId)
            meetup?.userId?.let { if (it.isNotBlank()) userIdsToLog.add(it) }

            userIdsToLog.forEach { uId ->
                docIdsToLog.forEach { docId ->
                    removedLogDao.insert(RemovedLog().apply {
                        id = UUID.randomUUID().toString()
                        type = "meetups"
                        this.userId = uId
                        this.docId = docId
                    })
                    removedLogDao.insert(RemovedLog().apply {
                        id = UUID.randomUUID().toString()
                        type = "meetup"
                        this.userId = uId
                        this.docId = docId
                    })
                }
            }

            if (meetup != null && (!meetup.meetupId.isNullOrEmpty() || !meetup.meetupIdRev.isNullOrEmpty())) {
                meetup.isDeletePending = true
                meetup.updated = true
                meetupDao.upsert(meetup)
            } else {
                meetupDao.deleteById(meetupId)
                meetup?.id?.let { if (it.isNotBlank()) meetupDao.deleteById(it) }
                meetup?.meetupId?.let { if (it.isNotBlank()) meetupDao.deleteById(it) }
            }

            realtimeSyncManager.notifyTableUpdated(TableDataUpdate("meetups", 0, 1))
            true
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }
}
