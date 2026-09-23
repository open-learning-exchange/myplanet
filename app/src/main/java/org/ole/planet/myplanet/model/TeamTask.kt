package org.ole.planet.myplanet.model

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import com.google.gson.JsonObject
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import org.ole.planet.myplanet.utils.GsonUtils
import org.ole.planet.myplanet.utils.addDocumentOrigin
import org.ole.planet.myplanet.utils.toGson
import org.ole.planet.myplanet.utils.toKotlinx

@Entity(tableName = "team_tasks", indices = [Index("teamId")])
class TeamTask {
    @PrimaryKey
    @JvmField
    var id: String = ""
    @JvmField
    var _id: String? = null
    var _rev: String? = null
    var title: String? = null
    var description: String? = null
    var link: String? = null
    var sync: String? = null
    var teamId: String? = null
    var isUpdated = false
    var assignee: String? = null
    var deadline: Long = 0
    var completedTime: Long = 0
    var status: String? = null
    var completed = false
    var isNotified = false

    override fun toString(): String {
        return title.orEmpty()
    }

    companion object {
        fun fromJson(obj: JsonObject?): TeamTask {
            val task = TeamTask()
            task.id = GsonUtils.getString("_id", obj)
            task._id = GsonUtils.getString("_id", obj)
            task._rev = GsonUtils.getString("_rev", obj)
            task.title = GsonUtils.getString("title", obj)
            task.status = GsonUtils.getString("status", obj)
            task.deadline = GsonUtils.getLong("deadline", obj)
            task.completedTime = GsonUtils.getLong("completedTime", obj)
            task.description = GsonUtils.getString("description", obj)
            task.link = GsonUtils.gson.toJson(GsonUtils.getJsonObject("link", obj))
            task.sync = GsonUtils.gson.toJson(GsonUtils.getJsonObject("sync", obj))
            task.teamId = GsonUtils.getString("teams", GsonUtils.getJsonObject("link", obj))
            val user = GsonUtils.getJsonObject("assignee", obj)
            if (user.has("_id")) {
                task.assignee = GsonUtils.getString("_id", user)
            }
            task.completed = GsonUtils.getBoolean("completed", obj)
            return task
        }

        fun serialize(task: TeamTask, user: UserEntity?): JsonObject {
            val syncJson = GsonUtils.gson.fromJson(task.sync, JsonObject::class.java)
            val linkJson = GsonUtils.gson.fromJson(task.link, JsonObject::class.java)
            val `object` = buildJsonObject {
                if (!task._id.isNullOrEmpty()) {
                    put("_id", task._id)
                    put("_rev", task._rev)
                }
                put("title", task.title)
                put("deadline", task.deadline)
                put("description", task.description)
                put("completed", task.completed)
                put("completedTime", task.completedTime)
                if (user != null) put("assignee", user.serialize().toKotlinx()) else put("assignee", "")
                put("sync", syncJson?.toKotlinx() ?: JsonNull)
                put("link", linkJson?.toKotlinx() ?: JsonNull)
            }.toGson()
            `object`.addDocumentOrigin()
            return `object`
        }
    }
}
