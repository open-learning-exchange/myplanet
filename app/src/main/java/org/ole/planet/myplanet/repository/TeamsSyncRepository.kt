package org.ole.planet.myplanet.repository

import android.content.Context
import com.google.gson.JsonArray
import com.google.gson.JsonObject
import io.realm.Realm
import org.ole.planet.myplanet.model.RealmTeamLog

interface TeamsSyncRepository {
    suspend fun getTeamsForUpload(): List<TeamUploadData>
    suspend fun markTeamUploaded(teamId: String?, rev: String)
    suspend fun markTeamsUploaded(uploadedTeams: Map<String, String>)
    suspend fun deleteLocalTeamRecord(teamId: String?)
    suspend fun deleteLocalTeamRecords(teamIds: List<String>)
    suspend fun syncTeamActivities()
    suspend fun insertTeamLog(json: JsonObject)
    suspend fun insertTeamLogs(logs: List<JsonObject>)
    fun serializeTeamActivities(log: RealmTeamLog, context: Context): JsonObject
    fun insertMyTeam(realm: Realm, doc: JsonObject)
    suspend fun batchInsertMyTeams(documents: List<JsonObject>): Int
    fun bulkInsertFromSync(realm: Realm, jsonArray: JsonArray)
    fun bulkInsertTasksFromSync(realm: Realm, jsonArray: JsonArray)
    suspend fun bulkInsertTeamActivitiesFromSync(jsonArray: JsonArray)
}
