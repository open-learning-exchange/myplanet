package org.ole.planet.myplanet.utilities

import org.ole.planet.myplanet.model.NewsItem
import org.ole.planet.myplanet.model.RealmNews
import java.text.SimpleDateFormat
import java.util.*

object NewsMapper {
    private val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())

    fun fromRealm(realmNews: RealmNews): NewsItem {
        return NewsItem(
            id = realmNews.id,
            _id = realmNews._id,
            _rev = realmNews._rev,
            userId = realmNews.userId,
            user = realmNews.user,
            message = realmNews.message,
            docType = realmNews.docType,
            viewableBy = realmNews.viewableBy,
            viewableId = realmNews.viewableId,
            avatar = realmNews.avatar,
            replyTo = realmNews.replyTo,
            userName = realmNews.userName,
            messagePlanetCode = realmNews.messagePlanetCode,
            messageType = realmNews.messageType,
            updatedDate = realmNews.updatedDate,
            time = realmNews.time,
            createdOn = realmNews.createdOn,
            parentCode = realmNews.parentCode,
            imageUrls = realmNews.imageUrls?.toList(),
            images = realmNews.images,
            labels = realmNews.labels?.toList(),
            viewIn = realmNews.viewIn,
            newsId = realmNews.newsId,
            newsRev = realmNews.newsRev,
            newsUser = realmNews.newsUser,
            aiProvider = realmNews.aiProvider,
            newsTitle = realmNews.newsTitle,
            conversations = realmNews.conversations,
            newsCreatedDate = realmNews.newsCreatedDate,
            newsUpdatedDate = realmNews.newsUpdatedDate,
            chat = realmNews.chat,
            isEdited = realmNews.isEdited,
            editedTime = realmNews.editedTime,
            sharedBy = realmNews.sharedBy,
            formattedUpdatedDate = formatDate(realmNews.updatedDate),
            formattedTime = formatDate(realmNews.time),
            formattedNewsCreatedDate = formatDate(realmNews.newsCreatedDate),
            formattedNewsUpdatedDate = formatDate(realmNews.newsUpdatedDate),
            isCommunityNews = realmNews.isCommunityNews
        )
    }

    private fun formatDate(dateInMillis: Long): String {
        return if (dateInMillis > 0) {
            dateFormat.format(Date(dateInMillis))
        } else {
            ""
        }
    }
}
