package org.ole.planet.myplanet.ui.voices

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import org.ole.planet.myplanet.model.RealmNews
import org.ole.planet.myplanet.repository.VoicesRepository

@HiltViewModel
class ReplyViewModel @Inject constructor(
    private val voicesRepository: VoicesRepository,
) : ViewModel() {

    private val _newsState = MutableStateFlow<Pair<RealmNews?, List<RealmNews>>?>(null)
    val newsState: StateFlow<Pair<RealmNews?, List<RealmNews>>?> = _newsState.asStateFlow()

    fun getNewsWithReplies(newsId: String) {
        viewModelScope.launch {
            _newsState.value = voicesRepository.getNewsWithReplies(newsId)
        }
    }
}
