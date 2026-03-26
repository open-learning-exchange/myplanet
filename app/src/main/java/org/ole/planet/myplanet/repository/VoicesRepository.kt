package org.ole.planet.myplanet.repository

import com.google.gson.JsonObject
import java.util.HashMap
import kotlinx.coroutines.flow.Flow
import org.ole.planet.myplanet.model.RealmMyLibrary
import org.ole.planet.myplanet.model.RealmNews
import org.ole.planet.myplanet.model.RealmUser

data class NewsUploadData(
    val id: String?,
    val _id: String?,
    val message: String?,
    val imageUrls: List<String>,
    val newsJson: JsonObject
)

data class NewsUpdateData(
    val id: String?,
    val _id: String?,
    val _rev: String?,
    val imagesArray: com.google.gson.JsonArray
)

interface VoicesRepository {
    suspend fun getNewsForUpload(serializeNews: (RealmNews) -> JsonObject): List<NewsUploadData>
    suspend fun markNewsUploaded(updates: List<NewsUpdateData>)
    suspend fun getLibraryResource(resourceId: String): RealmMyLibrary?
    suspend fun getCommunityNews(userIdentifier: String): Flow<List<RealmNews>>
    suspend fun getNewsWithReplies(newsId: String): Pair<RealmNews?, List<RealmNews>>
    suspend fun getCommunityVisibleNews(userIdentifier: String): List<RealmNews>
    suspend fun getNewsByTeamId(teamId: String): List<RealmNews>
    suspend fun createNews(map: HashMap<String?, String>, user: RealmUser?, imageList: List<String>?): RealmNews
    suspend fun createTeamNews(newsData: HashMap<String?, String>, user: RealmUser, imageList: List<String>?): Boolean
    suspend fun getDiscussionsByTeamIdFlow(teamId: String): Flow<List<RealmNews>>
    suspend fun shareNewsToCommunity(newsId: String, userId: String, planetCode: String, parentCode: String, teamName: String): Result<Unit>
    suspend fun updateTeamNotification(teamId: String, count: Int)
    suspend fun getFilteredNews(teamId: String): List<RealmNews>
    suspend fun getReplies(newsId: String?): List<RealmNews>
    suspend fun getReplyCount(newsId: String?): Int
    suspend fun deleteNews(newsId: String)
    suspend fun deletePost(newsId: String, teamName: String)
    suspend fun addLabel(newsId: String, label: String)
    suspend fun removeLabel(newsId: String, label: String)
    suspend fun getCommunityVoiceDates(startTime: Long, endTime: Long, userId: String?): List<String>
    suspend fun getNewsById(id: String): RealmNews?
    suspend fun postReply(message: String, news: RealmNews, currentUser: RealmUser, imageList: List<String>?)
    suspend fun editPost(newsId: String, message: String, imagesToRemove: Set<String>, newImages: List<String>?)
    suspend fun getPlanetNewsMessages(planetCode: String?): List<RealmNews>
    suspend fun insertNewsFromJson(doc: com.google.gson.JsonObject)
    suspend fun insertNewsList(docs: List<com.google.gson.JsonObject>)
    fun serializeNews(news: RealmNews): com.google.gson.JsonObject
}
