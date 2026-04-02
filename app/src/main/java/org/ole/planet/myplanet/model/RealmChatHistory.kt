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
    companion object {
        @JvmStatic
        fun fromJson(act: JsonObject?): RealmChatHistory {
            val chatHistory = RealmChatHistory()
            val chatHistoryId = JsonUtils.getString("_id", act)
            chatHistory.id = chatHistoryId
            chatHistory._rev = JsonUtils.getString("_rev", act)
            chatHistory._id = chatHistoryId
            chatHistory.title = JsonUtils.getString("title", act)
            chatHistory.createdDate = "${JsonUtils.getLong("createdDate", act)}"
            chatHistory.updatedDate = "${JsonUtils.getLong("updatedDate", act)}"
            chatHistory.user = JsonUtils.getString("user", act)
            chatHistory.aiProvider = JsonUtils.getString("aiProvider", act)
            chatHistory.conversations = parseConversations(JsonUtils.getJsonArray("conversations", act))
            chatHistory.lastUsed = System.currentTimeMillis()
            return chatHistory
        }

        private fun parseConversations(jsonArray: JsonArray): RealmList<RealmConversation> {
            val conversations = RealmList<RealmConversation>()
            val unmanagedConversations = jsonArray.map { JsonUtils.gson.fromJson(it, RealmConversation::class.java) }
            conversations.addAll(unmanagedConversations)
            return conversations
        }
    }
}
