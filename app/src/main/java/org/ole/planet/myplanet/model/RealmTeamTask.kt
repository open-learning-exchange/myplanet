package org.ole.planet.myplanet.model

import android.text.TextUtils
import com.google.gson.Gson
import com.google.gson.JsonObject
import com.opencsv.CSVWriter
import io.realm.Realm
import io.realm.RealmObject
import io.realm.annotations.PrimaryKey
import org.ole.planet.myplanet.MainApplication.Companion.context
import org.ole.planet.myplanet.model.RealmStepExam.Companion.examDataList
import org.ole.planet.myplanet.utilities.JsonUtils
import java.io.File
import java.io.FileWriter
import java.io.IOException

open class RealmTeamTask : RealmObject() {
    @JvmField
    @PrimaryKey
    var id: String? = null
    @JvmField
    var _id: String? = null
    @JvmField
    var _rev: String? = null
    @JvmField
    var title: String? = null
    @JvmField
    var description: String? = null
    @JvmField
    var link: String? = null
    @JvmField
    var sync: String? = null
    @JvmField
    var teamId: String? = null
    @JvmField
    var isUpdated = false
    @JvmField
    var assignee: String? = null
    @JvmField
    var deadline: Long = 0
    @JvmField
    var completedTime: Long = 0
    @JvmField
    var status: String? = null
    @JvmField
    var completed = false
    @JvmField
    var isNotified = false

    override fun toString(): String {
        return title!!
    }

    companion object {
        val taskDataList: MutableList<Array<String>> = mutableListOf()

        @JvmStatic
        fun insert(mRealm: Realm, obj: JsonObject?) {
            if (!mRealm.isInTransaction) {
                mRealm.beginTransaction()
            }
            var task = mRealm.where(RealmTeamTask::class.java).equalTo("_id", JsonUtils.getString("_id", obj)).findFirst()
            if (task == null) {
                task = mRealm.createObject(RealmTeamTask::class.java, JsonUtils.getString("_id", obj))
            }
            if (task != null) {
                task._id = JsonUtils.getString("_id", obj)
                task._rev = JsonUtils.getString("_rev", obj)
                task.title = JsonUtils.getString("title", obj)
                task.status = JsonUtils.getString("status", obj)
                task.deadline = JsonUtils.getLong("deadline", obj)
                task.completedTime = JsonUtils.getLong("completedTime", obj)
                task.description = JsonUtils.getString("description", obj)
                task.link = Gson().toJson(JsonUtils.getJsonObject("link", obj))
                task.sync = Gson().toJson(JsonUtils.getJsonObject("sync", obj))
                task.teamId = JsonUtils.getString("teams", JsonUtils.getJsonObject("link", obj))
                val user = JsonUtils.getJsonObject("assignee", obj)
                if (user.has("_id")) task.assignee = JsonUtils.getString("_id", user)
                task.completed = JsonUtils.getBoolean("completed", obj)
            }
            mRealm.commitTransaction()

            val csvRow = arrayOf(
                JsonUtils.getString("_id", obj),
                JsonUtils.getString("_rev", obj),
                JsonUtils.getString("title", obj),
                JsonUtils.getString("status", obj),
                JsonUtils.getLong("deadline", obj).toString(),
                JsonUtils.getLong("completedTime", obj).toString(),
                JsonUtils.getString("description", obj),
                JsonUtils.getString("link", obj),
                JsonUtils.getString("sync", obj),
                JsonUtils.getString("teams", JsonUtils.getJsonObject("link", obj)),
                JsonUtils.getString("assignee", JsonUtils.getJsonObject("assignee", obj)),
                JsonUtils.getBoolean("completed", obj).toString()
            )
            taskDataList.add(csvRow)
        }

        fun writeCsv(filePath: String, data: List<Array<String>>) {
            try {
                val file = File(filePath)
                file.parentFile?.mkdirs()
                val writer = CSVWriter(FileWriter(file))
                writer.writeNext(arrayOf("_id", "_rev", "title", "status", "deadline", "completedTime", "description", "link", "sync", "teams", "assignee", "completed"))
                for (row in data) {
                    writer.writeNext(row)
                }
                writer.close()
            } catch (e: IOException) {
                e.printStackTrace()
            }
        }

        fun teamTaskWriteCsv() {
            writeCsv("${context.getExternalFilesDir(null)}/ole/teamTask.csv", taskDataList)
        }

        @JvmStatic
        fun serialize(realm: Realm, task: RealmTeamTask): JsonObject {
            val `object` = JsonObject()
            if (!TextUtils.isEmpty(task._id)) {
                `object`.addProperty("_id", task._id)
                `object`.addProperty("_rev", task._rev)
            }
            `object`.addProperty("title", task.title)
            `object`.addProperty("deadline", task.deadline)
            `object`.addProperty("description", task.description)
            `object`.addProperty("completed", task.completed)
            `object`.addProperty("completedTime", task.completedTime)
            val user = realm.where(RealmUserModel::class.java).equalTo("id", task.assignee).findFirst()
            if (user != null) `object`.add("assignee", user.serialize())
            else `object`.addProperty("assignee", "")
            `object`.add("sync", Gson().fromJson(task.sync, JsonObject::class.java))
            `object`.add("link", Gson().fromJson(task.link, JsonObject::class.java))
            return `object`
        }
    }
}
