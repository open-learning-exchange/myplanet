package org.ole.planet.myplanet.ui.voices

import android.content.Context
import android.text.Spanned
import com.google.gson.JsonArray
import com.google.gson.JsonObject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.ole.planet.myplanet.model.RealmNews
import org.ole.planet.myplanet.model.RealmUserModel
import org.ole.planet.myplanet.model.dto.NewsViewData
import org.ole.planet.myplanet.repository.TeamsRepository
import org.ole.planet.myplanet.repository.UserRepository
import org.ole.planet.myplanet.repository.VoicesRepository
import org.ole.planet.myplanet.utilities.JsonUtils
import org.ole.planet.myplanet.utilities.Markdown
import org.ole.planet.myplanet.utilities.TimeUtils.formatDate
import java.io.File

object NewsMapper {
    suspend fun toNewsViewData(
        news: RealmNews,
        context: Context,
        currentUser: RealmUserModel?,
        teamsRepository: TeamsRepository,
        userRepository: UserRepository,
        voicesRepository: VoicesRepository,
        teamId: String? = null,
        fromLogin: Boolean = false,
        nonTeamMember: Boolean = false,
    ): NewsViewData = withContext(Dispatchers.IO) {
        val user = userRepository.getUserById(news.userId)
        val message = Markdown.setMarkdownText(news.message).toString()
        val date = formatDate(news.time)
        val (imageUrls, libraryImageUrls) = parseImageUrls(news, voicesRepository, context)
        val replyCount = voicesRepository.getReplies(news.id).size
        val canEdit = canEdit(news, currentUser, teamsRepository, teamId, fromLogin, nonTeamMember)
        val canDelete = canDelete(news, currentUser, teamsRepository, teamId, fromLogin, nonTeamMember)
        val canReply = canReply(fromLogin, nonTeamMember, currentUser)
        val canAddLabel = canAddLabel(news, currentUser, teamsRepository, teamId, fromLogin, nonTeamMember)
        val canShare = canShare(news, fromLogin, nonTeamMember, currentUser)

        NewsViewData(
            id = news.id,
            userId = news.userId,
            userName = user?.name ?: news.userName,
            userImage = user?.userImage,
            message = Markdown.setMarkdownText(message),
            imageUrls = imageUrls,
            libraryImageUrls = libraryImageUrls,
            date = date,
            replyCount = replyCount,
            isEdited = news.isEdited,
            canEdit = canEdit,
            canDelete = canDelete,
            canReply = canReply,
            canAddLabel = canAddLabel,
            canShare = canShare,
            labels = news.labels.toList(),
            user = user,
            fromLogin = fromLogin,
            nonTeamMember = nonTeamMember
        )
    }

    private suspend fun parseImageUrls(
        news: RealmNews,
        voicesRepository: VoicesRepository,
        context: Context
    ): Pair<List<String>, List<Pair<String, String>>> {
        val imageUrls = mutableListOf<String>()
        val libraryImageUrls = mutableListOf<Pair<String, String>>()

        news.imageUrls?.forEach {
            val imgObject = JsonUtils.gson.fromJson(it, JsonObject::class.java)
            val path = JsonUtils.getString("imageUrl", imgObject)
            imageUrls.add(path)
        }

        news.imagesArray?.forEach {
            val ob = it.asJsonObject
            val resourceId = JsonUtils.getString("resourceId", ob)
            val library = voicesRepository.getLibraryResource(resourceId)
            if (library != null) {
                val basePath = context.getExternalFilesDir(null)
                val imageFile = File(basePath, "ole/${library.id}/${library.resourceLocalAddress}")
                libraryImageUrls.add(Pair(resourceId, imageFile.absolutePath))
            }
        }
        return Pair(imageUrls, libraryImageUrls)
    }

    private fun isOwner(news: RealmNews?, currentUser: RealmUserModel?): Boolean = news?.userId == currentUser?._id
    private fun isSharedByCurrentUser(news: RealmNews?, currentUser: RealmUserModel?): Boolean = news?.sharedBy == currentUser?._id
    private fun isAdmin(currentUser: RealmUserModel?): Boolean = currentUser?.level.equals("admin", ignoreCase = true)
    private suspend fun isTeamLeader(teamsRepository: TeamsRepository, teamId: String?, userId: String?): Boolean {
        if (teamId.isNullOrEmpty() || userId.isNullOrEmpty()) return false
        return teamsRepository.isTeamLeader(teamId, userId)
    }

    private fun isLoggedInAndMember(fromLogin: Boolean, nonTeamMember: Boolean): Boolean = !fromLogin && !nonTeamMember

    private suspend fun canEdit(
        news: RealmNews?,
        currentUser: RealmUserModel?,
        teamsRepository: TeamsRepository,
        teamId: String?,
        fromLogin: Boolean,
        nonTeamMember: Boolean
    ): Boolean {
        return isLoggedInAndMember(fromLogin, nonTeamMember) && (isOwner(news, currentUser) || isAdmin(currentUser) || isTeamLeader(teamsRepository, teamId, currentUser?._id))
    }

    private suspend fun canDelete(
        news: RealmNews?,
        currentUser: RealmUserModel?,
        teamsRepository: TeamsRepository,
        teamId: String?,
        fromLogin: Boolean,
        nonTeamMember: Boolean
    ): Boolean {
        return isLoggedInAndMember(fromLogin, nonTeamMember) && (isOwner(news, currentUser) || isSharedByCurrentUser(news, currentUser) || isAdmin(currentUser) || isTeamLeader(teamsRepository, teamId, currentUser?._id))
    }

    private fun canReply(fromLogin: Boolean, nonTeamMember: Boolean, currentUser: RealmUserModel?): Boolean {
        return isLoggedInAndMember(fromLogin, nonTeamMember) && currentUser?.id?.startsWith("guest") == false
    }

    private suspend fun canAddLabel(
        news: RealmNews?,
        currentUser: RealmUserModel?,
        teamsRepository: TeamsRepository,
        teamId: String?,
        fromLogin: Boolean,
        nonTeamMember: Boolean
    ): Boolean {
        return isLoggedInAndMember(fromLogin, nonTeamMember) && (isOwner(news, currentUser) || isTeamLeader(teamsRepository, teamId, currentUser?._id))
    }

    private fun canShare(news: RealmNews?, fromLogin: Boolean, nonTeamMember: Boolean, currentUser: RealmUserModel?): Boolean {
        return isLoggedInAndMember(fromLogin, nonTeamMember) && news?.isCommunityNews == false && currentUser?.id?.startsWith("guest") == false
    }
}
