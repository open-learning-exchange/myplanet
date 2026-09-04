package org.ole.planet.myplanet.repository

import com.google.gson.Gson
import com.google.gson.JsonArray
import com.google.gson.JsonObject
import java.util.Calendar
import java.util.HashMap
import javax.inject.Inject
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import org.ole.planet.myplanet.data.room.dao.NewsDao
import org.ole.planet.myplanet.data.room.dao.NewsLogDao
import org.ole.planet.myplanet.di.PlainGson
import org.ole.planet.myplanet.model.News
import org.ole.planet.myplanet.model.UserEntity
import org.ole.planet.myplanet.services.SharedPrefManager
import org.ole.planet.myplanet.utils.DispatcherProvider
import org.ole.planet.myplanet.utils.DownloadUtils.extractLinks
import org.ole.planet.myplanet.utils.JsonUtils
import org.ole.planet.myplanet.utils.UrlUtils
import org.ole.planet.myplanet.utils.addDocumentOrigin

class VoicesRepositoryImpl @Inject constructor(
    private val dispatcherProvider: DispatcherProvider,
    private val gson: Gson,
    @PlainGson private val plainGson: Gson,
    private val sharedPrefManager: SharedPrefManager,
    private val newsDao: NewsDao,
    private val newsLogDao: NewsLogDao
) : VoicesRepository {
    private val concatenatedLinks = ArrayList<String>()

    override suspend fun getNewsForUpload(): List<NewsUploadData> {
        return newsDao.getAll()
            .mapNotNull { news ->
                if (news.userId?.startsWith("guest") == true) null
                else NewsUploadData(
                    id = news.id,
                    _id = news._id,
                    message = news.message,
                    imageUrls = news.imageUrls?.toList() ?: emptyList(),
                    newsJson = serializeNews(news)
                )
            }
    }

    override suspend fun markNewsUploaded(updates: List<NewsUpdateData>) {
        val ids = updates.mapNotNull { it.id }
        if (ids.isEmpty()) return
        val newsById = newsDao.getByIds(ids).associateBy { it.id }
        val toUpdate = mutableListOf<News>()
        updates.forEach { update ->
            update.id?.let { id ->
                newsById[id]?.let { news ->
                    news.imageUrls = emptyList()
                    news._id = update._id
                    news._rev = update._rev
                    news.images = gson.toJson(update.imagesArray)
                    toUpdate.add(news)
                }
            }
        }
        if (toUpdate.isNotEmpty()) {
            newsDao.upsertAll(toUpdate)
        }
    }

    override suspend fun getNewsWithReplies(newsId: String): Pair<News?, List<News>> {
        val news = newsDao.getById(newsId)
        val replies = newsDao.getReplies(newsId)
        return news to replies
    }

    override suspend fun isAlreadyShared(chatId: String, viewInId: String): Boolean {
        return newsDao.getByNewsId(chatId).any { news ->
            news.viewIn?.contains("\"_id\":\"$viewInId\"", ignoreCase = true) == true
        }
    }

    override suspend fun createNews(map: HashMap<String?, String>, user: UserEntity?, imageList: List<String>?): News {
        val news = News.createNews(map, user, imageList)
        newsDao.upsert(news)
        return news
    }

    override suspend fun createTeamNews(newsData: HashMap<String?, String>, user: UserEntity, imageList: List<String>?): Boolean {
        return try {
            val news = News.createNews(newsData, user, imageList)
            newsDao.upsert(news)
            true
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }


    private fun teamIdPattern(teamId: String): String {
        val escaped = teamId
            .replace("\\", "\\\\")
            .replace("%", "\\%")
            .replace("_", "\\_")
        return "%\"_id\":\"$escaped\"%"
    }

    private fun isVisibleToUser(news: News, userIdentifier: String): Boolean {
        if (news.viewableBy.equals("community", ignoreCase = true)) {
            return true
        }

        val viewIn = news.viewIn ?: return false
        if (viewIn.isEmpty()) {
            return false
        }

        return try {
            val array = news.parsedViewIn ?: gson.fromJson(viewIn, JsonArray::class.java)
            array?.any { element ->
                if (element == null || !element.isJsonObject) return@any false
                val obj = element.asJsonObject
                val section = JsonUtils.getString("section", obj)
                if (section.equals("community", ignoreCase = true)) {
                    val id = JsonUtils.getString("_id", obj)
                    id.isEmpty() || id == "@" || userIdentifier.isEmpty() || userIdentifier == "@" || id.equals(userIdentifier, ignoreCase = true)
                } else {
                    false
                }
            } == true
        } catch (throwable: Throwable) {
            false
        }
    }

    override suspend fun getCommunityNews(userIdentifier: String): Flow<List<News>> {
        return newsDao.getTopLevelMessagesFlow()
            .distinctUntilChanged { old, new ->
                old.size == new.size && old.zip(new).all { (o, n) ->
                    o.id == n.id && o.time == n.time &&
                            // Labels are semantically a set; order carries no meaning.
                            o.labels?.toSet() == n.labels?.toSet() &&
                            o.message == n.message &&
                            o.isEdited == n.isEdited &&
                            o.imageUrls == n.imageUrls &&
                            o.images == n.images &&
                            o.viewIn == n.viewIn &&
                            o.viewableBy == n.viewableBy &&
                            o.viewableId == n.viewableId &&
                            o.sharedBy == n.sharedBy
                }
            }
            .map { allNews ->
                allNews.mapNotNull { news ->
                    news.viewIn?.takeIf { it.isNotEmpty() }?.let { s ->
                        news.parsedViewIn = try { gson.fromJson(s, JsonArray::class.java) } catch (e: Exception) { null }
                    }

                    if (isVisibleToUser(news, userIdentifier)) {
                        news.sortDate = news.calculateSortDate()
                        news
                    } else {
                        null
                    }
                }.sortedByDescending { it.sortDate }
            }.flowOn(dispatcherProvider.default)
    }

    override suspend fun getDiscussionsByTeamIdFlow(teamId: String): Flow<List<News>> {
        return newsDao.getTopLevelByTeamFlow(teamId, teamIdPattern(teamId))
            .distinctUntilChanged { old, new ->
                old.size == new.size && old.zip(new).all { (o, n) ->
                    o.id == n.id && o.time == n.time &&
                            o.message == n.message &&
                            o.isEdited == n.isEdited &&
                            o.imageUrls == n.imageUrls &&
                            o.images == n.images &&
                            // Labels are semantically a set; order carries no meaning.
                            o.labels?.toSet() == n.labels?.toSet() &&
                            o.viewIn == n.viewIn &&
                            o.viewableBy == n.viewableBy &&
                            o.viewableId == n.viewableId &&
                            o.sharedBy == n.sharedBy
                }
            }
            .flowOn(dispatcherProvider.default)
    }

    override suspend fun shareNewsToCommunity(newsId: String, userId: String, planetCode: String, parentCode: String, teamName: String): Result<Unit> {
        return try {
            val news = newsDao.getById(newsId)
            if (news != null) {
                val viewInStr = news.viewIn
                val array = try {
                    if (viewInStr.isNullOrEmpty()) JsonArray() else gson.fromJson(viewInStr, JsonArray::class.java)
                } catch (e: Exception) {
                    null
                } ?: JsonArray()

                if (!array.isEmpty()) {
                    val firstElement = array.get(0)
                    if (firstElement.isJsonObject) {
                        val obj = firstElement.asJsonObject
                        if (!obj.has("name") && teamName.isNotEmpty()) {
                            obj.addProperty("name", teamName)
                        }
                    }
                }

                val effectivePlanetCode = planetCode.ifEmpty { sharedPrefManager.getPlanetCode() }
                val effectiveParentCode = parentCode.ifEmpty { sharedPrefManager.getParentCode() }
                val communityId = if (effectivePlanetCode.isNotEmpty() || effectiveParentCode.isNotEmpty()) {
                    "$effectivePlanetCode@$effectiveParentCode"
                } else {
                    ""
                }

                val ob = JsonObject()
                ob.addProperty("section", "community")
                ob.addProperty("_id", communityId)
                ob.addProperty("sharedDate", Calendar.getInstance().timeInMillis)
                array.add(ob)

                news.sharedBy = userId
                news.viewIn = gson.toJson(array)
                newsDao.upsert(news)
            }
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun deletePost(newsId: String, teamName: String) {
        val news = newsDao.getById(newsId) ?: return
        val viewInStr = news.viewIn
        val ar = try {
            if (viewInStr.isNullOrEmpty()) null else gson.fromJson(viewInStr, JsonArray::class.java)
        } catch (e: Exception) {
            null
        }

        if (teamName.isNotEmpty() || ar == null || ar.size() < 2) {
            val idsToDelete = collectNewsAndReplies(newsId)
            newsDao.deleteByIds(idsToDelete)
        } else {
            val filtered = JsonArray().apply {
                ar.forEach { elem ->
                    if (elem.isJsonObject) {
                        val obj = elem.asJsonObject
                        val isCommunity = JsonUtils.getString("section", obj).equals("community", ignoreCase = true)
                        val hasSharedDate = obj.has("sharedDate")
                        if (!isCommunity && !hasSharedDate) {
                            add(elem)
                        }
                    }
                }
            }
            if (filtered.isEmpty()) {
                val idsToDelete = collectNewsAndReplies(newsId)
                newsDao.deleteByIds(idsToDelete)
            } else {
                news.viewIn = gson.toJson(filtered)
                news.sharedBy = ""
                newsDao.upsert(news)
            }
        }
    }

    override suspend fun getFilteredNews(teamId: String): List<News> {
        return newsDao.getTopLevelByTeam(teamId, teamIdPattern(teamId))
    }

    override suspend fun getReplyCount(newsId: String?): Int {
        if (newsId == null) return 0
        return newsDao.getReplyCount(newsId)
    }

    // Gathers a post and all of its (recursive) replies for deletion.
    private suspend fun collectNewsAndReplies(newsId: String): List<String> {
        return newsDao.getNewsAndRepliesIds(newsId)
    }

    override suspend fun addLabel(newsId: String, label: String) {
        val news = newsDao.getById(newsId) ?: return
        val labels = news.labels?.toMutableList() ?: mutableListOf()
        labels.add(label)
        news.labels = labels
        newsDao.upsert(news)
    }

    override suspend fun removeLabel(newsId: String, label: String) {
        val news = newsDao.getById(newsId) ?: return
        val labels = news.labels?.toMutableList() ?: return
        labels.remove(label)
        news.labels = labels
        newsDao.upsert(news)
    }

    override suspend fun getCommunityVoiceDateCount(startTime: Long, endTime: Long, userId: String?): Int {
        return if (userId != null) {
            newsDao.countDistinctCommunityVoiceDatesForUser(startTime, endTime, userId)
        } else {
            newsDao.countDistinctCommunityVoiceDates(startTime, endTime)
        }
    }

    override suspend fun getNewsById(id: String): News? {
        return newsDao.getById(id)
    }

    override suspend fun postReply(message: String, news: News, currentUser: UserEntity, imageList: List<String>?) {
        val newsId = news.id
        val map = HashMap<String?, String>()
        map["message"] = message
        map["viewableBy"] = news.viewableBy ?: ""
        map["viewableId"] = news.viewableId ?: ""
        map["replyTo"] = newsId
        map["messageType"] = news.messageType ?: ""
        map["messagePlanetCode"] = news.messagePlanetCode ?: ""
        map["viewIn"] = news.viewIn ?: ""
        val reply = News.createNews(map, currentUser, imageList, isReply = true)
        newsDao.upsert(reply)
    }

    override suspend fun editPost(newsId: String, message: String, imagesToRemove: Set<String>, newImages: List<String>?): News? {
        if (message.isEmpty()) return null
        val news = newsDao.getById(newsId) ?: return null
        val urls = (news.imageUrls ?: emptyList()).toMutableList()
        if (imagesToRemove.isNotEmpty()) {
            val updatedUrls = urls.filter { imageUrlJson ->
                try {
                    val imgObject = plainGson.fromJson(imageUrlJson, JsonObject::class.java)
                    val path = JsonUtils.getString("imageUrl", imgObject)
                    !imagesToRemove.contains(path)
                } catch (_: Exception) {
                    true
                }
            }
            urls.clear()
            urls.addAll(updatedUrls)
        }
        newImages?.let { urls.addAll(it) }
        news.imageUrls = urls
        news.updateMessage(message)
        newsDao.upsert(news)
        return newsDao.getById(newsId)
    }

    override suspend fun getPlanetNewsMessages(planetCode: String?): List<News> {
        if (planetCode.isNullOrEmpty()) {
            return emptyList()
        }
        return newsDao.getPlanetMessages(planetCode)
    }

    override suspend fun insertNewsList(docs: List<JsonObject>) {
        // Pre-fetch existing rows in one query instead of a getByUnderscoreId per doc (an N+1
        // that ran serially inside the sync write lock for hundreds of news items).
        val mappedDocs = docs.map { it to JsonUtils.getString("_id", it) }
        val underscoreIds = mappedDocs.map { it.second }.filter { it.isNotEmpty() }
        val existing = newsDao.getByUnderscoreIds(underscoreIds).associateBy { it._id }
        val newsList = mappedDocs.map { (doc, id) -> buildNewsFromJson(doc, id, existing) }
        newsDao.upsertAll(newsList)
        saveConcatenatedLinksToPrefs()
    }

    private suspend fun buildNewsFromJson(doc: JsonObject, underscoreId: String, existing: Map<String?, News>? = null): News {
        val news = (existing?.get(underscoreId) ?: newsDao.getByUnderscoreId(underscoreId))
            ?: News().apply { id = underscoreId }
        news._rev = JsonUtils.getString("_rev", doc)
        news._id = underscoreId
        news.viewableBy = JsonUtils.getString("viewableBy", doc)
        news.docType = JsonUtils.getString("docType", doc)
        news.avatar = JsonUtils.getString("avatar", doc)
        news.updatedDate = JsonUtils.getLong("updatedDate", doc)
        news.viewableId = JsonUtils.getString("viewableId", doc)
        news.createdOn = JsonUtils.getString("createdOn", doc)
        news.messageType = JsonUtils.getString("messageType", doc)
        news.messagePlanetCode = JsonUtils.getString("messagePlanetCode", doc)
        news.replyTo = JsonUtils.getString("replyTo", doc)
        news.parentCode = JsonUtils.getString("parentCode", doc)
        val user = JsonUtils.getJsonObject("user", doc)
        news.user = plainGson.toJson(user)
        news.userId = JsonUtils.getString("_id", user)
        news.userName = JsonUtils.getString("name", user)
        news.time = JsonUtils.getLong("time", doc)
        val images = JsonUtils.getJsonArray("images", doc)
        val message = JsonUtils.getString("message", doc)
        news.message = message
        val links = extractLinks(message)
        val baseUrl = UrlUtils.getUrl()
        synchronized(concatenatedLinks) {
            for (link in links) {
                val concatenatedLink = "$baseUrl/$link"
                concatenatedLinks.add(concatenatedLink)
            }
        }
        news.images = plainGson.toJson(images)
        val labels = JsonUtils.getJsonArray("labels", doc)
        news.viewIn = plainGson.toJson(JsonUtils.getJsonArray("viewIn", doc))
        news.setLabels(labels)
        news.chat = JsonUtils.getBoolean("chat", doc)

        val newsObj = JsonUtils.getJsonObject("news", doc)
        news.newsId = JsonUtils.getString("_id", newsObj)
        news.newsRev = JsonUtils.getString("_rev", newsObj)
        news.newsUser = JsonUtils.getString("user", newsObj)
        news.aiProvider = JsonUtils.getString("aiProvider", newsObj)
        news.newsTitle = JsonUtils.getString("title", newsObj)
        news.conversations = plainGson.toJson(JsonUtils.getJsonArray("conversations", newsObj))
        news.newsCreatedDate = JsonUtils.getLong("createdDate", newsObj)
        news.newsUpdatedDate = JsonUtils.getLong("updatedDate", newsObj)
        news.sharedBy = JsonUtils.getString("sharedBy", newsObj)
        return news
    }

    private fun serializeNews(news: News): JsonObject {
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
        `object`.add("user", plainGson.fromJson(news.user, JsonObject::class.java))
        val newsObject = JsonObject()
        newsObject.addProperty("_id", news.newsId)
        newsObject.addProperty("_rev", news.newsRev)
        newsObject.addProperty("user", news.newsUser)
        newsObject.addProperty("aiProvider", news.aiProvider)
        newsObject.addProperty("title", news.newsTitle)
        newsObject.add("conversations", plainGson.fromJson(news.conversations, JsonArray::class.java))
        newsObject.addProperty("createdDate", news.newsCreatedDate)
        newsObject.addProperty("updatedDate", news.newsUpdatedDate)
        newsObject.addProperty("sharedBy", news.sharedBy)
        `object`.add("news", newsObject)
        `object`.addDocumentOrigin()
        return `object`
    }

    private fun addViewIn(`object`: JsonObject, news: News) {
        if (!news.viewableId.isNullOrEmpty()) {
            `object`.addProperty("viewableId", news.viewableId)
            `object`.addProperty("viewableBy", news.viewableBy)
        }
        val viewInStr = news.viewIn
        if (!viewInStr.isNullOrEmpty()) {
            val ar = plainGson.fromJson(viewInStr, JsonArray::class.java)
            if (!ar.isEmpty()) `object`.add("viewIn", ar)
        }
    }

    private fun saveConcatenatedLinksToPrefs() {
        val existingJsonLinks = sharedPrefManager.getConcatenatedLinks()
        val existingConcatenatedLinks = if (existingJsonLinks != null) {
            LinkedHashSet(plainGson.fromJson(existingJsonLinks, Array<String>::class.java).toList())
        } else {
            LinkedHashSet()
        }
        val linksToProcess: List<String>
        synchronized(concatenatedLinks) {
            linksToProcess = concatenatedLinks.toList()
        }
        existingConcatenatedLinks.addAll(linksToProcess)
        val jsonConcatenatedLinks = plainGson.toJson(existingConcatenatedLinks)
        sharedPrefManager.setConcatenatedLinks(jsonConcatenatedLinks)
    }

    override suspend fun countTeamChats(teamId: String): Long {
        return newsDao.countTeamChats(teamId)
    }

    override suspend fun countTopLevelByTeam(teamId: String): Long {
        return newsDao.countTopLevelByTeam(teamId, teamIdPattern(teamId))
    }

    override suspend fun getPendingNewsLogUploads(): List<org.ole.planet.myplanet.model.NewsLog> {
        return newsLogDao.getPendingUploads()
    }

    override suspend fun markNewsLogUploaded(localId: String, remoteId: String, rev: String): Boolean {
        return newsLogDao.markUploaded(localId, remoteId, rev) != 0
    }
}
