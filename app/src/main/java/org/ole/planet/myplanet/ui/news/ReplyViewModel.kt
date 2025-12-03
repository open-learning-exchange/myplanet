package org.ole.planet.myplanet.ui.news

import androidx.lifecycle.ViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import org.ole.planet.myplanet.datamanager.DatabaseService
import org.ole.planet.myplanet.model.RealmNews
import org.ole.planet.myplanet.model.RealmUserModel
import org.ole.planet.myplanet.model.dto.NewsItem
import org.ole.planet.myplanet.repository.NewsRepository
import org.ole.planet.myplanet.service.UserProfileDbHandler

@HiltViewModel
class ReplyViewModel @Inject constructor(
    private val newsRepository: NewsRepository,
    private val databaseService: DatabaseService,
    private val userProfileDbHandler: UserProfileDbHandler,
) : ViewModel() {

    suspend fun getNewsWithReplies(newsId: String): Pair<NewsItem?, List<NewsItem>> {
        val (news, replies) = newsRepository.getNewsWithReplies(newsId)
        val user = userProfileDbHandler.userModel
        val mappedNews = news?.let { mapNews(it, user) }
        val mappedReplies = replies.map { mapNews(it, user) }
        return Pair(mappedNews, mappedReplies)
    }

    private fun mapNews(news: RealmNews, user: RealmUserModel?): NewsItem {
        return databaseService.withRealm { realm ->
            NewsMapper.map(news, realm, user, null, "")
        }
    }
}
