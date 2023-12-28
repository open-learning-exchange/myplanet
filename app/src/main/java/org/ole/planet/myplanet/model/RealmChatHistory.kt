package org.ole.planet.myplanet.model

import com.google.gson.Gson
import com.google.gson.JsonArray
import com.google.gson.JsonObject
import io.realm.Realm
import io.realm.RealmList
import io.realm.RealmObject
import io.realm.annotations.PrimaryKey
import org.ole.planet.myplanet.utilities.JsonUtils

open class RealmChatHistory : RealmObject() {
    @PrimaryKey
    var id: String? = null
    var _id: String? = null
    var _rev: String? = null
    var user: String? = null
    var title: String? = null
    var updatedTime: String? = null
    var conversations: RealmList<Conversation>? = null
    companion object {
        @JvmStatic
        fun insert(mRealm: Realm, act: JsonObject?) {
            if (!mRealm.isInTransaction) mRealm.beginTransaction()
            val chatHistoryId = JsonUtils.getString("_id", act)
            val existingChatHistory = mRealm.where(RealmChatHistory::class.java).equalTo("_id", chatHistoryId).findFirst()
            existingChatHistory?.deleteFromRealm()
            val chatHistory = mRealm.createObject(RealmChatHistory::class.java, chatHistoryId)
            chatHistory._rev = JsonUtils.getString("_rev", act)
            chatHistory._id = JsonUtils.getString("_id", act)
            chatHistory.title = JsonUtils.getString("title", act)
            chatHistory.updatedTime = JsonUtils.getString("updatedTime", act)
            chatHistory.user = Gson().toJson(JsonUtils.getJsonObject("user", act))
            chatHistory.conversations = parseConversations(mRealm, JsonUtils.getJsonArray("conversations", act))
        }

        private fun parseConversations(realm: Realm, jsonArray: JsonArray): RealmList<Conversation> {
            val conversations = RealmList<Conversation>()
            for (element in jsonArray) {
                val conversation = Gson().fromJson(element, Conversation::class.java)
                val realmConversation = realm.copyToRealm(conversation)
                conversations.add(realmConversation)
            }
            return conversations
        }

        @JvmStatic
        fun addConversationToChatHistory(mRealm: Realm, chatHistoryId: String?, query: String?, response: String?) {
            val chatHistory = mRealm.where(RealmChatHistory::class.java).equalTo("_id", chatHistoryId).findFirst()
            if (chatHistory != null) {
                mRealm.beginTransaction()
                try {
                    val conversation = Conversation()
                    conversation.query = query
                    conversation.response = response
                    if (chatHistory.conversations == null) {
                        chatHistory.conversations = RealmList()
                    }
                    chatHistory.conversations!!.add(conversation)
                    mRealm.copyToRealmOrUpdate(chatHistory)
                    mRealm.commitTransaction()
                } catch (e: Exception) {
                    mRealm.cancelTransaction()
                    e.printStackTrace()
                }
            }
        }
    }
}