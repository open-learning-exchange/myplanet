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
    var id: String? = null
    var _id: String? = null
    var createdOn: String? = null
    var _rev: String? = null
    var time: Long = 0
    var title: String? = null
    var resourceId: String? = null
    var parentCode: String? = null
    var type: String? = null
    var user: String? = null
    var androidId: String? = null

    companion object {
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
