package org.ole.planet.myplanet.repository

import android.content.Context
import com.google.gson.JsonArray
import com.google.gson.JsonObject
import org.ole.planet.myplanet.model.TeamLog

interface TeamsSyncRepository {
    suspend fun getTeamsForUpload(): List<TeamUploadData>
    suspend fun markTeamUploaded(teamId: String?, rev: String)
    suspend fun markTeamsUploaded(uploadedTeams: Map<String, String>)
    suspend fun deleteLocalTeamRecord(teamId: String?)
    suspend fun deleteLocalTeamRecords(teamIds: List<String>)
    suspend fun syncTeamActivities()
    suspend fun insertTeamLog(json: JsonObject)
    suspend fun insertTeamLogs(logs: List<JsonObject>)
    fun serializeTeamActivities(log: TeamLog): JsonObject
    suspend fun insertMyTeam(doc: JsonObject)
    suspend fun batchInsertMyTeams(documents: List<JsonObject>): Int
    suspend fun bulkInsertFromSync(jsonArray: JsonArray)
    suspend fun bulkInsertTasksFromSync(jsonArray: JsonArray)
    suspend fun bulkInsertTeamActivitiesFromSync(jsonArray: JsonArray)
    suspend fun getPendingTaskUploads(): List<org.ole.planet.myplanet.model.TeamTask>
    suspend fun markTaskUploaded(localId: String, remoteId: String?, remoteRev: String?): Boolean
    suspend fun getPendingTeamLogUploads(): List<TeamLog>
    suspend fun markTeamLogUploaded(localId: String, remoteId: String, rev: String): Boolean
}
