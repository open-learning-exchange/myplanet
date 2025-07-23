package org.ole.planet.myplanet.model

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import io.realm.RealmList
import javax.inject.Inject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import org.ole.planet.myplanet.di.ChatRepository

@HiltViewModel
class ChatViewModel @Inject constructor(
    private val chatRepository: ChatRepository
) : ViewModel() {
    val selectedChatHistory: StateFlow<RealmList<Conversation>?> = chatRepository.selectedChatHistory.asStateFlow()
    val selectedId: StateFlow<String> = chatRepository.selectedId.asStateFlow()
    val selectedRev: StateFlow<String> = chatRepository.selectedRev.asStateFlow()
    val selectedAiProvider: StateFlow<String?> = chatRepository.selectedAiProvider.asStateFlow()

    fun setSelectedChatHistory(conversations: RealmList<Conversation>) {
        viewModelScope.launch(Dispatchers.IO) {
            chatRepository.selectedChatHistory.value = conversations
        }
    }

    fun setSelectedId(id: String) {
        viewModelScope.launch(Dispatchers.IO) { chatRepository.selectedId.value = id }
    }

    fun setSelectedRev(rev: String) {
        viewModelScope.launch(Dispatchers.IO) { chatRepository.selectedRev.value = rev }
    }

    fun setSelectedAiProvider(aiProvider: String?) {
        viewModelScope.launch(Dispatchers.IO) { chatRepository.selectedAiProvider.value = aiProvider }
    }
}

