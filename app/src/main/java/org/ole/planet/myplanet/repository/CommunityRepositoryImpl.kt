package org.ole.planet.myplanet.repository

import com.google.gson.JsonArray
import com.google.gson.JsonObject
import javax.inject.Inject
import kotlinx.coroutines.CoroutineDispatcher
import org.ole.planet.myplanet.data.DatabaseService
import org.ole.planet.myplanet.data.api.ApiInterface
import org.ole.planet.myplanet.data.room.dao.CommunityDao
import org.ole.planet.myplanet.di.RealmDispatcher
import org.ole.planet.myplanet.model.RealmCommunity
import org.ole.planet.myplanet.model.RealmMeetup
import org.ole.planet.myplanet.utils.JsonUtils

class CommunityRepositoryImpl @Inject constructor(
    databaseService: DatabaseService,
    @RealmDispatcher realmDispatcher: CoroutineDispatcher,
    private val apiInterface: ApiInterface,
    private val communityDao: CommunityDao
) : RealmRepository(databaseService, realmDispatcher), CommunityRepository {
    // Still extends RealmRepository for insertMeetupsFromSync (RealmMeetup remains on Realm);
    // RealmCommunity itself is now stored in Room via communityDao.

    override suspend fun replaceAll(rows: JsonArray) {
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
        communityDao.replaceAll(communities)
    }

    override suspend fun getAllSorted(): List<RealmCommunity> {
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
        executeTransaction { realm ->
            RealmMeetup.insertList(realm, "", docs)
        }
    }
}
