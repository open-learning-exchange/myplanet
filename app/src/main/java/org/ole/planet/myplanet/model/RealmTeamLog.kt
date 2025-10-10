package org.ole.planet.myplanet.model

import android.content.Context
import android.text.TextUtils
import com.google.gson.JsonObject
import io.realm.Realm
import io.realm.RealmObject
import io.realm.annotations.PrimaryKey
import java.util.Calendar
import org.ole.planet.myplanet.utilities.JsonUtils
import org.ole.planet.myplanet.utilities.NetworkUtils

open class RealmTeamLog : RealmObject() {
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
    var uploaded = false
    companion object {
        @JvmStatic
        fun getVisitCount(realm: Realm, userName: String?, teamId: String?): Long {
            return realm.where(RealmTeamLog::class.java).equalTo("type", "teamVisit").equalTo("user", userName).equalTo("teamId", teamId).count()
        }

        @JvmStatic
        fun getLastVisit(realm: Realm, userName: String?, teamId: String?): Long? {
            return realm.where(RealmTeamLog::class.java)
                .equalTo("type", "teamVisit")
                .equalTo("user", userName)
                .equalTo("teamId", teamId)
                .max("time")?.toLong()
        }

        @JvmStatic
        fun serializeTeamActivities(log: RealmTeamLog, context: Context): JsonObject {
            val ob = JsonObject()
            ob.addProperty("user", log.user)
            ob.addProperty("type", log.type)
            ob.addProperty("createdOn", log.createdOn)
            ob.addProperty("parentCode", log.parentCode)
            ob.addProperty("teamType", log.teamType)
            ob.addProperty("time", log.time)
            ob.addProperty("teamId", log.teamId)
            ob.addProperty("androidId", NetworkUtils.getUniqueIdentifier())
            ob.addProperty("deviceName", NetworkUtils.getDeviceName())
            ob.addProperty("customDeviceName", NetworkUtils.getCustomDeviceName(context))
            if (!TextUtils.isEmpty(log._rev)) {
                ob.addProperty("_rev", log._rev)
                ob.addProperty("_id", log._id)
            }
            return ob
        }

        @JvmStatic
        fun insert(mRealm: Realm, act: JsonObject?) {
            var tag = mRealm.where(RealmTeamLog::class.java)
                .equalTo("id", JsonUtils.getString("_id", act)).findFirst()
            if (tag == null) {
                tag = mRealm.createObject(RealmTeamLog::class.java, JsonUtils.getString("_id", act))
            }
            if (tag != null) {
                tag._rev = JsonUtils.getString("_rev", act)
                tag._id = JsonUtils.getString("_id", act)
                tag.type = JsonUtils.getString("type", act)
                tag.user = JsonUtils.getString("user", act)
                tag.createdOn = JsonUtils.getString("createdOn", act)
                tag.parentCode = JsonUtils.getString("parentCode", act)
                tag.time = JsonUtils.getLong("time", act)
                tag.teamId = JsonUtils.getString("teamId", act)
                tag.teamType = JsonUtils.getString("teamType", act)
            }
        }
    }
}
