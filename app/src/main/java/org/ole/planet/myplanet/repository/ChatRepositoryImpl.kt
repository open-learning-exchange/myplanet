package org.ole.planet.myplanet.repository

import android.content.Context
import android.content.Context.MODE_PRIVATE
import android.content.SharedPreferences
import android.text.TextUtils
import androidx.core.content.edit
import com.google.gson.JsonArray
import com.google.gson.JsonObject
import dagger.hilt.android.qualifiers.ApplicationContext
import io.realm.Case
import io.realm.Realm
import io.realm.RealmList
import io.realm.Sort
import javax.inject.Inject
import org.ole.planet.myplanet.data.DatabaseService
import org.ole.planet.myplanet.model.RealmChatHistory
import org.ole.planet.myplanet.model.RealmChatHistory.Companion.addConversationToChatHistory
import org.ole.planet.myplanet.model.RealmNews
import org.ole.planet.myplanet.utils.Constants.PREFS_NAME
import org.ole.planet.myplanet.utils.DownloadUtils.extractLinks
import org.ole.planet.myplanet.utils.JsonUtils
import org.ole.planet.myplanet.utils.UrlUtils

class ChatRepositoryImpl @Inject constructor(
    databaseService: DatabaseService,
    @param:ApplicationContext private val context: Context
) : RealmRepository(databaseService), ChatRepository {

    private val concatenatedLinks = ArrayList<String>()

    override suspend fun getChatHistoryForUser(userName: String?): List<RealmChatHistory> {
        if (userName.isNullOrEmpty()) {
            return emptyList()
        }
        return queryList(RealmChatHistory::class.java) {
            equalTo("user", userName)
            sort("id", Sort.DESCENDING)
        }
    }

    override suspend fun getPlanetNewsMessages(planetCode: String?): List<RealmNews> {
        if (planetCode.isNullOrEmpty()) {
            return emptyList()
        }
        return queryList(RealmNews::class.java) {
            equalTo("docType", "message", Case.INSENSITIVE)
            equalTo("createdOn", planetCode, Case.INSENSITIVE)
        }
    }

    override suspend fun getLatestRev(id: String): String? {
        return withRealm { realm ->
            realm.where(RealmChatHistory::class.java)
                .equalTo("_id", id)
                .findAll()
                .maxByOrNull { rev -> rev._rev?.split("-")?.get(0)?.toIntOrNull() ?: 0 }
                ?._rev
        }
    }

    override suspend fun saveNewChat(chat: JsonObject) {
        withRealmAsync { realm ->
            realm.executeTransaction {
                RealmChatHistory.insert(it, chat)
            }
        }
    }

    override suspend fun continueConversation(id: String, query: String, response: String, rev: String) {
        withRealmAsync { realm ->
            realm.executeTransaction {
                addConversationToChatHistory(it, id, query, response, rev)
            }
        }
    }

    override suspend fun insertNewsList(docs: List<JsonObject>) {
        executeTransaction { mRealm ->
            docs.forEach { doc ->
                insertNewsToRealm(mRealm, doc)
            }
        }
        saveConcatenatedLinksToPrefs()
    }

    override suspend fun insertNewsFromJson(doc: JsonObject) {
        executeTransaction { mRealm ->
            insertNewsToRealm(mRealm, doc)
        }
        saveConcatenatedLinksToPrefs()
    }

    private fun insertNewsToRealm(mRealm: Realm, doc: JsonObject) {
        var news = mRealm.where(RealmNews::class.java).equalTo("_id", JsonUtils.getString("_id", doc)).findFirst()
        if (news == null) {
            news = mRealm.createObject(RealmNews::class.java, JsonUtils.getString("_id", doc))
        }
        news?._rev = JsonUtils.getString("_rev", doc)
        news?._id = JsonUtils.getString("_id", doc)
        news?.viewableBy = JsonUtils.getString("viewableBy", doc)
        news?.docType = JsonUtils.getString("docType", doc)
        news?.avatar = JsonUtils.getString("avatar", doc)
        news?.updatedDate = JsonUtils.getLong("updatedDate", doc)
        news?.viewableId = JsonUtils.getString("viewableId", doc)
        news?.createdOn = JsonUtils.getString("createdOn", doc)
        news?.messageType = JsonUtils.getString("messageType", doc)
        news?.messagePlanetCode = JsonUtils.getString("messagePlanetCode", doc)
        news?.replyTo = JsonUtils.getString("replyTo", doc)
        news?.parentCode = JsonUtils.getString("parentCode", doc)
        val user = JsonUtils.getJsonObject("user", doc)
        news?.user = JsonUtils.gson.toJson(JsonUtils.getJsonObject("user", doc))
        news?.userId = JsonUtils.getString("_id", user)
        news?.userName = JsonUtils.getString("name", user)
        news?.time = JsonUtils.getLong("time", doc)
        val images = JsonUtils.getJsonArray("images", doc)
        val message = JsonUtils.getString("message", doc)
        news?.message = message
        val links = extractLinks(message)
        val baseUrl = UrlUtils.getUrl()
        synchronized(concatenatedLinks) {
            for (link in links) {
                val concatenatedLink = "$baseUrl/$link"
                concatenatedLinks.add(concatenatedLink)
            }
        }
        news?.images = JsonUtils.gson.toJson(images)
        val labels = JsonUtils.getJsonArray("labels", doc)
        news?.viewIn = JsonUtils.gson.toJson(JsonUtils.getJsonArray("viewIn", doc))
        news?.setLabels(labels)
        news?.chat = JsonUtils.getBoolean("chat", doc)

        val newsObj = JsonUtils.getJsonObject("news", doc)
        news?.newsId = JsonUtils.getString("_id", newsObj)
        news?.newsRev = JsonUtils.getString("_rev", newsObj)
        news?.newsUser = JsonUtils.getString("user", newsObj)
        news?.aiProvider = JsonUtils.getString("aiProvider", newsObj)
        news?.newsTitle = JsonUtils.getString("title", newsObj)
        news?.conversations = JsonUtils.gson.toJson(JsonUtils.getJsonArray("conversations", newsObj))
        news?.newsCreatedDate = JsonUtils.getLong("createdDate", newsObj)
        news?.newsUpdatedDate = JsonUtils.getLong("updatedDate", newsObj)
        news?.sharedBy = JsonUtils.getString("sharedBy", newsObj)
    }

    override fun serializeNews(news: RealmNews): JsonObject {
        val `object` = JsonObject()
        `object`.addProperty("chat", news.chat)
        `object`.addProperty("message", news.message)
        if (news._id != null) `object`.addProperty("_id", news._id)
        if (news._rev != null) `object`.addProperty("_rev", news._rev)
        `object`.addProperty("time", news.time)
        `object`.addProperty("createdOn", news.createdOn)
        `object`.addProperty("docType", news.docType)
        addViewIn(`object`, news)
        `object`.addProperty("avatar", news.avatar)
        `object`.addProperty("messageType", news.messageType)
        `object`.addProperty("messagePlanetCode", news.messagePlanetCode)
        `object`.addProperty("createdOn", news.createdOn)
        `object`.addProperty("replyTo", news.replyTo)
        `object`.addProperty("parentCode", news.parentCode)
        `object`.add("images", news.imagesArray)
        `object`.add("labels", news.labelsArray)
        `object`.add("user", JsonUtils.gson.fromJson(news.user, JsonObject::class.java))
        val newsObject = JsonObject()
        newsObject.addProperty("_id", news.newsId)
        newsObject.addProperty("_rev", news.newsRev)
        newsObject.addProperty("user", news.newsUser)
        newsObject.addProperty("aiProvider", news.aiProvider)
        newsObject.addProperty("title", news.newsTitle)
        newsObject.add("conversations", JsonUtils.gson.fromJson(news.conversations, JsonArray::class.java))
        newsObject.addProperty("createdDate", news.newsCreatedDate)
        newsObject.addProperty("updatedDate", news.newsUpdatedDate)
        newsObject.addProperty("sharedBy", news.sharedBy)
        `object`.add("news", newsObject)
        return `object`
    }

    private fun addViewIn(`object`: JsonObject, news: RealmNews) {
        if (!TextUtils.isEmpty(news.viewableId)) {
            `object`.addProperty("viewableId", news.viewableId)
            `object`.addProperty("viewableBy", news.viewableBy)
        }
        if (!TextUtils.isEmpty(news.viewIn)) {
            val ar = JsonUtils.gson.fromJson(news.viewIn, JsonArray::class.java)
            if (ar.size() > 0) `object`.add("viewIn", ar)
        }
    }

    private fun saveConcatenatedLinksToPrefs() {
        val settings: SharedPreferences = context.getSharedPreferences(PREFS_NAME, MODE_PRIVATE)
        val existingJsonLinks = settings.getString("concatenated_links", null)
        val existingConcatenatedLinks = if (existingJsonLinks != null) {
            JsonUtils.gson.fromJson(existingJsonLinks, Array<String>::class.java).toMutableList()
        } else {
            mutableListOf()
        }
        val linksToProcess: List<String>
        synchronized(concatenatedLinks) {
            linksToProcess = concatenatedLinks.toList()
        }
        for (link in linksToProcess) {
            if (!existingConcatenatedLinks.contains(link)) {
                existingConcatenatedLinks.add(link)
            }
        }
        val jsonConcatenatedLinks = JsonUtils.gson.toJson(existingConcatenatedLinks)
        settings.edit { putString("concatenated_links", jsonConcatenatedLinks) }
    }
}
