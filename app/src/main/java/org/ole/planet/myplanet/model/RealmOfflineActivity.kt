package org.ole.planet.myplanet.model

import android.content.Context
import com.google.gson.JsonObject
import io.realm.Realm
import io.realm.RealmObject
import io.realm.Sort
import io.realm.annotations.PrimaryKey
import org.ole.planet.myplanet.service.UserProfileDbHandler
import org.ole.planet.myplanet.utilities.JsonUtils
import org.ole.planet.myplanet.utilities.NetworkUtils

open class RealmOfflineActivity : RealmObject() {
    @PrimaryKey
    var id: String? = null
    @JvmField
    var _id: String? = null
    @JvmField
    var _rev: String? = null
    @JvmField
    var userName: String? = null
    @JvmField
    var userId: String? = null
    @JvmField
    var type: String? = null
    @JvmField
    var description: String? = null
    @JvmField
    var createdOn: String? = null
    @JvmField
    var parentCode: String? = null
    @JvmField
    var loginTime: Long? = null
    @JvmField
    var logoutTime: Long? = null
    @JvmField
    var androidId: String? = null
    fun changeRev(r: JsonObject?) {
        if (r != null) {
            _rev = JsonUtils.getString("_rev", r)
            _id = JsonUtils.getString("_id", r)
        }
    }

    companion object {
        @JvmStatic
        fun serializeLoginActivities(realm_offlineActivities: RealmOfflineActivity, context: Context): JsonObject {
            val ob = JsonObject()
            ob.addProperty("user", realm_offlineActivities.userName)
            ob.addProperty("type", realm_offlineActivities.type)
            ob.addProperty("loginTime", realm_offlineActivities.loginTime)
            ob.addProperty("logoutTime", realm_offlineActivities.logoutTime)
            ob.addProperty("createdOn", realm_offlineActivities.createdOn)
            ob.addProperty("parentCode", realm_offlineActivities.parentCode)
            ob.addProperty("androidId", NetworkUtils.getUniqueIdentifier())
            ob.addProperty("deviceName", NetworkUtils.getDeviceName())
            ob.addProperty("customDeviceName", NetworkUtils.getCustomDeviceName(context))
            if (realm_offlineActivities._id != null) {
                ob.addProperty("_id", realm_offlineActivities.logoutTime)
            }
            if (realm_offlineActivities._rev != null) {
                ob.addProperty("_rev", realm_offlineActivities._rev)
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
            var activities = mRealm.where(RealmOfflineActivity::class.java)
                .equalTo("_id", JsonUtils.getString("_id", act))
                .findFirst()
            if (activities == null) activities = mRealm.createObject(
                RealmOfflineActivity::class.java, JsonUtils.getString("_id", act)
            )
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
