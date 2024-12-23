package org.ole.planet.myplanet.model

import android.content.Context
import com.google.gson.JsonObject
import com.opencsv.CSVWriter
import io.realm.kotlin.Realm
import io.realm.kotlin.types.RealmObject
import io.realm.kotlin.types.annotations.PrimaryKey
import org.ole.planet.myplanet.MainApplication
import org.ole.planet.myplanet.utilities.JsonUtils
import org.ole.planet.myplanet.utilities.NetworkUtils
import java.io.File
import java.io.FileWriter
import java.io.IOException
import java.util.Calendar

class RealmTeamLog : RealmObject {
    @PrimaryKey
    var id: String? = null
    var _id: String? = null
    var _rev: String? = null
    var teamId: String? = null
    var user: String? = null
    var type: String? = null
    var teamType: String? = null
    var createdOn: String? = null
    var parentCode: String? = null
    var time: Long? = null
    var uploaded: Boolean = false

    companion object {
        private val teamLogDataList: MutableList<Array<String>> = mutableListOf()

        fun getVisitCount(realm: Realm, userName: String?, teamId: String?): Long {
            return realm.query<RealmTeamLog>(RealmTeamLog::class, "type == $0 AND user == $1 AND teamId == $2", "teamVisit", userName, teamId).count().find()
        }

        fun getVisitByTeam(realm: Realm, teamId: String?): Long {
            val calendar = Calendar.getInstance()
            calendar.add(Calendar.DAY_OF_YEAR, -30)
            return realm.query<RealmTeamLog>(RealmTeamLog::class, "type == 'teamVisit' AND teamId == $0 AND time > $1", teamId ?: "", calendar.timeInMillis).count().find()
        }

        fun serializeTeamActivities(log: RealmTeamLog, context: Context): JsonObject {
            return JsonObject().apply {
                addProperty("user", log.user)
                addProperty("type", log.type)
                addProperty("createdOn", log.createdOn)
                addProperty("parentCode", log.parentCode)
                addProperty("teamType", log.teamType)
                addProperty("time", log.time)
                addProperty("teamId", log.teamId)
                addProperty("androidId", NetworkUtils.getUniqueIdentifier())
                addProperty("deviceName", NetworkUtils.getDeviceName())
                addProperty("customDeviceName", NetworkUtils.getCustomDeviceName(context))
                if (!log._rev.isNullOrEmpty()) {
                    addProperty("_rev", log._rev)
                    addProperty("_id", log._id)
                }
            }
        }

        suspend fun insert(realm: Realm, act: JsonObject?) {
            if (act == null) return

            realm.write {
                val id = JsonUtils.getString("_id", act)

                val existingLog = query(RealmTeamLog::class, "id == $0", id).first().find()

                val tag = existingLog ?: copyToRealm(RealmTeamLog().apply {
                    this.id = id
                })

                tag.apply {
                    _rev = JsonUtils.getString("_rev", act)
                    _id = JsonUtils.getString("_id", act)
                    type = JsonUtils.getString("type", act)
                    user = JsonUtils.getString("user", act)
                    createdOn = JsonUtils.getString("createdOn", act)
                    parentCode = JsonUtils.getString("parentCode", act)
                    time = JsonUtils.getLong("time", act)
                    teamId = JsonUtils.getString("teamId", act)
                    teamType = JsonUtils.getString("teamType", act)
                }

                if (existingLog == null) {
                    copyToRealm(tag)
                }
            }

            val csvRow = arrayOf(
                JsonUtils.getString("_id", act),
                JsonUtils.getString("_rev", act),
                JsonUtils.getString("user", act),
                JsonUtils.getString("type", act),
                JsonUtils.getString("createdOn", act),
                JsonUtils.getString("parentCode", act),
                JsonUtils.getLong("time", act).toString(),
                JsonUtils.getString("teamId", act),
                JsonUtils.getString("teamType", act)
            )
            teamLogDataList.add(csvRow)
        }

        fun writeCsv(filePath: String, data: List<Array<String>>) {
            try {
                val file = File(filePath)
                file.parentFile?.mkdirs()
                CSVWriter(FileWriter(file)).use { writer ->
                    writer.writeNext(arrayOf("_id", "_rev", "user", "type", "createdOn", "parentCode", "time", "teamId", "teamType"))
                    data.forEach { row ->
                        writer.writeNext(row)
                    }
                }
            } catch (e: IOException) {
                e.printStackTrace()
            }
        }

        fun teamLogWriteCsv() {
            writeCsv("${MainApplication.context.getExternalFilesDir(null)}/ole/teamLog.csv", teamLogDataList)
        }
    }
}
