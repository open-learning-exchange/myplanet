package org.ole.planet.myplanet.model

import androidx.room.Entity
import androidx.room.Ignore
import androidx.room.PrimaryKey

@Entity(tableName = "feedback", indices = [androidx.room.Index("openTime"), androidx.room.Index("owner"), androidx.room.Index("isUploaded")])
open class Feedback {
    @PrimaryKey
    @JvmField
    var id: String = ""
    @JvmField
    var _id: String? = null
    var title: String? = null
    var source: String? = null
    var status: String? = null
    var priority: String? = null
    var owner: String? = null
    var openTime: Long = 0
    var type: String? = null
    var url: String? = null
    var isUploaded = false
    var _rev: String? = null
    var messages: String? = null
        set(value) {
            field = value
            cachedMessages = null
            cachedMessageList = null
        }
    var item: String? = null
    var parentCode: String? = null
    var state: String? = null

    // Parse caches for [messages], read and filled by FeedbackJson.kt; the [messages] setter clears
    // them. `cachedMessages` holds a Gson JsonArray, typed Any? to keep the entity Gson-free.
    @Ignore
    @Transient
    var cachedMessages: Any? = null

    @Ignore
    @Transient
    var cachedMessageList: List<FeedbackReply>? = null

    companion object
}
