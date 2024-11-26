package org.ole.planet.myplanet.model

import android.content.SharedPreferences
import io.realm.kotlin.types.RealmObject
import io.realm.kotlin.types.annotations.PrimaryKey
import io.realm.kotlin.Realm
import com.google.gson.JsonObject
import org.ole.planet.myplanet.utilities.NetworkUtils
import java.util.Date
import java.util.UUID

class RealmResourceActivity : RealmObject {
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

    constructor()

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

        suspend fun onSynced(realm: Realm, settings: SharedPreferences) {
            realm.write {
                val userId = settings.getString("userId", "") ?: ""
                val user = query(RealmUserModel::class).query("id == $0", userId).first().find() ?: return@write

                if (user.id.startsWith("guest") == true) return@write

                copyToRealm(RealmResourceActivity().apply {
                    id = UUID.randomUUID().toString()
                    this.user = user.name
                    _rev = null
                    _id = null
                    parentCode = user.parentCode
                    createdOn = user.planetCode
                    type = "sync"
                    time = Date().time
                })
            }
        }
    }
}