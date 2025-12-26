package org.ole.planet.myplanet.ui.voices

import androidx.lifecycle.ViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import org.ole.planet.myplanet.model.RealmVoices
import org.ole.planet.myplanet.repository.VoicesRepository

@HiltViewModel
class VoicesReplyViewModel @Inject constructor(
    private val voicesRepository: VoicesRepository,
) : ViewModel() {

    suspend fun getVoicesWithReplies(voicesId: String): Pair<RealmVoices?, List<RealmVoices>> {
        return voicesRepository.getVoicesWithReplies(voicesId)
    }
}
