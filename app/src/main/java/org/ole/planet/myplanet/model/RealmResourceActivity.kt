package org.ole.planet.myplanet.model

import android.content.SharedPreferences
import com.google.gson.JsonObject
import io.realm.Realm
import io.realm.RealmObject
import io.realm.annotations.PrimaryKey
import java.util.Date
import java.util.UUID
import org.ole.planet.myplanet.utilities.NetworkUtils

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
        fun onSynced(settings: SharedPreferences) {
            var mRealm: Realm? = null
            try {
                mRealm = Realm.getDefaultInstance()
                mRealm.executeTransaction { realm ->
                    val user = realm.where(RealmUserModel::class.java)
                        .equalTo("id", settings.getString("userId", "")).findFirst()
                    if (user != null && user.id?.startsWith("guest") == false) {
                        val activities = realm.createObject(RealmResourceActivity::class.java, UUID.randomUUID().toString())
                        activities.user = user.name
                        activities.parentCode = user.parentCode
                        activities.createdOn = user.planetCode
                        activities.type = "sync"
                        activities.time = Date().time
                    }
                }
            } finally {
                mRealm?.close()
            }
        }
    }
}
