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
import org.ole.planet.myplanet.data.DatabaseService
import org.ole.planet.myplanet.data.findCopyByField
import org.ole.planet.myplanet.model.RealmMyLibrary
import org.ole.planet.myplanet.model.RealmNews
import org.ole.planet.myplanet.model.RealmNews.Companion.createNews
import org.ole.planet.myplanet.model.RealmUserModel

class VoicesRepositoryImpl @Inject constructor(
    databaseService: DatabaseService,
    private val gson: Gson,
) : RealmRepository(databaseService), VoicesRepository {
    override suspend fun getLibraryResource(resourceId: String): RealmMyLibrary? {
        return withRealm { realm ->
            realm.findCopyByField(RealmMyLibrary::class.java, "_id", resourceId)
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

    override suspend fun getReplies(newsId: String?): List<RealmNews> {
        return withRealm { realm ->
            realm.where(RealmNews::class.java)
                .sort("time", Sort.DESCENDING)
                .equalTo("replyTo", newsId, Case.INSENSITIVE)
                .findAll()
                .let { realm.copyFromRealm(it) }
        }
    }

    override suspend fun deleteNews(newsId: String) {
        withRealm { realm ->
            realm.executeTransaction {
                deleteRepliesOf(newsId, it)
                it.where(RealmNews::class.java).equalTo("id", newsId).findAll().deleteAllFromRealm()
            }
        }
    }

    private fun deleteRepliesOf(newsId: String, realm: io.realm.Realm) {
        val replies = realm.where(RealmNews::class.java).equalTo("replyTo", newsId).findAll()
        replies.forEach { reply ->
            deleteRepliesOf(reply.id!!, realm)
            reply.deleteFromRealm()
        }
    }

    override suspend fun addLabel(newsId: String, label: String) {
        withRealm { realm ->
            realm.executeTransaction {
                val news = it.where(RealmNews::class.java).equalTo("id", newsId).findFirst()
                news?.labels?.add(label)
            }
        }
    }

    override suspend fun removeLabel(newsId: String, label: String) {
        withRealm { realm ->
            realm.executeTransaction {
                val news = it.where(RealmNews::class.java).equalTo("id", newsId).findFirst()
                news?.labels?.remove(label)
            }
        }
    }

    override suspend fun getCommunityVoiceDates(startTime: Long, endTime: Long, userId: String?): List<String> {
        return withRealm { realm ->
            val query = realm.where(RealmNews::class.java)
                .greaterThanOrEqualTo("time", startTime)
                .lessThanOrEqualTo("time", endTime)
            if (userId != null) query.equalTo("userId", userId)
            val results = query.findAll()
            results.filter { isCommunitySection(it) }
                .map { getDateFromTimestamp(it.time) }
                .distinct()
        }
    }

    private fun isCommunitySection(news: RealmNews): Boolean {
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

    private val dateFormat = object : ThreadLocal<java.text.SimpleDateFormat>() {
        override fun initialValue() = java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.getDefault())
    }

    private fun getDateFromTimestamp(timestamp: Long): String {
        return dateFormat.get()!!.format(java.util.Date(timestamp))
    }
}
