package org.ole.planet.myplanet.model

import com.google.gson.Gson
import com.google.gson.JsonObject
import com.opencsv.CSVWriter
import io.realm.kotlin.Realm
import io.realm.kotlin.ext.realmListOf
import io.realm.kotlin.types.RealmList
import io.realm.kotlin.types.RealmObject
import io.realm.kotlin.types.annotations.PrimaryKey
import org.ole.planet.myplanet.MainApplication
import org.ole.planet.myplanet.utilities.JsonUtils
import java.io.File
import java.io.FileWriter
import java.io.IOException
import java.util.Date


class RealmChatHistory : RealmObject {
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

    constructor()

    companion object {
        private val chatDataList: MutableList<Array<String>> = mutableListOf()

        suspend fun insert(realm: Realm, act: JsonObject?) {
            realm.write {
                val chatHistoryId = JsonUtils.getString("_id", act)

                query(RealmChatHistory::class)
                    .query("_id == $0", chatHistoryId)
                    .first()
                    .find()
                    ?.let { findLatest(it)?.let { obj -> delete(obj) } }

                val chatHistory = RealmChatHistory().apply {
                    id = chatHistoryId
                    _rev = JsonUtils.getString("_rev", act)
                    _id = JsonUtils.getString("_id", act)
                    title = JsonUtils.getString("title", act)
                    createdDate = JsonUtils.getString("createdDate", act)
                    updatedDate = JsonUtils.getString("updatedDate", act)
                    user = JsonUtils.getString("user", act)
                    aiProvider = JsonUtils.getString("aiProvider", act)
                    conversations = parseConversations(act)
                    lastUsed = Date().time
                }

                this.copyToRealm(chatHistory)

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

        private fun parseConversations(act: JsonObject?): RealmList<Conversation>? {
            val jsonArray = JsonUtils.getJsonArray("conversations", act)
            return if (jsonArray.isEmpty) null else realmListOf<Conversation>().apply {
                jsonArray.forEach { element ->
                    add(Gson().fromJson(element, Conversation::class.java))
                }
            }
        }

        suspend fun addConversationToChatHistory(realm: Realm, chatHistoryId: String?, query: String?, response: String?) {
            realm.write {
                val chatHistory = query(RealmChatHistory::class)
                    .query("_id == $0", chatHistoryId)
                    .first()
                    .find()

                if (chatHistory != null) {
                    try {
                        val conversation = Conversation().apply {
                            this.query = query
                            this.response = response
                        }

                        if (chatHistory.conversations == null) {
                            chatHistory.conversations = realmListOf()
                        }
                        chatHistory.conversations?.add(conversation)
                        chatHistory.lastUsed = Date().time

                        findLatest(chatHistory)?.let { latest ->
                            latest.conversations = chatHistory.conversations
                            latest.lastUsed = chatHistory.lastUsed
                        }
                    } catch (e: Exception) {
                        e.printStackTrace()
                    }
                }
            }
        }

        fun writeCsv(filePath: String, data: List<Array<String>>) {
            try {
                val file = File(filePath)
                file.parentFile?.mkdirs()
                CSVWriter(FileWriter(file)).use { writer ->
                    writer.writeNext(arrayOf("chatHistoryId", "chatHistory_rev", "title", "createdDate", "updatedDate", "user", "aiProvider", "conversations"))
                    for (row in data) {
                        writer.writeNext(row)
                    }
                }
            } catch (e: IOException) {
                e.printStackTrace()
            }
        }

        fun chatWriteCsv() {
            writeCsv("${MainApplication.context.getExternalFilesDir(null)}/ole/chatHistory.csv", chatDataList)
        }
    }
}
