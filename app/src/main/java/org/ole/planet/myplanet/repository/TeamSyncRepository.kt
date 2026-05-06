package org.ole.planet.myplanet.repository

import com.google.gson.JsonObject

interface TeamSyncRepository {
    suspend fun insertTeamLog(json: JsonObject)
    suspend fun insertTeamLogs(logs: List<JsonObject>)
    suspend fun insertMyTeam(doc: com.google.gson.JsonObject)
    suspend fun insertMyTeams(docs: List<com.google.gson.JsonObject>)
    suspend fun bulkInsertFromSync(jsonArray: com.google.gson.JsonArray)
    suspend fun bulkInsertTasksFromSync(jsonArray: com.google.gson.JsonArray)
    suspend fun bulkInsertTeamActivitiesFromSync(jsonArray: com.google.gson.JsonArray)
}
