package org.ole.planet.myplanet.ui.news

import io.realm.Realm
import org.ole.planet.myplanet.model.RealmMyTeam
import org.ole.planet.myplanet.model.RealmNews
import org.ole.planet.myplanet.model.RealmUserModel

class NewsPermissionChecker(
    private val realm: Realm,
    private val currentUser: RealmUserModel?,
    private val sessionUser: RealmUserModel?,
    private val fromLogin: Boolean,
    private val nonTeamMember: Boolean,
    private val teamId: String?
) {
    fun isTeamLeader(): Boolean {
        if (teamId == null) return false
        val team = realm.where(RealmMyTeam::class.java)
            .equalTo("teamId", teamId)
            .equalTo("isLeader", true)
            .findFirst()
        return team?.userId == currentUser?._id
    }

    fun isGuestUser() = sessionUser?.id?.startsWith("guest") == true

    private fun isOwner(news: RealmNews?) = news?.userId == currentUser?._id

    private fun isSharedByCurrentUser(news: RealmNews?) = news?.sharedBy == currentUser?._id

    private fun isAdmin() = currentUser?.level.equals("admin", ignoreCase = true)

    private fun isLoggedInAndMember() = !fromLogin && !nonTeamMember

    fun canEdit(news: RealmNews?) =
        isLoggedInAndMember() && (isOwner(news) || isAdmin() || isTeamLeader())

    fun canDelete(news: RealmNews?) =
        isLoggedInAndMember() && (isOwner(news) || isSharedByCurrentUser(news) || isAdmin() || isTeamLeader())

    fun canReply() = isLoggedInAndMember() && !isGuestUser()

    fun canAddLabel(news: RealmNews?) =
        isLoggedInAndMember() && (isOwner(news) || isTeamLeader() || isAdmin())

    fun canShare(news: RealmNews?) =
        isLoggedInAndMember() && news?.isCommunityNews == false && !isGuestUser()
}

