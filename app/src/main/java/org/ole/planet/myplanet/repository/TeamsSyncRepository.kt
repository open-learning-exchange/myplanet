package org.ole.planet.myplanet.repository

import android.content.Context
import com.google.gson.JsonArray
import com.google.gson.JsonObject
import io.realm.Realm
import org.ole.planet.myplanet.model.RealmTeamLog

data class TeamUploadData(
    val teamId: String?,
    val serialized: JsonObject,
    val isDeletePending: Boolean = false
)

interface TeamsSyncRepository {
    suspend fun getTeamsForUpload(): List<TeamUploadData>
    suspend fun markTeamUploaded(teamId: String?, rev: String)
    suspend fun deleteLocalTeamRecord(teamId: String?)
    suspend fun syncTeamActivities()
    suspend fun insertTeamLog(json: JsonObject)
    suspend fun insertTeamLogs(logs: List<JsonObject>)
    fun serializeTeamActivities(log: RealmTeamLog, context: Context): JsonObject
    fun insertMyTeam(realm: Realm, doc: JsonObject)
    suspend fun batchInsertMyTeams(documents: List<JsonObject>): Int
    fun bulkInsertFromSync(realm: Realm, jsonArray: JsonArray)
    fun bulkInsertTasksFromSync(realm: Realm, jsonArray: JsonArray)
    fun bulkInsertTeamActivitiesFromSync(realm: Realm, jsonArray: JsonArray)
}
