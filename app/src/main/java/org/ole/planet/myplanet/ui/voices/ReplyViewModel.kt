package org.ole.planet.myplanet.ui.voices

import androidx.lifecycle.ViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import org.ole.planet.myplanet.model.RealmNews
import org.ole.planet.myplanet.repository.VoicesRepository

@HiltViewModel
class ReplyViewModel @Inject constructor(
    private val voicesRepository: VoicesRepository,
) : ViewModel() {

    suspend fun getNewsWithReplies(newsId: String): Pair<RealmNews?, List<RealmNews>> {
        return voicesRepository.getNewsWithReplies(newsId)
    }
}
