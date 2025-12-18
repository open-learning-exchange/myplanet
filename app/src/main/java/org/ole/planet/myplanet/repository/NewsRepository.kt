package org.ole.planet.myplanet.repository

import java.util.HashMap
import kotlinx.coroutines.flow.Flow
import org.ole.planet.myplanet.model.RealmMyLibrary
import org.ole.planet.myplanet.model.RealmNews
import org.ole.planet.myplanet.model.RealmUserModel

interface NewsRepository {
    suspend fun getLibraryResource(resourceId: String): RealmMyLibrary?
    suspend fun getCommunityNews(userIdentifier: String): Flow<List<RealmNews>>
    suspend fun getNewsWithReplies(newsId: String): Pair<RealmNews?, List<RealmNews>>
    suspend fun getCommunityVisibleNews(userIdentifier: String): List<RealmNews>
    suspend fun getNewsByTeamId(teamId: String): List<RealmNews>
    suspend fun createNews(map: HashMap<String?, String>, user: RealmUserModel?, images: List<String>?): RealmNews
    suspend fun getDiscussionsByTeamIdFlow(teamId: String): Flow<List<RealmNews>>
    suspend fun shareNewsToCommunity(newsId: String, userId: String, planetCode: String, parentCode: String, teamName: String): Result<Unit>
    suspend fun updateTeamNotification(teamId: String, count: Int)
    suspend fun getFilteredNews(teamId: String): List<RealmNews>
    suspend fun getReplies(newsId: String?): List<RealmNews>
    suspend fun deletePost(newsId: String, teamName: String)
    suspend fun editPost(newsId: String, message: String, images: List<String>?, imagesToRemove: List<String>?)
    suspend fun postReply(
        message: String,
        replyTo: String,
        viewableBy: String,
        viewableId: String,
        messageType: String,
        messagePlanetCode: String,
        viewIn: String,
        user: RealmUserModel?,
        images: List<String>?
    )
    suspend fun addLabel(newsId: String, label: String)
    suspend fun removeLabel(newsId: String, label: String)
}
