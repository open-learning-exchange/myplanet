package org.ole.planet.myplanet.model

import com.google.gson.Gson
import com.google.gson.JsonArray
import com.google.gson.JsonObject
import com.opencsv.CSVWriter
import io.realm.Realm
import io.realm.RealmList
import io.realm.RealmObject
import io.realm.annotations.PrimaryKey
import org.ole.planet.myplanet.MainApplication.Companion.context
import org.ole.planet.myplanet.utilities.JsonUtils
import java.util.Date
import java.io.File
import java.io.FileWriter
import java.io.IOException

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
        private val chatDataList: MutableList<Array<String>> = mutableListOf()

        @JvmStatic
        fun insert(mRealm: Realm, act: JsonObject?) {
            mRealm.executeTransaction { realmInstance ->
                val chatHistoryId = JsonUtils.getString("_id", act)
                val existingChatHistory = realmInstance.where(RealmChatHistory::class.java)
                    .equalTo("_id", chatHistoryId)
                    .findFirst()
                existingChatHistory?.deleteFromRealm()

                val chatHistory = realmInstance.createObject(RealmChatHistory::class.java, chatHistoryId)
                chatHistory._rev = JsonUtils.getString("_rev", act)
                chatHistory._id = JsonUtils.getString("_id", act)
                chatHistory.title = JsonUtils.getString("title", act)
                chatHistory.createdDate = JsonUtils.getString("createdDate", act)
                chatHistory.updatedDate = JsonUtils.getString("updatedDate", act)
                chatHistory.user = JsonUtils.getString("user", act)
                chatHistory.aiProvider = JsonUtils.getString("aiProvider", act)
                chatHistory.conversations = parseConversations(realmInstance, JsonUtils.getJsonArray("conversations", act))
                chatHistory.lastUsed = Date().time

                val csvRow = arrayOf(
                    JsonUtils.getString("_id", act),
                    JsonUtils.getString("_rev", act),
                    JsonUtils.getString("title", act),
                    JsonUtils.getString("createdDate", act),
                    JsonUtils.getString("updatedDate", act),
                    JsonUtils.getString("user", act),
                    JsonUtils.getString("aiProvider", act),
                    JsonUtils.getJsonArray("conversations", act).toString()
                )
                chatDataList.add(csvRow)
            }
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

        fun addConversationToChatHistory(mRealm: Realm, chatHistoryId: String?, query: String?, response: String?, newRev: String?) {
            val chatHistory = mRealm.where(RealmChatHistory::class.java).equalTo("_id", chatHistoryId).findFirst()
            if (chatHistory != null) {
                if (!mRealm.isInTransaction) {
                    mRealm.beginTransaction()
                }
                try {
                    val conversation = Conversation()
                    conversation.query = query
                    conversation.response = response
                    if (chatHistory.conversations == null) {
                        chatHistory.conversations = RealmList()
                    }
                    chatHistory.conversations?.add(conversation)
                    chatHistory.lastUsed = Date().time
                    if (!newRev.isNullOrEmpty()) {
                        chatHistory._rev = newRev
                    }
                    mRealm.copyToRealmOrUpdate(chatHistory)
                } catch (e: Exception) {
                    mRealm.cancelTransaction()
                    e.printStackTrace()
                }
            }
        }

        fun writeCsv(filePath: String, data: List<Array<String>>) {
            try {
                val file = File(filePath)
                file.parentFile?.mkdirs()
                val writer = CSVWriter(FileWriter(file))
                writer.writeNext(arrayOf("chatHistoryId", "chatHistory_rev", "title", "createdDate", "updatedDate", "user", "aiProvider", "conversations"))
                for (row in data) {
                    writer.writeNext(row)
                }
                writer.close()
            } catch (e: IOException) {
                e.printStackTrace()
            }
        }

        fun chatWriteCsv() {
            writeCsv("${context.getExternalFilesDir(null)}/ole/chatHistory.csv", chatDataList)
        }
    }
}
