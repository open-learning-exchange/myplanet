package org.ole.planet.myplanet.repository

import android.content.Context
import com.google.gson.JsonObject
import org.ole.planet.myplanet.model.RealmTeamLog

interface TeamSyncRepository {
    suspend fun insertTeamLog(json: JsonObject)
    suspend fun insertTeamLogs(logs: List<JsonObject>)
    fun serializeTeamActivities(log: RealmTeamLog, context: Context): JsonObject
    fun insertMyTeam(doc: com.google.gson.JsonObject)
    fun bulkInsertFromSync(jsonArray: com.google.gson.JsonArray)
    fun bulkInsertTasksFromSync(jsonArray: com.google.gson.JsonArray)
    fun bulkInsertTeamActivitiesFromSync(jsonArray: com.google.gson.JsonArray)
}
