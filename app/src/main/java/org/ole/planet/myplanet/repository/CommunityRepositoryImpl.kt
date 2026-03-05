package org.ole.planet.myplanet.repository

import com.google.gson.JsonArray
import io.realm.Sort
import javax.inject.Inject
import org.ole.planet.myplanet.data.DatabaseService
import org.ole.planet.myplanet.data.api.ApiInterface
import org.ole.planet.myplanet.model.RealmCommunity
import org.ole.planet.myplanet.utils.JsonUtils

class CommunityRepositoryImpl @Inject constructor(
    databaseService: DatabaseService,
    private val apiInterface: ApiInterface
) : RealmRepository(databaseService), CommunityRepository {

    override suspend fun replaceAll(rows: JsonArray) {
        executeTransaction { realm ->
            realm.delete(RealmCommunity::class.java)
            for (j in rows) {
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
}
