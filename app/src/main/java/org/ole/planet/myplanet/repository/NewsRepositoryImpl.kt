package org.ole.planet.myplanet.repository

import com.google.gson.Gson
import com.google.gson.JsonArray
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
import org.ole.planet.myplanet.model.RealmNews
import org.ole.planet.myplanet.model.RealmNews.Companion.createNews
import org.ole.planet.myplanet.model.RealmUserModel

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
}
