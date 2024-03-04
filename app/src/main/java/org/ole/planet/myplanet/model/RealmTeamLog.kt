package org.ole.planet.myplanet.model

import android.content.Context
import android.text.TextUtils
import com.google.gson.JsonObject
import io.realm.Realm
import io.realm.RealmObject
import io.realm.annotations.PrimaryKey
import org.ole.planet.myplanet.utilities.JsonUtils
import org.ole.planet.myplanet.utilities.NetworkUtils
import org.ole.planet.myplanet.utilities.Utilities
import java.util.Calendar

open class RealmTeamLog : RealmObject() {
    @PrimaryKey
    var id: String? = null
    @JvmField
    var _id: String? = null
    @JvmField
    var _rev: String? = null
    @JvmField
    var teamId: String? = null
    @JvmField
    var user: String? = null
    @JvmField
    var type: String? = null
    @JvmField
    var teamType: String? = null
    @JvmField
    var createdOn: String? = null
    @JvmField
    var parentCode: String? = null
    @JvmField
    var time: Long? = null
    @JvmField
    var uploaded = false
    companion object {
        @JvmStatic
        fun getVisitCount(realm: Realm, userName: String?, teamId: String?): Long {
            return realm.where(RealmTeamLog::class.java).equalTo("type", "teamVisit")
                .equalTo("user", userName).equalTo("teamId", teamId).count()
        }

        @JvmStatic
        fun getVisitByTeam(realm: Realm, teamId: String?): Long {
            val calendar = Calendar.getInstance()
            calendar.add(Calendar.DAY_OF_YEAR, -30)
            return realm.where(RealmTeamLog::class.java).equalTo("type", "teamVisit")
                .equalTo("teamId", teamId).greaterThan("time", calendar.timeInMillis).count()
        }

        @JvmStatic
        fun serializeTeamActivities(log: RealmTeamLog, context: Context?): JsonObject {
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
            Utilities.log("Insert team visits")
            var tag = mRealm.where(RealmTeamLog::class.java).equalTo("id", JsonUtils.getString("_id", act)).findFirst()
            if (tag == null) tag = mRealm.createObject(RealmTeamLog::class.java, JsonUtils.getString("_id", act))
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
