package org.ole.planet.myplanet.ui.news

import android.content.Context
import com.google.gson.Gson
import com.google.gson.JsonArray
import com.google.gson.JsonObject
import io.realm.Case
import io.realm.Realm
import io.realm.Sort
import org.ole.planet.myplanet.model.ChatMessage
import org.ole.planet.myplanet.model.Conversation
import org.ole.planet.myplanet.model.RealmMyLibrary
import org.ole.planet.myplanet.model.RealmMyTeam
import org.ole.planet.myplanet.model.RealmNews
import org.ole.planet.myplanet.model.RealmUserModel
import org.ole.planet.myplanet.model.dto.NewsItem
import org.ole.planet.myplanet.utilities.Constants
import org.ole.planet.myplanet.utilities.GsonUtils
import org.ole.planet.myplanet.utilities.JsonUtils
import org.ole.planet.myplanet.utilities.TimeUtils
import java.io.File
import java.util.Locale

object NewsMapper {

    fun mapToNewsItem(
        context: Context,
        realm: Realm,
        news: RealmNews,
        currentUser: RealmUserModel?,
        teamId: String? = null
    ): NewsItem {
        val userModel = fetchUser(realm, news.userId)
        val replies = getReplies(realm, news.id)
        val replyCount = replies.size

        val isLeader = isTeamLeader(realm, teamId, currentUser)

        val imageUrls = mutableListOf<String>()
        val libraryImages = mutableListOf<String>()

        // Process images
        val newsImageUrls = news.imageUrls
        if (!newsImageUrls.isNullOrEmpty()) {
            newsImageUrls.forEach { imageUrl ->
                try {
                    val imgObject = GsonUtils.gson.fromJson(imageUrl, JsonObject::class.java)
                    val path = JsonUtils.getString("imageUrl", imgObject)
                    if (path.isNotEmpty()) {
                        imageUrls.add(path)
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
        }

        val imagesArray = news.imagesArray
        if (imagesArray.size() > 0) {
             for (i in 0 until imagesArray.size()) {
                val ob = imagesArray[i]?.asJsonObject
                val resourceId = JsonUtils.getString("resourceId", ob)
                val path = resolveLibraryImagePath(context, realm, resourceId)
                if (path != null) {
                    libraryImages.add(path)
                }
            }
        }

        // Parse conversations
        val conversationsList = mutableListOf<ChatMessage>()
        if (news.conversations?.isNotEmpty() == true) {
            try {
                val convs = GsonUtils.gson.fromJson(news.conversations, Array<Conversation>::class.java)
                convs?.forEach { conversation ->
                     val query = conversation.query
                    val response = conversation.response
                    if (query != null) {
                        conversationsList.add(ChatMessage(query, ChatMessage.QUERY))
                    }
                    if (response != null) {
                        conversationsList.add(ChatMessage(response, ChatMessage.RESPONSE, ChatMessage.RESPONSE_SOURCE_SHARED_VIEW_MODEL))
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }

        // Shared Team Name
        var sharedTeamName = ""
         if (!news.viewIn.isNullOrEmpty()) {
            try {
                val ar = GsonUtils.gson.fromJson(news.viewIn, JsonArray::class.java)
                if (ar.size() > 1) {
                    val ob = ar[0].asJsonObject
                    if (ob.has("name") && !ob.get("name").isJsonNull) {
                        sharedTeamName = ob.get("name").asString
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }

        val labels = mutableListOf<String>()
        news.labels?.forEach { labels.add(it) }

        // Permissions
        val isGuest = currentUser?.id?.startsWith("guest") == true
        val isOwner = news.userId == currentUser?._id
        val isSharedByCurrent = news.sharedBy == currentUser?._id
        val isAdmin = currentUser?.level.equals("admin", ignoreCase = true)

        val canEdit = isOwner || isAdmin || isLeader
        val canDelete = isOwner || isSharedByCurrent || isAdmin || isLeader
        val canReply = !isGuest
        val canAddLabel = isOwner || isLeader
        val canShare = !news.isCommunityNews && !isGuest

        val showSharedFrom = sharedTeamName.isNotEmpty() && teamId.isNullOrEmpty()
        val dateText = if (showSharedFrom) "${TimeUtils.formatDate(news.time)} | Shared from $sharedTeamName" else TimeUtils.formatDate(news.time)

        return NewsItem(
            id = news.id,
            message = news.message,
            time = news.time,
            dateText = dateText,
            userName = userModel?.name ?: news.userName,
            userImage = userModel?.userImage,
            userId = news.userId,
            sharedBy = news.sharedBy,
            isEdited = news.isEdited,
            imageUrls = imageUrls,
            libraryImages = libraryImages,
            replyCount = replyCount,
            labels = labels,
            conversations = conversationsList,
            sharedTeamName = sharedTeamName,
            viewIn = news.viewIn,
            isCommunityNews = news.isCommunityNews,
            imagesArray = news.images, // passing the raw json string just in case
            canEdit = canEdit,
            canDelete = canDelete,
            canReply = canReply,
            canShare = canShare,
            canAddLabel = canAddLabel,
            newsId = news.newsId,
            newsRev = news.newsRev,
            chat = news.chat
        )
    }

    private fun fetchUser(realm: Realm, userId: String?): RealmUserModel? {
        if (userId.isNullOrEmpty()) return null
        return realm.where(RealmUserModel::class.java).equalTo("id", userId).findFirst()
    }

    private fun getReplies(realm: Realm, newsId: String?): List<RealmNews> {
        if (newsId == null) return emptyList()
        return realm.where(RealmNews::class.java)
            .equalTo("replyTo", newsId, Case.INSENSITIVE)
            .findAll()
    }

    private fun isTeamLeader(realm: Realm, teamId: String?, currentUser: RealmUserModel?): Boolean {
        if (teamId == null || currentUser == null) return false
        val team = realm.where(RealmMyTeam::class.java)
            .equalTo("teamId", teamId)
            .equalTo("isLeader", true)
            .findFirst()
        return team?.userId == currentUser._id
    }

    private fun resolveLibraryImagePath(context: Context, realm: Realm, resourceId: String?): String? {
        if (resourceId == null) return null
        val library = realm.where(RealmMyLibrary::class.java)
            .equalTo("_id", resourceId)
            .findFirst() ?: return null

        val basePath = context.getExternalFilesDir(null) ?: return null
        val imageFile = File(basePath, "ole/${library.id}/${library.resourceLocalAddress}")
        return if (imageFile.exists()) imageFile.absolutePath else null
    }
}
