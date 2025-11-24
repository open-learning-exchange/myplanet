package org.ole.planet.myplanet.model

import android.text.TextUtils
import com.google.gson.JsonObject
import io.realm.Realm
import io.realm.RealmObject
import io.realm.annotations.PrimaryKey
import org.ole.planet.myplanet.utilities.GsonUtils
import org.ole.planet.myplanet.utilities.JsonUtils

open class RealmTeamTask : RealmObject() {
    @PrimaryKey
    var id: String? = null
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
        @JvmStatic
        fun insert(mRealm: Realm, obj: JsonObject?) {
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
                task.link = GsonUtils.gson.toJson(JsonUtils.getJsonObject("link", obj))
                task.sync = GsonUtils.gson.toJson(JsonUtils.getJsonObject("sync", obj))
                task.teamId = JsonUtils.getString("teams", JsonUtils.getJsonObject("link", obj))
                val user = JsonUtils.getJsonObject("assignee", obj)
                if (user.has("_id")) {
                    task.assignee = JsonUtils.getString("_id", user)
                }
                task.completed = JsonUtils.getBoolean("completed", obj)
            }
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
            `object`.add("sync", GsonUtils.gson.fromJson(task.sync, JsonObject::class.java))
            `object`.add("link", GsonUtils.gson.fromJson(task.link, JsonObject::class.java))
            return `object`
        }
    }
}
