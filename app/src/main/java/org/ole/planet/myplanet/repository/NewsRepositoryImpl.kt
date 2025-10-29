package org.ole.planet.myplanet.repository

import com.google.gson.Gson
import com.google.gson.JsonArray
import io.realm.Case
import io.realm.RealmList
import io.realm.Sort
import java.util.HashMap
import javax.inject.Inject
import org.ole.planet.myplanet.datamanager.DatabaseService
import org.ole.planet.myplanet.model.RealmNews
import org.ole.planet.myplanet.model.RealmNews.Companion.createNews
import org.ole.planet.myplanet.model.RealmUserModel

class NewsRepositoryImpl @Inject constructor(
    databaseService: DatabaseService,
    private val gson: Gson,
) : RealmRepository(databaseService), NewsRepository {

    override suspend fun getNewsWithReplies(newsId: String): Pair<RealmNews?, List<RealmNews>> {
        val news = findByField(RealmNews::class.java, "id", newsId)
        val replies = queryList(RealmNews::class.java) {
            equalTo("replyTo", newsId, Case.INSENSITIVE)
            sort("time", Sort.DESCENDING)
        }
        return news to replies
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

    override suspend fun createNews(
        map: HashMap<String?, String>,
        user: RealmUserModel?,
        imageUrls: List<String>?,
    ): RealmNews {
        return withRealmAsync { realm ->
            val realmImages = imageUrls?.let { urls ->
                RealmList<String>().apply { addAll(urls) }
            }
            val managedNews = createNews(map, realm, user, realmImages)
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
}
