package org.ole.planet.myplanet.repository

import com.google.gson.JsonArray
import com.google.gson.JsonObject
import java.util.HashMap
import kotlinx.coroutines.flow.Flow
import org.ole.planet.myplanet.model.MyLibrary
import org.ole.planet.myplanet.model.News
import org.ole.planet.myplanet.model.UserEntity

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
    val imagesArray: JsonArray
)

interface VoicesRepository {
    suspend fun getNewsForUpload(): List<NewsUploadData>
    suspend fun markNewsUploaded(updates: List<NewsUpdateData>)
    suspend fun getLibraryResource(resourceId: String): MyLibrary?
    suspend fun getCommunityNews(userIdentifier: String): Flow<List<News>>
    suspend fun getNewsWithReplies(newsId: String): Pair<News?, List<News>>
    suspend fun getCommunityVisibleNews(userIdentifier: String): List<News>
    suspend fun getNewsByTeamId(teamId: String): List<News>
    suspend fun isAlreadyShared(chatId: String, viewInId: String): Boolean
    suspend fun createNews(map: HashMap<String?, String>, user: UserEntity?, imageList: List<String>?): News
    suspend fun createTeamNews(newsData: HashMap<String?, String>, user: UserEntity, imageList: List<String>?): Boolean
    suspend fun getDiscussionsByTeamIdFlow(teamId: String): Flow<List<News>>
    suspend fun shareNewsToCommunity(newsId: String, userId: String, planetCode: String, parentCode: String, teamName: String): Result<Unit>
    suspend fun updateTeamNotification(teamId: String, count: Int)
    suspend fun getFilteredNews(teamId: String): List<News>
    suspend fun getUserById(userId: String): UserEntity?
    suspend fun getReplyCount(newsId: String?): Int
    suspend fun deleteNews(newsId: String)
    suspend fun deletePost(newsId: String, teamName: String)
    suspend fun addLabel(newsId: String, label: String)
    suspend fun removeLabel(newsId: String, label: String)
    suspend fun getCommunityVoiceDates(startTime: Long, endTime: Long, userId: String?): List<String>
    suspend fun getNewsById(id: String): News?
    suspend fun postReply(message: String, news: News, currentUser: UserEntity, imageList: List<String>?)
    suspend fun editPost(newsId: String, message: String, imagesToRemove: Set<String>, newImages: List<String>?)
    suspend fun getPlanetNewsMessages(planetCode: String?): List<News>
    suspend fun insertNewsFromJson(doc: JsonObject)
    suspend fun insertNewsList(docs: List<JsonObject>)
    suspend fun getPrivateImageUrlsCreatedAfter(timestamp: Long): List<String>
}
