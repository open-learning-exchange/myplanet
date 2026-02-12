package org.ole.planet.myplanet.repository

import com.google.gson.JsonArray
import io.realm.Sort
import org.ole.planet.myplanet.data.DatabaseService
import org.ole.planet.myplanet.model.RealmCommunity
import org.ole.planet.myplanet.utils.JsonUtils
import javax.inject.Inject

class CommunityRepositoryImpl @Inject constructor(
    databaseService: DatabaseService
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
}
