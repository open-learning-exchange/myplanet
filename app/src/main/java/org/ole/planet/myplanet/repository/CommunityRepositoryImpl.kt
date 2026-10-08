package org.ole.planet.myplanet.repository

import android.util.Log
import com.google.gson.JsonArray
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CancellationException
import org.ole.planet.myplanet.data.api.ApiInterface
import org.ole.planet.myplanet.data.room.dao.CommunityDao
import org.ole.planet.myplanet.model.Community
import org.ole.planet.myplanet.utils.GsonUtils
import org.ole.planet.myplanet.utils.toGson

@Singleton
class CommunityRepositoryImpl @Inject constructor(
    private val apiInterface: ApiInterface,
    private val communityDao: CommunityDao
) : CommunityRepository {

    override suspend fun replaceAll(rows: JsonArray) {
        val communities = mutableListOf<Community>()
        for (j in rows) {
            var jsonDoc = j.asJsonObject
            jsonDoc = GsonUtils.getJsonObject("doc", jsonDoc)
            val id = GsonUtils.getString("_id", jsonDoc)
            val community = Community()
            community.id = id
            if (GsonUtils.getString("name", jsonDoc) == "learning") {
                community.weight = 0
            }
            community.localDomain = GsonUtils.getString("localDomain", jsonDoc)
            community.name = GsonUtils.getString("name", jsonDoc)
            community.parentDomain = GsonUtils.getString("parentDomain", jsonDoc)
            community.registrationRequest = GsonUtils.getString("registrationRequest", jsonDoc)
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
                val arr = GsonUtils.getJsonArray("rows", response.body()?.toGson())
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
