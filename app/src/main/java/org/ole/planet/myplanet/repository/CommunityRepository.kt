package org.ole.planet.myplanet.repository

import com.google.gson.JsonArray
import org.ole.planet.myplanet.model.RealmCommunity

interface CommunityRepository {
    suspend fun replaceAll(rows: JsonArray)
    suspend fun getAllSorted(): List<RealmCommunity>
    suspend fun syncCommunityDocs(): Boolean
    fun bulkInsertFromSync(realm: io.realm.Realm, jsonArray: com.google.gson.JsonArray)
}
