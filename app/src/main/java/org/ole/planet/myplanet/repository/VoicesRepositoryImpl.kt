package org.ole.planet.myplanet.repository

import android.text.TextUtils
import com.google.gson.Gson
import com.google.gson.JsonArray
import com.google.gson.JsonObject
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Calendar
import java.util.HashMap
import java.util.UUID
import javax.inject.Inject
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import org.ole.planet.myplanet.data.room.dao.MyLibraryDao
import org.ole.planet.myplanet.data.room.dao.NewsDao
import org.ole.planet.myplanet.model.MyLibrary
import org.ole.planet.myplanet.model.News
import org.ole.planet.myplanet.data.room.dao.TeamNotificationDao
import org.ole.planet.myplanet.model.TeamNotification
import org.ole.planet.myplanet.model.UserEntity
import org.ole.planet.myplanet.services.SharedPrefManager
import org.ole.planet.myplanet.utils.DispatcherProvider
import org.ole.planet.myplanet.utils.DownloadUtils.extractLinks
import org.ole.planet.myplanet.utils.JsonUtils
import org.ole.planet.myplanet.utils.UrlUtils

class VoicesRepositoryImpl @Inject constructor(
    private val dispatcherProvider: DispatcherProvider,
    private val gson: Gson,
    private val sharedPrefManager: SharedPrefManager,
    private val userRepositoryLazy: dagger.Lazy<UserRepository>,
    private val teamNotificationDao: TeamNotificationDao,
    private val newsDao: NewsDao,
    private val myLibraryDao: MyLibraryDao
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

    override suspend fun getUserById(userId: String): UserEntity? {
        return userRepositoryLazy.get().getUserById(userId)
    }

    override suspend fun getLibraryResource(resourceId: String): MyLibrary? {
        return myLibraryDao.getByUnderscoreId(resourceId)
    }

    override suspend fun getNewsWithReplies(newsId: String): Pair<News?, List<News>> {
        val news = newsDao.getById(newsId)
        val replies = newsDao.getReplies(newsId)
        return news to replies
    }

    override suspend fun getCommunityVisibleNews(userIdentifier: String): List<News> {
        return newsDao.getTopLevelMessages().filter { news ->
            isVisibleToUser(news, userIdentifier)
        }
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

    override suspend fun getNewsByTeamId(teamId: String): List<News> {
        return newsDao.getTopLevel().filter { matchesTeam(it, teamId) }
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
            val array = gson.fromJson(viewIn, JsonArray::class.java)
            array?.any { element ->
                element != null && element.isJsonObject &&
                    JsonUtils.getString("_id", element.asJsonObject).equals(userIdentifier, ignoreCase = true)
            } == true
        } catch (throwable: Throwable) {
            false
        }
    }

    // Team-discussion visibility: top-level post targeted at the team, either via the
    // viewableBy/viewableId columns or an entry inside the viewIn JSON.
    private fun matchesTeam(news: News, teamId: String): Boolean {
        val byViewable = news.viewableBy.equals("teams", ignoreCase = true) &&
            news.viewableId.equals(teamId, ignoreCase = true)
        val byViewIn = news.viewIn?.contains("\"_id\":\"$teamId\"", ignoreCase = true) == true
        return byViewable || byViewIn
    }

    override suspend fun getCommunityNews(userIdentifier: String): Flow<List<News>> {
        return newsDao.getTopLevelMessagesFlow()
            .distinctUntilChanged { old, new ->
                old.size == new.size && old.zip(new).all { (o, n) -> o.id == n.id && o.time == n.time }
            }
            .map { allNews ->
                allNews.filter { news ->
                    isVisibleToUser(news, userIdentifier)
                }.map { news ->
                    news.sortDate = news.calculateSortDate()
                    news
                }
            }.flowOn(dispatcherProvider.default)
    }

    override suspend fun getDiscussionsByTeamIdFlow(teamId: String): Flow<List<News>> {
        return newsDao.getTopLevelFlow()
            .map { allNews -> allNews.filter { matchesTeam(it, teamId) } }
            .distinctUntilChanged { old, new ->
                old.size == new.size && old.zip(new).all { (o, n) -> o.id == n.id && o.time == n.time }
            }
            .flowOn(dispatcherProvider.default)
    }

    override suspend fun shareNewsToCommunity(newsId: String, userId: String, planetCode: String, parentCode: String, teamName: String): Result<Unit> {
        return try {
            val news = newsDao.getById(newsId)
            if (news != null) {
                val array = gson.fromJson(news.viewIn, JsonArray::class.java)
                if (array != null && array.size() > 0) {
                    val firstElement = array.get(0)
                    if (firstElement.isJsonObject) {
                        val obj = firstElement.asJsonObject
                        if (!obj.has("name")) {
                            obj.addProperty("name", teamName)
                        }
                    }
                }

                val ob = JsonObject()
                ob.addProperty("section", "community")
                ob.addProperty("_id", "$planetCode@$parentCode")
                ob.addProperty("sharedDate", Calendar.getInstance().timeInMillis)
                array?.add(ob)

                news.sharedBy = userId
                news.viewIn = gson.toJson(array)
                newsDao.upsert(news)
            }
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun updateTeamNotification(teamId: String, count: Int) {
        val existing = teamNotificationDao.findByParentAndType(teamId, "chat")
        if (existing != null) {
            existing.lastCount = count
            teamNotificationDao.update(existing)
        } else {
            val notification = TeamNotification().apply {
                id = UUID.randomUUID().toString()
                parentId = teamId
                type = "chat"
                lastCount = count
            }
            teamNotificationDao.insert(notification)
        }
    }

    override suspend fun deletePost(newsId: String, teamName: String) {
        val news = newsDao.getById(newsId) ?: return
        val ar = try {
            gson.fromJson(news.viewIn, JsonArray::class.java)
        } catch (e: Exception) {
            null
        }

        if (teamName.isNotEmpty() || ar == null || ar.size() < 2) {
            val idsToDelete = collectNewsAndReplies(newsId)
            newsDao.deleteByIds(idsToDelete)
        } else {
            val filtered = JsonArray().apply {
                ar.forEach { elem ->
                    if (elem.isJsonObject && !elem.asJsonObject.has("sharedDate")) {
                        add(elem)
                    }
                }
            }
            news.viewIn = gson.toJson(filtered)
            newsDao.upsert(news)
        }
    }

    override suspend fun getFilteredNews(teamId: String): List<News> {
        return newsDao.getTopLevel().filter { matchesTeam(it, teamId) }
    }

    override suspend fun getReplyCount(newsId: String?): Int {
        if (newsId == null) return 0
        return newsDao.getReplyCount(newsId)
    }

    override suspend fun deleteNews(newsId: String) {
        val idsToDelete = collectNewsAndReplies(newsId)
        newsDao.deleteByIds(idsToDelete)
    }

    // Gathers a post and all of its (recursive) replies for deletion.
    private suspend fun collectNewsAndReplies(newsId: String): List<String> {
        val ids = mutableListOf(newsId)
        newsDao.getDirectReplies(newsId).forEach { reply ->
            ids.addAll(collectNewsAndReplies(reply.id))
        }
        return ids
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

    override suspend fun getCommunityVoiceDates(startTime: Long, endTime: Long, userId: String?): List<String> {
        val results = if (userId != null) {
            newsDao.getInTimeRangeForUser(startTime, endTime, userId)
        } else {
            newsDao.getInTimeRange(startTime, endTime)
        }
        return results.filter { isCommunitySection(it) }
            .map { getDateFromTimestamp(it.time) }
            .distinct()
    }

    override suspend fun getNewsById(id: String): News? {
        return newsDao.getById(id)
    }

    override suspend fun postReply(message: String, news: News, currentUser: UserEntity, imageList: List<String>?) {
        val newsId = news._id ?: news.id
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

    override suspend fun editPost(newsId: String, message: String, imagesToRemove: Set<String>, newImages: List<String>?) {
        if (message.isEmpty()) return
        val news = newsDao.getById(newsId) ?: return
        val urls = (news.imageUrls ?: emptyList()).toMutableList()
        if (imagesToRemove.isNotEmpty()) {
            val updatedUrls = urls.filter { imageUrlJson ->
                try {
                    val imgObject = JsonUtils.gson.fromJson(imageUrlJson, JsonObject::class.java)
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
    }

    private fun isCommunitySection(news: News): Boolean {
        news.viewIn?.let { viewInStr ->
            try {
                val viewInArray = org.json.JSONArray(viewInStr)
                for (i in 0 until viewInArray.length()) {
                    val viewInObj = viewInArray.getJSONObject(i)
                    if (viewInObj.optString("section") == "community") {
                        return true
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
        return false
    }

    private val dateFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd")

    private fun getDateFromTimestamp(timestamp: Long): String {
        return Instant.ofEpochMilli(timestamp)
            .atZone(ZoneId.systemDefault())
            .format(dateFormatter)
    }

    override suspend fun getPlanetNewsMessages(planetCode: String?): List<News> {
        if (planetCode.isNullOrEmpty()) {
            return emptyList()
        }
        return newsDao.getPlanetMessages(planetCode)
    }

    override suspend fun insertNewsList(docs: List<JsonObject>) {
        val newsList = docs.map { buildNewsFromJson(it) }
        newsDao.upsertAll(newsList)
        saveConcatenatedLinksToPrefs()
    }

    override suspend fun insertNewsFromJson(doc: JsonObject) {
        newsDao.upsert(buildNewsFromJson(doc))
        saveConcatenatedLinksToPrefs()
    }

    private suspend fun buildNewsFromJson(doc: JsonObject): News {
        val underscoreId = JsonUtils.getString("_id", doc)
        val news = newsDao.getByUnderscoreId(underscoreId) ?: News().apply { id = underscoreId }
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
        news.user = JsonUtils.gson.toJson(JsonUtils.getJsonObject("user", doc))
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
        news.images = JsonUtils.gson.toJson(images)
        val labels = JsonUtils.getJsonArray("labels", doc)
        news.viewIn = JsonUtils.gson.toJson(JsonUtils.getJsonArray("viewIn", doc))
        news.setLabels(labels)
        news.chat = JsonUtils.getBoolean("chat", doc)

        val newsObj = JsonUtils.getJsonObject("news", doc)
        news.newsId = JsonUtils.getString("_id", newsObj)
        news.newsRev = JsonUtils.getString("_rev", newsObj)
        news.newsUser = JsonUtils.getString("user", newsObj)
        news.aiProvider = JsonUtils.getString("aiProvider", newsObj)
        news.newsTitle = JsonUtils.getString("title", newsObj)
        news.conversations = JsonUtils.gson.toJson(JsonUtils.getJsonArray("conversations", newsObj))
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

    private fun addViewIn(`object`: JsonObject, news: News) {
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
        val existingJsonLinks = sharedPrefManager.getConcatenatedLinks()
        val existingConcatenatedLinks = if (existingJsonLinks != null) {
            LinkedHashSet(JsonUtils.gson.fromJson(existingJsonLinks, Array<String>::class.java).toList())
        } else {
            LinkedHashSet()
        }
        val linksToProcess: List<String>
        synchronized(concatenatedLinks) {
            linksToProcess = concatenatedLinks.toList()
        }
        existingConcatenatedLinks.addAll(linksToProcess)
        val jsonConcatenatedLinks = JsonUtils.gson.toJson(existingConcatenatedLinks)
        sharedPrefManager.setConcatenatedLinks(jsonConcatenatedLinks)
    }

    override suspend fun getPrivateImageUrlsCreatedAfter(timestamp: Long): List<String> {
        return myLibraryDao.getPrivateImagesCreatedAfter(timestamp)
            .mapNotNull { it.resourceRemoteAddress }
    }
}
