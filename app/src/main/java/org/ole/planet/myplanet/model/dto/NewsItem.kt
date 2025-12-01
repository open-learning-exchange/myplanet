package org.ole.planet.myplanet.model.dto

import org.ole.planet.myplanet.model.ChatMessage

data class NewsItem(
    val id: String?,
    val message: String?,
    val time: Long,
    val userName: String?,
    val userImage: String?,
    val userId: String?,
    val sharedBy: String?,
    val isEdited: Boolean,
    val imageUrls: List<String>?,
    val libraryImages: List<String>?,
    val replyCount: Int,
    val labels: List<String>?,
    val conversations: List<ChatMessage>?,
    val sharedTeamName: String?,
    val viewIn: String?,
    val isCommunityNews: Boolean,
    val imagesArray: String?,
    val canEdit: Boolean,
    val canDelete: Boolean,
    val canReply: Boolean,
    val canShare: Boolean,
    val canAddLabel: Boolean,
    val newsId: String?,
    val newsRev: String?,
    val chat: Boolean
)
