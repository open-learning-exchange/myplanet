package org.ole.planet.myplanet.repository

import android.util.Log
import java.util.Calendar
import java.util.HashMap
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonObjectBuilder
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import org.ole.planet.myplanet.data.room.dao.NewsDao
import org.ole.planet.myplanet.data.room.dao.NewsLogDao
import org.ole.planet.myplanet.model.News
import org.ole.planet.myplanet.model.UserEntity
import org.ole.planet.myplanet.services.SharedPrefManager
import org.ole.planet.myplanet.utils.DispatcherProvider
import org.ole.planet.myplanet.utils.DownloadUtils.extractLinks
import org.ole.planet.myplanet.utils.KJson
import org.ole.planet.myplanet.utils.UrlUtils
import org.ole.planet.myplanet.utils.addDocumentOrigin
import org.ole.planet.myplanet.utils.withGsonBridgedNumbers

class VoicesRepositoryImpl @Inject constructor(
    private val dispatcherProvider: DispatcherProvider,
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
                    news.images = KJson.toGsonString(update.imagesArray, htmlSafe = true)
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
        val escaped = viewInId
            .replace("\\", "\\\\")
            .replace("%", "\\%")
            .replace("_", "\\_")
        val pattern = "%\"_id\":\"$escaped\"%"
        return newsDao.isSharedWith(chatId, pattern)
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
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e("VoicesRepository", "Failed to create team news", e)
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
            val array = news.parsedViewIn ?: KJson.parseArrayOrNull(viewIn)
            array?.any { element ->
                val obj = element as? JsonObject ?: return@any false
                val section = KJson.getString("section", obj)
                if (section.equals("community", ignoreCase = true)) {
                    val id = KJson.getString("_id", obj)
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
                        news.parsedViewIn = try { KJson.parseArrayOrNull(s) } catch (e: Exception) { null }
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
                    if (viewInStr.isNullOrEmpty()) null else KJson.parseArrayOrNull(viewInStr)
                } catch (e: Exception) {
                    null
                }.orEmpty().toMutableList()

                val first = array.firstOrNull() as? JsonObject
                if (first != null && !first.containsKey("name") && teamName.isNotEmpty()) {
                    array[0] = JsonObject(first + ("name" to JsonPrimitive(teamName)))
                }

                val effectivePlanetCode = planetCode.ifEmpty { sharedPrefManager.getPlanetCode() }
                val effectiveParentCode = parentCode.ifEmpty { sharedPrefManager.getParentCode() }
                val communityId = if (effectivePlanetCode.isNotEmpty() || effectiveParentCode.isNotEmpty()) {
                    "$effectivePlanetCode@$effectiveParentCode"
                } else {
                    ""
                }

                array.add(
                    buildJsonObject {
                        put("section", "community")
                        put("_id", communityId)
                        put("sharedDate", Calendar.getInstance().timeInMillis)
                    }
                )

                news.sharedBy = userId
                // viewIn is matched with SQL LIKE, so it keeps the exact text Gson wrote.
                news.viewIn = KJson.toGsonString(JsonArray(array), htmlSafe = true, numbers = KJson.GsonNumbers.LITERAL)
                newsDao.upsert(news)
            }
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e("VoicesRepository", "Failed to share news to community", e)
            Result.failure(e)
        }
    }

    override suspend fun deletePost(newsId: String, teamName: String) {
        val news = newsDao.getById(newsId) ?: return
        val viewInStr = news.viewIn
        val ar = try {
            if (viewInStr.isNullOrEmpty()) null else KJson.parseArrayOrNull(viewInStr)
        } catch (e: Exception) {
            null
        }

        if (teamName.isNotEmpty() || ar == null || ar.size < 2) {
            val idsToDelete = collectNewsAndReplies(newsId)
            newsDao.deleteByIds(idsToDelete)
        } else {
            val filtered = ar.filter { elem ->
                val obj = elem as? JsonObject ?: return@filter false
                val isCommunity = KJson.getString("section", obj).equals("community", ignoreCase = true)
                val hasSharedDate = obj.containsKey("sharedDate")
                !isCommunity && !hasSharedDate
            }
            if (filtered.isEmpty()) {
                val idsToDelete = collectNewsAndReplies(newsId)
                newsDao.deleteByIds(idsToDelete)
            } else {
                news.viewIn = KJson.toGsonString(JsonArray(filtered), htmlSafe = true, numbers = KJson.GsonNumbers.LITERAL)
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
                    val imgObject = KJson.parseObjectOrNull(imageUrlJson)
                    val path = KJson.getString("imageUrl", imgObject)
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
        val underscoreIds = ArrayList<String>(docs.size)
        val mappedDocs = docs.map { doc ->
            val id = KJson.getString("_id", doc)
            if (id.isNotEmpty()) {
                underscoreIds.add(id)
            }
            doc to id
        }
        val existing = newsDao.getByUnderscoreIds(underscoreIds).associateBy { it._id }
        val newsList = mappedDocs.map { (doc, id) -> buildNewsFromJson(doc, id, existing) }
        newsDao.upsertAll(newsList)
        saveConcatenatedLinksToPrefs()
    }

    private suspend fun buildNewsFromJson(doc: JsonObject, underscoreId: String, existing: Map<String?, News>? = null): News {
        val news = (existing?.get(underscoreId) ?: newsDao.getByUnderscoreId(underscoreId))
            ?: News().apply { id = underscoreId }
        news._rev = KJson.getString("_rev", doc)
        news._id = underscoreId
        news.viewableBy = KJson.getString("viewableBy", doc)
        news.docType = KJson.getString("docType", doc)
        news.avatar = KJson.getString("avatar", doc)
        news.updatedDate = KJson.getLong("updatedDate", doc)
        news.viewableId = KJson.getString("viewableId", doc)
        news.createdOn = KJson.getString("createdOn", doc)
        news.messageType = KJson.getString("messageType", doc)
        news.messagePlanetCode = KJson.getString("messagePlanetCode", doc)
        news.replyTo = KJson.getString("replyTo", doc)
        news.parentCode = KJson.getString("parentCode", doc)
        val user = KJson.getJsonObject("user", doc)
        news.user = KJson.gsonToJson(user)
        news.userId = KJson.getString("_id", user)
        news.userName = KJson.getString("name", user)
        news.time = KJson.getLong("time", doc)
        val images = KJson.getJsonArray("images", doc)
        val message = KJson.getString("message", doc)
        news.message = message
        val links = extractLinks(message)
        val baseUrl = UrlUtils.getUrl()
        synchronized(concatenatedLinks) {
            for (link in links) {
                val concatenatedLink = "$baseUrl/$link"
                concatenatedLinks.add(concatenatedLink)
            }
        }
        news.images = KJson.gsonToJson(images)
        val labels = KJson.getJsonArray("labels", doc)
        news.viewIn = KJson.gsonToJson(KJson.getJsonArray("viewIn", doc))
        news.setLabels(labels.withGsonBridgedNumbers())
        news.chat = KJson.getBoolean("chat", doc)

        val newsObj = KJson.getJsonObject("news", doc)
        news.newsId = KJson.getString("_id", newsObj)
        news.newsRev = KJson.getString("_rev", newsObj)
        news.newsUser = KJson.getString("user", newsObj)
        news.aiProvider = KJson.getString("aiProvider", newsObj)
        news.newsTitle = KJson.getString("title", newsObj)
        news.conversations = KJson.gsonToJson(KJson.getJsonArray("conversations", newsObj))
        news.newsCreatedDate = KJson.getLong("createdDate", newsObj)
        news.newsUpdatedDate = KJson.getLong("updatedDate", newsObj)
        news.sharedBy = KJson.getString("sharedBy", newsObj)
        return news
    }

    private fun serializeNews(news: News): JsonObject = buildJsonObject {
        put("chat", news.chat)
        put("message", news.message)
        if (news._id != null) put("_id", news._id)
        if (news._rev != null) put("_rev", news._rev)
        put("time", news.time)
        put("createdOn", news.createdOn)
        put("docType", news.docType)
        addViewIn(this, news)
        put("avatar", news.avatar)
        put("messageType", news.messageType)
        put("messagePlanetCode", news.messagePlanetCode)
        put("createdOn", news.createdOn)
        put("replyTo", news.replyTo)
        put("parentCode", news.parentCode)
        put("images", news.imagesArray)
        put("labels", news.labelsArray.withGsonBridgedNumbers())
        put("user", KJson.parseObjectOrNull(news.user) ?: JsonNull)
        put(
            "news",
            buildJsonObject {
                put("_id", news.newsId)
                put("_rev", news.newsRev)
                put("user", news.newsUser)
                put("aiProvider", news.aiProvider)
                put("title", news.newsTitle)
                put("conversations", KJson.parseArrayOrNull(news.conversations) ?: JsonNull)
                put("createdDate", news.newsCreatedDate)
                put("updatedDate", news.newsUpdatedDate)
                put("sharedBy", news.sharedBy)
            }
        )
        addDocumentOrigin()
    }

    private fun addViewIn(builder: JsonObjectBuilder, news: News) {
        if (!news.viewableId.isNullOrEmpty()) {
            builder.put("viewableId", news.viewableId)
            builder.put("viewableBy", news.viewableBy)
        }
        val viewInStr = news.viewIn
        if (!viewInStr.isNullOrEmpty()) {
            val ar = KJson.parseArrayOrNull(viewInStr) ?: throw NullPointerException("blank viewIn")
            if (ar.isNotEmpty()) builder.put("viewIn", ar)
        }
    }

    /** The stored link list as Gson read it into an `Array<String>`: primitives by their text, `null` kept. */
    private fun storedLinks(text: String): List<String?> {
        val array = KJson.parseArrayOrNull(text) ?: throw NullPointerException("blank concatenated links")
        return array.map { element ->
            when (element) {
                is JsonNull -> null
                is JsonPrimitive -> element.content
                else -> throw IllegalStateException("Expected a string but was $element")
            }
        }
    }

    private fun saveConcatenatedLinksToPrefs() {
        val existingJsonLinks = sharedPrefManager.getConcatenatedLinks()
        val existingConcatenatedLinks = if (existingJsonLinks != null) {
            LinkedHashSet(storedLinks(existingJsonLinks))
        } else {
            LinkedHashSet()
        }
        val linksToProcess: List<String>
        synchronized(concatenatedLinks) {
            linksToProcess = concatenatedLinks.toList()
        }
        existingConcatenatedLinks.addAll(linksToProcess)
        val jsonConcatenatedLinks = KJson.gsonToJson(JsonArray(existingConcatenatedLinks.map { JsonPrimitive(it) }))
        sharedPrefManager.setConcatenatedLinks(jsonConcatenatedLinks)
    }

    override suspend fun countTeamChats(teamId: String): Long {
        return newsDao.countTeamChats(teamId)
    }

    override suspend fun countTopLevelByTeam(teamId: String): Long {
        return newsDao.countTopLevelByTeam(teamId, teamIdPattern(teamId))
    }

    override suspend fun countTopLevelByTeams(teamIds: List<String>): Map<String, Long> {
        if (teamIds.isEmpty()) return emptyMap()
        val counts = teamIds.associateWith { 0L }.toMutableMap()
        val patterns = teamIds.map { it to "\"_id\":\"$it\"" }
        newsDao.getTopLevelTeamMembership(teamIds).forEach { row ->
            val isTeamsViewable = row.viewableBy.equals("teams", ignoreCase = true)
            patterns.forEach { (teamId, pattern) ->
                val matchesViewable = isTeamsViewable &&
                    row.viewableId.equals(teamId, ignoreCase = true)
                val matchesViewIn = row.viewIn?.contains(pattern, ignoreCase = true) == true
                if (matchesViewable || matchesViewIn) {
                    counts[teamId] = (counts[teamId] ?: 0L) + 1L
                }
            }
        }
        return counts
    }

    override suspend fun getPendingNewsLogUploads(): List<org.ole.planet.myplanet.model.NewsLog> {
        return newsLogDao.getPendingUploads()
    }

    override suspend fun markNewsLogUploaded(localId: String, remoteId: String, rev: String): Boolean {
        return newsLogDao.markUploaded(localId, remoteId, rev) != 0
    }
}
