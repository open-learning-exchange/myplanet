package org.ole.planet.myplanet.model

import android.content.SharedPreferences
import com.google.gson.JsonObject
import io.realm.Realm
import io.realm.RealmObject
import io.realm.annotations.PrimaryKey
import org.ole.planet.myplanet.utilities.NetworkUtils
import java.util.Date
import java.util.UUID

open class RealmResourceActivity : RealmObject() {
    @PrimaryKey
    @JvmField
    var id: String? = null
    @JvmField
    var _id: String? = null
    @JvmField
    var createdOn: String? = null
    @JvmField
    var _rev: String? = null
    @JvmField
    var time: Long = 0
    @JvmField
    var title: String? = null
    @JvmField
    var resourceId: String? = null
    @JvmField
    var parentCode: String? = null
    @JvmField
    var type: String? = null
    @JvmField
    var user: String? = null
    @JvmField
    var androidId: String? = null

    companion object {
        @JvmStatic
        fun serializeResourceActivities(realmResourceActivities: RealmResourceActivity): JsonObject {
            val ob = JsonObject()
            ob.addProperty("user", realmResourceActivities.user)
            ob.addProperty("resourceId", realmResourceActivities.resourceId)
            ob.addProperty("type", realmResourceActivities.type)
            ob.addProperty("title", realmResourceActivities.title)
            ob.addProperty("time", realmResourceActivities.time)
            ob.addProperty("createdOn", realmResourceActivities.createdOn)
            ob.addProperty("parentCode", realmResourceActivities.parentCode)
            ob.addProperty("androidId", NetworkUtils.getUniqueIdentifier())
            ob.addProperty("deviceName", NetworkUtils.getDeviceName())
            return ob
        }

        @JvmStatic
        fun onSynced(mRealm: Realm, settings: SharedPreferences) {
            if (!mRealm.isInTransaction) {
                mRealm.beginTransaction()
            }
            val user = mRealm.where(RealmUserModel::class.java).equalTo("id", settings.getString("userId", "")).findFirst()
                ?: return
            if (user.id?.startsWith("guest") == true) {
                return
            }
            val activities = mRealm.createObject(RealmResourceActivity::class.java, UUID.randomUUID().toString())
            activities.user = user.name
            activities._rev = null
            activities._id = null
            activities.parentCode = user.parentCode
            activities.createdOn = user.planetCode
            activities.type = "sync"
            activities.time = Date().time
            mRealm.commitTransaction()
        }
    }
}
