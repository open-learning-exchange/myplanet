package org.ole.planet.myplanet.model

import com.google.gson.JsonObject
import io.realm.Realm
import io.realm.RealmObject
import io.realm.annotations.PrimaryKey
import java.util.Date
import java.util.UUID

open class RealmNotification : RealmObject() {
    @PrimaryKey
    var id: String = UUID.randomUUID().toString()
    var userId: String = ""
    var message: String = ""
    var isRead: Boolean = false
    var createdAt: Date = Date()
    var type: String = ""
    var relatedId: String? = null
    var title: String? = null
    var link: String? = null
    var priority: Int = 0
    var isFromServer: Boolean = false
    var rev: String? = null
    var needsSync: Boolean = false

    companion object {
        @JvmStatic
        fun insert(mRealm: Realm, doc: JsonObject) {
            val id = doc.get("_id")?.asString ?: return
            val notification = mRealm.where(RealmNotification::class.java)
                .equalTo("id", id).findFirst()
                ?: mRealm.createObject(RealmNotification::class.java, id)
            notification.apply {
                userId = doc.get("user")?.asString ?: ""
                message = doc.get("message")?.asString ?: ""
                type = doc.get("type")?.asString ?: ""
                link = doc.get("link")?.asString
                priority = doc.get("priority")?.asInt ?: 0
                rev = doc.get("_rev")?.asString
                // Preserve local read state if a change is pending upload
                if (!needsSync) {
                    isRead = doc.get("status")?.asString != "unread"
                }
                createdAt = doc.get("time")?.let { Date(it.asLong) } ?: Date()
                isFromServer = true
            }
        }
    }
}
