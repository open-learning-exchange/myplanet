package org.ole.planet.myplanet.model

import com.google.gson.Gson
import com.google.gson.JsonArray
import com.google.gson.JsonObject
import io.realm.Realm
import io.realm.RealmObject
import io.realm.annotations.PrimaryKey
import org.ole.planet.myplanet.utilities.JsonUtils
import java.util.Date
import java.util.UUID

open class RealmNotification : RealmObject() {
    @PrimaryKey
    var id: String = "${UUID.randomUUID()}"
    var userId: String ?= null
    var message: String ?= null
    var isRead: Boolean = false
    var createdAt: Date = Date()
    var type: String ?= null
    var relatedId: String? = null

    companion object {
        fun insertNotification(mRealm: Realm, act: JsonArray) {
            if (!mRealm.isInTransaction) {
                mRealm.beginTransaction()
            }

            val gson = Gson()
            val jsonObject = gson.fromJson(act.toString(), JsonObject::class.java)

            var notification = mRealm.where(RealmNotification::class.java).equalTo("_id", JsonUtils.getString("_id", jsonObject)).findFirst()
            if (notification == null) {
                notification = mRealm.createObject(RealmNotification::class.java, JsonUtils.getString("_id", jsonObject))
            }
            if (notification != null) {
                notification.id = JsonUtils.getString("_id", jsonObject)
                notification.userId = JsonUtils.getString("userId", jsonObject)
                notification.message = JsonUtils.getString("message", jsonObject)
                notification.type = JsonUtils.getString("type", jsonObject)
                notification.isRead = JsonUtils.getString("status", jsonObject) == "read"
//                notification.createdAt = Date(jsonObject.getLong("time"))
                notification.relatedId = JsonUtils.getString("_id", jsonObject)
            }
            mRealm.commitTransaction()
        }
    }
}

//            val realm = Realm.getDefaultInstance()
//            mRealm.executeTransaction { realmTransaction ->
//                val notification = realmTransaction.where(RealmNotification::class.java)
//                    .equalTo("id", couchDbDoc.getString("_id"))
//                    .findFirst() ?: realmTransaction.createObject(RealmNotification::class.java, couchDbDoc.getString("_id"))
//
//                notification.userId = couchDbDoc.getString("user")
//                notification.message = couchDbDoc.getString("message")
//                notification.type = couchDbDoc.getString("type")
//                notification.isRead = couchDbDoc.getString("status") == "read"
//                notification.createdAt = Date(couchDbDoc.getLong("time"))
//                notification.relatedId = couchDbDoc.getString("_id")
//
//                realmTransaction.insertOrUpdate(notification)
//            }
//            mRealm.close()
