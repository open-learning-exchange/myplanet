package org.ole.planet.myplanet.ui.news

import androidx.lifecycle.ViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import org.ole.planet.myplanet.model.NewsItem
import org.ole.planet.myplanet.model.RealmNews
import org.ole.planet.myplanet.repository.NewsRepository

@HiltViewModel
class ReplyViewModel @Inject constructor(
    private val newsRepository: NewsRepository,
) : ViewModel() {

    suspend fun getNewsWithReplies(newsId: String): Pair<RealmNews?, List<RealmNews>> {
        return newsRepository.getNewsWithReplies(newsId)
    }

    suspend fun getNewsItemWithReplies(newsId: String): Pair<NewsItem?, List<NewsItem>> {
        return newsRepository.getNewsItemWithReplies(newsId)
    }

    suspend fun deleteNews(newsId: String) {
        newsRepository.deleteNews(newsId)
    }

    suspend fun addLabel(newsId: String, label: String) {
        newsRepository.addLabel(newsId, label)
    }

    suspend fun removeLabel(newsId: String, label: String) {
        newsRepository.removeLabel(newsId, label)
    }
}
