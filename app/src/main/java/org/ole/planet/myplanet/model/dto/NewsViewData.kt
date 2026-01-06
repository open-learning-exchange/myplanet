package org.ole.planet.myplanet.model.dto

import android.text.Spanned
import org.ole.planet.myplanet.model.RealmUserModel

data class NewsViewData(
    val id: String?,
    val userId: String?,
    val userName: String?,
    val userImage: String?,
    val message: Spanned?,
    val imageUrls: List<String>,
    val libraryImageUrls: List<Pair<String, String>>,
    val date: String?,
    val replyCount: Int,
    val isEdited: Boolean,
    val canEdit: Boolean,
    val canDelete: Boolean,
    val canReply: Boolean,
    val canAddLabel: Boolean,
    val canShare: Boolean,
    val labels: List<String>,
    val user: RealmUserModel?,
    val fromLogin: Boolean,
    val nonTeamMember: Boolean,
    val parentNews: NewsViewData? = null
)
