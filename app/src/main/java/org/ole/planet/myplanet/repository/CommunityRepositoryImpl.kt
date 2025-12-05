package org.ole.planet.myplanet.repository

import com.google.gson.JsonObject
import org.ole.planet.myplanet.datamanager.ApiInterface
import org.ole.planet.myplanet.datamanager.DatabaseService
import org.ole.planet.myplanet.model.RealmCommunity
import org.ole.planet.myplanet.utilities.JsonUtils
import javax.inject.Inject

class CommunityRepositoryImpl @Inject constructor(
    private val apiInterface: ApiInterface,
    databaseService: DatabaseService
) : RealmRepository(databaseService), CommunityRepository {
    override suspend fun fetchCommunityRegistrationRequests(): JsonObject? {
        return try {
            val response = apiInterface.getCommunityRegistrationRequests("https://planet.earth.ole.org/db/communityregistrationrequests/_all_docs?include_docs=true")
            if (response.isSuccessful) {
                response.body()?.let {
                    saveCommunityData(it)
                }
                response.body()
            } else {
                null
            }
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    private suspend fun saveCommunityData(data: JsonObject) {
        val arr = JsonUtils.getJsonArray("rows", data)
        executeTransaction { realm ->
            realm.delete(RealmCommunity::class.java)
            for (j in arr) {
                var jsonDoc = j.asJsonObject
                jsonDoc = JsonUtils.getJsonObject("doc", jsonDoc)
                val id = JsonUtils.getString("_id", jsonDoc)
                val community = realm.createObject(RealmCommunity::class.java, id)
                if (JsonUtils.getString("name", jsonDoc) == "learning") {
                    community.weight = 0
                }
                community.localDomain = JsonUtils.getString("localDomain", jsonDoc)
                community.name = JsonUtils.getString("name", jsonDoc)
                community.parentDomain = JsonUtils.getString("parentDomain", jsonDoc)
                community.registrationRequest = JsonUtils.getString("registrationRequest", jsonDoc)
            }
        }
    }
}
