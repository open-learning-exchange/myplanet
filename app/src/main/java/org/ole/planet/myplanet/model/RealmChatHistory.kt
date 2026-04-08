package org.ole.planet.myplanet.model

import com.google.gson.JsonArray
import com.google.gson.JsonObject
import io.realm.Realm
import io.realm.RealmList
import io.realm.RealmObject
import io.realm.annotations.PrimaryKey
import java.util.Date
import org.ole.planet.myplanet.utils.JsonUtils

open class RealmChatHistory : RealmObject() {
    @PrimaryKey
    var id: String? = null
    var _id: String? = null
    var _rev: String? = null
    var user: String? = null
    var aiProvider: String? = null
    var title: String? = null
    var createdDate: String? = null
    var updatedDate: String? = null
    var lastUsed: Long = 0
    var conversations: RealmList<RealmConversation>? = null
}
