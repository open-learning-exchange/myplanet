package org.ole.planet.myplanet.repository

import com.google.gson.JsonArray
import com.google.gson.JsonObject
import javax.inject.Inject
import org.ole.planet.myplanet.data.api.ApiInterface
import org.ole.planet.myplanet.data.room.dao.CommunityDao
import org.ole.planet.myplanet.data.room.dao.MeetupDao
import org.ole.planet.myplanet.model.Community
import org.ole.planet.myplanet.model.Meetup
import org.ole.planet.myplanet.utils.JsonUtils

import org.ole.planet.myplanet.data.room.dao.RemovedLogDao

class CommunityRepositoryImpl @Inject constructor(
    private val apiInterface: ApiInterface,
    private val communityDao: CommunityDao,
    private val meetupDao: MeetupDao,
    private val removedLogDao: RemovedLogDao,
    private val userRepository: UserRepository
) : CommunityRepository {

    override suspend fun replaceAll(rows: JsonArray) {
        val communities = mutableListOf<Community>()
        for (j in rows) {
            var jsonDoc = j.asJsonObject
            jsonDoc = JsonUtils.getJsonObject("doc", jsonDoc)
            val id = JsonUtils.getString("_id", jsonDoc)
            val community = Community()
            community.id = id
            if (JsonUtils.getString("name", jsonDoc) == "learning") {
                community.weight = 0
            }
            community.localDomain = JsonUtils.getString("localDomain", jsonDoc)
            community.name = JsonUtils.getString("name", jsonDoc)
            community.parentDomain = JsonUtils.getString("parentDomain", jsonDoc)
            community.registrationRequest = JsonUtils.getString("registrationRequest", jsonDoc)
            communities.add(community)
        }
        communityDao.replaceAll(communities)
    }

    override suspend fun getAllSorted(): List<Community> {
        return communityDao.getAllSorted()
    }

    override suspend fun syncCommunityDocs(): Boolean {
        return try {
            val response = apiInterface.getJsonObject("", "https://planet.earth.ole.org/db/communityregistrationrequests/_all_docs?include_docs=true")
            if (response.isSuccessful && response.body() != null) {
                val arr = JsonUtils.getJsonArray("rows", response.body())
                replaceAll(arr)
                true
            } else {
                false
            }
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }

    override suspend fun insertMeetupsFromSync(docs: List<JsonObject>) {
        if (docs.isEmpty()) return
        val currentUserId = userRepository.getUserModel()?.id
        val removedIds = if (!currentUserId.isNullOrBlank()) {
            (removedLogDao.getRemovedDocIds("meetups", currentUserId) + removedLogDao.getRemovedDocIds("meetup", currentUserId)).filterNotNull().toSet()
        } else emptySet()

        val ids = docs.map { JsonUtils.getString("_id", it) }
        val existingByMeetupId = meetupDao.getByMeetupIds(ids).associateBy { it.meetupId }

        val meetupsToInsert = docs.mapNotNull { meetupDoc ->
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
    }
}
