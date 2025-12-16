package org.ole.planet.myplanet.repository

import com.google.gson.Gson
import com.google.gson.JsonArray
import com.google.gson.JsonObject
import io.realm.Case
import io.realm.Sort
import java.util.Calendar
import java.util.HashMap
import java.util.UUID
import javax.inject.Inject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import org.ole.planet.myplanet.datamanager.DatabaseService
import org.ole.planet.myplanet.datamanager.findCopyByField
import org.ole.planet.myplanet.model.RealmNews
import org.ole.planet.myplanet.model.RealmNews.Companion.createNews
import org.ole.planet.myplanet.model.RealmUserModel
import org.ole.planet.myplanet.utilities.JsonUtils
import org.ole.planet.myplanet.utilities.GsonUtils
import io.realm.Realm

class NewsRepositoryImpl @Inject constructor(
    databaseService: DatabaseService,
    private val gson: Gson,
) : RealmRepository(databaseService), NewsRepository {

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

    override suspend fun getNewsByTeamId(teamId: String): List<RealmNews> {
        return withRealm { realm ->
            val allNews = realm.where(RealmNews::class.java)
                .isEmpty("replyTo")
                .sort("time", Sort.DESCENDING)
                .findAll()

            val filteredList = mutableListOf<RealmNews>()
            for (news in allNews) {
                if (!news.viewableBy.isNullOrEmpty() && news.viewableBy.equals("teams", ignoreCase = true) && news.viewableId.equals(teamId, ignoreCase = true)) {
                    filteredList.add(realm.copyFromRealm(news))
                } else if (!news.viewIn.isNullOrEmpty()) {
                    try {
                        val ar = gson.fromJson(news.viewIn, JsonArray::class.java)
                        for (e in ar) {
                            val ob = e.asJsonObject
                            if (ob["_id"].asString.equals(teamId, ignoreCase = true)) {
                                filteredList.add(realm.copyFromRealm(news))
                                break
                            }
                        }
                    } catch (e: Exception) {
                        e.printStackTrace()
                    }
                }
            }
            filteredList
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
            val array = gson.fromJson(viewIn, JsonArray::class.java)
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
        .flowOn(Dispatchers.Main) // Realm async queries require a Looper thread.

        return allNewsFlow.map { allNews ->
            // allNews are unmanaged copies (POJOs) created by copyFromRealm in queryListFlow.
            // It is safe to process them on a background thread.
            allNews.filter { news ->
                isVisibleToUser(news, userIdentifier)
            }.map { news ->
                news.sortDate = news.calculateSortDate()
                news
            }
        }.flowOn(Dispatchers.Default)
    }

    override suspend fun getDiscussionsByTeamIdFlow(teamId: String): Flow<List<RealmNews>> {
        return queryListFlow(RealmNews::class.java) {
            isEmpty("replyTo")
            sort("time", Sort.DESCENDING)
        }.map { discussions ->
            discussions.filter { news ->
                val viewableByTeams = !news.viewableBy.isNullOrEmpty() &&
                        news.viewableBy.equals("teams", ignoreCase = true) &&
                        news.viewableId.equals(teamId, ignoreCase = true)

                val viewInTeam = if (!news.viewIn.isNullOrEmpty()) {
                    try {
                        val ar = gson.fromJson(news.viewIn, JsonArray::class.java)
                        ar.any { e ->
                            val ob = e.asJsonObject
                            ob["_id"].asString.equals(teamId, ignoreCase = true)
                        }
                    } catch (e: Exception) {
                        false
                    }
                } else {
                    false
                }

                viewableByTeams || viewInTeam
            }
        }.flowOn(Dispatchers.Default)
    }

    override suspend fun shareNewsToCommunity(newsId: String, userId: String, planetCode: String, parentCode: String, teamName: String): Result<Unit> {
        return try {
            databaseService.executeTransactionAsync { realm ->
                val news = realm.where(RealmNews::class.java).equalTo("id", newsId).findFirst()
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
                }
            }
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun updateTeamNotification(teamId: String, count: Int) {
        withRealm { realm ->
            realm.executeTransaction {
                var notification = it.where(org.ole.planet.myplanet.model.RealmTeamNotification::class.java)
                    .equalTo("type", "chat")
                    .equalTo("parentId", teamId)
                    .findFirst()

                if (notification == null) {
                    notification = it.createObject(org.ole.planet.myplanet.model.RealmTeamNotification::class.java, UUID.randomUUID().toString())
                    notification.parentId = teamId
                    notification.type = "chat"
                }
                notification.lastCount = count
            }
        }
    }

    override suspend fun getFilteredNews(teamId: String): List<RealmNews> {
        return withRealm { realm ->
            val query = realm.where(RealmNews::class.java)
                .isEmpty("replyTo")
                .beginGroup()
                .equalTo("viewableBy", "teams", Case.INSENSITIVE)
                .equalTo("viewableId", teamId, Case.INSENSITIVE)
                .endGroup()
                .or()
                .contains("viewIn", "\"_id\":\"$teamId\"", Case.INSENSITIVE)
                .sort("time", Sort.DESCENDING)

            realm.copyFromRealm(query.findAll())
        }
    }

    override suspend fun deletePost(newsId: String, teamName: String) {
        withRealm { realm ->
            val news = realm.where(RealmNews::class.java).equalTo("id", newsId).findFirst()
            news?.let {
                val ar = GsonUtils.gson.fromJson(it.viewIn, JsonArray::class.java)
                if (teamName.isNotEmpty() || ar.size() < 2) {
                    it.id?.let { id -> deleteChildPosts(id, realm) }
                    it.deleteFromRealm()
                } else {
                    val filtered = JsonArray().apply {
                        ar.forEach { elem ->
                            if (!elem.asJsonObject.has("sharedDate")) {
                                add(elem)
                            }
                        }
                    }
                    it.viewIn = GsonUtils.gson.toJson(filtered)
                }
            }
        }
    }

    private fun deleteChildPosts(parentId: String, realm: Realm) {
        val children = realm.where(RealmNews::class.java)
            .equalTo("replyTo", parentId)
            .findAll()
        children.forEach { child ->
            child.id?.let { id -> deleteChildPosts(id, realm) }
            child.deleteFromRealm()
        }
    }

    override suspend fun editPost(
        newsId: String,
        message: String,
        imagesToRemove: Set<String>,
        imagesToAdd: List<String>
    ) {
        withRealm { realm ->
            realm.executeTransaction {
                val news = it.where(RealmNews::class.java).equalTo("id", newsId).findFirst()
                news?.let { newsItem ->
                    if (imagesToRemove.isNotEmpty()) {
                        newsItem.imageUrls?.let { imageUrls ->
                            val updatedUrls = imageUrls.filter { imageUrlJson ->
                                try {
                                    val imgObject = GsonUtils.gson.fromJson(imageUrlJson, JsonObject::class.java)
                                    val path = JsonUtils.getString("imageUrl", imgObject)
                                    !imagesToRemove.contains(path)
                                } catch (_: Exception) {
                                    true
                                }
                            }
                            newsItem.imageUrls?.clear()
                            newsItem.imageUrls?.addAll(updatedUrls)
                        }
                    }
                    imagesToAdd.forEach { imageUrl -> newsItem.imageUrls?.add(imageUrl) }
                    newsItem.updateMessage(message)
                }
            }
        }
    }

    override suspend fun postReply(
        message: String,
        replyToId: String,
        user: RealmUserModel?,
        imageList: List<String>?
    ) {
        withRealm { realm ->
            realm.executeTransaction {
                val news = it.where(RealmNews::class.java).equalTo("id", replyToId).findFirst()
                news?.let { newsItem ->
                    val map = HashMap<String?, String>()
                    map["message"] = message
                    map["viewableBy"] = newsItem.viewableBy ?: ""
                    map["viewableId"] = newsItem.viewableId ?: ""
                    map["replyTo"] = newsItem.id ?: ""
                    map["messageType"] = newsItem.messageType ?: ""
                    map["messagePlanetCode"] = newsItem.messagePlanetCode ?: ""
                    map["viewIn"] = newsItem.viewIn ?: ""
                    createNews(map, it, user, imageList?.let { it1 -> io.realm.RealmList<String>().apply { addAll(it1) } }, true)
                }
            }
        }
    }
}
