package org.ole.planet.myplanet.repository

import com.google.gson.JsonArray
import io.realm.Sort
import javax.inject.Inject
import kotlinx.coroutines.CoroutineDispatcher
import org.ole.planet.myplanet.data.DatabaseService
import org.ole.planet.myplanet.data.api.ApiInterface
import io.realm.Case
import org.ole.planet.myplanet.di.RealmDispatcher
import org.ole.planet.myplanet.model.RealmCommunity
import org.ole.planet.myplanet.model.RealmMeetup
import org.ole.planet.myplanet.utils.JsonUtils

class CommunityRepositoryImpl @Inject constructor(
    databaseService: DatabaseService,
    @RealmDispatcher realmDispatcher: CoroutineDispatcher,
    private val apiInterface: ApiInterface
) : RealmRepository(databaseService, realmDispatcher), CommunityRepository {

    override suspend fun replaceAll(rows: JsonArray) {
        executeTransaction { realm ->
            realm.delete(RealmCommunity::class.java)
            val communities = mutableListOf<RealmCommunity>()
            for (j in rows) {
                var jsonDoc = j.asJsonObject
                jsonDoc = JsonUtils.getJsonObject("doc", jsonDoc)
                val id = JsonUtils.getString("_id", jsonDoc)
                val community = RealmCommunity()
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
            realm.insertOrUpdate(communities)
        }
    }

    override suspend fun getAllSorted(): List<RealmCommunity> {
        return withRealm { realm ->
            realm.where(RealmCommunity::class.java)
                .sort("weight", Sort.ASCENDING)
                .findAll()
                .let { realm.copyFromRealm(it) }
        }
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

    override fun bulkInsertFromSync(realm: io.realm.Realm, jsonArray: com.google.gson.JsonArray) {
        val documentList = mutableListOf<com.google.gson.JsonObject>()
        for (j in jsonArray) {
            var jsonDoc = j.asJsonObject
            jsonDoc = org.ole.planet.myplanet.utils.JsonUtils.getJsonObject("doc", jsonDoc)
            val id = org.ole.planet.myplanet.utils.JsonUtils.getString("_id", jsonDoc)
            if (!id.startsWith("_design")) {
                documentList.add(jsonDoc)
            }
        }
        documentList.forEach { jsonDoc ->
            insertMeetup("", realm, jsonDoc)
        }
    }

    override suspend fun getMyMeetupIds(userId: String?): com.google.gson.JsonArray {
        return withRealm { realm ->
            val meetups = realm.where(RealmMeetup::class.java).isNotEmpty("userId")
                .equalTo("userId", userId, Case.INSENSITIVE).findAll()
            val ids = com.google.gson.JsonArray()
            for (lib in meetups ?: emptyList()) {
                ids.add(lib.meetupId)
            }
            ids
        }
    }

    override fun insertMeetup(userId: String?, realm: io.realm.Realm, doc: com.google.gson.JsonObject) {
        var myMeetupsDB = realm.where(RealmMeetup::class.java)
            .equalTo("meetupId", JsonUtils.getString("_id", doc)).findFirst()
        if (myMeetupsDB == null) {
            myMeetupsDB = realm.createObject(RealmMeetup::class.java, JsonUtils.getString("_id", doc))
        }
        myMeetupsDB?.meetupId = JsonUtils.getString("_id", doc)
        myMeetupsDB?.userId = userId
        myMeetupsDB?.meetupIdRev = JsonUtils.getString("_rev", doc)
        myMeetupsDB?.title = JsonUtils.getString("title", doc)
        myMeetupsDB?.description = JsonUtils.getString("description", doc)
        myMeetupsDB?.startDate = JsonUtils.getLong("startDate", doc)
        myMeetupsDB?.endDate = JsonUtils.getLong("endDate", doc)
        myMeetupsDB?.recurring = JsonUtils.getString("recurring", doc)
        myMeetupsDB?.startTime = JsonUtils.getString("startTime", doc)
        myMeetupsDB?.endTime = JsonUtils.getString("endTime", doc)
        myMeetupsDB?.category = JsonUtils.getString("category", doc)
        myMeetupsDB?.meetupLocation = JsonUtils.getString("meetupLocation", doc)
        myMeetupsDB?.meetupLink = JsonUtils.getString("meetupLink", doc)
        myMeetupsDB?.creator = JsonUtils.getString("createdBy", doc)
        myMeetupsDB?.day = JsonUtils.getJsonArray("day", doc).toString()
        myMeetupsDB?.link = JsonUtils.getJsonObject("link", doc).toString()
        myMeetupsDB?.teamId = JsonUtils.getString("teams", JsonUtils.getJsonObject("link", doc))
    }
}
