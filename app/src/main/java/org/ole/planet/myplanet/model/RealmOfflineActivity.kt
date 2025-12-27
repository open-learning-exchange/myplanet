package org.ole.planet.myplanet.model

import android.content.Context
import com.google.gson.JsonObject
import io.realm.Realm
import io.realm.RealmObject
import io.realm.Sort
import io.realm.annotations.Index
import io.realm.annotations.PrimaryKey
import org.ole.planet.myplanet.service.UserProfileDbHandler
import org.ole.planet.myplanet.utilities.JsonUtils
import org.ole.planet.myplanet.utilities.NetworkUtils

open class RealmOfflineActivity : RealmObject() {
    @PrimaryKey
    var id: String? = null
    var _id: String? = null
    @Index
    var _rev: String? = null
    var userName: String? = null
    var userId: String? = null
    var type: String? = null
    var description: String? = null
    var createdOn: String? = null
    var parentCode: String? = null
    var loginTime: Long? = null
    var logoutTime: Long? = null
    var androidId: String? = null
    fun changeRev(r: JsonObject?) {
        if (r != null) {
            _rev = JsonUtils.getString("_rev", r)
            _id = JsonUtils.getString("_id", r)
        }
    }

    companion object {
        @JvmStatic
        fun serializeLoginActivities(realmOfflineActivities: RealmOfflineActivity, context: Context): JsonObject {
            val ob = JsonObject()
            ob.addProperty("user", realmOfflineActivities.userName)
            ob.addProperty("type", realmOfflineActivities.type)
            ob.addProperty("loginTime", realmOfflineActivities.loginTime)
            ob.addProperty("logoutTime", realmOfflineActivities.logoutTime)
            ob.addProperty("createdOn", realmOfflineActivities.createdOn)
            ob.addProperty("parentCode", realmOfflineActivities.parentCode)
            ob.addProperty("androidId", NetworkUtils.getUniqueIdentifier())
            ob.addProperty("deviceName", NetworkUtils.getDeviceName())
            ob.addProperty("customDeviceName", NetworkUtils.getCustomDeviceName(context))
            if (realmOfflineActivities._id != null) {
                ob.addProperty("_id", realmOfflineActivities.logoutTime)
            }
            if (realmOfflineActivities._rev != null) {
                ob.addProperty("_rev", realmOfflineActivities._rev)
            }
            return ob
        }

        @JvmStatic
        fun getRecentLogin(mRealm: Realm): RealmOfflineActivity? {
            return mRealm.where(RealmOfflineActivity::class.java)
                .equalTo("type", UserProfileDbHandler.KEY_LOGIN).sort("loginTime", Sort.DESCENDING)
                .findFirst()
        }

        @JvmStatic
        fun insert(mRealm: Realm, act: JsonObject?) {
            var activities = mRealm.where(RealmOfflineActivity::class.java).equalTo("_id", JsonUtils.getString("_id", act)).findFirst()
            if (activities == null) {
                activities = mRealm.createObject(RealmOfflineActivity::class.java, JsonUtils.getString("_id", act))
            }
            if (activities != null) {
                activities._rev = JsonUtils.getString("_rev", act)
                activities._id = JsonUtils.getString("_id", act)
                activities.loginTime = JsonUtils.getLong("loginTime", act)
                activities.type = JsonUtils.getString("type", act)
                activities.userName = JsonUtils.getString("user", act)
                activities.parentCode = JsonUtils.getString("parentCode", act)
                activities.createdOn = JsonUtils.getString("createdOn", act)
                activities.userName = JsonUtils.getString("user", act)
                activities.logoutTime = JsonUtils.getLong("logoutTime", act)
                activities.androidId = JsonUtils.getString("androidId", act)
            }
        }
    }
}
