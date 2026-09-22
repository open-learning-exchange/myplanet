package org.ole.planet.myplanet.model

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import com.google.gson.JsonObject
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.put
import org.ole.planet.myplanet.utils.GsonUtils
import org.ole.planet.myplanet.utils.KotlinxJsonUtils
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
            val kObj = obj?.toKotlinx()?.jsonObject
            val task = TeamTask()
            task.id = KotlinxJsonUtils.getString("_id", kObj)
            task._id = KotlinxJsonUtils.getString("_id", kObj)
            task._rev = KotlinxJsonUtils.getString("_rev", kObj)
            task.title = KotlinxJsonUtils.getString("title", kObj)
            task.status = KotlinxJsonUtils.getString("status", kObj)
            task.deadline = KotlinxJsonUtils.getLong("deadline", kObj)
            task.completedTime = KotlinxJsonUtils.getLong("completedTime", kObj)
            task.description = KotlinxJsonUtils.getString("description", kObj)
            val kLink = KotlinxJsonUtils.getJsonObject("link", kObj)
            task.link = kLink.toString()
            task.sync = KotlinxJsonUtils.getJsonObject("sync", kObj).toString()
            task.teamId = KotlinxJsonUtils.getString("teams", kLink)
            val user = KotlinxJsonUtils.getJsonObject("assignee", kObj)
            if (user.containsKey("_id")) {
                task.assignee = KotlinxJsonUtils.getString("_id", user)
            }
            task.completed = KotlinxJsonUtils.getBoolean("completed", kObj)
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
