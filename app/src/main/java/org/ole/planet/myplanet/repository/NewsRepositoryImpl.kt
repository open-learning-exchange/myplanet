package org.ole.planet.myplanet.repository

import com.google.gson.Gson
import io.realm.Case
import io.realm.Sort
import java.util.HashMap
import javax.inject.Inject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import org.ole.planet.myplanet.datamanager.DatabaseService
import org.ole.planet.myplanet.datamanager.findCopyByField
import org.ole.planet.myplanet.model.NewsItem
import org.ole.planet.myplanet.model.RealmMyLibrary
import org.ole.planet.myplanet.model.RealmNews
import org.ole.planet.myplanet.model.RealmNews.Companion.createNews
import org.ole.planet.myplanet.model.RealmUserModel
import org.ole.planet.myplanet.utilities.JsonUtils

class NewsRepositoryImpl @Inject constructor(
    databaseService: DatabaseService,
    private val gson: Gson,
) : RealmRepository(databaseService), NewsRepository {

    override suspend fun getCommunityNewsItems(userIdentifier: String): List<NewsItem> {
        return withRealm(ensureLatest = true) { realm ->
            val allNews = realm.where(RealmNews::class.java)
                .isEmpty("replyTo")
                .equalTo("docType", "message", Case.INSENSITIVE)
                .sort("time", Sort.DESCENDING)
                .findAll()

            val filteredNews = allNews.filter { isVisibleToUser(it, userIdentifier) }
            mapToNewsItems(realm, filteredNews)
        }
    }

    override suspend fun getNewsItemsByIds(ids: List<String>): List<NewsItem> {
        return withRealm(ensureLatest = true) { realm ->
            if (ids.isEmpty()) return@withRealm emptyList()
            val newsList = realm.where(RealmNews::class.java)
                .`in`("id", ids.toTypedArray())
                .findAll()
            mapToNewsItems(realm, newsList)
        }
    }

    override suspend fun getNewsItemWithReplies(newsId: String): Pair<NewsItem?, List<NewsItem>> {
        return withRealm(ensureLatest = true) { realm ->
            val news = realm.where(RealmNews::class.java).equalTo("id", newsId).findFirst()
            val replies = realm.where(RealmNews::class.java)
                .equalTo("replyTo", newsId, Case.INSENSITIVE)
                .sort("time", Sort.DESCENDING)
                .findAll()

            val combined = mutableListOf<RealmNews>()
            if (news != null) combined.add(news)
            combined.addAll(replies)

            val mapped = mapToNewsItems(realm, combined)

            val mappedNews = mapped.find { it.id == newsId }
            val mappedReplies = mapped.filter { it.id != newsId }

            mappedNews to mappedReplies
        }
    }

    private fun mapToNewsItems(realm: io.realm.Realm, newsList: List<RealmNews>): List<NewsItem> {
        if (newsList.isEmpty()) return emptyList()

        val userIds = newsList.mapNotNull { it.userId }.distinct()
        val users = realm.where(RealmUserModel::class.java)
            .`in`("id", userIds.toTypedArray())
            .findAll()
            .associateBy { it.id }

        val allResourceIds = mutableSetOf<String>()
        newsList.forEach { news ->
            news.imagesArray.forEach {
                if (it.isJsonObject) {
                    val id = JsonUtils.getString("resourceId", it.asJsonObject)
                    if (!id.isNullOrEmpty()) allResourceIds.add(id)
                }
            }
        }

        val libraryMap = if (allResourceIds.isNotEmpty()) {
            realm.where(RealmMyLibrary::class.java)
                .`in`("_id", allResourceIds.toTypedArray())
                .findAll()
                .associate { it._id to "${it.id}/${it.resourceLocalAddress}" }
        } else {
            emptyMap()
        }

        return newsList.map { news ->
            val user = users[news.userId]
            val replyCount = realm.where(RealmNews::class.java)
                .equalTo("replyTo", news.id, Case.INSENSITIVE)
                .count()
                .toInt()

            val imagesArray = news.imagesArray
            val imagesList = mutableListOf<com.google.gson.JsonObject>()
            val myResourceIds = mutableListOf<String>()
            imagesArray.forEach {
                if (it.isJsonObject) {
                    val obj = it.asJsonObject
                    imagesList.add(obj)
                    val id = JsonUtils.getString("resourceId", obj)
                    if (!id.isNullOrEmpty()) myResourceIds.add(id)
                }
            }

            val subsetMap = libraryMap.filterKeys { it in myResourceIds }
            val userFullName = user?.let { "${it.firstName} ${it.lastName}".trim().ifBlank { it.name } } ?: news.userName

            NewsItem(
                id = news.id,
                _id = news._id,
                _rev = news._rev,
                userId = news.userId,
                user = news.user,
                message = news.message,
                docType = news.docType,
                viewableBy = news.viewableBy,
                viewableId = news.viewableId,
                avatar = news.avatar,
                replyTo = news.replyTo,
                userName = news.userName,
                messagePlanetCode = news.messagePlanetCode,
                messageType = news.messageType,
                updatedDate = news.updatedDate,
                time = news.time,
                createdOn = news.createdOn,
                parentCode = news.parentCode,
                imageUrls = news.imageUrls?.toList() ?: emptyList(),
                images = news.images,
                labels = news.labels?.toList() ?: emptyList(),
                viewIn = news.viewIn,
                newsId = news.newsId,
                newsRev = news.newsRev,
                newsUser = news.newsUser,
                aiProvider = news.aiProvider,
                newsTitle = news.newsTitle,
                conversations = news.conversations,
                newsCreatedDate = news.newsCreatedDate,
                newsUpdatedDate = news.newsUpdatedDate,
                chat = news.chat,
                isEdited = news.isEdited,
                editedTime = news.editedTime,
                sharedBy = news.sharedBy,
                sortDate = news.calculateSortDate(),
                userImage = user?.userImage,
                userFullName = userFullName,
                replyCount = replyCount,
                isCommunityNews = news.isCommunityNews,
                imagesArray = imagesList,
                resolvedLibraryImages = subsetMap
            )
        }.sortedByDescending { it.sortDate }
    }

    override suspend fun deleteNews(newsId: String) {
        withRealm { realm ->
            realm.executeTransaction { r ->
                val news = r.where(RealmNews::class.java).equalTo("id", newsId).findFirst()
                if (news != null) {
                    val viewIn = news.viewIn
                    val ar = if (!viewIn.isNullOrEmpty()) gson.fromJson(viewIn, com.google.gson.JsonArray::class.java) else com.google.gson.JsonArray()

                    if (ar.size() < 2) {
                        deleteChildPosts(r, newsId)
                        news.deleteFromRealm()
                    } else {
                        val filtered = com.google.gson.JsonArray()
                        ar.forEach { elem ->
                            if (!elem.asJsonObject.has("sharedDate")) {
                                filtered.add(elem)
                            }
                        }
                        news.viewIn = gson.toJson(filtered)
                    }
                }
            }
        }
    }

    private fun deleteChildPosts(realm: io.realm.Realm, parentId: String) {
        val children = realm.where(RealmNews::class.java).equalTo("replyTo", parentId).findAll()
        children.forEach {
            deleteChildPosts(realm, it.id ?: "")
            it.deleteFromRealm()
        }
    }

    override suspend fun addLabel(newsId: String, label: String) {
        withRealm { realm ->
            realm.executeTransaction { r ->
                val news = r.where(RealmNews::class.java).equalTo("id", newsId).findFirst()
                if (news != null) {
                    var labels = news.labels
                    if (labels == null) {
                        labels = io.realm.RealmList()
                        news.labels = labels
                    }
                    if (!labels.contains(label)) {
                        labels.add(label)
                    }
                }
            }
        }
    }

    override suspend fun removeLabel(newsId: String, label: String) {
        withRealm { realm ->
            realm.executeTransaction { r ->
                val news = r.where(RealmNews::class.java).equalTo("id", newsId).findFirst()
                news?.labels?.remove(label)
            }
        }
    }

    override suspend fun getNewsWithReplies(newsId: String): Pair<RealmNews?, List<RealmNews>> {
        return withRealm(ensureLatest = true) { realm ->
            val news = realm.findCopyByField(RealmNews::class.java, "id", newsId)
            val replies = realm.where(RealmNews::class.java)
                .equalTo("replyTo", newsId, Case.INSENSITIVE)
                .sort("time", Sort.DESCENDING)
                .findAll()
                .let { realm.copyFromRealm(it) }
            news to replies
        }
    }

    override suspend fun getCommunityVisibleNews(userIdentifier: String): List<RealmNews> {
        val allNews = queryList(RealmNews::class.java) {
            isEmpty("replyTo")
            equalTo("docType", "message", Case.INSENSITIVE)
            sort("time", Sort.DESCENDING)
        }
        if (allNews.isEmpty()) {
            return emptyList()
        }

        return allNews.filter { news ->
            isVisibleToUser(news, userIdentifier)
        }
    }

    override suspend fun createNews(map: HashMap<String?, String>, user: RealmUserModel?): RealmNews {
        return withRealmAsync { realm ->
            val managedNews = createNews(map, realm, user, null)
            realm.copyFromRealm(managedNews)
        }
    }

    private fun isVisibleToUser(news: RealmNews, userIdentifier: String): Boolean {
        if (news.viewableBy.equals("community", ignoreCase = true)) {
            return true
        }

        val viewIn = news.viewIn ?: return false
        if (viewIn.isEmpty()) {
            return false
        }

        return try {
            val array = gson.fromJson(viewIn, com.google.gson.JsonArray::class.java)
            array?.any { element ->
                element != null && element.isJsonObject &&
                    element.asJsonObject.has("_id") &&
                    element.asJsonObject.get("_id").asString.equals(userIdentifier, ignoreCase = true)
            } == true
        } catch (throwable: Throwable) {
            false
        }
    }

    override suspend fun getCommunityNews(userIdentifier: String): Flow<List<RealmNews>> {
        val allNewsFlow = queryListFlow(RealmNews::class.java) {
            isEmpty("replyTo")
            equalTo("docType", "message", Case.INSENSITIVE)
            sort("time", Sort.DESCENDING)
        }
        .flowOn(Dispatchers.Main)

        return allNewsFlow.map { allNews ->
            allNews.filter { news ->
                isVisibleToUser(news, userIdentifier)
            }.map { news ->
                news.sortDate = news.calculateSortDate()
                news
            }
        }.flowOn(Dispatchers.Default)
    }
}
