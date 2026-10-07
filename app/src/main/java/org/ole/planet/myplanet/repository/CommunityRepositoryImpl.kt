package org.ole.planet.myplanet.repository

import android.util.Log
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CancellationException
import kotlinx.serialization.json.JsonArray
import org.ole.planet.myplanet.data.api.ApiInterface
import org.ole.planet.myplanet.data.room.dao.CommunityDao
import org.ole.planet.myplanet.model.Community
import org.ole.planet.myplanet.utils.KJson

@Singleton
class CommunityRepositoryImpl @Inject constructor(
    private val apiInterface: ApiInterface,
    private val communityDao: CommunityDao
) : CommunityRepository {

    override suspend fun replaceAll(rows: JsonArray) {
        val communities = mutableListOf<Community>()
        for (j in rows) {
            var jsonDoc = KJson.asJsonObject(j)
            jsonDoc = KJson.getJsonObject("doc", jsonDoc)
            val id = KJson.getString("_id", jsonDoc)
            val community = Community()
            community.id = id
            if (KJson.getString("name", jsonDoc) == "learning") {
                community.weight = 0
            }
            community.localDomain = KJson.getString("localDomain", jsonDoc)
            community.name = KJson.getString("name", jsonDoc)
            community.parentDomain = KJson.getString("parentDomain", jsonDoc)
            community.registrationRequest = KJson.getString("registrationRequest", jsonDoc)
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
                val arr = KJson.getJsonArray("rows", response.body())
                replaceAll(arr)
                true
            } else {
                false
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.w(TAG, "syncCommunityDocs failed", e)
            false
        }
    }

    companion object {
        private const val TAG = "CommunityRepositoryImpl"
    }
}
