package org.ole.planet.myplanet.ui.voices

import org.ole.planet.myplanet.model.News
import org.ole.planet.myplanet.model.UserEntity

interface VoicesEditActions {
    suspend fun editPost(newsId: String, message: String, imagesToRemove: Set<String>, newImages: List<String>?): News?
    suspend fun postReply(message: String, news: News, currentUser: UserEntity, imageList: List<String>?)
    suspend fun getNewsById(id: String): News?
}
