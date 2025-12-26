package org.ole.planet.myplanet.model

import com.google.gson.JsonArray
import com.google.gson.JsonObject
import io.realm.Realm
import io.realm.RealmList
import io.realm.RealmObject
import io.realm.annotations.PrimaryKey
import java.util.Date
import org.ole.planet.myplanet.utilities.JsonUtils

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
    var conversations: RealmList<Conversation>? = null
    companion object {
        @JvmStatic
        fun insert(mRealm: Realm, act: JsonObject?) {
            val chatHistoryId = JsonUtils.getString("_id", act)
            val existingChatHistory = mRealm.where(RealmChatHistory::class.java).equalTo("_id", chatHistoryId).findFirst()
            existingChatHistory?.deleteFromRealm()
            val chatHistory = mRealm.createObject(RealmChatHistory::class.java, chatHistoryId)
            chatHistory._rev = JsonUtils.getString("_rev", act)
            chatHistory._id = JsonUtils.getString("_id", act)
            chatHistory.title = JsonUtils.getString("title", act)
            chatHistory.createdDate = JsonUtils.getString("createdDate", act)
            chatHistory.updatedDate = JsonUtils.getString("updatedDate", act)
            chatHistory.user = JsonUtils.getString("user", act)
            chatHistory.aiProvider = JsonUtils.getString("aiProvider", act)
            chatHistory.conversations = parseConversations(mRealm, JsonUtils.getJsonArray("conversations", act))
            chatHistory.lastUsed = Date().time
        }

        private fun parseConversations(realm: Realm, jsonArray: JsonArray): RealmList<Conversation> {
            val conversations = RealmList<Conversation>()
            for (element in jsonArray) {
                val conversation = JsonUtils.gson.fromJson(element, Conversation::class.java)
                val realmConversation = realm.copyToRealm(conversation)
                conversations.add(realmConversation)
            }
            return conversations
        }

        fun addConversationToChatHistory(mRealm: Realm, chatHistoryId: String?, query: String?, response: String?, newRev: String?) {
            val chatHistory = mRealm.where(RealmChatHistory::class.java).equalTo("_id", chatHistoryId).findFirst()
            if (chatHistory != null) {
                if (chatHistory.conversations == null) {
                    chatHistory.conversations = RealmList()
                }
                val conversation = mRealm.createObject(Conversation::class.java)
                conversation.query = query
                conversation.response = response
                chatHistory.conversations?.add(conversation)
                chatHistory.lastUsed = Date().time
                if (!newRev.isNullOrEmpty()) {
                    chatHistory._rev = newRev
                }
            }
        }
    }
}
