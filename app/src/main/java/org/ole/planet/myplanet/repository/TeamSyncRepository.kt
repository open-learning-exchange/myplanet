package org.ole.planet.myplanet.repository

import com.google.gson.JsonObject

interface TeamSyncRepository {
    suspend fun insertTeamLog(json: JsonObject)
    suspend fun insertTeamLogs(logs: List<JsonObject>)
    fun insertMyTeam(realm: io.realm.Realm, doc: com.google.gson.JsonObject)
    fun bulkInsertFromSync(realm: io.realm.Realm, jsonArray: com.google.gson.JsonArray)
    fun bulkInsertTasksFromSync(realm: io.realm.Realm, jsonArray: com.google.gson.JsonArray)
    fun bulkInsertTeamActivitiesFromSync(realm: io.realm.Realm, jsonArray: com.google.gson.JsonArray)
}
