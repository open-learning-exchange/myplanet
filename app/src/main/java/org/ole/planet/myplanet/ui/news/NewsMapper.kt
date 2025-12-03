package org.ole.planet.myplanet.ui.news

import android.text.TextUtils
import com.google.gson.JsonArray
import com.google.gson.JsonObject
import io.realm.Realm
import org.ole.planet.myplanet.model.RealmMyTeam
import org.ole.planet.myplanet.model.RealmNews
import org.ole.planet.myplanet.model.RealmUserModel
import org.ole.planet.myplanet.model.dto.NewsItem
import org.ole.planet.myplanet.utilities.GsonUtils
import org.ole.planet.myplanet.utilities.JsonUtils

object NewsMapper {
    fun map(
        news: RealmNews,
        realm: Realm,
        currentUser: RealmUserModel?,
        teamId: String?,
        teamName: String
    ): NewsItem {
        val userModel = realm.where(RealmUserModel::class.java)
            .equalTo("id", news.userId)
            .findFirst()

        val canEdit = canEdit(news, currentUser, teamId, realm)
        val canDelete = canDelete(news, currentUser, teamId, realm)
        val canReply = canReply(currentUser)
        val canAddLabel = canAddLabel(news, currentUser, teamId, realm)
        val canShare = canShare(news, currentUser)

        val replies = realm.where(RealmNews::class.java)
            .equalTo("replyTo", news.id)
            .findAll()

        val imageUrls = news.imageUrls?.mapNotNull {
            try {
                val imgObject = GsonUtils.gson.fromJson(it, JsonObject::class.java)
                JsonUtils.getString("imageUrl", imgObject)
            } catch (e: Exception) {
                null
            }
        } ?: emptyList()

        val libraryImages = news.imagesArray.mapNotNull {
            val ob = it.asJsonObject
            JsonUtils.getString("resourceId", ob)
        }

        val sharedTeamName = extractSharedTeamName(news, teamName)

        return NewsItem(
            id = news.id ?: "",
            _id = news._id,
            message = news.message,
            time = news.time,
            userName = news.userName,
            userId = news.userId,
            isEdited = news.isEdited,
            sharedBy = news.sharedBy,
            imageUrls = imageUrls,
            libraryImages = libraryImages,
            labels = news.labels?.toList() ?: emptyList(),
            viewIn = news.viewIn,
            newsId = news.newsId,
            conversations = news.conversations,
            isCommunityNews = news.isCommunityNews,
            replyTo = news.replyTo,
            userImage = userModel?.userImage,
            userFullName = userModel?.getFullNameWithMiddleName()?.trim(),
            canEdit = canEdit,
            canDelete = canDelete,
            canReply = canReply,
            canAddLabel = canAddLabel,
            canShare = canShare,
            replyCount = replies.size,
            sharedTeamName = sharedTeamName,
            docType = news.docType,
            avatar = news.avatar,
            messagePlanetCode = news.messagePlanetCode,
            messageType = news.messageType,
            parentCode = news.parentCode,
            createdOn = news.createdOn,
            user = news.user,
            images = news.images,
            newsRev = news.newsRev,
            newsUser = news.newsUser,
            aiProvider = news.aiProvider,
            newsTitle = news.newsTitle,
            newsCreatedDate = news.newsCreatedDate,
            newsUpdatedDate = news.newsUpdatedDate,
            chat = news.chat,
            editedTime = news.editedTime
        )
    }

    private fun extractSharedTeamName(news: RealmNews, teamName: String): String {
        if (!TextUtils.isEmpty(news.viewIn)) {
            try {
                val ar = GsonUtils.gson.fromJson(news.viewIn, JsonArray::class.java)
                if (ar.size() > 1) {
                    val ob = ar[0].asJsonObject
                    if (ob.has("name") && !ob.get("name").isJsonNull) {
                        return ob.get("name").asString
                    }
                }
            } catch (e: Exception) {
                // Ignore if viewIn is not a valid JSON
            }
        }
        return ""
    }

    private fun isOwner(news: RealmNews, currentUser: RealmUserModel?): Boolean =
        news.userId == currentUser?._id

    private fun isSharedByCurrentUser(news: RealmNews, currentUser: RealmUserModel?): Boolean =
        news.sharedBy == currentUser?._id

    private fun isAdmin(currentUser: RealmUserModel?): Boolean =
        currentUser?.level.equals("admin", ignoreCase = true)

    private fun isTeamLeader(teamId: String?, currentUser: RealmUserModel?, realm: Realm): Boolean {
        if (teamId == null) return false
        val team = realm.where(RealmMyTeam::class.java)
            .equalTo("teamId", teamId)
            .equalTo("userId", currentUser?._id)
            .findFirst()
        return team?.isLeader == true
    }

    private fun canEdit(news: RealmNews, currentUser: RealmUserModel?, teamId: String?, realm: Realm): Boolean =
        (isOwner(news, currentUser) || isAdmin(currentUser) || isTeamLeader(teamId, currentUser, realm))

    private fun canDelete(news: RealmNews, currentUser: RealmUserModel?, teamId: String?, realm: Realm): Boolean =
        (isOwner(news, currentUser) || isSharedByCurrentUser(news, currentUser) || isAdmin(currentUser) || isTeamLeader(teamId, currentUser, realm))

    private fun canReply(currentUser: RealmUserModel?): Boolean =
        currentUser?.id?.startsWith("guest") == false

    private fun canAddLabel(news: RealmNews, currentUser: RealmUserModel?, teamId: String?, realm: Realm): Boolean =
        (isOwner(news, currentUser) || isTeamLeader(teamId, currentUser, realm))

    private fun canShare(news: RealmNews, currentUser: RealmUserModel?): Boolean =
        !news.isCommunityNews && currentUser?.id?.startsWith("guest") == false
}
