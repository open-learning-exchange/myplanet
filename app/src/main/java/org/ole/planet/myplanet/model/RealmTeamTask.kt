package org.ole.planet.myplanet.model

import com.google.gson.Gson
import com.google.gson.JsonObject
import com.opencsv.CSVWriter
import io.realm.kotlin.Realm
import io.realm.kotlin.ext.query
import io.realm.kotlin.types.RealmObject
import io.realm.kotlin.types.annotations.PrimaryKey
import org.ole.planet.myplanet.MainApplication
import org.ole.planet.myplanet.utilities.JsonUtils
import java.io.File
import java.io.FileWriter
import java.io.IOException

class RealmTeamTask : RealmObject {
    @PrimaryKey
    var id: String? = null
    var _id: String? = null
    var _rev: String? = null
    var title: String? = null
    var description: String? = null
    var link: String? = null
    var sync: String? = null
    var teamId: String? = null
    var isUpdated: Boolean = false
    var assignee: String? = null
    var deadline: Long = 0
    var completedTime: Long = 0
    var status: String? = null
    var completed: Boolean = false
    var isNotified: Boolean = false

    override fun toString(): String {
        return title ?: ""
    }

    companion object {
        private val taskDataList: MutableList<Array<String>> = mutableListOf()

        suspend fun insert(realm: Realm, obj: JsonObject?) {
            obj?.let { jsonObj ->
                val taskId = JsonUtils.getString("_id", jsonObj)

                realm.write {
                    val existingTask = query<RealmTeamTask>("_id == $0", taskId).first().find()

                    val task = existingTask ?: RealmTeamTask().apply { _id = taskId }

                    copyToRealm(task.apply {
                        _rev = JsonUtils.getString("_rev", jsonObj)
                        title = JsonUtils.getString("title", jsonObj)
                        status = JsonUtils.getString("status", jsonObj)
                        deadline = JsonUtils.getLong("deadline", jsonObj)
                        completedTime = JsonUtils.getLong("completedTime", jsonObj)
                        description = JsonUtils.getString("description", jsonObj)
                        link = Gson().toJson(JsonUtils.getJsonObject("link", jsonObj))
                        sync = Gson().toJson(JsonUtils.getJsonObject("sync", jsonObj))
                        teamId = JsonUtils.getString("teams", JsonUtils.getJsonObject("link", jsonObj))

                        val user = JsonUtils.getJsonObject("assignee", jsonObj)
                        if (user.has("_id")) {
                            assignee = JsonUtils.getString("_id", user)
                        }
                        completed = JsonUtils.getBoolean("completed", jsonObj)
                    })
                }

                val csvRow = arrayOf(
                    JsonUtils.getString("_id", jsonObj),
                    JsonUtils.getString("_rev", jsonObj),
                    JsonUtils.getString("title", jsonObj),
                    JsonUtils.getString("status", jsonObj),
                    JsonUtils.getLong("deadline", jsonObj).toString(),
                    JsonUtils.getLong("completedTime", jsonObj).toString(),
                    JsonUtils.getString("description", jsonObj),
                    JsonUtils.getString("link", jsonObj),
                    JsonUtils.getString("sync", jsonObj),
                    JsonUtils.getString("teams", JsonUtils.getJsonObject("link", jsonObj)),
                    JsonUtils.getString("assignee", JsonUtils.getJsonObject("assignee", jsonObj)),
                    JsonUtils.getBoolean("completed", jsonObj).toString()
                )
                taskDataList.add(csvRow)
            }
        }

        fun writeCsv(filePath: String, data: List<Array<String>>) {
            try {
                val file = File(filePath)
                file.parentFile?.mkdirs()
                val writer = CSVWriter(FileWriter(file))
                writer.writeNext(arrayOf(
                    "_id", "_rev", "title", "status", "deadline", "completedTime",
                    "description", "link", "sync", "teams", "assignee", "completed"
                ))
                for (row in data) {
                    writer.writeNext(row)
                }
                writer.close()
            } catch (e: IOException) {
                e.printStackTrace()
            }
        }

        fun teamTaskWriteCsv() {
            writeCsv("${MainApplication.context.getExternalFilesDir(null)}/ole/teamTask.csv", taskDataList)
        }

        fun serialize(realm: Realm, task: RealmTeamTask): JsonObject {
            return JsonObject().apply {
                task._id?.let {
                    addProperty("_id", it)
                    addProperty("_rev", task._rev)
                }
                addProperty("title", task.title)
                addProperty("deadline", task.deadline)
                addProperty("description", task.description)
                addProperty("completed", task.completed)
                addProperty("completedTime", task.completedTime)

                val user = realm.query<RealmUserModel>("id == $0", task.assignee).first().find()

                if (user != null) {
                    add("assignee", user.serialize())
                } else {
                    addProperty("assignee", "")
                }

                add("sync", Gson().fromJson(task.sync, JsonObject::class.java))
                add("link", Gson().fromJson(task.link, JsonObject::class.java))
            }
        }
    }
}
