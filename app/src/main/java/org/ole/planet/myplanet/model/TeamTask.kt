package org.ole.planet.myplanet.model

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import org.ole.planet.myplanet.utils.JsonUtils
import org.ole.planet.myplanet.utils.KJson
import org.ole.planet.myplanet.utils.addDocumentOrigin

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
            task.id = JsonUtils.getString("_id", obj)
            task._id = JsonUtils.getString("_id", obj)
            task._rev = JsonUtils.getString("_rev", obj)
            task.title = JsonUtils.getString("title", obj)
            task.status = JsonUtils.getString("status", obj)
            task.deadline = JsonUtils.getLong("deadline", obj)
            task.completedTime = JsonUtils.getLong("completedTime", obj)
            task.description = JsonUtils.getString("description", obj)
            val kLink = JsonUtils.getJsonObject("link", obj)
            task.link = kLink.toString()
            task.sync = JsonUtils.getJsonObject("sync", obj).toString()
            task.teamId = JsonUtils.getString("teams", kLink)
            val user = JsonUtils.getJsonObject("assignee", obj)
            if (user.containsKey("_id")) {
                task.assignee = JsonUtils.getString("_id", user)
            }
            task.completed = JsonUtils.getBoolean("completed", obj)
            return task
        }

        fun serialize(task: TeamTask, user: UserEntity?): JsonObject {
            val syncJson = KJson.parseObjectOrNull(task.sync)
            val linkJson = KJson.parseObjectOrNull(task.link)
            return buildJsonObject {
                if (!task._id.isNullOrEmpty()) {
                    put("_id", task._id)
                    put("_rev", task._rev)
                }
                put("title", task.title)
                put("deadline", task.deadline)
                put("description", task.description)
                put("completed", task.completed)
                put("completedTime", task.completedTime)
                if (user != null) put("assignee", user.serialize()) else put("assignee", "")
                put("sync", syncJson ?: JsonNull)
                put("link", linkJson ?: JsonNull)
                addDocumentOrigin()
            }
        }
    }
}
